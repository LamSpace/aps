package io.github.lamspace.generator;

import io.github.lamspace.Callback;
import io.github.lamspace.ClassFilter;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a proxy subclass of {@code targetClass} using ASM.
 * <p>
 * For each non-final, non-static instance method:
 * <ul>
 *   <li>Generates an override that delegates to {@link Callback#intercept}</li>
 *   <li>Pre-computes a {@code MethodHandle} bound to the superclass method</li>
 *   <li>Stores both the {@code Method} and {@code MethodHandle} in static fields</li>
 * </ul>
 */
public class ClassGenerator {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final Class<?> targetClass;
    private final ClassFilter filter;
    private final Object[] constructorArgs;

    /**
     * Creates a generator for the given target class with no constructor arguments.
     *
     * @param targetClass the class to proxy
     * @param filter      method filter; {@code null} means all methods are intercepted
     */
    public ClassGenerator(Class<?> targetClass, ClassFilter filter) {
        this(targetClass, filter, new Object[0]);
    }

    /**
     * Creates a generator for the given target class with constructor arguments.
     *
     * @param targetClass     the class to proxy
     * @param filter          method filter; {@code null} means all methods are intercepted
     * @param constructorArgs arguments to pass to the superclass constructor;
     *                        empty array for the default no-arg constructor
     */
    public ClassGenerator(Class<?> targetClass, ClassFilter filter,
                          Object... constructorArgs) {
        this.targetClass = targetClass;
        this.filter = filter;
        this.constructorArgs = (constructorArgs == null) ? new Object[0] : constructorArgs;
    }

    /**
     * Returns the parameter types for the generated class constructor.
     *
     * @return an array starting with {@link Callback}{@code .class} followed by
     * the types of the constructor arguments
     */
    public Class<?>[] constructorArgs() {
        Class<?>[] all = new Class<?>[1 + constructorArgs.length];
        all[0] = Callback.class;
        for (int i = 0; i < constructorArgs.length; i++) {
            Object arg = constructorArgs[i];
            all[i + 1] = (arg != null) ? arg.getClass() : Object.class;
        }
        return all;
    }

    /**
     * Generates the subclass bytecode. The class is placed in the same runtime
     * package as the target class so that {@code defineHiddenClass} can load it.
     *
     * @return valid JVM classfile bytes
     */
    public byte[] generate() {
        String targetInternal = Type.getInternalName(targetClass);
        String packagePrefix = targetInternal.contains("/")
                ? targetInternal.substring(0, targetInternal.lastIndexOf('/') + 1)
                : "";
        String simpleName = targetInternal.substring(targetInternal.lastIndexOf('/') + 1);
        String generatedInternal = packagePrefix + simpleName + "$$APS$$" + COUNTER.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                generatedInternal, null, targetInternal, null);

        // -- Callback field --
        String callbackDesc = Type.getDescriptor(Callback.class);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "_callback", callbackDesc, null, null);

        // -- Constructor: stores callback, delegates to super() --
        generateConstructor(cw, generatedInternal, targetInternal, callbackDesc);

        // -- Method overrides (populates ClinitRegistry + static fields) --
        MethodDispatcher.dispatchMethods(cw, targetClass, generatedInternal, filter);

        // -- <clinit> initializer --
        generateClinit(cw, generatedInternal);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateClinit(ClassWriter cw, String generatedInternal) {
        List<ClinitRegistry.Entry> entries = ClinitRegistry.drain();
        if (entries.isEmpty()) {
            return;
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>", "()V", null, null);
        mv.visitCode();

        // Get a Lookup for the generated class itself
        //   Lookup lookup = MethodHandles.lookup();
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "java/lang/invoke/MethodHandles",
                "lookup",
                "()Ljava/lang/invoke/MethodHandles$Lookup;",
                false);
        int lookupSlot = 0; // first local variable after <clinit> entry
        mv.visitVarInsn(Opcodes.ASTORE, lookupSlot);

        for (ClinitRegistry.Entry entry : entries) {
            Method method = entry.method();
            String methodField = entry.methodFieldName();
            String handleField = entry.handleFieldName();
            String targetInternal = Type.getInternalName(entry.targetClass());

            // 1. Store the Method object
            mv.visitLdcInsn(Type.getType("L" + targetInternal + ";"));
            mv.visitLdcInsn(method.getName());

            Class<?>[] paramTypes = method.getParameterTypes();
            BytecodeUtils.pushInt(mv, paramTypes.length);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitInsn(Opcodes.DUP);
                BytecodeUtils.pushInt(mv, i);
                BytecodeUtils.pushClassConstant(mv, paramTypes[i]);
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class",
                    "getDeclaredMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)Ljava/lang/reflect/Method;",
                    false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, generatedInternal,
                    methodField, "Ljava/lang/reflect/Method;");

            // 2. Store the MethodHandle using Lookup.findSpecial
            //   lookup.findSpecial(TargetClass.class, "name", methodType, GeneratedClass.class)
            mv.visitVarInsn(Opcodes.ALOAD, lookupSlot);
            mv.visitLdcInsn(Type.getType("L" + targetInternal + ";"));
            mv.visitLdcInsn(method.getName());

            // Build MethodType: MethodType.methodType(returnType, paramTypes...)
            Class<?> returnType = method.getReturnType();
            BytecodeUtils.pushClassConstant(mv, returnType);
            if (paramTypes.length == 0) {
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "java/lang/invoke/MethodType",
                        "methodType",
                        "(Ljava/lang/Class;)Ljava/lang/invoke/MethodType;",
                        false);
            } else {
                // Build Class[] for param types
                BytecodeUtils.pushInt(mv, paramTypes.length);
                mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
                for (int i = 0; i < paramTypes.length; i++) {
                    mv.visitInsn(Opcodes.DUP);
                    BytecodeUtils.pushInt(mv, i);
                    BytecodeUtils.pushClassConstant(mv, paramTypes[i]);
                    mv.visitInsn(Opcodes.AASTORE);
                }
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "java/lang/invoke/MethodType",
                        "methodType",
                        "(Ljava/lang/Class;[Ljava/lang/Class;)Ljava/lang/invoke/MethodType;",
                        false);
            }
            mv.visitLdcInsn(Type.getType("L" + generatedInternal + ";"));
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandles$Lookup",
                    "findSpecial",
                    "(Ljava/lang/Class;Ljava/lang/String;"
                            + "Ljava/lang/invoke/MethodType;"
                            + "Ljava/lang/Class;)Ljava/lang/invoke/MethodHandle;",
                    false);

            // Pre-compute asSpreader so per-invocation hot path avoids
            // MethodHandle allocation.
            mv.visitLdcInsn(Type.getType("[Ljava/lang/Object;"));
            BytecodeUtils.pushInt(mv, method.getParameterTypes().length);
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/lang/invoke/MethodHandle",
                    "asSpreader",
                    "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;",
                    false);

            mv.visitFieldInsn(Opcodes.PUTSTATIC, generatedInternal,
                    handleField, "Ljava/lang/invoke/MethodHandle;");
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateConstructor(ClassWriter cw, String generatedInternal,
                                     String targetInternal, String callbackDesc) {
        if (constructorArgs.length == 0) {
            generateNoArgConstructor(cw, generatedInternal, targetInternal, callbackDesc);
        } else {
            generateArgConstructor(cw, generatedInternal, targetInternal, callbackDesc);
        }
    }

    private void generateNoArgConstructor(ClassWriter cw, String generatedInternal,
                                          String targetInternal, String callbackDesc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(" + callbackDesc + ")V", null, null);
        mv.visitCode();
        // super()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, targetInternal, "<init>", "()V", false);
        // this._callback = callback
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal, "_callback", callbackDesc);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }

    private void generateArgConstructor(ClassWriter cw, String generatedInternal,
                                        String targetInternal, String callbackDesc) {
        // Build descriptor
        StringBuilder descBuilder = new StringBuilder("(");
        descBuilder.append(callbackDesc); // Callback param
        Class<?>[] superParamTypes = new Class<?>[constructorArgs.length];
        for (int i = 0; i < constructorArgs.length; i++) {
            Object arg = constructorArgs[i];
            Class<?> argType = (arg != null) ? arg.getClass() : Object.class;
            superParamTypes[i] = argType;
            descBuilder.append(Type.getDescriptor(argType));
        }
        descBuilder.append(")V");
        String desc = descBuilder.toString();

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", desc, null, null);
        mv.visitCode();

        // super(constructorArgs...)
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        int superArgSlot = 2; // skip this (slot 0) and Callback (slot 1)
        for (int i = 0; i < constructorArgs.length; i++) {
            Class<?> argType = superParamTypes[i];
            if (argType == int.class || argType == Integer.class
                    || argType == boolean.class || argType == Boolean.class
                    || argType == byte.class || argType == Byte.class
                    || argType == char.class || argType == Character.class
                    || argType == short.class || argType == Short.class) {
                mv.visitVarInsn(Opcodes.ILOAD, superArgSlot);
                superArgSlot++;
            } else if (argType == long.class || argType == Long.class) {
                mv.visitVarInsn(Opcodes.LLOAD, superArgSlot);
                superArgSlot += 2;
            } else if (argType == float.class || argType == Float.class) {
                mv.visitVarInsn(Opcodes.FLOAD, superArgSlot);
                superArgSlot++;
            } else if (argType == double.class || argType == Double.class) {
                mv.visitVarInsn(Opcodes.DLOAD, superArgSlot);
                superArgSlot += 2;
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, superArgSlot);
                superArgSlot++;
            }
        }

        String superDesc = Type.getConstructorDescriptor(
                findConstructor(targetClass, superParamTypes));
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, targetInternal, "<init>", superDesc, false);

        // this._callback = callback (always in slot 1)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal, "_callback", callbackDesc);

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private Constructor<?> findConstructor(Class<?> clazz, Class<?>[] paramTypes) {
        outer:
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] existing = ctor.getParameterTypes();
            if (existing.length == paramTypes.length) {
                for (int i = 0; i < existing.length; i++) {
                    if (!wrap(existing[i]).isAssignableFrom(wrap(paramTypes[i]))) {
                        continue outer;
                    }
                }
                return ctor;
            }
        }
        throw new IllegalArgumentException(
                "No matching constructor found on " + clazz.getName());
    }

    private static Class<?> wrap(Class<?> type) {
        if (type == int.class) return Integer.class;
        if (type == long.class) return Long.class;
        if (type == double.class) return Double.class;
        if (type == float.class) return Float.class;
        if (type == boolean.class) return Boolean.class;
        if (type == byte.class) return Byte.class;
        if (type == char.class) return Character.class;
        if (type == short.class) return Short.class;
        return type;
    }
}

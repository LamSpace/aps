package io.github.lamspace.generator;

import io.github.lamspace.ClassFilter;
import org.objectweb.asm.*;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates method override bytecode for a proxy subclass.
 * Each override marshals arguments into an {@code Object[]}, calls
 * {@code Callback.intercept(...)}, and unboxes the return value.
 * Superclass invocations are pre-bound as static MethodHandle fields.
 */
public class MethodDispatcher {

    private static final String CALLBACK_FIELD = "_callback";

    private MethodDispatcher() {
    }

    /**
     * Generates method overrides, static Method/MethodHandle fields,
     * and registers clinit entries for all proxyable methods on the target class.
     *
     * @param cw                the ClassWriter for the generated subclass
     * @param targetClass       the class being proxied
     * @param generatedInternal ASM internal name of the generated class
     * @param filter            if non-null, only methods accepted by the filter
     *                          are routed through the Callback; others call
     *                          super directly
     * @return list of method names for which dispatchers were generated
     */
    public static List<String> dispatchMethods(ClassWriter cw, Class<?> targetClass,
                                                String generatedInternal,
                                                ClassFilter filter) {
        List<String> dispatchedMethods = new ArrayList<>();

        for (Method method : targetClass.getDeclaredMethods()) {
            int mods = method.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)
                    || Modifier.isPrivate(mods)) {
                continue;
            }

            boolean shouldIntercept = (filter == null) || filter.accept(method);

            String suffix = "$" + dispatchedMethods.size();
            String methodFieldName = "_method$" + method.getName() + suffix;
            String handleFieldName = "_handle$" + method.getName() + suffix;

            addStaticField(cw, methodFieldName, "Ljava/lang/reflect/Method;");
            addStaticField(cw, handleFieldName, "Ljava/lang/invoke/MethodHandle;");

            ClinitRegistry.register(targetClass, method, generatedInternal,
                    methodFieldName, handleFieldName);

            generateOverride(cw, method, generatedInternal, shouldIntercept,
                    methodFieldName, handleFieldName);

            dispatchedMethods.add(method.getName());
        }

        return dispatchedMethods;
    }

    private static void addStaticField(ClassWriter cw, String name, String desc) {
        var fv = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                name, desc, null, null);
        fv.visitEnd();
    }

    private static void generateOverride(ClassWriter cw, Method method,
                                          String generatedInternal,
                                          boolean shouldIntercept,
                                          String methodFieldName,
                                          String handleFieldName) {
        String name = method.getName();
        String desc = Type.getMethodDescriptor(method);
        String targetInternal = Type.getInternalName(method.getDeclaringClass());

        Class<?>[] exceptions = method.getExceptionTypes();
        String[] exceptionNames = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
            exceptionNames[i] = Type.getInternalName(exceptions[i]);
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc,
                null, exceptionNames);
        mv.visitCode();

        if (!shouldIntercept) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            loadArguments(mv, method);
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    targetInternal, name, desc, false);
            Class<?> returnType = method.getReturnType();
            if (returnType == void.class) {
                mv.visitInsn(Opcodes.RETURN);
            } else if (returnType.isPrimitive()) {
                mv.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
            } else {
                mv.visitInsn(Opcodes.ARETURN);
            }
            mv.visitMaxs(0, 0);
            mv.visitEnd();
            return;
        }

        // -- Try block: bind handle + call callback --
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchRuntime = new Label();
        Label catchError = new Label();
        Label catchChecked = new Label();

        mv.visitTryCatchBlock(tryStart, tryEnd, catchRuntime, "java/lang/RuntimeException");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchError, "java/lang/Error");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchChecked, "java/lang/Exception");

        mv.visitLabel(tryStart);

        // Calculate total slots used by method parameters
        int totalParamSlots = 0;
        for (Class<?> paramType : method.getParameterTypes()) {
            totalParamSlots += (paramType == double.class || paramType == long.class) ? 2 : 1;
        }

        // 1. Bind the handle: _handle$X.bindTo(this)
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal,
                handleFieldName, "Ljava/lang/invoke/MethodHandle;");
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandle",
                "bindTo",
                "(Ljava/lang/Object;)Ljava/lang/invoke/MethodHandle;",
                false);

        // 2. Spread: bound.asSpreader(Object[].class, paramCount)
        Class<?>[] paramTypes = method.getParameterTypes();
        mv.visitLdcInsn(Type.getType("[Ljava/lang/Object;"));
        pushInt(mv, paramTypes.length);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/invoke/MethodHandle",
                "asSpreader",
                "(Ljava/lang/Class;I)Ljava/lang/invoke/MethodHandle;",
                false);

        // Store spread handle in a temp local variable
        int spreadHandleSlot = totalParamSlots + 1; // after 'this' + all params
        mv.visitVarInsn(Opcodes.ASTORE, spreadHandleSlot);
        int exceptionSlot = spreadHandleSlot + 1; // next available slot for caught exception

        // 3. Load callback
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                CALLBACK_FIELD, "Lio/github/lamspace/Callback;");

        // Arg 1: proxy = this
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // Arg 2: _method$X
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal,
                methodFieldName, "Ljava/lang/reflect/Method;");

        // Arg 3: spread handle
        mv.visitVarInsn(Opcodes.ALOAD, spreadHandleSlot);

        // Arg 4: new Object[] { arg0, arg1, ... }
        pushInt(mv, paramTypes.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

        int slot = 1;
        for (int i = 0; i < paramTypes.length; i++) {
            mv.visitInsn(Opcodes.DUP);
            pushInt(mv, i);
            Class<?> type = paramTypes[i];
            if (type.isPrimitive()) {
                mv.visitVarInsn(loadOpcode(type), slot);
                boxPrimitive(mv, type);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
            }
            mv.visitInsn(Opcodes.AASTORE);
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }

        // 4. Call callback.intercept
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "io/github/lamspace/Callback",
                "intercept",
                "(Ljava/lang/Object;Ljava/lang/reflect/Method;"
                        + "Ljava/lang/invoke/MethodHandle;"
                        + "[Ljava/lang/Object;)Ljava/lang/Object;",
                true);

        // 5. Unbox return value
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        } else if (returnType.isPrimitive()) {
            unboxPrimitive(mv, returnType);
            mv.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
        } else {
            mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(returnType));
            mv.visitInsn(Opcodes.ARETURN);
        }

        mv.visitLabel(tryEnd);

        // -- Catch RuntimeExceptions: rethrow directly --
        mv.visitLabel(catchRuntime);
        mv.visitInsn(Opcodes.ATHROW);

        // -- Catch Errors: rethrow directly --
        mv.visitLabel(catchError);
        mv.visitInsn(Opcodes.ATHROW);

        // -- Catch checked Exceptions: wrap in UndeclaredThrowableException --
        mv.visitLabel(catchChecked);
        // Store caught exception
        mv.visitVarInsn(Opcodes.ASTORE, exceptionSlot);
        // new UndeclaredThrowableException(caught)
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/reflect/UndeclaredThrowableException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, exceptionSlot);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/reflect/UndeclaredThrowableException",
                "<init>",
                "(Ljava/lang/Throwable;)V",
                false);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    // -- Primitive boxing/unboxing helpers --

    private static void loadArguments(MethodVisitor mv, Method method) {
        int slot = 1;
        for (Class<?> type : method.getParameterTypes()) {
            mv.visitVarInsn(loadOpcode(type), slot);
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }
    }

    private static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    private static int loadOpcode(Class<?> type) {
        if (type == double.class) return Opcodes.DLOAD;
        if (type == float.class) return Opcodes.FLOAD;
        if (type == long.class) return Opcodes.LLOAD;
        if (type == int.class || type == boolean.class || type == byte.class
                || type == char.class || type == short.class)
            return Opcodes.ILOAD;
        return Opcodes.ALOAD;
    }

    private static void boxPrimitive(MethodVisitor mv, Class<?> type) {
        String wrapper;
        String desc;
        if (type == boolean.class) {
            wrapper = "java/lang/Boolean"; desc = "(Z)Ljava/lang/Boolean;";
        } else if (type == byte.class) {
            wrapper = "java/lang/Byte"; desc = "(B)Ljava/lang/Byte;";
        } else if (type == char.class) {
            wrapper = "java/lang/Character"; desc = "(C)Ljava/lang/Character;";
        } else if (type == short.class) {
            wrapper = "java/lang/Short"; desc = "(S)Ljava/lang/Short;";
        } else if (type == int.class) {
            wrapper = "java/lang/Integer"; desc = "(I)Ljava/lang/Integer;";
        } else if (type == float.class) {
            wrapper = "java/lang/Float"; desc = "(F)Ljava/lang/Float;";
        } else if (type == long.class) {
            wrapper = "java/lang/Long"; desc = "(J)Ljava/lang/Long;";
        } else if (type == double.class) {
            wrapper = "java/lang/Double"; desc = "(D)Ljava/lang/Double;";
        } else {
            return;
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf", desc, false);
    }

    private static void unboxPrimitive(MethodVisitor mv, Class<?> type) {
        String wrapper;
        String unboxMethod;
        String desc;
        if (type == boolean.class) {
            wrapper = "java/lang/Boolean"; unboxMethod = "booleanValue"; desc = "()Z";
        } else if (type == byte.class) {
            wrapper = "java/lang/Byte"; unboxMethod = "byteValue"; desc = "()B";
        } else if (type == char.class) {
            wrapper = "java/lang/Character"; unboxMethod = "charValue"; desc = "()C";
        } else if (type == short.class) {
            wrapper = "java/lang/Short"; unboxMethod = "shortValue"; desc = "()S";
        } else if (type == int.class) {
            wrapper = "java/lang/Integer"; unboxMethod = "intValue"; desc = "()I";
        } else if (type == float.class) {
            wrapper = "java/lang/Float"; unboxMethod = "floatValue"; desc = "()F";
        } else if (type == long.class) {
            wrapper = "java/lang/Long"; unboxMethod = "longValue"; desc = "()J";
        } else if (type == double.class) {
            wrapper = "java/lang/Double"; unboxMethod = "doubleValue"; desc = "()D";
        } else {
            return;
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, unboxMethod, desc, false);
    }
}

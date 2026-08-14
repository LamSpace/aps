/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace.generator;

import io.github.lamspace.Interceptor;
import io.github.lamspace.MethodMapping;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a proxy subclass of {@code targetClass} using ASM.
 *
 * <p>Stores one instance field per distinct Interceptor (deduped by
 * reference equality). Each method override directly {@code GETFIELD}s
 * its assigned field — no array indirection.
 *
 * <p>The generated class implements {@code DispatchTarget} for
 * hashCode-based super-method dispatch.
 */
public class ClassGenerator {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final Class<?> targetClass;
    private final Interceptor[] interceptors;
    private final MethodMapping mapping;
    private final Object[] constructorArgs;

    /**
     * Creates a generator for the given target class with no constructor
     * arguments.
     *
     * @param targetClass  the class to proxy
     * @param interceptors deduped interceptor instances
     * @param mapping      method → interceptor index mapping
     */
    public ClassGenerator(Class<?> targetClass, Interceptor[] interceptors,
                          MethodMapping mapping) {
        this(targetClass, interceptors, mapping, new Object[0]);
    }

    /**
     * Creates a generator for the given target class with constructor
     * arguments.
     *
     * @param targetClass     the class to proxy
     * @param interceptors    deduped interceptor instances
     * @param mapping         method → interceptor index mapping
     * @param constructorArgs arguments to pass to the superclass constructor
     */
    public ClassGenerator(Class<?> targetClass, Interceptor[] interceptors,
                          MethodMapping mapping,
                          Object... constructorArgs) {
        this.targetClass = targetClass;
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
        this.constructorArgs = (constructorArgs == null)
                ? new Object[0] : constructorArgs;
    }

    /**
     * Returns the parameter types for the generated class constructor.
     *
     * @return an array of Interceptor.class (one per distinct interceptor)
     * followed by the types of the constructor arguments
     */
    public Class<?>[] constructorArgs() {
        Class<?>[] all = new Class<?>[interceptors.length
                + constructorArgs.length];
        for (int i = 0; i < interceptors.length; i++) {
            all[i] = Interceptor.class;
        }
        for (int i = 0; i < constructorArgs.length; i++) {
            Object arg = constructorArgs[i];
            all[interceptors.length + i] = (arg != null)
                    ? arg.getClass() : Object.class;
        }
        return all;
    }

    /**
     * Generates the subclass bytecode. The class is placed in the same
     * runtime package as the target class.
     *
     * @return valid JVM classfile bytes
     */
    public byte[] generate() {
        String targetInternal = Type.getInternalName(targetClass);
        String packagePrefix = targetInternal.contains("/")
                ? targetInternal.substring(0,
                targetInternal.lastIndexOf('/') + 1)
                : "";
        String simpleName = targetInternal.substring(
                targetInternal.lastIndexOf('/') + 1);
        String generatedInternal = packagePrefix + simpleName
                + "$$AcceleratedProxy$$" + COUNTER.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Extends targetClass, implements DispatchTarget
        String[] interfaces = {"io/github/lamspace/DispatchTarget"};
        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                generatedInternal, null, targetInternal, interfaces);

        // -- Interceptor fields (one per distinct interceptor) --
        String interceptorDesc = Type.getDescriptor(Interceptor.class);
        for (int i = 0; i < interceptors.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    "_interceptor$" + i, interceptorDesc, null, null);
        }

        // -- Constructor: stores interceptors, delegates to super() --
        generateConstructor(cw, generatedInternal, targetInternal,
                interceptorDesc);

        // -- Method overrides (populates ClinitRegistry + static fields) --
        ClinitRegistry registry = new ClinitRegistry();
        MethodDispatcher.dispatchMethods(cw, targetClass,
                generatedInternal, mapping, interceptors.length,
                registry);

        // -- Drain ClinitRegistry entries before dispatch and clinit --
        List<ClinitRegistry.Entry> entries = registry.drain();

        // -- dispatch(Method, Object[]) --
        List<Method> methods = new ArrayList<>();
        for (ClinitRegistry.Entry entry : entries) {
            methods.add(entry.method());
        }
        Map<Method, Integer> hashMap =
                DispatchGenerator.resolveHashes(methods);
        List<MethodInfo> infos = new ArrayList<>();
        for (ClinitRegistry.Entry entry : entries) {
            infos.add(new MethodInfo(entry.method(),
                    entry.methodFieldName(),
                    hashMap.get(entry.method())));
        }
        DispatchGenerator.generateDispatch(cw, generatedInternal,
                targetInternal, null, infos, true);

        // -- <clinit> initializer --
        generateClinit(cw, generatedInternal, entries);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateClinit(ClassWriter cw, String generatedInternal,
                                List<ClinitRegistry.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC,
                "<clinit>", "()V", null, null);
        mv.visitCode();

        for (ClinitRegistry.Entry entry : entries) {
            Method method = entry.method();
            String methodField = entry.methodFieldName();
            String targetInternal = Type.getInternalName(
                    entry.targetClass());

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
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                    "java/lang/Class",
                    "getDeclaredMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)"
                            + "Ljava/lang/reflect/Method;",
                    false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, generatedInternal,
                    methodField, "Ljava/lang/reflect/Method;");
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateConstructor(ClassWriter cw,
                                     String generatedInternal,
                                     String targetInternal,
                                     String interceptorDesc) {
        // Build descriptor: (LInterceptor;...LInterceptor; [superArgs])V
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < interceptors.length; i++) {
            desc.append(interceptorDesc);
        }
        Class<?>[] superParamTypes = new Class<?>[constructorArgs.length];
        for (int i = 0; i < constructorArgs.length; i++) {
            Object arg = constructorArgs[i];
            Class<?> argType = (arg != null) ? arg.getClass()
                    : Object.class;
            superParamTypes[i] = argType;
            desc.append(Type.getDescriptor(argType));
        }
        desc.append(")V");

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                desc.toString(), null, null);
        mv.visitCode();

        // super(superArgs...)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        int slot = 1 + interceptors.length;
        for (int i = 0; i < constructorArgs.length; i++) {
            Class<?> argType = superParamTypes[i];
            if (argType == int.class || argType == Integer.class
                    || argType == boolean.class
                    || argType == Boolean.class
                    || argType == byte.class || argType == Byte.class
                    || argType == char.class
                    || argType == Character.class
                    || argType == short.class
                    || argType == Short.class) {
                mv.visitVarInsn(Opcodes.ILOAD, slot);
                slot++;
            } else if (argType == long.class
                    || argType == Long.class) {
                mv.visitVarInsn(Opcodes.LLOAD, slot);
                slot += 2;
            } else if (argType == float.class
                    || argType == Float.class) {
                mv.visitVarInsn(Opcodes.FLOAD, slot);
                slot++;
            } else if (argType == double.class
                    || argType == Double.class) {
                mv.visitVarInsn(Opcodes.DLOAD, slot);
                slot += 2;
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                slot++;
            }
        }

        String superDesc = Type.getConstructorDescriptor(
                findConstructor(targetClass, superParamTypes));
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, targetInternal,
                "<init>", superDesc, false);

        // this._interceptor$i = arg(i+1)
        for (int i = 0; i < interceptors.length; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1 + i);
            mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal,
                    "_interceptor$" + i, interceptorDesc);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private Constructor<?> findConstructor(Class<?> clazz,
                                           Class<?>[] paramTypes) {
        outer:
        for (Constructor<?> ctor : clazz.getDeclaredConstructors()) {
            Class<?>[] existing = ctor.getParameterTypes();
            if (existing.length == paramTypes.length) {
                for (int i = 0; i < existing.length; i++) {
                    if (constructorArgs[i] == null) {
                        if (existing[i].isPrimitive()) {
                            continue outer;
                        }
                        continue;
                    }
                    if (!wrap(existing[i]).isAssignableFrom(
                            wrap(paramTypes[i]))) {
                        continue outer;
                    }
                }
                return ctor;
            }
        }
        throw new IllegalArgumentException(
                "No matching constructor found on "
                        + clazz.getName());
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

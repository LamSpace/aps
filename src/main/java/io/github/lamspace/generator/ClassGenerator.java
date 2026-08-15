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

import io.github.lamspace.ConstructorInterceptor;
import io.github.lamspace.Interceptor;
import io.github.lamspace.MethodMapping;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Label;
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
    private final boolean ctorIntercept;

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
        this(targetClass, interceptors, mapping, false, new Object[0]);
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
        this(targetClass, interceptors, mapping, false, constructorArgs);
    }

    /**
     * Creates a generator for the given target class with constructor
     * interception optionally enabled.
     *
     * @param targetClass     the class to proxy
     * @param interceptors    deduped interceptor instances
     * @param mapping         method → interceptor index mapping
     * @param ctorIntercept   whether to emit the constructor interception hook
     * @param constructorArgs arguments to pass to the superclass constructor
     */
    public ClassGenerator(Class<?> targetClass, Interceptor[] interceptors,
                          MethodMapping mapping, boolean ctorIntercept,
                          Object... constructorArgs) {
        this.targetClass = targetClass;
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
        this.ctorIntercept = ctorIntercept;
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

        // -- Reflected superclass Constructor for the interception hook --
        if (ctorIntercept) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC
                    | Opcodes.ACC_FINAL, "_ctor$",
                    "Ljava/lang/reflect/Constructor;", null, null);
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
                targetInternal, infos, true);

        // -- <clinit> initializer --
        generateClinit(cw, generatedInternal, entries);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateClinit(ClassWriter cw, String generatedInternal,
                                List<ClinitRegistry.Entry> entries) {
        if (entries.isEmpty() && !ctorIntercept) {
            return;
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC,
                "<clinit>", "()V", null, null);
        mv.visitCode();

        if (ctorIntercept) {
            Class<?>[] paramTypes =
                    findConstructor(targetClass, superParamTypes())
                            .getParameterTypes();
            mv.visitLdcInsn(Type.getType(
                    "L" + Type.getInternalName(targetClass) + ";"));
            BytecodeUtils.pushInt(mv, paramTypes.length);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitInsn(Opcodes.DUP);
                BytecodeUtils.pushInt(mv, i);
                BytecodeUtils.pushClassConstant(mv, paramTypes[i]);
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class",
                    "getDeclaredConstructor",
                    "([Ljava/lang/Class;)Ljava/lang/reflect/Constructor;", false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, generatedInternal, "_ctor$",
                    "Ljava/lang/reflect/Constructor;");
        }

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
        int ctorInterceptorSlot = 1 + interceptors.length;
        Class<?>[] superParamTypes = superParamTypes();

        // Build descriptor: (LInterceptor;... [LConstructorInterceptor;]
        //                     [superArgs])V
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < interceptors.length; i++) {
            desc.append(interceptorDesc);
        }
        if (ctorIntercept) {
            desc.append(Type.getDescriptor(ConstructorInterceptor.class));
        }
        for (Class<?> argType : superParamTypes) {
            desc.append(Type.getDescriptor(argType));
        }
        desc.append(")V");

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                desc.toString(), null, null);
        mv.visitCode();

        if (ctorIntercept) {
            generateInterceptedConstructor(mv, generatedInternal,
                    targetInternal, ctorInterceptorSlot, superParamTypes);
        } else {
            // super(superArgs...): load each value-typed arg as a reference,
            // then unbox/cast to the superclass constructor's declared type
            Constructor<?> ctor = findConstructor(targetClass, superParamTypes);
            Class<?>[] declaredParams = ctor.getParameterTypes();
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            int slot = ctorInterceptorSlot;
            for (int i = 0; i < declaredParams.length; i++) {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
                Class<?> pt = declaredParams[i];
                if (pt.isPrimitive()) {
                    BytecodeUtils.unboxPrimitive(mv, pt);
                } else {
                    mv.visitTypeInsn(Opcodes.CHECKCAST,
                            Type.getInternalName(pt));
                }
                slot++;
            }
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL, targetInternal,
                    "<init>", Type.getConstructorDescriptor(ctor), false);
        }

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

    private void generateInterceptedConstructor(MethodVisitor mv,
                                               String generatedInternal,
                                               String targetInternal,
                                               int ctorInterceptorSlot,
                                               Class<?>[] superParamTypes) {
        Constructor<?> ctor = findConstructor(targetClass, superParamTypes);
        Class<?>[] declaredParams = ctor.getParameterTypes();

        int firstArgSlot = ctorInterceptorSlot + 1;
        int argsSlot = firstArgSlot + constructorArgs.length;
        int rewrittenSlot = argsSlot + 1;
        int exceptionSlot = argsSlot + 2;

        // 1. box the super args (value types are always references) into Object[]
        BytecodeUtils.pushInt(mv, constructorArgs.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        int slot = firstArgSlot;
        for (Class<?> vt : superParamTypes) {
            mv.visitInsn(Opcodes.DUP);
            BytecodeUtils.pushInt(mv, slot - firstArgSlot);
            mv.visitVarInsn(BytecodeUtils.loadOpcode(vt), slot);
            BytecodeUtils.boxPrimitive(mv, vt);
            mv.visitInsn(Opcodes.AASTORE);
            slot++;
        }
        mv.visitVarInsn(Opcodes.ASTORE, argsSlot);

        // 2. rewritten = ctorInterceptor.before(_ctor$, args), wrapping
        //    checked exceptions in UndeclaredThrowableException
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchRuntime = new Label();
        Label catchError = new Label();
        Label catchChecked = new Label();
        Label afterBefore = new Label();
        mv.visitTryCatchBlock(tryStart, tryEnd, catchRuntime,
                "java/lang/RuntimeException");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchError, "java/lang/Error");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchChecked,
                "java/lang/Exception");
        mv.visitLabel(tryStart);
        mv.visitVarInsn(Opcodes.ALOAD, ctorInterceptorSlot);
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal, "_ctor$",
                "Ljava/lang/reflect/Constructor;");
        mv.visitVarInsn(Opcodes.ALOAD, argsSlot);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "io/github/lamspace/ConstructorInterceptor", "before",
                "(Ljava/lang/reflect/Constructor;[Ljava/lang/Object;)"
                        + "[Ljava/lang/Object;", true);
        mv.visitLabel(tryEnd);
        mv.visitVarInsn(Opcodes.ASTORE, rewrittenSlot);
        mv.visitJumpInsn(Opcodes.GOTO, afterBefore);
        mv.visitLabel(catchRuntime);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(catchError);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(catchChecked);
        mv.visitVarInsn(Opcodes.ASTORE, exceptionSlot);
        mv.visitTypeInsn(Opcodes.NEW,
                "java/lang/reflect/UndeclaredThrowableException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, exceptionSlot);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/reflect/UndeclaredThrowableException",
                "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(afterBefore);

        // 3. super(rewritten args, unboxed to the declared parameter types)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        for (int i = 0; i < declaredParams.length; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, rewrittenSlot);
            BytecodeUtils.pushInt(mv, i);
            mv.visitInsn(Opcodes.AALOAD);
            Class<?> pt = declaredParams[i];
            if (pt.isPrimitive()) {
                BytecodeUtils.unboxPrimitive(mv, pt);
            } else {
                mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(pt));
            }
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, targetInternal, "<init>",
                Type.getConstructorDescriptor(ctor), false);

        // 4. ctorInterceptor.after(this, _ctor$, rewritten)
        mv.visitVarInsn(Opcodes.ALOAD, ctorInterceptorSlot);
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal, "_ctor$",
                "Ljava/lang/reflect/Constructor;");
        mv.visitVarInsn(Opcodes.ALOAD, rewrittenSlot);
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "io/github/lamspace/ConstructorInterceptor", "after",
                "(Ljava/lang/Object;Ljava/lang/reflect/Constructor;"
                        + "[Ljava/lang/Object;)V", true);
    }

    private Class<?>[] superParamTypes() {
        Class<?>[] types = new Class<?>[constructorArgs.length];
        for (int i = 0; i < constructorArgs.length; i++) {
            Object arg = constructorArgs[i];
            types[i] = (arg != null) ? arg.getClass() : Object.class;
        }
        return types;
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

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
import org.objectweb.asm.Label;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a static-method proxy class that declares one {@code public
 * static} shadow per collected static method of the target class.
 *
 * <p>The generated class extends {@code Object}, lives in
 * {@code io.github.lamspace}, and does not implement {@code DispatchTarget}.
 * Methods mapped to an interceptor route through
 * {@link Interceptor#intercept} with a {@code null} proxy; unmapped methods
 * pass through to the original via a direct {@code INVOKESTATIC}.
 */
public class StaticMethodGenerator {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);
    private static final String INTERCEPTOR_FIELD_PREFIX = "_staticInterceptor$";

    private final Method[] methods;
    private final Interceptor[] interceptors;
    private final MethodMapping mapping;

    public StaticMethodGenerator(Method[] methods, Interceptor[] interceptors,
                                 MethodMapping mapping) {
        this.methods = methods.clone();
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
    }

    /**
     * Generates the static-proxy classfile bytes.
     *
     * @return valid JVM classfile bytes
     */
    public byte[] generate() {
        String generatedInternal = "io/github/lamspace/StaticProxy"
                + "$$AcceleratedProxy$$" + COUNTER.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                generatedInternal, null, "java/lang/Object", null);

        // -- Static interceptor fields (one per distinct interceptor) --
        String interceptorDesc = Type.getDescriptor(Interceptor.class);
        for (int i = 0; i < interceptors.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                    INTERCEPTOR_FIELD_PREFIX + i, interceptorDesc, null, null);
        }

        // -- Shadow methods (+ Method fields for intercepted ones) --
        ClinitRegistry registry = new ClinitRegistry();
        int[] indices = mapping.indices();
        for (int i = 0; i < methods.length; i++) {
            Method method = methods[i];
            int interceptorIndex = indices[i];
            if (interceptorIndex >= 0) {
                String fieldName = "_method$" + method.getName() + "$" + i;
                cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                        fieldName, "Ljava/lang/reflect/Method;", null, null);
                registry.register(method.getDeclaringClass(), method,
                        generatedInternal, fieldName, i);
                generateIntercepted(cw, method, generatedInternal,
                        interceptorIndex, fieldName);
            } else {
                generatePassthrough(cw, method);
            }
        }

        List<ClinitRegistry.Entry> entries = registry.drain();

        // -- Static interceptor binding + <clinit> Method resolution --
        generateBind(cw, generatedInternal, interceptors.length,
                interceptorDesc);
        generateClinit(cw, generatedInternal, entries);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generatePassthrough(ClassWriter cw, Method method) {
        String name = method.getName();
        String desc = Type.getMethodDescriptor(method);
        String owner = Type.getInternalName(method.getDeclaringClass());

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, desc, null,
                exceptionNames(method));
        mv.visitCode();
        loadArguments(mv, method);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, owner, name, desc, false);
        returnResult(mv, method.getReturnType(), desc);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateIntercepted(ClassWriter cw, Method method,
                                     String generatedInternal,
                                     int interceptorIndex, String fieldName) {
        String name = method.getName();
        String desc = Type.getMethodDescriptor(method);

        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, name, desc, null,
                exceptionNames(method));
        mv.visitCode();

        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchRuntime = new Label();
        Label catchError = new Label();
        Label catchChecked = new Label();
        mv.visitTryCatchBlock(tryStart, tryEnd, catchRuntime,
                "java/lang/RuntimeException");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchError,
                "java/lang/Error");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchChecked,
                "java/lang/Exception");
        mv.visitLabel(tryStart);

        Class<?>[] paramTypes = method.getParameterTypes();

        // _staticInterceptor$N (no `this`: static field, not instance)
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal,
                INTERCEPTOR_FIELD_PREFIX + interceptorIndex,
                Type.getDescriptor(Interceptor.class));
        // proxy = null
        mv.visitInsn(Opcodes.ACONST_NULL);
        // Method field
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal, fieldName,
                "Ljava/lang/reflect/Method;");
        // Object[] args (first param at local slot 0 — no `this`)
        BytecodeUtils.pushInt(mv, paramTypes.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");
        int slot = 0;
        for (int i = 0; i < paramTypes.length; i++) {
            mv.visitInsn(Opcodes.DUP);
            BytecodeUtils.pushInt(mv, i);
            Class<?> type = paramTypes[i];
            if (type.isPrimitive()) {
                mv.visitVarInsn(BytecodeUtils.loadOpcode(type), slot);
                BytecodeUtils.boxPrimitive(mv, type);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
            }
            mv.visitInsn(Opcodes.AASTORE);
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "io/github/lamspace/Interceptor", "intercept",
                "(Ljava/lang/Object;Ljava/lang/reflect/Method;"
                        + "[Ljava/lang/Object;)Ljava/lang/Object;",
                true);

        returnFromObject(mv, method.getReturnType(), desc);

        mv.visitLabel(tryEnd);
        mv.visitLabel(catchRuntime);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(catchError);
        mv.visitInsn(Opcodes.ATHROW);
        mv.visitLabel(catchChecked);
        mv.visitVarInsn(Opcodes.ASTORE, slot);
        mv.visitTypeInsn(Opcodes.NEW,
                "java/lang/reflect/UndeclaredThrowableException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, slot);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/reflect/UndeclaredThrowableException",
                "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateBind(ClassWriter cw, String generatedInternal,
                              int count, String interceptorDesc) {
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_STATIC, "__bindStatics",
                "([Lio/github/lamspace/Interceptor;)V", null, null);
        mv.visitCode();
        for (int i = 0; i < count; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            BytecodeUtils.pushInt(mv, i);
            mv.visitInsn(Opcodes.AALOAD);
            mv.visitTypeInsn(Opcodes.CHECKCAST, "io/github/lamspace/Interceptor");
            mv.visitFieldInsn(Opcodes.PUTSTATIC, generatedInternal,
                    INTERCEPTOR_FIELD_PREFIX + i, interceptorDesc);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private void generateClinit(ClassWriter cw, String generatedInternal,
                                List<ClinitRegistry.Entry> entries) {
        if (entries.isEmpty()) {
            return;
        }
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "<clinit>",
                "()V", null, null);
        mv.visitCode();
        for (ClinitRegistry.Entry entry : entries) {
            Method method = entry.method();
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
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class",
                    "getDeclaredMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)"
                            + "Ljava/lang/reflect/Method;",
                    false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, generatedInternal,
                    entry.methodFieldName(), "Ljava/lang/reflect/Method;");
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static String[] exceptionNames(Method method) {
        Class<?>[] exceptions = method.getExceptionTypes();
        String[] names = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
            names[i] = Type.getInternalName(exceptions[i]);
        }
        return names;
    }

    private static void loadArguments(MethodVisitor mv, Method method) {
        int slot = 0;
        for (Class<?> type : method.getParameterTypes()) {
            mv.visitVarInsn(BytecodeUtils.loadOpcode(type), slot);
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }
    }

    private static void returnResult(MethodVisitor mv, Class<?> returnType,
                                     String desc) {
        if (returnType == void.class) {
            mv.visitInsn(Opcodes.RETURN);
        } else if (returnType.isPrimitive()) {
            mv.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
        } else {
            mv.visitInsn(Opcodes.ARETURN);
        }
    }

    private static void returnFromObject(MethodVisitor mv,
                                         Class<?> returnType, String desc) {
        if (returnType == void.class) {
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        } else if (returnType.isPrimitive()) {
            BytecodeUtils.unboxPrimitive(mv, returnType);
            mv.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
        } else {
            mv.visitTypeInsn(Opcodes.CHECKCAST,
                    Type.getInternalName(returnType));
            mv.visitInsn(Opcodes.ARETURN);
        }
    }
}

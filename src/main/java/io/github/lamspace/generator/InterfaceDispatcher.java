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
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.List;

/**
 * Generates method implementation bodies for an interface proxy class.
 * Each implementation marshals arguments into an {@code Object[]} and
 * calls {@link io.github.lamspace.Interceptor#intercept}. The generated
 * {@code dispatch()} method (see {@link DispatchGenerator}) routes super
 * calls for Object methods and throws {@code AbstractMethodError} for
 * interface methods.
 */
final class InterfaceDispatcher {

    private static final String INTERCEPTOR_FIELD_PREFIX = "_interceptor$";

    private InterfaceDispatcher() {
    }

    /**
     * Generates method implementations, static Method fields, and registers
     * clinit entries for all public instance methods on the interface.
     *
     * @param cw                the ClassWriter for the generated class
     * @param resolved          the resolved (merged, sorted) method list
     * @param generatedInternal ASM internal name of the generated class
     * @param mapping           method → interceptor index mapping
     * @param interceptorCount  number of distinct Interceptor instances
     * @param registry          clinit entry registry
     */
    static void dispatchMethods(ClassWriter cw,
                                List<InterfaceMethodResolver.ResolvedMethod> resolved,
                                String generatedInternal,
                                MethodMapping mapping,
                                int interceptorCount,
                                ClinitRegistry registry) {
        for (int i = 0; i < resolved.size(); i++) {
            InterfaceMethodResolver.ResolvedMethod rm = resolved.get(i);
            Method method = rm.canonical();

            int interceptorIndex = mapping.indices()[i];
            boolean shouldIntercept = interceptorIndex >= 0;

            String suffix = "$" + i;
            String methodFieldName = "_method$" + method.getName()
                    + suffix;

            addStaticField(cw, methodFieldName,
                    "Ljava/lang/reflect/Method;");

            registry.register(rm.owner(), method, methodFieldName);

            generateImplementation(cw, method, generatedInternal,
                    shouldIntercept, interceptorIndex, methodFieldName);
        }
    }

    /**
     * Declares a private static field with the given name and descriptor.
     */
    private static void addStaticField(ClassWriter cw, String name,
                                       String desc) {
        var fv = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                name, desc, null, null);
        fv.visitEnd();
    }

    /**
     * Emits one interface method implementation. Unmatched methods throw
     * {@code AbstractMethodError}; matched methods marshal their arguments
     * into an {@code Object[]}, call {@code Interceptor.intercept}, and
     * unbox the result. Checked exceptions from the interceptor are wrapped
     * in {@code UndeclaredThrowableException}.
     */
    private static void generateImplementation(ClassWriter cw,
                                               Method method,
                                               String generatedInternal,
                                               boolean shouldIntercept,
                                               int interceptorIndex,
                                               String methodFieldName) {
        String name = method.getName();
        String desc = Type.getMethodDescriptor(method);

        Class<?>[] exceptions = method.getExceptionTypes();
        String[] exceptionNames = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
            exceptionNames[i] = Type.getInternalName(exceptions[i]);
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc,
                null, exceptionNames);
        mv.visitCode();

        if (!shouldIntercept) {
            // Unmatched interface method: throw AbstractMethodError
            mv.visitTypeInsn(Opcodes.NEW,
                    "java/lang/AbstractMethodError");
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("Method " + name
                    + " is not intercepted");
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/lang/AbstractMethodError",
                    "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(3, 1);
            mv.visitEnd();
            return;
        }

        Class<?>[] paramTypes = method.getParameterTypes();
        int totalParamSlots = 0;
        for (Class<?> paramType : paramTypes) {
            totalParamSlots += (paramType == double.class
                    || paramType == long.class) ? 2 : 1;
        }

        // -- Try block: call callback --
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

        // 1. Load interceptor: this._interceptor$N
        String fieldName = INTERCEPTOR_FIELD_PREFIX + interceptorIndex;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                fieldName, Type.getDescriptor(Interceptor.class));

        // 2. Arg 1: proxy = this
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // 3. Arg 2: _method$X
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal,
                methodFieldName, "Ljava/lang/reflect/Method;");

        // 4. Arg 3: new Object[]{ args... }
        BytecodeUtils.pushInt(mv, paramTypes.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

        int slot = 1;
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
            slot += (type == double.class || type == long.class)
                    ? 2 : 1;
        }

        // 5. Call Interceptor.intercept(proxy, method, args)
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "io/github/lamspace/Interceptor",
                "intercept",
                "(Ljava/lang/Object;Ljava/lang/reflect/Method;"
                        + "[Ljava/lang/Object;)Ljava/lang/Object;",
                true);

        // 6. Unbox return value
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        } else if (returnType.isPrimitive()) {
            BytecodeUtils.unboxPrimitive(mv, returnType);
            mv.visitInsn(Type.getReturnType(desc)
                    .getOpcode(Opcodes.IRETURN));
        } else {
            mv.visitTypeInsn(Opcodes.CHECKCAST,
                    Type.getInternalName(returnType));
            mv.visitInsn(Opcodes.ARETURN);
        }

        mv.visitLabel(tryEnd);

        // -- Catch RuntimeException: rethrow directly --
        mv.visitLabel(catchRuntime);
        mv.visitInsn(Opcodes.ATHROW);

        // -- Catch Error: rethrow directly --
        mv.visitLabel(catchError);
        mv.visitInsn(Opcodes.ATHROW);

        // -- Catch checked Exception: wrap in
        //    UndeclaredThrowableException --
        mv.visitLabel(catchChecked);
        int exceptionSlot = totalParamSlots + 1;
        mv.visitVarInsn(Opcodes.ASTORE, exceptionSlot);
        mv.visitTypeInsn(Opcodes.NEW,
                "java/lang/reflect/UndeclaredThrowableException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, exceptionSlot);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/reflect/UndeclaredThrowableException",
                "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}

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

import io.github.lamspace.ClassFilter;
import io.github.lamspace.Interceptor;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates method override bytecode for a proxy subclass.
 * Each override marshals arguments into an {@code Object[]}, calls
 * {@link io.github.lamspace.Interceptor#intercept}, and unboxes the
 * return value. Superclass invocations go through the generated
 * {@code dispatch(Method, Object[])} method (see {@link DispatchGenerator})
 * which uses a hashCode-driven if-else chain with direct
 * {@code INVOKESPECIAL} super calls.
 */
public class MethodDispatcher {

    private static final String CALLBACK_FIELD = "_callback";

    private MethodDispatcher() {
    }

    /**
     * Generates method overrides and per-method static Method fields,
     * and registers clinit entries for all proxyable methods on the target class.
     *
     * @param cw                the ClassWriter for the generated subclass
     * @param targetClass       the class being proxied
     * @param generatedInternal ASM internal name of the generated class
     * @param filter            if non-null, only methods accepted by the filter
     *                          are routed through the Interceptor; others call
     *                          super directly
     * @return list of method names for which dispatchers were generated
     */
    public static List<String> dispatchMethods(ClassWriter cw, Class<?> targetClass,
                                               String generatedInternal,
                                               ClassFilter filter,
                                               ClinitRegistry registry) {
        List<String> dispatchedMethods = new ArrayList<>();

        for (Method method : targetClass.getDeclaredMethods()) {
            int mods = method.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)
                    || Modifier.isPrivate(mods)) {
                continue;
            }

            boolean shouldIntercept = (filter == null) || filter.accept(method);

            int index = dispatchedMethods.size();
            String methodFieldName = "_method$" + method.getName() + "$" + index;

            addStaticField(cw, methodFieldName, "Ljava/lang/reflect/Method;");

            registry.register(targetClass, method, generatedInternal,
                    methodFieldName, index);

            generateOverride(cw, method, generatedInternal, shouldIntercept,
                    methodFieldName, index);

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
                                         int methodIndex) {
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

        // -- Try block: call callback.intercept(proxy, method, index, args) --
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchRuntime = new Label();
        Label catchError = new Label();
        Label catchChecked = new Label();

        mv.visitTryCatchBlock(tryStart, tryEnd, catchRuntime, "java/lang/RuntimeException");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchError, "java/lang/Error");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchChecked, "java/lang/Exception");

        mv.visitLabel(tryStart);

        Class<?>[] paramTypes = method.getParameterTypes();

        // 1. Load interceptor field
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                CALLBACK_FIELD, Type.getDescriptor(Interceptor.class));

        // 2. Arg 1: proxy = this
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // 3. Arg 2: _method$N (static Method field)
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal,
                methodFieldName, "Ljava/lang/reflect/Method;");

        // 4. Arg 3: new Object[] { arg0, arg1, ... }
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
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }

        // 5. Call interceptor.intercept(proxy, method, args)
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
        int excSlot = slot + 1;
        mv.visitVarInsn(Opcodes.ASTORE, excSlot);
        mv.visitTypeInsn(Opcodes.NEW, "java/lang/reflect/UndeclaredThrowableException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, excSlot);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/reflect/UndeclaredThrowableException",
                "<init>",
                "(Ljava/lang/Throwable;)V",
                false);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private static void loadArguments(MethodVisitor mv, Method method) {
        int slot = 1;
        for (Class<?> type : method.getParameterTypes()) {
            mv.visitVarInsn(BytecodeUtils.loadOpcode(type), slot);
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }
    }

}

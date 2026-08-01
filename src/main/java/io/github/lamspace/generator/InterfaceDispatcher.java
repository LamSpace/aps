package io.github.lamspace.generator;

import io.github.lamspace.ClassFilter;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates method implementation bodies for an interface proxy class.
 * Each implementation marshals arguments into an {@code Object[]} and
 * calls {@code InterfaceCallback.intercept(...)}. No MethodHandle binding
 * is needed — interface methods have no super implementation.
 */
final class InterfaceDispatcher {

    private static final String CALLBACK_FIELD = "_callback";

    private InterfaceDispatcher() {}

    /**
     * Generates method implementations, static Method fields, and registers
     * clinit entries for all public instance methods on the interface.
     *
     * @param cw                the ClassWriter for the generated class
     * @param interfaceClass    the interface being implemented
     * @param generatedInternal ASM internal name of the generated class
     * @param filter            if non-null, only methods accepted by the
     *                          filter are routed through the callback;
     *                          others throw AbstractMethodError
     * @return list of method names for which dispatchers were generated
     */
    static List<String> dispatchMethods(ClassWriter cw, Class<?> interfaceClass,
                                         String generatedInternal,
                                         ClassFilter filter) {
        List<String> dispatchedMethods = new ArrayList<>();

        for (Method method : interfaceClass.getMethods()) {
            int mods = method.getModifiers();
            // Skip static and final methods — they can't be overridden
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                continue;
            }

            boolean shouldIntercept = (filter == null) || filter.accept(method);

            String suffix = "$" + dispatchedMethods.size();
            String methodFieldName = "_method$" + method.getName() + suffix;
            // No handle field for interfaces — pass empty string
            String handleFieldName = "";

            addStaticField(cw, methodFieldName, "Ljava/lang/reflect/Method;");

            ClinitRegistry.register(interfaceClass, method, generatedInternal,
                    methodFieldName, handleFieldName);

            generateImplementation(cw, method, generatedInternal,
                    shouldIntercept, methodFieldName);

            dispatchedMethods.add(method.getName());
        }

        return dispatchedMethods;
    }

    private static void addStaticField(ClassWriter cw, String name, String desc) {
        var fv = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                name, desc, null, null);
        fv.visitEnd();
    }

    private static void generateImplementation(ClassWriter cw, Method method,
                                                String generatedInternal,
                                                boolean shouldIntercept,
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
            // Filter rejected: throw AbstractMethodError
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/AbstractMethodError");
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("Method " + name + " is not intercepted");
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

        // 1. Load callback: this._callback
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                CALLBACK_FIELD, "Lio/github/lamspace/InterfaceCallback;");

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
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }

        // 5. Call InterfaceCallback.intercept(proxy, method, args)
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "io/github/lamspace/InterfaceCallback",
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

        // -- Catch checked Exception: wrap in UndeclaredThrowableException --
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

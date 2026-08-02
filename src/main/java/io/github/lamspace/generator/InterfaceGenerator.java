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
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a proxy implementation class that {@code extends Object}
 * and {@code implements} the target interface and {@code DispatchTarget}.
 * <p>
 * For each interface method, generates an implementation that delegates
 * to {@link io.github.lamspace.Interceptor#intercept}. The generated
 * {@code dispatch(Method, Object[])} method routes Object methods
 * ({@code equals}, {@code hashCode}, {@code toString}) to direct
 * {@code INVOKESPECIAL} super calls; all other methods throw
 * {@code AbstractMethodError}.
 */
public class InterfaceGenerator {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final Class<?> interfaceClass;
    private final ClassFilter filter;

    /**
     * Creates a generator for the given interface.
     *
     * @param interfaceClass the interface to implement
     * @param filter         method filter; {@code null} means all methods
     *                       are routed through the interceptor
     */
    public InterfaceGenerator(Class<?> interfaceClass, ClassFilter filter) {
        this.interfaceClass = interfaceClass;
        this.filter = filter;
    }

    /**
     * Generates the implementation class bytecode. The class is placed in
     * the same runtime package as the target interface.
     *
     * @return valid JVM classfile bytes
     */
    public byte[] generate() {
        String targetInternal = Type.getInternalName(interfaceClass);
        String simpleName = targetInternal.substring(
                targetInternal.lastIndexOf('/') + 1);
        // Place generated class in the APS package. The interface may be in a
        // restricted module (e.g., java.lang) where we cannot define classes.
        String generatedInternal = "io/github/lamspace/"
                + simpleName + "$$APS$$" + COUNTER.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Implements target interface + DispatchTarget
        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                generatedInternal, null, "java/lang/Object",
                new String[]{targetInternal, "io/github/lamspace/DispatchTarget"});

        // -- Interceptor field --
        String callbackDesc = Type.getDescriptor(Interceptor.class);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "_callback", callbackDesc, null, null);

        // -- Constructor: stores interceptor, calls super() --
        generateConstructor(cw, generatedInternal, callbackDesc);

        // -- Method implementations + static Method fields --
        InterfaceDispatcher.dispatchMethods(cw, interfaceClass,
                generatedInternal, filter);

        // -- Drain ClinitRegistry entries for dispatch generation --
        List<ClinitRegistry.Entry> entries = ClinitRegistry.drain();

        // -- dispatch(Method, Object[]) for Object methods --
        List<MethodInfo> infos = new ArrayList<>();
        for (ClinitRegistry.Entry entry : entries) {
            infos.add(new MethodInfo(entry.method(), entry.methodFieldName(),
                    DispatchGenerator.computeHash(entry.method())));
        }
        DispatchGenerator.generateDispatch(cw, generatedInternal,
                "java/lang/Object", infos, false);

        // -- <clinit> for Method objects only --
        generateClinit(cw, generatedInternal, entries);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateConstructor(ClassWriter cw, String generatedInternal,
                                     String callbackDesc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(" + callbackDesc + ")V", null, null);
        mv.visitCode();
        // super()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        // this._callback = callback (slot 1)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal,
                "_callback", callbackDesc);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
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

            // Store the Method object via reflection
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
                    methodField, "Ljava/lang/reflect/Method;");
            // No MethodHandle storage — interfaces have no super
            // implementation to bind.
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}

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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a proxy implementation class that {@code extends Object}
 * and {@code implements} the target interface and {@code DispatchTarget}.
 *
 * <p>Stores one instance field per distinct Interceptor. Each interface
 * method implementation directly {@code GETFIELD}s its assigned field.
 */
public class InterfaceGenerator {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final Class<?> interfaceClass;
    private final Interceptor[] interceptors;
    private final MethodMapping mapping;

    /**
     * Creates a generator for the given interface.
     *
     * @param interfaceClass the interface to implement
     * @param interceptors   deduped interceptor instances
     * @param mapping        method → interceptor index mapping
     */
    public InterfaceGenerator(Class<?> interfaceClass,
                              Interceptor[] interceptors,
                              MethodMapping mapping) {
        this.interfaceClass = interfaceClass;
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
    }

    /**
     * Generates the implementation class bytecode.
     *
     * @return valid JVM classfile bytes
     */
    public byte[] generate() {
        String targetInternal = Type.getInternalName(interfaceClass);
        String simpleName = targetInternal.substring(
                targetInternal.lastIndexOf('/') + 1);
        String generatedInternal = "io/github/lamspace/"
                + simpleName + "$$AcceleratedProxy$$"
                + COUNTER.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Implements target interface + DispatchTarget
        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                generatedInternal, null, "java/lang/Object",
                new String[]{targetInternal,
                        "io/github/lamspace/DispatchTarget"});

        // -- Interceptor fields (one per distinct interceptor) --
        String interceptorDesc = Type.getDescriptor(Interceptor.class);
        for (int i = 0; i < interceptors.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    "_interceptor$" + i, interceptorDesc, null, null);
        }

        // -- Constructor: stores interceptors, calls super() --
        generateConstructor(cw, generatedInternal, interceptorDesc);

        // -- Method implementations + static Method fields --
        ClinitRegistry registry = new ClinitRegistry();
        InterfaceDispatcher.dispatchMethods(cw, interfaceClass,
                generatedInternal, mapping, interceptors.length,
                registry);

        // -- Drain ClinitRegistry entries for dispatch generation --
        List<ClinitRegistry.Entry> entries = registry.drain();

        // -- dispatch(Method, Object[]) for Object methods --
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
                "java/lang/Object", infos, false);

        // -- <clinit> for Method objects only --
        generateClinit(cw, generatedInternal, entries);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateConstructor(ClassWriter cw,
                                     String generatedInternal,
                                     String interceptorDesc) {
        // Build descriptor: (LInterceptor;...LInterceptor;)V
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < interceptors.length; i++) {
            desc.append(interceptorDesc);
        }
        desc.append(")V");

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                desc.toString(), null, null);
        mv.visitCode();
        // super()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        // this._interceptor$i = arg(i+1)
        for (int i = 0; i < interceptors.length; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1 + i);
            mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal,
                    "_interceptor$" + i, interceptorDesc);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1 + interceptors.length);
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
}

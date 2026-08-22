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

    private final Class<?>[] interfaces;
    private final Interceptor[] interceptors;
    private final MethodMapping mapping;
    private final String packagePrefix;

    /**
     * Creates a generator for the given interfaces.
     *
     * @param interfaces    the interfaces to implement
     * @param interceptors  deduped interceptor instances
     * @param mapping       method → interceptor index mapping
     * @param packagePrefix internal-name package prefix for the generated
     *                      class (e.g. {@code "io/github/lamspace/"} or
     *                      {@code "com/example/pkg/"})
     */
    public InterfaceGenerator(Class<?>[] interfaces,
                              Interceptor[] interceptors,
                              MethodMapping mapping,
                              String packagePrefix) {
        this.interfaces = interfaces.clone();
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
        this.packagePrefix = packagePrefix;
    }

    /**
     * Generates the implementation class bytecode.
     *
     * @return valid JVM classfile bytes
     */
    public byte[] generate() {
        List<InterfaceMethodResolver.ResolvedMethod> resolved =
                InterfaceMethodResolver.resolve(interfaces);

        String baseName;
        if (interfaces.length == 1) {
            String targetInternal = Type.getInternalName(interfaces[0]);
            baseName = targetInternal.substring(
                    targetInternal.lastIndexOf('/') + 1);
        } else {
            baseName = "MultiInterface";
        }
        String generatedInternal = packagePrefix + baseName
                + "$$OpenProxy$$" + COUNTER.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        // Implements all interfaces + DispatchTarget
        String[] implemented = new String[interfaces.length + 2];
        for (int i = 0; i < interfaces.length; i++) {
            implemented[i] = Type.getInternalName(interfaces[i]);
        }
        implemented[interfaces.length] = "io/github/lamspace/DispatchTarget";
        implemented[interfaces.length + 1] = "io/github/lamspace/internal/Rebindable";

        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                generatedInternal, null, "java/lang/Object", implemented);

        // -- Interceptor fields (one per distinct interceptor) --
        String interceptorDesc = Type.getDescriptor(Interceptor.class);
        for (int i = 0; i < interceptors.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE,
                    "_interceptor$" + i, interceptorDesc, null, null);
        }

        // -- Constructor: stores interceptors, calls super() --
        generateConstructor(cw, generatedInternal, interceptorDesc);

        // -- rebind(Interceptor[]): swap interceptors on a live instance --
        generateRebindMethod(cw, generatedInternal, interceptorDesc);

        // -- Method implementations + static Method fields --
        ClinitRegistry registry = new ClinitRegistry();
        InterfaceDispatcher.dispatchMethods(cw, resolved, generatedInternal,
                mapping, interceptors.length, registry);

        // -- Drain ClinitRegistry entries for dispatch generation --
        List<ClinitRegistry.Entry> entries = registry.drain();

        // -- dispatch(Method, Object[]): one branch per Method variant, each
        //    carrying the merged method's default owner so every variant routes
        //    to the same handler --
        List<MethodInfo> infos = new ArrayList<>();
        for (int i = 0; i < resolved.size(); i++) {
            InterfaceMethodResolver.ResolvedMethod rm = resolved.get(i);
            String fieldName = entries.get(i).methodFieldName();
            for (Method variant : rm.variants()) {
                infos.add(new MethodInfo(variant, fieldName, rm.defaultOwner()));
            }
        }
        DispatchGenerator.generateDispatch(cw, generatedInternal,
                "java/lang/Object", infos, false);

        // -- <clinit> for Method objects only --
        generateClinit(cw, generatedInternal, entries);

        cw.visitEnd();
        return cw.toByteArray();
    }

    /**
     * Emits the generated constructor {@code (Interceptor...)}: calls
     * {@code super()} and stores each interceptor argument into its field.
     */
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

    /**
     * Emits the {@code public void rebind(Interceptor[])} method body via
     * {@link BytecodeUtils#generateRebind}.
     */
    private void generateRebindMethod(ClassWriter cw, String generatedInternal,
                                      String interceptorDesc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "rebind",
                "([Lio/github/lamspace/Interceptor;)V", null, null);
        mv.visitCode();
        BytecodeUtils.generateRebind(mv, generatedInternal,
                interceptors.length, interceptorDesc);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Emits the {@code <clinit>} block that resolves each registered
     * {@code Method} object into its static field via {@code Class.getMethod}
     * on the owning interface. Emits nothing when there are no entries.
     */
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
                    "getMethod",
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

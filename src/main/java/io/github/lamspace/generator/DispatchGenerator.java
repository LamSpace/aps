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

import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.util.*;

/**
 * Generates the {@code dispatch(Method, Object[])} method for proxy classes.
 * Uses a hashCode-driven if-else chain with direct {@code INVOKESPECIAL}
 * super calls — no MethodHandle involvement.
 */
public final class DispatchGenerator {

    private DispatchGenerator() {
    }

    /**
     * Computes a deterministic, collision-resistant dispatch hash for a method.
     * Uses {@code Method.hashCode()} (declaring class name XOR method name)
     * combined with each parameter type's {@code Class.getName().hashCode()}
     * so that overloaded methods with the same name but different parameter
     * types produce distinct hashes.
     *
     * <p>This method is called both at generation time (to embed hash
     * constants) and at runtime (from the generated {@code dispatch()}
     * bytecode via {@code INVOKESTATIC}). Using {@code Class.getName()}
     * rather than {@code Class.hashCode()} ensures the hash is deterministic
     * across JVM instances.
     */
    public static int methodDispatchHash(Method method) {
        int hash = method.hashCode();
        for (Class<?> pt : method.getParameterTypes()) {
            hash = 31 * hash + pt.getName().hashCode();
        }
        return hash;
    }

    /**
     * Pre-computes the dispatch hash for a method. Delegates to
     * {@link #methodDispatchHash(Method)}.
     */
    static int computeHash(Method method) {
        return methodDispatchHash(method);
    }

    /**
     * Detects hash collisions among the given methods. With
     * {@link #methodDispatchHash(Method)} collisions are extremely unlikely,
     * but this serves as a safety net with an incrementing counter fallback.
     *
     * @param methods the methods to check
     * @return a map from method to its final collision-free dispatch hash
     */
    static Map<Method, Integer> resolveHashes(List<Method> methods) {
        Map<Method, Integer> result = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        for (Method m : methods) {
            int hash = computeHash(m);
            int salt = 1;
            while (!seen.add(hash)) {
                // Collision (extremely unlikely): fallback counter
                hash = hash * 31 + salt++;
            }
            result.put(m, hash);
        }
        return result;
    }

    /**
     * Generates the dispatch method bytecode.
     *
     * @param cw                     ClassWriter
     * @param generatedInternal      ASM internal name of the generated class
     * @param superInternal          ASM internal name of the superclass (target class
     *                               for class proxies, "java/lang/Object" for interface)
     * @param interfaceInternalName  ASM internal name of the target interface, or
     *                               {@code null} for class proxies (owner of the
     *                               {@code INVOKESPECIAL} for default methods)
     * @param infos                  per-method metadata with pre-resolved hashes
     * @param isClassProxy           true = class proxy (direct super calls),
     *                               false = interface proxy (AbstractMethodError
     *                               for non-default methods, direct INVOKESPECIAL
     *                               for default methods)
     */
    static void generateDispatch(ClassWriter cw,
                                 String generatedInternal,
                                 String superInternal,
                                 String interfaceInternalName,
                                 List<MethodInfo> infos,
                                 boolean isClassProxy) {
        String desc = "(Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;";
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "dispatch", desc, null,
                new String[]{"java/lang/Throwable"});
        mv.visitCode();

        // int hash = DispatchGenerator.methodDispatchHash(method);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                "io/github/lamspace/generator/DispatchGenerator",
                "methodDispatchHash",
                "(Ljava/lang/reflect/Method;)I", false);
        int hashSlot = 3;
        mv.visitVarInsn(Opcodes.ISTORE, hashSlot);

        Label nextLabel = null;

        for (int i = 0; i < infos.size(); i++) {
            MethodInfo info = infos.get(i);
            Method method = info.method();

            Label branchLabel = new Label();
            if (nextLabel == null) {
                mv.visitVarInsn(Opcodes.ILOAD, hashSlot);
                mv.visitLdcInsn(info.methodHash());
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, branchLabel);
            } else {
                mv.visitLabel(nextLabel);
                mv.visitVarInsn(Opcodes.ILOAD, hashSlot);
                mv.visitLdcInsn(info.methodHash());
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, branchLabel);
            }

            // Branch body
            Class<?> declaringClass = method.getDeclaringClass();
            boolean isObjectMethod = declaringClass == Object.class;
            boolean isDefault = method.isDefault();

            if (isClassProxy || isObjectMethod) {
                // Direct super call: this.super.method(args...)
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this

                Class<?>[] paramTypes = method.getParameterTypes();
                int argSlot = 2; // args parameter
                for (int j = 0; j < paramTypes.length; j++) {
                    mv.visitVarInsn(Opcodes.ALOAD, argSlot);
                    BytecodeUtils.pushInt(mv, j);
                    mv.visitInsn(Opcodes.AALOAD);
                    Class<?> pt = paramTypes[j];
                    if (pt.isPrimitive()) {
                        BytecodeUtils.unboxPrimitive(mv, pt);
                    } else if (pt != Object.class) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST,
                                Type.getInternalName(pt));
                    }
                }

                String owner = isObjectMethod ? "java/lang/Object" : superInternal;
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        owner,
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        false);

                // Box return if needed
                Class<?> rt = method.getReturnType();
                if (rt == void.class) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                } else if (rt.isPrimitive()) {
                    BytecodeUtils.boxPrimitive(mv, rt);
                }
                mv.visitInsn(Opcodes.ARETURN);
            } else if (isDefault) {
                // Default interface method: invoke the interface's default
                // implementation directly. Owner is the target interface (a
                // direct superinterface), so method resolution finds inherited
                // default methods too.
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this

                Class<?>[] paramTypes = method.getParameterTypes();
                int argSlot = 2; // args parameter
                for (int j = 0; j < paramTypes.length; j++) {
                    mv.visitVarInsn(Opcodes.ALOAD, argSlot);
                    BytecodeUtils.pushInt(mv, j);
                    mv.visitInsn(Opcodes.AALOAD);
                    Class<?> pt = paramTypes[j];
                    if (pt.isPrimitive()) {
                        BytecodeUtils.unboxPrimitive(mv, pt);
                    } else if (pt != Object.class) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST,
                                Type.getInternalName(pt));
                    }
                }

                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        interfaceInternalName,
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        true); // interface owner

                Class<?> rt = method.getReturnType();
                if (rt == void.class) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                } else if (rt.isPrimitive()) {
                    BytecodeUtils.boxPrimitive(mv, rt);
                }
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                // Interface proxy, non-Object method: throw AbstractMethodError
                mv.visitTypeInsn(Opcodes.NEW, "java/lang/AbstractMethodError");
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn("Cannot invoke super on interface method: "
                        + method.getName());
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "java/lang/AbstractMethodError",
                        "<init>", "(Ljava/lang/String;)V", false);
                mv.visitInsn(Opcodes.ATHROW);
            }

            nextLabel = branchLabel;
        }

        // Fallback: return null (unreachable in practice)
        if (nextLabel != null) {
            mv.visitLabel(nextLabel);
        }
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}

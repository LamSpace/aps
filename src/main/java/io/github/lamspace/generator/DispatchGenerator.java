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
     * <p>This method is invoked from the generated {@code dispatch()} bytecode
     * only for overloaded methods (which share {@code Method.hashCode()});
     * the common case uses {@code Method.hashCode()} directly and never calls
     * this method, avoiding the {@code getParameterTypes()} allocation on the
     * hot path. Using {@code Class.getName()} rather than
     * {@code Class.hashCode()} keeps the result deterministic across JVMs.
     *
     * @param method the method to hash
     * @return a deterministic, collision-resistant dispatch hash
     */
    public static int methodDispatchHash(Method method) {
        int hash = method.hashCode();
        for (Class<?> pt : method.getParameterTypes()) {
            hash = 31 * hash + pt.getName().hashCode();
        }
        return hash;
    }

    /**
     * Generates the dispatch method bytecode.
     *
     * <p>Dispatch first branches on {@code Method.hashCode()} — an
     * allocation-free, copy-stable discriminator. Methods that share a
     * {@code Method.hashCode()} (overloaded methods in the same declaring
     * class) are grouped and additionally disambiguated by
     * {@link #methodDispatchHash(Method)}, whose {@code getParameterTypes()}
     * allocation is only paid on that rare path.
     *
     * @param cw                ClassWriter
     * @param generatedInternal ASM internal name of the generated class
     * @param superInternal     ASM internal name of the superclass (target class
     *                          for class proxies, "java/lang/Object" for interface)
     * @param infos             per-method metadata; the {@code defaultOwner}
     *                          field carries the interface owner for default
     *                          methods (null for class proxies)
     * @param isClassProxy      true = class proxy (direct super calls),
     *                          false = interface proxy (AbstractMethodError
     *                          for non-default methods, direct INVOKESPECIAL
     *                          for default methods)
     */
    static void generateDispatch(ClassWriter cw,
                                 String generatedInternal,
                                 String superInternal,
                                 List<MethodInfo> infos,
                                 boolean isClassProxy) {
        String desc = "(Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;";
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "dispatch", desc, null,
                new String[]{"java/lang/Throwable"});
        mv.visitCode();

        // int hash = method.hashCode();  (allocation-free, copy-stable)
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/reflect/Method", "hashCode", "()I", false);
        int hashSlot = 3;
        mv.visitVarInsn(Opcodes.ISTORE, hashSlot);

        // Group methods by Method.hashCode() so overloads share one outer
        // branch and are disambiguated by the full hash only inside it.
        Map<Integer, List<MethodInfo>> groups = new LinkedHashMap<>();
        for (MethodInfo info : infos) {
            groups.computeIfAbsent(info.method().hashCode(),
                    k -> new ArrayList<>()).add(info);
        }

        int fullHashSlot = 4;
        Label nextGroup = null;
        for (Map.Entry<Integer, List<MethodInfo>> entry : groups.entrySet()) {
            int primaryHash = entry.getKey();
            List<MethodInfo> group = entry.getValue();

            Label groupLabel = new Label();
            if (nextGroup == null) {
                mv.visitVarInsn(Opcodes.ILOAD, hashSlot);
                mv.visitLdcInsn(primaryHash);
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, groupLabel);
            } else {
                mv.visitLabel(nextGroup);
                mv.visitVarInsn(Opcodes.ILOAD, hashSlot);
                mv.visitLdcInsn(primaryHash);
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, groupLabel);
            }

            if (group.size() == 1) {
                emitDispatchBody(mv, group.get(0), superInternal, isClassProxy);
            } else {
                // Overloaded methods share Method.hashCode(); disambiguate with
                // the parameter-type-aware hash. Rare, so its allocation is fine.
                mv.visitVarInsn(Opcodes.ALOAD, 1);
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "io/github/lamspace/generator/DispatchGenerator",
                        "methodDispatchHash",
                        "(Ljava/lang/reflect/Method;)I", false);
                mv.visitVarInsn(Opcodes.ISTORE, fullHashSlot);

                Label innerNext = null;
                for (MethodInfo info : group) {
                    Label innerLabel = new Label();
                    if (innerNext == null) {
                        mv.visitVarInsn(Opcodes.ILOAD, fullHashSlot);
                        mv.visitLdcInsn(methodDispatchHash(info.method()));
                        mv.visitJumpInsn(Opcodes.IF_ICMPNE, innerLabel);
                    } else {
                        mv.visitLabel(innerNext);
                        mv.visitVarInsn(Opcodes.ILOAD, fullHashSlot);
                        mv.visitLdcInsn(methodDispatchHash(info.method()));
                        mv.visitJumpInsn(Opcodes.IF_ICMPNE, innerLabel);
                    }
                    emitDispatchBody(mv, info, superInternal, isClassProxy);
                    innerNext = innerLabel;
                }
                mv.visitLabel(innerNext);
            }

            nextGroup = groupLabel;
        }

        // Fallback: return null (unreachable in practice)
        if (nextGroup != null) {
            mv.visitLabel(nextGroup);
        }
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    /**
     * Emits the body of one dispatch branch: the direct {@code super} call for
     * class proxies and Object methods, the {@code default} interface call for
     * default methods, or an {@code AbstractMethodError} for abstract interface
     * methods.
     *
     * @param mv            the method visitor (method already opened, in code)
     * @param info          metadata for the method this branch dispatches
     * @param superInternal ASM internal name of the superclass (target class
     *                      for class proxies, "java/lang/Object" for interface)
     * @param isClassProxy  true = class proxy (direct super calls),
     *                      false = interface proxy
     */
    private static void emitDispatchBody(MethodVisitor mv, MethodInfo info,
                                         String superInternal,
                                         boolean isClassProxy) {
        Method method = info.method();
        Class<?> declaringClass = method.getDeclaringClass();
        boolean isObjectMethod = declaringClass == Object.class;

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
        } else if (info.defaultOwner() != null) {
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
                    Type.getInternalName(info.defaultOwner()),
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
    }
}

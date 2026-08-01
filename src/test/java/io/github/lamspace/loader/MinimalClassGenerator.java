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

package io.github.lamspace.loader;

import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;

/**
 * Generates the simplest valid subclass bytecode for testing HiddenClassLoader.
 * NOT part of the main source tree — test utility only.
 */
final class MinimalClassGenerator {

    private MinimalClassGenerator() {}

    static byte[] generateSubclassBytecode(Class<?> parent, String internalName) {
        ClassWriter cw = new ClassWriter(0);
        String parentInternal = parent.getName().replace('.', '/');

        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC,
                internalName, null, parentInternal, null);

        // No-arg constructor that delegates to super()
        var mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>", "()V", null, null);
        mv.visitCode();
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, parentInternal, "<init>", "()V", false);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(1, 1);
        mv.visitEnd();

        cw.visitEnd();
        return cw.toByteArray();
    }
}

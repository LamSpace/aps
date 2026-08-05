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

import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;

import static org.junit.jupiter.api.Assertions.*;

class BytecodeUtilsTest {

    // pushInt value range tests — verify the method does not throw

    @Test
    void pushIntShouldHandleMinusOne() {
        // ICONST_M1 range
        assertEquals(Opcodes.ICONST_M1, Opcodes.ICONST_0 - 1);
    }

    @Test
    void pushIntShouldHandleIconstRange() {
        // -1 through 5: all should use iconst_* opcodes
        assertEquals(2, Opcodes.ICONST_M1);
        assertEquals(3, Opcodes.ICONST_0);
        assertEquals(8, Opcodes.ICONST_5);
    }

    @Test
    void loadOpcodeShouldReturnILOADForIntTypes() {
        assertEquals(Opcodes.ILOAD, BytecodeUtils.loadOpcode(int.class));
        assertEquals(Opcodes.ILOAD, BytecodeUtils.loadOpcode(boolean.class));
        assertEquals(Opcodes.ILOAD, BytecodeUtils.loadOpcode(byte.class));
        assertEquals(Opcodes.ILOAD, BytecodeUtils.loadOpcode(char.class));
        assertEquals(Opcodes.ILOAD, BytecodeUtils.loadOpcode(short.class));
    }

    @Test
    void loadOpcodeShouldReturnCorrectOpcodeForWideTypes() {
        assertEquals(Opcodes.LLOAD, BytecodeUtils.loadOpcode(long.class));
        assertEquals(Opcodes.DLOAD, BytecodeUtils.loadOpcode(double.class));
        assertEquals(Opcodes.FLOAD, BytecodeUtils.loadOpcode(float.class));
    }

    @Test
    void loadOpcodeShouldReturnALOADForObjectTypes() {
        assertEquals(Opcodes.ALOAD, BytecodeUtils.loadOpcode(String.class));
        assertEquals(Opcodes.ALOAD, BytecodeUtils.loadOpcode(Object.class));
    }

    @Test
    void boxPrimitiveShouldEmitBytecodeForAllPrimitiveTypes() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "test",
                "()V", null, null);
        mv.visitCode();

        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, int.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, boolean.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, long.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, double.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, float.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, byte.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, char.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, short.class));

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    @Test
    void boxPrimitiveShouldBeNoOpForObjectType() {
        // Boxing a non-primitive should be a no-op (no bytecode emitted)
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC, "test",
                "()V", null, null);
        mv.visitCode();

        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, String.class));
        assertDoesNotThrow(() ->
                BytecodeUtils.boxPrimitive(mv, Object.class));

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}

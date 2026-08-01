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

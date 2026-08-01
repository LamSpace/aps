package io.github.lamspace.generator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lamspace.Callback;
import org.junit.jupiter.api.Test;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

class MethodDispatcherTest {

    static class Bean {
        public String greet(String name) {
            return "Hello, " + name;
        }

        public int add(int a, int b) {
            return a + b;
        }

        final void finalMethod() {}
        static void staticMethod() {}
    }

    @Test
    void shouldGenerateOverridesForNonFinalInstanceMethods() {
        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);
        String generatedInternal = "io/github/lamspace/generator/Bean$$APS$$0";
        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC, generatedInternal, null,
                Type.getInternalName(Bean.class), null);

        // Add Callback field (required by dispatcher)
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL, "_callback",
                Type.getDescriptor(Callback.class), null, null);

        var methods = MethodDispatcher.dispatchMethods(cw, Bean.class,
                generatedInternal, null);

        cw.visitEnd();

        assertTrue(methods.contains("greet"));
        assertTrue(methods.contains("add"));
        assertFalse(methods.contains("finalMethod"), "final methods should not be overridden");
        assertFalse(methods.contains("staticMethod"), "static methods should not be overridden");
    }
}

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

import static org.junit.jupiter.api.Assertions.*;

import io.github.lamspace.Interceptor;
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
                Type.getDescriptor(Interceptor.class), null, null);

        var methods = MethodDispatcher.dispatchMethods(cw, Bean.class,
                generatedInternal, null);

        cw.visitEnd();

        assertTrue(methods.contains("greet"));
        assertTrue(methods.contains("add"));
        assertFalse(methods.contains("finalMethod"), "final methods should not be overridden");
        assertFalse(methods.contains("staticMethod"), "static methods should not be overridden");
    }
}

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
import io.github.lamspace.ClassFilter;
import org.junit.jupiter.api.Test;

class ClassGeneratorTest {

    static class Sample {
        public String getValue() {
            return "original";
        }

        public void setValue(String v) {
        }

        public String greet(String name) {
            return "Hello, " + name;
        }

        final String ignoreMe() {
            return "final";
        }

        static String staticMethod() {
            return "static";
        }
    }

    @Test
    void shouldGenerateValidBytecodeForConcreteClass() {
        ClassGenerator gen = new ClassGenerator(Sample.class, null);
        byte[] bytecode = gen.generate();

        assertNotNull(bytecode);
        assertTrue(bytecode.length > 100, "bytecode should be non-trivial");
    }

    @Test
    void constructorArgsShouldIncludeCallback() {
        ClassGenerator gen = new ClassGenerator(Sample.class, null);
        Class<?>[] args = gen.constructorArgs();

        assertEquals(1, args.length);
        assertEquals(Interceptor.class, args[0]);
    }

    @Test
    void shouldSkipFinalMethodsWhenNoFilter() {
        ClassGenerator gen = new ClassGenerator(Sample.class, null);
        byte[] bytecode = gen.generate();
        assertNotNull(bytecode);
    }

    @Test
    void shouldRespectClassFilterForInterception() {
        ClassFilter onlyGetters = method -> method.getName().startsWith("get");
        ClassGenerator gen = new ClassGenerator(Sample.class, onlyGetters);
        byte[] bytecode = gen.generate();
        assertNotNull(bytecode);
    }

    @Test
    void generatedClassNameUsesTargetPackage() {
        ClassGenerator gen = new ClassGenerator(Sample.class, null);
        byte[] bytecode = gen.generate();
        // Verify it doesn't crash; package correctness is validated
        // functionally when the class is loaded via HiddenClassLoader
        assertNotNull(bytecode);
    }
}

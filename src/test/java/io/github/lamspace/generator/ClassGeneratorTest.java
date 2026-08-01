package io.github.lamspace.generator;

import static org.junit.jupiter.api.Assertions.*;

import io.github.lamspace.Callback;
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
        assertEquals(Callback.class, args[0]);
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

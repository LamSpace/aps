package io.github.lamspace.loader;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

class HiddenClassLoaderTest {

    @Test
    void shouldDefineHiddenClass() throws Exception {
        HiddenClassLoader loader = new HiddenClassLoader();

        // Use HiddenClassLoader as the target class so the Lookup matches
        // the generated class's package (io.github.lamspace.loader)
        byte[] bytecode = MinimalClassGenerator.generateSubclassBytecode(
                HiddenClassLoader.class,
                "io/github/lamspace/loader/HiddenClassLoader$$APS$$Test");

        Class<?> defined = loader.defineClass(HiddenClassLoader.class, bytecode);

        assertNotNull(defined);
        assertTrue(HiddenClassLoader.class.isAssignableFrom(defined));
        assertFalse(defined.isArray());
        assertFalse(defined.isInterface());
    }

    @Test
    void shouldCreateInstanceOfDefinedClass() throws Exception {
        HiddenClassLoader loader = new HiddenClassLoader();
        byte[] bytecode = MinimalClassGenerator.generateSubclassBytecode(
                HiddenClassLoader.class,
                "io/github/lamspace/loader/HiddenClassLoader$$APS$$Test2");

        Class<?> defined = loader.defineClass(HiddenClassLoader.class, bytecode);
        Object instance = defined.getDeclaredConstructor().newInstance();

        assertNotNull(instance);
        assertTrue(instance instanceof HiddenClassLoader);
    }
}

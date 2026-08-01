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

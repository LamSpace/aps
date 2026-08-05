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

import static org.junit.jupiter.api.Assertions.*;

class ClinitRegistryTest {

    @Test
    void shouldReturnRegisteredEntriesOnDrain() {
        ClinitRegistry registry = new ClinitRegistry();

        registry.register(String.class,
                String.class.getDeclaredMethods()[0], "gen/String",
                "_method$0", 0);
        registry.register(Integer.class,
                Integer.class.getDeclaredMethods()[0], "gen/Integer",
                "_method$1", 1);

        var entries = registry.drain();
        assertEquals(2, entries.size());
        assertEquals("_method$0", entries.get(0).methodFieldName());
        assertEquals("_method$1", entries.get(1).methodFieldName());
    }

    @Test
    void shouldClearEntriesAfterDrain() {
        ClinitRegistry registry = new ClinitRegistry();

        registry.register(String.class,
                String.class.getDeclaredMethods()[0], "gen/String",
                "_method$0", 0);

        registry.drain();
        assertTrue(registry.drain().isEmpty(),
                "Registry should be empty after drain");
    }

    @Test
    void shouldIsolateInstances() {
        ClinitRegistry r1 = new ClinitRegistry();
        ClinitRegistry r2 = new ClinitRegistry();

        r1.register(String.class,
                String.class.getDeclaredMethods()[0], "gen/String",
                "_method_r1", 0);
        r2.register(Integer.class,
                Integer.class.getDeclaredMethods()[0], "gen/Integer",
                "_method_r2", 0);

        var e1 = r1.drain();
        var e2 = r2.drain();

        assertEquals(1, e1.size());
        assertEquals(1, e2.size());
        assertEquals("_method_r1", e1.get(0).methodFieldName());
        assertEquals("_method_r2", e2.get(0).methodFieldName());
    }

    @Test
    void shouldStartEmpty() {
        ClinitRegistry registry = new ClinitRegistry();
        assertTrue(registry.drain().isEmpty());
    }
}

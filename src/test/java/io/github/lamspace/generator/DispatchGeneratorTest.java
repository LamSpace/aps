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

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class DispatchGeneratorTest {

    @Test
    void methodDispatchHashShouldBeDeterministic() throws Exception {
        Method m = String.class.getMethod("length");
        int h1 = DispatchGenerator.methodDispatchHash(m);
        int h2 = DispatchGenerator.methodDispatchHash(m);
        assertEquals(h1, h2, "Same method should produce same hash");
    }

    @Test
    void overloadedMethodsShouldProduceDifferentHashes() throws Exception {
        Method indexOf1 = String.class.getMethod("indexOf", int.class);
        Method indexOf2 = String.class.getMethod("indexOf", String.class);

        int h1 = DispatchGenerator.methodDispatchHash(indexOf1);
        int h2 = DispatchGenerator.methodDispatchHash(indexOf2);

        assertNotEquals(h1, h2,
                "Overloaded methods must produce different hashes");
    }

    @Test
    void methodsWithSameNameAndSignatureShouldBeEqual() throws Exception {
        // hashCode of Class.getName() is deterministic across JVM instances
        Method m1 = String.class.getMethod("isEmpty");
        Method m2 = String.class.getMethod("isEmpty");

        int h1 = DispatchGenerator.methodDispatchHash(m1);
        int h2 = DispatchGenerator.methodDispatchHash(m2);

        assertEquals(h1, h2);
    }

    @Test
    void resolveHashesShouldProduceNoDuplicates() throws Exception {
        List<Method> methods = List.of(
                String.class.getMethod("length"),
                String.class.getMethod("isEmpty"),
                String.class.getMethod("charAt", int.class),
                String.class.getMethod("indexOf", int.class),
                String.class.getMethod("indexOf", String.class)
        );

        var result = DispatchGenerator.resolveHashes(methods);
        assertEquals(methods.size(), result.size());

        // All hashes must be unique
        long distinctCount = result.values().stream().distinct().count();
        assertEquals(methods.size(), distinctCount,
                "All resolved hashes must be unique");
    }

    @Test
    void resolveHashesShouldHandleEmptyList() {
        var result = DispatchGenerator.resolveHashes(new ArrayList<>());
        assertTrue(result.isEmpty());
    }
}

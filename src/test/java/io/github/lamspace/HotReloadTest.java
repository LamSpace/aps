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

package io.github.lamspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class HotReloadTest {

    public static class Greeter {
        public String hello(String name) {
            return "Hello, " + name;
        }
    }

    @Test
    void evictForcesRegeneration() {
        Greeter p1 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        Class<?> c1 = p1.getClass();
        Greeter p2 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        assertSame(c1, p2.getClass());   // cached: same class

        AcceleratedProxy.evict(Greeter.class);
        Greeter p3 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        assertNotSame(c1, p3.getClass()); // evicted: fresh class
    }

    @Test
    void oldInstanceSurvivesEviction() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> "intercepted");
        AcceleratedProxy.evict(Greeter.class);
        assertEquals("intercepted", p.hello("x"));
    }

    @Test
    void evictClassLoaderEvictsThatLoader() {
        Greeter p1 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        Class<?> c1 = p1.getClass();

        AcceleratedProxy.evictClassLoader(Greeter.class.getClassLoader());
        Greeter p2 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        assertNotSame(c1, p2.getClass());   // evicted: fresh class
    }

    @Test
    void evictClassLoaderIgnoresUnrelatedLoader() {
        Greeter p1 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        Class<?> c1 = p1.getClass();

        ClassLoader unrelated = new ClassLoader() {
        };
        AcceleratedProxy.evictClassLoader(unrelated);
        Greeter p2 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        assertSame(c1, p2.getClass());      // untouched: same cached class
    }

    @Test
    void evictIsIdempotentAndRejectsNull() {
        assertDoesNotThrow(() -> AcceleratedProxy.evict(Greeter.class));
        assertDoesNotThrow(() -> AcceleratedProxy.evictClassLoader(
                HotReloadTest.class.getClassLoader()));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.evict(null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.evictClassLoader(null));
    }
}

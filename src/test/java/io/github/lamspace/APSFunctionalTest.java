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

import io.github.lamspace.APS;
import io.github.lamspace.Callback;
import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class APSFunctionalTest {

    static class Greeter {
        public String hello(String name) {
            return "Hello, " + name;
        }

        public int add(int a, int b) {
            return a + b;
        }

        public void sideEffect() {
        }
    }

    @Test
    void shouldInterceptAndReturnModifiedValue() {
        Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) -> {
            String original = (String) APS.invokeSuper(obj, index, args);
            return "[intercepted] " + original;
        });

        String result = proxy.hello("World");
        assertTrue(result.startsWith("[intercepted]"));
        assertTrue(result.contains("Hello, World"));
    }

    @Test
    void shouldPassThroughToSuperWhenNotModified() {
        Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) ->
                APS.invokeSuper(obj, index, args));

        assertEquals("Hello, APS", proxy.hello("APS"));
    }

    @Test
    void shouldHandlePrimitiveReturnType() {
        Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) ->
                APS.invokeSuper(obj, index, args));

        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void shouldHandleVoidReturnType() {
        AtomicBoolean called = new AtomicBoolean(false);
        Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) -> {
            called.set(true);
            return APS.invokeSuper(obj, index, args);
        });

        proxy.sideEffect();
        assertTrue(called.get());
    }

    @Test
    void shouldWorkWithClassFilter() {
        Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) ->
                        "[filtered] " + APS.invokeSuper(obj, index, args),
                method -> method.getName().startsWith("hello"));

        assertEquals("[filtered] Hello, Alice", proxy.hello("Alice"));
        assertEquals(10, proxy.add(6, 4));
    }

    static class BeanWithNoDefaultConstructor {
        final String name;

        public BeanWithNoDefaultConstructor(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    @Test
    void shouldProxyClassWithoutDefaultConstructor() {
        BeanWithNoDefaultConstructor proxy = APS.create(
                BeanWithNoDefaultConstructor.class,
                (obj, method, index, args) -> APS.invokeSuper(obj, index, args),
                null,
                "Bob"
        );

        assertEquals("Bob", proxy.getName());
    }

    @Test
    void shouldHandleMethodThatThrows() {
        Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) -> {
            throw new RuntimeException("test exception");
        });

        assertThrows(RuntimeException.class, () -> proxy.hello("fail"));
    }

    @Test
    void shouldPropagateCheckedExceptionViaUndeclaredThrowable() {
        Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) -> {
            throw new Exception("checked exception from interceptor");
        });

        assertThrows(java.lang.reflect.UndeclaredThrowableException.class,
                () -> proxy.hello("x"));
    }
}

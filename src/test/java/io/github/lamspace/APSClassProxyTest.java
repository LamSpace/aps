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

class APSClassProxyTest {

    // ---- Target classes ----

    static class Greeter {
        public String hello(String name) {
            return "Hello, " + name;
        }
    }

    static class Calculator {
        public int add(int a, int b) {
            return a + b;
        }

        public long multiply(long a, long b) {
            return a * b;
        }

        public double divide(double a, double b) {
            return a / b;
        }

        public float sum(float a, float b) {
            return a + b;
        }

        public boolean isPositive(int n) {
            return n > 0;
        }

        public byte nextByte(byte b) {
            return (byte) (b + 1);
        }

        public char toUpper(char c) {
            return Character.toUpperCase(c);
        }

        public short doubleShort(short s) {
            return (short) (s * 2);
        }
    }

    static class StringOps {
        public String concat(String a, String b) {
            return a + b;
        }
    }

    static class Bean {
        private final String name;

        public Bean(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    static class VoidOps {
        public void run() {
        }
    }

    // ---- Basic interception ----

    @Test
    void shouldInterceptAndModifyReturn() {
        Greeter proxy = APS.proxy(Greeter.class, (obj, method, args) -> {
            Object result = APS.invokeSuper(obj, method, args);
            return "[" + result + "]";
        });

        assertEquals("[Hello, World]", proxy.hello("World"));
    }

    @Test
    void shouldPassThroughToSuper() {
        Greeter proxy = APS.proxy(Greeter.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));

        assertEquals("Hello, APS", proxy.hello("APS"));
    }

    @Test
    void shouldModifyArguments() {
        Calculator proxy = APS.proxy(Calculator.class, (obj, method, args) -> {
            args[0] = ((int) args[0]) * 10;
            args[1] = ((int) args[1]) * 10;
            return APS.invokeSuper(obj, method, args);
        });

        assertEquals(70, proxy.add(3, 4));
    }

    // ---- Primitive return types ----

    @Test
    void shouldHandleIntReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void shouldHandleLongReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals(12L, proxy.multiply(3L, 4L));
    }

    @Test
    void shouldHandleDoubleReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals(2.5, proxy.divide(5.0, 2.0), 0.001);
    }

    @Test
    void shouldHandleFloatReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals(7.0f, proxy.sum(3.0f, 4.0f), 0.001f);
    }

    @Test
    void shouldHandleBooleanReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertTrue(proxy.isPositive(5));
        assertFalse(proxy.isPositive(-1));
    }

    @Test
    void shouldHandleByteReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals((byte) 6, proxy.nextByte((byte) 5));
    }

    @Test
    void shouldHandleCharReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals('A', proxy.toUpper('a'));
    }

    @Test
    void shouldHandleShortReturn() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals((short) 20, proxy.doubleShort((short) 10));
    }

    // ---- Void method ----

    @Test
    void shouldHandleVoidMethod() {
        boolean[] called = {false};
        VoidOps proxy = APS.proxy(VoidOps.class, (obj, method, args) -> {
            called[0] = true;
            return APS.invokeSuper(obj, method, args);
        });

        proxy.run();
        assertTrue(called[0]);
    }

    // ---- Reference return type ----

    @Test
    void shouldHandleStringReturn() {
        StringOps proxy = APS.proxy(StringOps.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args));
        assertEquals("ab", proxy.concat("a", "b"));
    }

    // ---- Constructor arguments ----

    @Test
    void shouldProxyClassWithoutDefaultConstructor() {
        Bean proxy = APS.proxy(Bean.class,
                (obj, method, args) -> APS.invokeSuper(obj, method, args),
                null, "Bob");

        assertEquals("Bob", proxy.getName());
    }

    // ---- Filter ----

    @Test
    void shouldSkipFilteredMethods() {
        Calculator proxy = APS.proxy(Calculator.class,
                (obj, method, args) -> 999,
                method -> method.getName().startsWith("add"));

        assertEquals(999, proxy.add(1, 2));
        // multiply is not filtered — calls super directly
        assertEquals(12L, proxy.multiply(3L, 4L));
    }

    // ---- Exception propagation ----

    @Test
    void shouldPropagateRuntimeException() {
        Greeter proxy = APS.proxy(Greeter.class, (obj, method, args) -> {
            throw new RuntimeException("test error");
        });

        assertThrows(RuntimeException.class, () -> proxy.hello("fail"));
    }

    @Test
    void shouldWrapCheckedException() {
        Greeter proxy = APS.proxy(Greeter.class, (obj, method, args) -> {
            throw new Exception("checked error");
        });

        assertThrows(java.lang.reflect.UndeclaredThrowableException.class,
                () -> proxy.hello("fail"));
    }

    // ---- Null validation ----

    @Test
    void shouldRejectNullTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> APS.proxy(null, (obj, method, args) -> null));
    }

    @Test
    void shouldRejectNullInterceptor() {
        assertThrows(IllegalArgumentException.class,
                () -> APS.proxy(Greeter.class, null));
    }
}

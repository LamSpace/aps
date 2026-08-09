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

class AcceleratedProxyInterfaceProxyTest {

    // ---- Test interfaces ----

    interface Greeter {
        String hello(String name);
    }

    interface Calculator {
        int add(int a, int b);

        long multiply(long a, long b);

        double divide(double a, double b);

        float sum(float a, float b);

        boolean isPositive(int n);

        byte nextByte(byte b);

        char toUpper(char c);

        short doubleShort(short s);
    }

    interface StringOps {
        String concat(String a, String b);
    }

    interface VoidOps {
        void run();
    }

    interface GreeterWithDefault {
        String hello(String name);

        default String greet() {
            return "Hello, World";
        }
    }

    // ---- Basic interception ----

    @Test
    void shouldInterceptAndReturnModifiedValue() {
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                (obj, method, args) -> "[intercepted] " + args[0]);

        assertEquals("[intercepted] World", proxy.hello("World"));
    }

    @Test
    void shouldReturnFixedValue() {
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                (obj, method, args) -> "fixed");

        assertEquals("fixed", proxy.hello("anything"));
    }

    @Test
    void shouldModifyArguments() {
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
            args[0] = "[" + args[0] + "]";
            return args[0];
        });

        assertEquals("[World]", proxy.hello("World"));
    }

    // ---- Primitive return types ----

    @Test
    void shouldHandleIntReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> (int) args[0] + (int) args[1]);
        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void shouldHandleLongReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> (long) args[0] * (long) args[1]);
        assertEquals(12L, proxy.multiply(3L, 4L));
    }

    @Test
    void shouldHandleDoubleReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> (double) args[0] / (double) args[1]);
        assertEquals(2.5, proxy.divide(5.0, 2.0), 0.001);
    }

    @Test
    void shouldHandleFloatReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> (float) args[0] + (float) args[1]);
        assertEquals(7.0f, proxy.sum(3.0f, 4.0f), 0.001f);
    }

    @Test
    void shouldHandleBooleanReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> (int) args[0] > 0);
        assertTrue(proxy.isPositive(5));
    }

    @Test
    void shouldHandleByteReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> (byte) (((byte) args[0]) + 1));
        assertEquals((byte) 6, proxy.nextByte((byte) 5));
    }

    @Test
    void shouldHandleCharReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> Character.toUpperCase((char) args[0]));
        assertEquals('A', proxy.toUpper('a'));
    }

    @Test
    void shouldHandleShortReturn() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (obj, method, args) -> (short) (((short) args[0]) * 2));
        assertEquals((short) 20, proxy.doubleShort((short) 10));
    }

    // ---- Void method ----

    @Test
    void shouldHandleVoidMethod() {
        boolean[] called = {false};
        VoidOps proxy = AcceleratedProxy.proxy(VoidOps.class, (obj, method, args) -> {
            called[0] = true;
            return null;
        });

        proxy.run();
        assertTrue(called[0]);
    }

    // ---- Reference return type ----

    @Test
    void shouldHandleStringReturn() {
        StringOps proxy = AcceleratedProxy.proxy(StringOps.class,
                (obj, method, args) -> (String) args[0] + (String) args[1]);
        assertEquals("ab", proxy.concat("a", "b"));
    }

    // ---- Default methods ----

    @Test
    void shouldInterceptDefaultMethod() {
        GreeterWithDefault proxy = AcceleratedProxy.proxy(GreeterWithDefault.class,
                (obj, method, args) -> "[overridden]");

        assertEquals("[overridden]", proxy.greet());
    }

    // ---- invokeSuper on interface proxy ----

    @Test
    void invokeSuperShouldThrowForInterfaceMethod() {
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));

        assertThrows(AbstractMethodError.class, () -> proxy.hello("x"));
    }

    // ---- Filter ----

    @Test
    void shouldThrowForFilteredMethods() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().startsWith("add"),
                        (obj, method, args) -> 42));

        assertEquals(42, proxy.add(1, 2));
        assertThrows(AbstractMethodError.class, () -> proxy.multiply(3L, 4L));
    }

    // ---- Exception propagation ----

    @Test
    void shouldPropagateRuntimeException() {
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
            throw new RuntimeException("test error");
        });

        assertThrows(RuntimeException.class, () -> proxy.hello("fail"));
    }

    @Test
    void shouldWrapCheckedException() {
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
            throw new Exception("checked error");
        });

        assertThrows(java.lang.reflect.UndeclaredThrowableException.class,
                () -> proxy.hello("fail"));
    }

    // ---- Null validation ----

    @Test
    void shouldRejectNullTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.proxy(null, (obj, method, args) -> null));
    }

    @Test
    void shouldRejectNullInterceptor() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.proxy(Runnable.class,
                        (Interceptor) null));
    }
}

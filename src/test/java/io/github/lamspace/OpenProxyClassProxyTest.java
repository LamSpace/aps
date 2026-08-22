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

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class OpenProxyClassProxyTest {

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
        Greeter proxy = OpenProxy.proxy(Greeter.class, (obj, method, args) -> {
            Object result = OpenProxy.invokeSuper(obj, method, args);
            return "[" + result + "]";
        });

        assertEquals("[Hello, World]", proxy.hello("World"));
    }

    @Test
    void shouldPassThroughToSuper() {
        Greeter proxy = OpenProxy.proxy(Greeter.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));

        assertEquals("Hello, OpenProxy", proxy.hello("OpenProxy"));
    }

    @Test
    void generatedClassNameShouldCarryOpenProxyMarker() {
        Greeter proxy = OpenProxy.proxy(Greeter.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));

        assertTrue(proxy.getClass().getName().contains("$$OpenProxy$$"),
                "generated class name should carry the $$OpenProxy$$ marker: "
                        + proxy.getClass().getName());
    }

    @Test
    void shouldModifyArguments() {
        Calculator proxy = OpenProxy.proxy(Calculator.class, (obj, method, args) -> {
            args[0] = ((int) args[0]) * 10;
            args[1] = ((int) args[1]) * 10;
            return OpenProxy.invokeSuper(obj, method, args);
        });

        assertEquals(70, proxy.add(3, 4));
    }

    // ---- Primitive return types ----

    @Test
    void shouldHandleIntReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void shouldHandleLongReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals(12L, proxy.multiply(3L, 4L));
    }

    @Test
    void shouldHandleDoubleReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals(2.5, proxy.divide(5.0, 2.0), 0.001);
    }

    @Test
    void shouldHandleFloatReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals(7.0f, proxy.sum(3.0f, 4.0f), 0.001f);
    }

    @Test
    void shouldHandleBooleanReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertTrue(proxy.isPositive(5));
        assertFalse(proxy.isPositive(-1));
    }

    @Test
    void shouldHandleByteReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals((byte) 6, proxy.nextByte((byte) 5));
    }

    @Test
    void shouldHandleCharReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals('A', proxy.toUpper('a'));
    }

    @Test
    void shouldHandleShortReturn() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals((short) 20, proxy.doubleShort((short) 10));
    }

    // ---- Void method ----

    @Test
    void shouldHandleVoidMethod() {
        boolean[] called = {false};
        VoidOps proxy = OpenProxy.proxy(VoidOps.class, (obj, method, args) -> {
            called[0] = true;
            return OpenProxy.invokeSuper(obj, method, args);
        });

        proxy.run();
        assertTrue(called[0]);
    }

    // ---- Reference return type ----

    @Test
    void shouldHandleStringReturn() {
        StringOps proxy = OpenProxy.proxy(StringOps.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        assertEquals("ab", proxy.concat("a", "b"));
    }

    // ---- Constructor arguments ----

    @Test
    void shouldProxyClassWithoutDefaultConstructor() {
        Bean proxy = OpenProxy.proxy(Bean.class,
                new Object[]{"Bob"},
                Group.otherwise((obj, method, args) ->
                        OpenProxy.invokeSuper(obj, method, args)));

        assertEquals("Bob", proxy.getName());
    }

    // ---- Filter ----

    @Test
    void shouldSkipFilteredMethods() {
        Calculator proxy = OpenProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().startsWith("add"),
                        (obj, method, args) -> 999));

        assertEquals(999, proxy.add(1, 2));
        // multiply is not filtered — calls super directly
        assertEquals(12L, proxy.multiply(3L, 4L));
    }

    // ---- Exception propagation ----

    @Test
    void shouldPropagateRuntimeException() {
        Greeter proxy = OpenProxy.proxy(Greeter.class, (obj, method, args) -> {
            throw new RuntimeException("test error");
        });

        assertThrows(RuntimeException.class, () -> proxy.hello("fail"));
    }

    @Test
    void shouldWrapCheckedException() {
        Greeter proxy = OpenProxy.proxy(Greeter.class, (obj, method, args) -> {
            throw new Exception("checked error");
        });

        assertThrows(java.lang.reflect.UndeclaredThrowableException.class,
                () -> proxy.hello("fail"));
    }

    // ---- Null validation ----

    @Test
    void shouldRejectNullTarget() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenProxy.proxy((Class<?>) null, (obj, method, args) -> null));
    }

    @Test
    void shouldRejectNullInterceptor() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenProxy.proxy(Greeter.class,
                        (Interceptor) null));
    }

    // ---- Overloaded methods ----

    static class OverloadedTarget {
        public String greet(String name) {
            return "Hello, " + name;
        }

        public String greet(int count) {
            return "Count: " + count;
        }

        public String greet(double value) {
            return "Value: " + String.format("%.1f", value);
        }

        public String greet(String name, int count) {
            return name + " x" + count;
        }
    }

    @Test
    void shouldDispatchOverloadedMethodsCorrectly() throws Throwable {
        OverloadedTarget proxy = OpenProxy.proxy(OverloadedTarget.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));

        assertEquals("Hello, World", proxy.greet("World"));
        assertEquals("Count: 42", proxy.greet(42));
        assertEquals("Value: 3.1", proxy.greet(3.1));
        assertEquals("OpenProxy x3", proxy.greet("OpenProxy", 3));
    }

    // ---- Cache behavior ----

    @Test
    void shouldReuseCachedProxyClass() {
        Greeter p1 = OpenProxy.proxy(Greeter.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
        Greeter p2 = OpenProxy.proxy(Greeter.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));

        // Same target class + same filter (null) → same proxy class reused
        assertSame(p1.getClass(), p2.getClass(),
                "Repeated proxy() with same params should reuse cached class");
    }

    @Test
    void shouldUseDifferentClassesForDifferentFilters() {
        Greeter p1 = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("hello"),
                        (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args)));
        Greeter p2 = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("goodbye"),
                        (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args)));

        assertNotSame(p1.getClass(), p2.getClass(),
                "Different filters should produce different proxy classes");
    }

    @Test
    void shouldDispatchOverloadedMethodsViaDispatchMethod() throws Throwable {
        OverloadedTarget proxy = OpenProxy.proxy(OverloadedTarget.class,
                (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));

        Method greetString = OverloadedTarget.class.getMethod("greet", String.class);
        Method greetInt = OverloadedTarget.class.getMethod("greet", int.class);
        Method greetDouble = OverloadedTarget.class.getMethod("greet", double.class);
        Method greetStringInt = OverloadedTarget.class.getMethod("greet", String.class, int.class);

        // Verify that each overloaded method dispatches to the correct branch
        assertEquals("Hello, OpenProxy",
                ((DispatchTarget) proxy).dispatch(greetString, new Object[]{"OpenProxy"}));
        assertEquals("Count: 7",
                ((DispatchTarget) proxy).dispatch(greetInt, new Object[]{7}));
        assertEquals("Value: 2.7",
                ((DispatchTarget) proxy).dispatch(greetDouble, new Object[]{2.7}));
        assertEquals("Test x5",
                ((DispatchTarget) proxy).dispatch(greetStringInt, new Object[]{"Test", 5}));
    }
}

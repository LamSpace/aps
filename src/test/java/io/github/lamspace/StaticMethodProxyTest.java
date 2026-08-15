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

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class StaticMethodProxyTest {

    public static class Statics {
        public static int add(int a, int b) { return a + b; }
        public static String greet(String name) { return "Hello, " + name; }
        public static void sideEffect(List<String> log) { log.add("ran"); }
        public static long big(long v) { return v * 2; }
        public static double div(double v) { return v / 2; }
        public static boolean flag(boolean b) { return !b; }
        public static int sum(int[] xs) { int t = 0; for (int x : xs) t += x; return t; }
        public static String[] names() { return new String[]{"a", "b"}; }
        public static int overloaded() { return 1; }
        public static int overloaded(int x) { return x + 1; }
        public static int overloaded(String s) { return s.length(); }
        public static final int finalled() { return 99; }
        private static int priv() { return 7; }
    }

    public static class Parent {
        public static int inherited() { return 10; }
    }

    public static class Child extends Parent {
        public static int own() { return 20; }
    }

    public static class RedeclaredChild extends Parent {
        public static int inherited() { return 30; }
    }

    private static Interceptor callOriginal() {
        return (o, m, a) -> m.invoke(null, a);
    }

    @Test
    void passthroughCallsOriginal() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.of(m -> false, (o, m, a) -> null));
        assertEquals(5, (Integer) proxyClass
                .getMethod("add", int.class, int.class).invoke(null, 2, 3));
    }

    @Test
    void interceptedReceivesNullProxyAndCorrectMethod() throws Exception {
        AtomicReference<Object> proxyRef = new AtomicReference<>(new Object());
        AtomicReference<Method> methodRef = new AtomicReference<>();
        Object[][] argsRef = new Object[1][];
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise((o, m, a) -> {
                    proxyRef.set(o);
                    methodRef.set(m);
                    argsRef[0] = a;
                    return m.invoke(null, a);
                }));
        Object result = proxyClass.getMethod("greet", String.class)
                .invoke(null, "World");
        assertEquals("Hello, World", result);
        assertNull(proxyRef.get());
        assertEquals("greet", methodRef.get().getName());
        assertArrayEquals(new Object[]{"World"}, argsRef[0]);
    }

    @Test
    void callOriginalReturnsOriginalResult() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        assertEquals("Hello, X", proxyClass
                .getMethod("greet", String.class).invoke(null, "X"));
    }

    @Test
    void primitiveAndReferenceReturnTypes() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        assertEquals(4L, (Long) proxyClass.getMethod("big", long.class)
                .invoke(null, 2L));
        assertEquals(2.5d, (Double) proxyClass.getMethod("div", double.class)
                .invoke(null, 5.0d));
        assertTrue((Boolean) proxyClass.getMethod("flag", boolean.class)
                .invoke(null, false));
        assertEquals(5, (Integer) proxyClass.getMethod("add", int.class, int.class)
                .invoke(null, 2, 3));
    }

    @Test
    void voidReturnType() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        List<String> log = new ArrayList<>();
        Object result = proxyClass.getMethod("sideEffect", List.class)
                .invoke(null, log);
        assertNull(result);
        assertEquals(List.of("ran"), log);
    }

    @Test
    void arrayParameterAndReturn() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        assertEquals(6, (Integer) proxyClass.getMethod("sum", int[].class)
                .invoke(null, new int[]{1, 2, 3}));
        assertArrayEquals(new String[]{"a", "b"},
                (String[]) proxyClass.getMethod("names").invoke(null));
    }

    @Test
    void overloadedStaticsDispatchByParams() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        assertEquals(1, (Integer) proxyClass.getMethod("overloaded").invoke(null));
        assertEquals(3, (Integer) proxyClass.getMethod("overloaded", int.class)
                .invoke(null, 2));
        assertEquals(4, (Integer) proxyClass.getMethod("overloaded", String.class)
                .invoke(null, "abcd"));
    }

    @Test
    void inheritedStaticMethodShadowed() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Child.class,
                Group.otherwise(callOriginal()));
        assertEquals(10, (Integer) proxyClass.getMethod("inherited").invoke(null));
        assertEquals(20, (Integer) proxyClass.getMethod("own").invoke(null));
    }

    @Test
    void redeclaredStaticShadowsParent() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(RedeclaredChild.class,
                Group.otherwise(callOriginal()));
        assertEquals(30, (Integer) proxyClass.getMethod("inherited").invoke(null));
    }

    @Test
    void finalAndPrivateStaticsSkipped() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        assertThrows(NoSuchMethodException.class,
                () -> proxyClass.getMethod("finalled"));
        assertThrows(NoSuchMethodException.class,
                () -> proxyClass.getMethod("priv"));
    }

    @Test
    void groupMatchingBindsDifferentInterceptors() throws Exception {
        AtomicReference<String> seen = new AtomicReference<>();
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.of(m -> m.getName().equals("add"),
                        (o, m, a) -> { seen.set("add"); return m.invoke(null, a); }),
                Group.otherwise((o, m, a) -> { seen.set("other"); return m.invoke(null, a); }));
        proxyClass.getMethod("add", int.class, int.class).invoke(null, 1, 2);
        assertEquals("add", seen.get());
        proxyClass.getMethod("greet", String.class).invoke(null, "x");
        assertEquals("other", seen.get());
    }

    @Test
    void sharedInterceptorAcrossGroupsRoutesCorrectly() throws Exception {
        AtomicInteger count = new AtomicInteger();
        Interceptor shared = (o, m, a) -> {
            count.incrementAndGet();
            return m.invoke(null, a);
        };
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.of(m -> m.getName().equals("add"), shared),
                Group.of(m -> m.getName().equals("greet"), shared));
        proxyClass.getMethod("add", int.class, int.class).invoke(null, 1, 2);
        proxyClass.getMethod("greet", String.class).invoke(null, "x");
        assertEquals(2, count.get());
    }

    @Test
    void runtimeAndCheckedExceptionsPropagate() throws Exception {
        Class<?> rt = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise((o, m, a) -> { throw new IllegalStateException("boom"); }));
        InvocationTargetException e = assertThrows(InvocationTargetException.class,
                () -> rt.getMethod("add", int.class, int.class).invoke(null, 1, 2));
        assertInstanceOf(IllegalStateException.class, e.getCause());

        Class<?> ch = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise((o, m, a) -> { throw new Exception("checked"); }));
        InvocationTargetException ce = assertThrows(InvocationTargetException.class,
                () -> ch.getMethod("add", int.class, int.class).invoke(null, 1, 2));
        assertInstanceOf(UndeclaredThrowableException.class, ce.getCause());
        assertInstanceOf(Exception.class,
                ((UndeclaredThrowableException) ce.getCause()).getCause());
    }

    @Test
    void errorPropagatesAsIs() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise((o, m, a) -> { throw new AssertionError("boom"); }));
        InvocationTargetException e = assertThrows(InvocationTargetException.class,
                () -> proxyClass.getMethod("add", int.class, int.class).invoke(null, 1, 2));
        assertInstanceOf(AssertionError.class, e.getCause());
    }

    @Test
    void methodHandleInvocationWorks() throws Throwable {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        MethodHandle mh = MethodHandles.lookup().findStatic(proxyClass, "add",
                MethodType.methodType(int.class, int.class, int.class));
        assertEquals(5, (Integer) mh.invoke(2, 3));
    }

    @Test
    void eachCallReturnsDistinctClass() {
        Class<?> a = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        Class<?> b = AcceleratedProxy.proxyStatic(Statics.class,
                Group.otherwise(callOriginal()));
        assertNotSame(a, b);
    }

    @Test
    void convenienceOverloadMatchesOtherwise() throws Exception {
        Class<?> proxyClass = AcceleratedProxy.proxyStatic(Statics.class,
                callOriginal());
        assertEquals(5, (Integer) proxyClass
                .getMethod("add", int.class, int.class).invoke(null, 2, 3));
    }

    @Test
    void invalidArgumentsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxyStatic((Class<?>) null,
                        Group.otherwise(callOriginal())));
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxyStatic(Statics.class));
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxyStatic(Runnable.class,
                        Group.otherwise(callOriginal())));
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxyStatic(Statics.class, (Interceptor) null));
    }
}

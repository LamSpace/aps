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

import java.lang.reflect.Constructor;
import java.lang.reflect.UndeclaredThrowableException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class ConstructorInterceptionTest {

    static class Ordered {
        static final List<String> EVENTS = new ArrayList<>();

        Ordered() {
            EVENTS.add("super");
        }
    }

    static class Point {
        private final int x;
        private final int y;

        Point(int x, int y) {
            this.x = x;
            this.y = y;
        }

        int x() {
            return x;
        }
    }

    static class Boxed {
        final int i;
        final long l;
        final double d;
        final boolean b;
        final String s;

        Boxed(int i, long l, double d, boolean b, String s) {
            this.i = i;
            this.l = l;
            this.d = d;
            this.b = b;
            this.s = s;
        }
    }

    static class Greeter {
        public String hello(String name) {
            return "Hello, " + name;
        }
    }

    private static Group passthrough() {
        return Group.otherwise(
                (o, m, a) -> AcceleratedProxy.invokeSuper(o, m, a));
    }

    // ---- Task 1: ordering, correct ctor/args, after, default after ----

    @Test
    void invokesBeforeSuperAfterInOrder() {
        Ordered.EVENTS.clear();
        ConstructorInterceptor ci = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                Ordered.EVENTS.add("before");
                return args;
            }

            @Override
            public void after(Object proxy, Constructor<?> ctor, Object[] args) {
                Ordered.EVENTS.add("after");
            }
        };
        AcceleratedProxy.proxy(Ordered.class, ci, passthrough());
        assertEquals(List.of("before", "super", "after"), Ordered.EVENTS);
    }

    @Test
    void beforeReceivesCorrectConstructorAndArgs() {
        Class<?>[][] ctorParams = new Class<?>[1][];
        Object[][] received = new Object[1][];
        ConstructorInterceptor ci = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                ctorParams[0] = ctor.getParameterTypes();
                received[0] = args;
                return args;
            }
        };
        Point proxy = AcceleratedProxy.proxy(Point.class, new Object[]{3, 4},
                ci, passthrough());
        assertArrayEquals(new Class<?>[]{int.class, int.class}, ctorParams[0]);
        assertArrayEquals(new Object[]{3, 4}, received[0]);
        assertEquals(3, proxy.x());
    }

    @Test
    void afterReceivesProxyInstance() {
        AtomicReference<Object> captured = new AtomicReference<>();
        ConstructorInterceptor ci = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                return args;
            }

            @Override
            public void after(Object proxy, Constructor<?> ctor, Object[] args) {
                captured.set(proxy);
            }
        };
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class, ci,
                passthrough());
        assertSame(proxy, captured.get());
    }

    @Test
    void defaultAfterIsNoOp() {
        ConstructorInterceptor ci = (ctor, args) -> args;
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class, ci,
                passthrough());
        assertNotNull(proxy);
        assertEquals("Hello, X", proxy.hello("X"));
    }

    // ---- Task 2: argument rewriting ----

    @Test
    void beforeRewritesArguments() {
        ConstructorInterceptor ci = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                return new Object[]{20, 30L, 4.5d, true, "rewritten"};
            }
        };
        Boxed proxy = AcceleratedProxy.proxy(Boxed.class,
                new Object[]{1, 2L, 3.0d, false, "orig"}, ci, passthrough());
        assertEquals(20, proxy.i);
        assertEquals(30L, proxy.l);
        assertEquals(4.5d, proxy.d);
        assertTrue(proxy.b);
        assertEquals("rewritten", proxy.s);
    }

    @Test
    void beforeMayReturnSameArray() {
        ConstructorInterceptor ci = (ctor, args) -> args;
        Boxed proxy = AcceleratedProxy.proxy(Boxed.class,
                new Object[]{1, 2L, 3.0d, false, "orig"}, ci, passthrough());
        assertEquals(1, proxy.i);
        assertEquals(2L, proxy.l);
        assertEquals("orig", proxy.s);
    }

    // ---- Task 3: veto ----

    @Test
    void beforeVetoWithRuntimeException() {
        ConstructorInterceptor ci = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                throw new IllegalStateException("veto");
            }
        };
        assertThrows(IllegalStateException.class,
                () -> AcceleratedProxy.proxy(Greeter.class, ci,
                        passthrough()));
    }

    @Test
    void beforeVetoWithCheckedException() {
        ConstructorInterceptor ci = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args)
                    throws Exception {
                throw new Exception("checked veto");
            }
        };
        UndeclaredThrowableException ex =
                assertThrows(UndeclaredThrowableException.class,
                        () -> AcceleratedProxy.proxy(Greeter.class, ci,
                                passthrough()));
        assertInstanceOf(Exception.class, ex.getCause());
        assertEquals("checked veto", ex.getCause().getMessage());
    }

    // ---- Task 4: cache correctness + convenience overloads ----

    @Test
    void sameClassSharedAcrossInterceptorInstances() {
        AtomicInteger aCount = new AtomicInteger();
        AtomicInteger bCount = new AtomicInteger();
        ConstructorInterceptor a = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                aCount.incrementAndGet();
                return args;
            }
        };
        ConstructorInterceptor b = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                bCount.incrementAndGet();
                return args;
            }
        };
        Greeter pa = AcceleratedProxy.proxy(Greeter.class, a, passthrough());
        Greeter pb = AcceleratedProxy.proxy(Greeter.class, b, passthrough());
        assertSame(pa.getClass(), pb.getClass());
        assertEquals(1, aCount.get());
        assertEquals(1, bCount.get());
    }

    @Test
    void interceptedAndNonInterceptedUseDistinctClasses() {
        Greeter plain = AcceleratedProxy.proxy(Greeter.class,
                (o, m, a) -> AcceleratedProxy.invokeSuper(o, m, a));
        Greeter intercepted = AcceleratedProxy.proxy(Greeter.class,
                (ConstructorInterceptor) (ctor, args) -> args, passthrough());
        assertNotSame(plain.getClass(), intercepted.getClass());
    }

    @Test
    void convenienceOverloadMatchesOtherwiseGroup() {
        AtomicInteger count = new AtomicInteger();
        ConstructorInterceptor ci = new ConstructorInterceptor() {
            @Override
            public Object[] before(Constructor<?> ctor, Object[] args) {
                count.incrementAndGet();
                return args;
            }
        };
        Interceptor in = (o, m, a) -> AcceleratedProxy.invokeSuper(o, m, a);
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class, in, ci);
        assertEquals("Hello, X", proxy.hello("X"));
        assertEquals(1, count.get());
    }

    @Test
    void nullCtorInterceptorThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(Greeter.class,
                        (ConstructorInterceptor) null, passthrough()));
    }

    @Test
    void interfaceTargetRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(Runnable.class,
                        (ConstructorInterceptor) (ctor, args) -> args,
                        passthrough()));
    }
}

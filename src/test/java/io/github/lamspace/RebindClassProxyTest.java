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

class RebindClassProxyTest {

    public static class Greeter {
        public String hello(String name) { return "Hello, " + name; }
    }

    public static class Pair {
        public String a() { return "a"; }
        public String b() { return "b"; }
    }

    private static Interceptor constant(String value) {
        return (o, m, a) -> value;
    }

    @Test
    void rebindSwapsInterceptor() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("old"));
        assertEquals("old", p.hello("x"));
        AcceleratedProxy.rebind(p, constant("new"));
        assertEquals("new", p.hello("x"));
    }

    @Test
    void rebindMultiInterceptorPreservesIndices() {
        Pair p = AcceleratedProxy.proxy(Pair.class,
                Group.of(m -> m.getName().equals("a"), constant("A1")),
                Group.otherwise(constant("B1")));
        assertEquals("A1", p.a());
        assertEquals("B1", p.b());

        AcceleratedProxy.rebind(p,
                new Interceptor[]{constant("A2"), constant("B2")});
        assertEquals("A2", p.a());
        assertEquals("B2", p.b());
    }

    @Test
    void rebindLengthMismatchThrows() {
        Pair p = AcceleratedProxy.proxy(Pair.class,
                Group.of(m -> m.getName().equals("a"), constant("A1")),
                Group.otherwise(constant("B1")));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, new Interceptor[]{constant("x")}));
    }

    @Test
    void passthroughMethodUnaffectedByRebind() {
        Pair p = AcceleratedProxy.proxy(Pair.class,
                Group.of(m -> m.getName().equals("a"), constant("A1")));
        assertEquals("A1", p.a());
        assertEquals("b", p.b());   // matched by no Group -> passthrough

        AcceleratedProxy.rebind(p, constant("A2"));
        assertEquals("A2", p.a());
        assertEquals("b", p.b());   // still passthrough, no interceptor touched
    }

    @Test
    void rebindRejectsNullAndNonProxy() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind("not a proxy", constant("x")));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(null, constant("x")));
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("old"));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor) null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor[]) null));
    }

    @Test
    void invokeSuperStillWorksAfterRebind() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, (o, m, a) ->
                AcceleratedProxy.invokeSuper(o, m, a) + " (intercepted)");
        assertEquals("Hello, x (intercepted)", p.hello("x"));
        AcceleratedProxy.rebind(p, (o, m, a) ->
                "REBOUND:" + AcceleratedProxy.invokeSuper(o, m, a));
        assertEquals("REBOUND:Hello, x", p.hello("x"));
    }

    @Test
    void rebindIsPerInstance() {
        Greeter p1 = AcceleratedProxy.proxy(Greeter.class, constant("one"));
        Greeter p2 = AcceleratedProxy.proxy(Greeter.class, constant("two"));
        assertSame(p1.getClass(), p2.getClass());  // same cached class
        AcceleratedProxy.rebind(p1, constant("one-R"));
        assertEquals("one-R", p1.hello("x"));
        assertEquals("two", p2.hello("x"));
    }

    @Test
    void singleOverloadMatchesArray() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("old"));
        AcceleratedProxy.rebind(p, constant("new"));
        assertEquals("new", p.hello("x"));
        AcceleratedProxy.rebind(p, new Interceptor[]{constant("arr")});
        assertEquals("arr", p.hello("x"));
    }

    @Test
    void repeatedRebindReplacesCleanly() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("0"));
        AcceleratedProxy.rebind(p, constant("1"));
        AcceleratedProxy.rebind(p, constant("2"));
        assertEquals("2", p.hello("x"));
    }

    @Test
    void rebindPassthroughOnlyProxyRejectsNonEmptyArray() {
        // No method matches any Group -> 0 distinct interceptors, all passthrough.
        Greeter p = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> false, constant("unused")));
        assertEquals("Hello, x", p.hello("x"));   // passthrough to super

        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, constant("x")));   // length 1, expected 0
        assertDoesNotThrow(() -> AcceleratedProxy.rebind(p, new Interceptor[]{}));
        assertEquals("Hello, x", p.hello("x"));   // still passthrough
    }
}

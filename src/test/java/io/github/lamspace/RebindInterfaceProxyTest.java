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

class RebindInterfaceProxyTest {

    public interface Echo {
        String echo(String s);
    }

    public interface Multi {
        String a();

        String b();
    }

    private static Interceptor constant(String value) {
        return (o, m, a) -> value;
    }

    @Test
    void rebindInterfaceProxySwapsInterceptor() {
        Echo p = AcceleratedProxy.proxy(Echo.class, constant("old"));
        assertEquals("old", p.echo("x"));
        AcceleratedProxy.rebind(p, constant("new"));
        assertEquals("new", p.echo("x"));
    }

    @Test
    void rebindIsPerInstance() {
        Echo p1 = AcceleratedProxy.proxy(Echo.class, constant("one"));
        Echo p2 = AcceleratedProxy.proxy(Echo.class, constant("two"));
        assertSame(p1.getClass(), p2.getClass());
        AcceleratedProxy.rebind(p1, constant("one-R"));
        assertEquals("one-R", p1.echo("x"));
        assertEquals("two", p2.echo("x"));
    }

    @Test
    void rebindRejectsNullAndNonProxy() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(new Object(), constant("x")));
        Echo p = AcceleratedProxy.proxy(Echo.class, constant("old"));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor) null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor[]) null));
    }

    @Test
    void rebindMultiInterceptorPreservesIndices() {
        Multi p = AcceleratedProxy.proxy(Multi.class,
                Group.of(m -> m.getName().equals("a"), constant("A1")),
                Group.otherwise(constant("B1")));
        assertEquals("A1", p.a());
        assertEquals("B1", p.b());

        AcceleratedProxy.rebind(p,
                new Interceptor[]{constant("A2"), constant("B2")});
        assertEquals("A2", p.a());
        assertEquals("B2", p.b());
    }
}

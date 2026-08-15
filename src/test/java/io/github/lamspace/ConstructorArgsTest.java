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

class ConstructorArgsTest {

    static class Primitives {
        final int i;
        final long l;
        final double d;
        final float f;
        final boolean b;
        final byte by;
        final char c;
        final short s;

        Primitives(int i, long l, double d, float f, boolean b, byte by,
                   char c, short s) {
            this.i = i;
            this.l = l;
            this.d = d;
            this.f = f;
            this.b = b;
            this.by = by;
            this.c = c;
            this.s = s;
        }
    }

    static class Named {
        final String name;

        Named(String name) {
            this.name = name;
        }
    }

    private static Group passthrough() {
        return Group.otherwise(
                (o, m, a) -> AcceleratedProxy.invokeSuper(o, m, a));
    }

    @Test
    void boxedPrimitiveArgs() {
        Primitives proxy = AcceleratedProxy.proxy(Primitives.class,
                new Object[]{1, 2L, 3.0d, 4.0f, true, (byte) 5, 'c',
                        (short) 6},
                passthrough());
        assertEquals(1, proxy.i);
        assertEquals(2L, proxy.l);
        assertEquals(3.0d, proxy.d);
        assertEquals(4.0f, proxy.f);
        assertTrue(proxy.b);
        assertEquals((byte) 5, proxy.by);
        assertEquals('c', proxy.c);
        assertEquals((short) 6, proxy.s);
    }

    @Test
    void referenceArg() {
        Named proxy = AcceleratedProxy.proxy(Named.class,
                new Object[]{"hello"}, passthrough());
        assertEquals("hello", proxy.name);
    }

    @Test
    void nullArg() {
        Named proxy = AcceleratedProxy.proxy(Named.class,
                new Object[]{null}, passthrough());
        assertNull(proxy.name);
    }
}

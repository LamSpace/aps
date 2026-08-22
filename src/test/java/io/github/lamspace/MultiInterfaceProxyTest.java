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

class MultiInterfaceProxyTest {

    interface Greeter {
        String hello(String name);
    }

    interface Auditable {
        String audit();
    }

    interface Named {
        String hello(String name);          // same signature as Greeter.hello
    }

    interface DefaultGreeter {
        String hello(String name);

        default String greet() {
            return "Hello, World";
        }
    }

    interface AbstractGreeter {
        String greet();                     // abstract same-signature as default
    }

    interface DefaultGreeter2 {
        default String greet() {
            return "Hello again";
        }
    }

    interface NumberGreeter {
        int hello(String name);             // different return type conflict
    }

    interface Parent {
        String inherited();
    }

    interface Child extends Parent {
        String own();
    }

    @Test
    void proxiesMultipleInterfaces() {
        Object p = OpenProxy.proxy(
                new Class<?>[]{Greeter.class, Auditable.class},
                (obj, method, args) -> "[" + method.getName() + "]");
        Greeter g = (Greeter) p;
        Auditable a = (Auditable) p;
        assertSame(g, a);
        assertEquals("[hello]", g.hello("x"));
        assertEquals("[audit]", a.audit());
    }

    @Test
    void mergesSharedMethod() {
        Object p = OpenProxy.proxy(
                new Class<?>[]{Greeter.class, Named.class},
                (obj, method, args) -> "[" + args[0] + "]");
        assertEquals("[World]", ((Greeter) p).hello("World"));
        assertEquals("[World]", ((Named) p).hello("World"));
    }

    @Test
    void oneDefaultPlusAbstractInvokesDefault() {
        Object p = OpenProxy.proxy(
                new Class<?>[]{DefaultGreeter.class, AbstractGreeter.class},
                (obj, method, args) -> {
                    if (method.isDefault()) {
                        return OpenProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals("Hello, World", ((DefaultGreeter) p).greet());
    }

    @Test
    void twoDefaultsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                OpenProxy.proxy(new Class<?>[]{
                                DefaultGreeter.class, DefaultGreeter2.class},
                        (obj, method, args) -> null));
    }

    @Test
    void differentReturnTypeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                OpenProxy.proxy(new Class<?>[]{
                                Greeter.class, NumberGreeter.class},
                        (obj, method, args) -> null));
    }

    @Test
    void threeInterfaces() {
        Object p = OpenProxy.proxy(new Class<?>[]{
                        Greeter.class, Auditable.class, Named.class},
                (obj, method, args) -> method.getName());
        assertEquals("hello", ((Greeter) p).hello("x"));
        assertEquals("audit", ((Auditable) p).audit());
    }

    @Test
    void parentChildDedup() {
        Object p = OpenProxy.proxy(
                new Class<?>[]{Child.class, Parent.class},
                (obj, method, args) -> "[" + method.getName() + "]");
        assertEquals("[inherited]", ((Child) p).inherited());
        assertEquals("[own]", ((Child) p).own());
    }

    @Test
    void objectMethodsBehave() {
        Object p = OpenProxy.proxy(
                new Class<?>[]{Greeter.class, Auditable.class},
                (obj, method, args) -> null);
        assertTrue(p.equals(p));
        assertNotNull(p.toString());
        assertEquals(p.hashCode(), p.hashCode());
    }

    @Test
    void groupChainAcrossInterfaces() {
        Object p = OpenProxy.proxy(
                new Class<?>[]{Greeter.class, Auditable.class},
                Group.of(m -> m.getName().equals("hello"),
                        (obj, method, args) -> "[hello]"),
                Group.otherwise((obj, method, args) -> "[other]"));
        assertEquals("[hello]", ((Greeter) p).hello("x"));
        assertEquals("[other]", ((Auditable) p).audit());
    }

    @Test
    void cacheReusesGeneratedClass() {
        Interceptor i = (obj, method, args) -> null;
        Object p1 = OpenProxy.proxy(
                new Class<?>[]{Greeter.class, Auditable.class}, i);
        Object p2 = OpenProxy.proxy(
                new Class<?>[]{Greeter.class, Auditable.class}, i);
        assertEquals(p1.getClass(), p2.getClass());
    }

    @Test
    void invokeSuperRoutesBothHashesToSameDefault() throws Throwable {
        Object p = OpenProxy.proxy(
                new Class<?>[]{DefaultGreeter.class, AbstractGreeter.class},
                (obj, method, args) ->
                        OpenProxy.invokeSuper(obj, method, args));
        Method viaDefault = DefaultGreeter.class.getMethod("greet");
        Method viaAbstract = AbstractGreeter.class.getMethod("greet");
        assertEquals("Hello, World",
                OpenProxy.invokeSuper(p, viaDefault, new Object[0]));
        assertEquals("Hello, World",
                OpenProxy.invokeSuper(p, viaAbstract, new Object[0]));
    }

    @Test
    void invalidInputsThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> OpenProxy.proxy((Class<?>[]) null,
                        (obj, m, a) -> null));
        assertThrows(IllegalArgumentException.class,
                () -> OpenProxy.proxy(new Class<?>[0],
                        (obj, m, a) -> null));
        assertThrows(IllegalArgumentException.class,
                () -> OpenProxy.proxy(new Class<?>[]{String.class},
                        (obj, m, a) -> null));
    }

    @Test
    void singleInterfaceStillWorks() {
        Greeter g = OpenProxy.proxy(Greeter.class,
                (obj, method, args) -> "[solo]");
        assertEquals("[solo]", g.hello("x"));
    }
}

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

class DefaultMethodInvocationTest {

    interface DirectDefault {
        String hello(String name);

        default String greet() {
            return "Hello, World";
        }

        default int add(int a, int b) {
            return a + b;
        }

        default void run() {
        }
    }

    interface Parent {
        default String inheritedGreet() {
            return "Hello, inherited";
        }
    }

    interface Child extends Parent {
        String own();
    }

    interface ThrowingDefault {
        default String boom() {
            throw new IllegalStateException("boom");
        }
    }

    @Test
    void invokeSuperOnDirectlyDeclaredDefaultReturnsDefaultImpl() {
        DirectDefault proxy = OpenProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.isDefault()) {
                        return OpenProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals("Hello, World", proxy.greet());
    }

    @Test
    void invokeSuperOnDirectDefaultWithArgsAndPrimitiveReturn() {
        DirectDefault proxy = OpenProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.getName().equals("add")) {
                        return OpenProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void invokeSuperOnDirectVoidDefault() {
        DirectDefault proxy = OpenProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.getName().equals("run")) {
                        return OpenProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertDoesNotThrow(proxy::run);
    }

    @Test
    void invokeSuperOnInheritedDefaultReturnsDefaultImpl() {
        Child proxy = OpenProxy.proxy(Child.class,
                (obj, method, args) -> {
                    if (method.getName().equals("inheritedGreet")) {
                        return OpenProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals("Hello, inherited", proxy.inheritedGreet());
    }

    @Test
    void invokeSuperOnNonDefaultStillThrows() {
        DirectDefault proxy = OpenProxy.proxy(DirectDefault.class,
                (obj, method, args) ->
                        OpenProxy.invokeSuper(obj, method, args));
        assertThrows(AbstractMethodError.class, () -> proxy.hello("x"));
    }

    @Test
    void interceptorCanReplaceDefault() {
        DirectDefault proxy = OpenProxy.proxy(DirectDefault.class,
                (obj, method, args) -> "[overridden]");
        assertEquals("[overridden]", proxy.greet());
    }

    @Test
    void defaultMethodSeesModifiedArguments() {
        DirectDefault proxy = OpenProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.getName().equals("add")) {
                        args[0] = (int) args[0] + 10;
                        return OpenProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals(17, proxy.add(3, 4));
    }

    @Test
    void objectMethodsStillDispatchCorrectly() {
        DirectDefault proxy = OpenProxy.proxy(DirectDefault.class,
                (obj, method, args) ->
                        OpenProxy.invokeSuper(obj, method, args));
        assertNotNull(proxy.toString());
        assertEquals(proxy.hashCode(), proxy.hashCode());
    }

    @Test
    void exceptionFromDefaultMethodPropagates() {
        ThrowingDefault proxy = OpenProxy.proxy(ThrowingDefault.class,
                (obj, method, args) ->
                        OpenProxy.invokeSuper(obj, method, args));
        assertThrows(IllegalStateException.class, proxy::boom);
    }
}

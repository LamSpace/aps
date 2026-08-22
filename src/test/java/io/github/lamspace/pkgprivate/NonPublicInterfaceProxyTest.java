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

package io.github.lamspace.pkgprivate;

import io.github.lamspace.OpenProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class NonPublicInterfaceProxyTest {

    @Test
    void proxiesPackagePrivateInterface() {
        SecretService proxy = OpenProxy.proxy(SecretService.class,
                (o, m, a) -> "hi " + a[0]);

        assertEquals("hi bob", proxy.greet("bob"));
    }

    @Test
    void invokeSuperCallsPackagePrivateDefaultMethod() {
        SecretService proxy = OpenProxy.proxy(SecretService.class,
                (o, m, a) -> OpenProxy.invokeSuper(o, m, a));

        assertEquals("HELLO", proxy.shout("hello"));
    }

    /**
     * Public interface in the test package, mixed with a package-private one.
     */
    public interface PublicMarker {
        String mark();
    }

    @Test
    void mixedPublicAndPackagePrivateInterfaces() {
        Object proxy = OpenProxy.proxy(
                new Class<?>[]{PublicMarker.class, SecretService.class},
                (o, m, a) -> "x");

        assertEquals("x", ((PublicMarker) proxy).mark());
        assertEquals("x", ((SecretService) proxy).greet("ignored"));
    }

    @Test
    void mixedPublicInDifferentPackageAndPackagePrivate() {
        Object proxy = OpenProxy.proxy(
                new Class<?>[]{java.util.function.Function.class,
                        SecretService.class},
                (o, m, a) -> "x");

        assertEquals("x",
                ((java.util.function.Function<String, String>) proxy)
                        .apply("ignored"));
        assertEquals("x", ((SecretService) proxy).greet("ignored"));
    }

    @Test
    void nonPublicInterfacesInDifferentPackagesThrow() throws Exception {
        Class<?> other = Class.forName(
                "io.github.lamspace.otherpkg.OtherSecretService");

        assertThrows(IllegalArgumentException.class, () ->
                OpenProxy.proxy(
                        new Class<?>[]{SecretService.class, other},
                        (o, m, a) -> null));
    }

    @Test
    void cachesGeneratedClassPerInterface() {
        SecretService first = OpenProxy.proxy(SecretService.class,
                (o, m, a) -> "x");
        SecretService second = OpenProxy.proxy(SecretService.class,
                (o, m, a) -> "y");

        assertSame(first.getClass(), second.getClass());
    }

    @Test
    void evictAndReproxy() {
        SecretService first = OpenProxy.proxy(SecretService.class,
                (o, m, a) -> "a");
        Class<?> cls = first.getClass();

        OpenProxy.evict(SecretService.class);
        SecretService second = OpenProxy.proxy(SecretService.class,
                (o, m, a) -> "b");

        assertNotSame(cls, second.getClass());
        assertEquals("b", second.greet("x"));
    }
}

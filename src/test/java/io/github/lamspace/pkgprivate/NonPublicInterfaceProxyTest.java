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

import io.github.lamspace.AcceleratedProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NonPublicInterfaceProxyTest {

    @Test
    void proxiesPackagePrivateInterface() {
        SecretService proxy = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> "hi " + a[0]);

        assertEquals("hi bob", proxy.greet("bob"));
    }

    @Test
    void invokeSuperCallsPackagePrivateDefaultMethod() {
        SecretService proxy = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> AcceleratedProxy.invokeSuper(o, m, a));

        assertEquals("HELLO", proxy.shout("hello"));
    }
}

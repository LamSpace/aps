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

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicJdkInterfaceProxyTest {

    @Test
    void proxiesPublicJdkInterfaceWithoutAddOpens() {
        // Guards the all-public path: a public java.base interface must stay
        // proxyable via MethodHandles.lookup() — no --add-opens required. If
        // the private lookup were applied unconditionally, this would throw an
        // IllegalArgumentException carrying an --add-opens hint.
        Function<String, String> proxy = AcceleratedProxy.proxy(
                Function.class, (o, m, a) -> "intercepted:" + a[0]);

        assertEquals("intercepted:abc", proxy.apply("abc"));
    }
}

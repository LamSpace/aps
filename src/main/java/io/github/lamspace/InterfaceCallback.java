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

import java.lang.reflect.Method;

/**
 * Intercepts method calls on an interface proxy instance.
 * A single InterfaceCallback receives all method invocations for
 * interface proxies — mirroring the model of
 * {@link java.lang.reflect.InvocationHandler} but backed by
 * MethodHandle-based dispatch instead of reflection.
 *
 * <p>Unlike {@link Callback} (used for class proxies), there is no
 * {@code superHandle} parameter — interface methods have no super
 * implementation to delegate to.
 */
@FunctionalInterface
public interface InterfaceCallback {

    /**
     * Called for every method invocation on the interface proxy.
     *
     * @param proxy  the proxy instance
     * @param method the intercepted method (for metadata: name, annotations, etc.)
     * @param args   the method arguments, boxed; empty array for no-arg methods
     * @return the method's return value (null for void methods, boxed for primitives)
     * @throws Throwable any throwable the interceptor wishes to propagate
     */
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}

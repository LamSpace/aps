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
 * Intercepts method calls on a proxy instance.
 * A single Interceptor receives all method invocations for both class
 * and interface proxies — replacing the former {@code Callback} and
 * {@code InterfaceCallback}.
 *
 * <p>To invoke the original superclass method, use
 * {@link AcceleratedProxy#invokeSuper(Object, Method, Object[])}.
 *
 * <pre>{@code
 *   Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
 *       System.out.println("before " + method.getName());
 *       return AcceleratedProxy.invokeSuper(obj, method, args);
 *   });
 * }</pre>
 */
@FunctionalInterface
public interface Interceptor {

    /**
     * Called for every method invocation on the proxy.
     *
     * @param proxy  the proxy instance
     * @param method the intercepted method (for metadata and dispatch)
     * @param args   the method arguments, boxed; empty array for no-arg methods
     * @return the method's return value (null for void, boxed for primitives)
     * @throws Throwable any throwable the interceptor wishes to propagate
     */
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}

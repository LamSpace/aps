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

/**
 * APS (Accelerated Proxy Solution) — a high-performance dynamic proxy
 * library for Java, using hashCode-based dispatch with direct
 * {@code INVOKESPECIAL} super calls.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 *   MyClass proxy = AcceleratedProxy.proxy(MyClass.class, (obj, method, args) -> {
 *       System.out.println("before " + method.getName());
 *       return AcceleratedProxy.invokeSuper(obj, method, args);
 *   });
 * }</pre>
 *
 * @see io.github.lamspace.AcceleratedProxy
 * @see io.github.lamspace.Interceptor
 * @see io.github.lamspace.Group
 * @see io.github.lamspace.MethodPredicate
 * @see io.github.lamspace.Intercept
 * @see io.github.lamspace.Around
 */
package io.github.lamspace;

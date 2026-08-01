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
 * APS (Accelerated Proxy Solution) — a high-performance, MethodHandle-based
 * dynamic proxy library for Java.
 *
 * <h2>Quick start</h2>
 * <pre>{@code
 *   MyClass proxy = APS.create(MyClass.class, (obj, method, superHandle, args) -> {
 *       System.out.println("before " + method.getName());
 *       return superHandle.invoke(args);
 *   });
 * }</pre>
 *
 * @see io.github.lamspace.APS
 * @see io.github.lamspace.Callback
 * @see io.github.lamspace.ClassFilter
 */
package io.github.lamspace;

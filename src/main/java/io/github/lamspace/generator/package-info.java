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
 * Bytecode generation engine. Uses ASM to generate proxy classes at runtime.
 *
 * <p>The core components are:
 * <ul>
 *   <li>{@link io.github.lamspace.generator.ClassGenerator} — orchestrates
 *       subclass bytecode generation (fields, constructor, methods, dispatch, clinit)</li>
 *   <li>{@link io.github.lamspace.generator.InterfaceGenerator} — orchestrates
 *       interface implementation bytecode generation</li>
 *   <li>{@link io.github.lamspace.generator.MethodDispatcher} — generates
 *       per-method override bytecode with Interceptor delegation</li>
 *   <li>{@link io.github.lamspace.generator.InterfaceDispatcher} — generates
 *       per-method implementation bytecode for interface proxies</li>
 *   <li>{@link io.github.lamspace.generator.DispatchGenerator} — generates
 *       the hashCode-driven {@code dispatch()} method for super-method invocation</li>
 * </ul>
 */
package io.github.lamspace.generator;

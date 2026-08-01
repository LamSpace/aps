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
 * Bytecode generation engine. Uses ASM to generate proxy subclasses at runtime.
 *
 * <p>The two main components are:
 * <ul>
 *   <li>{@link io.github.lamspace.generator.ClassGenerator} — orchestrates
 *       subclass bytecode generation (constructors, fields, methods, clinit)</li>
 *   <li>{@link io.github.lamspace.generator.MethodDispatcher} — generates
 *       per-method override bytecode with Callback delegation and
 *       MethodHandle super-call binding</li>
 * </ul>
 */
package io.github.lamspace.generator;

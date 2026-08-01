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

package io.github.lamspace.generator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects (target class, method, generated class, field name, index) tuples
 * during method dispatch generation. ClassGenerator reads this registry
 * after all methods are generated to emit a single {@code <clinit>} block
 * that fills the {@code _methods} and {@code _handles} arrays.
 */
final class ClinitRegistry {

    /**
     * A tuple recording a single method dispatch registration.
     *
     * @param targetClass       the original class or interface declaring the method
     * @param method            the method being proxied
     * @param generatedInternal the internal name of the generated proxy class
     * @param methodFieldName   the name of the static field holding the {@code Method} object
     * @param index             the dispatch table index for this method
     */
    record Entry(Class<?> targetClass, Method method, String generatedInternal,
                 String methodFieldName, int index) {
    }

    private static final List<Entry> entries = new ArrayList<>();

    private ClinitRegistry() {
    }

    /**
     * Registers a method dispatch entry. Called during bytecode generation
     * for each proxied method. The registered entries are later consumed
     * by {@link #drain()} to emit the {@code <clinit>} block.
     *
     * @param targetClass       the original class or interface declaring the method
     * @param method            the method being proxied
     * @param generatedInternal the internal name of the generated proxy class
     * @param methodFieldName   the name of the static field holding the {@code Method} object
     * @param index             the dispatch table index for this method
     */
    static void register(Class<?> targetClass, Method method,
                         String generatedInternal,
                         String methodFieldName, int index) {
        entries.add(new Entry(targetClass, method, generatedInternal,
                methodFieldName, index));
    }

    /**
     * Drains all registered entries and clears the internal registry.
     * Returns a snapshot of the entries in registration order so the
     * caller can emit the {@code <clinit>} block. After this call the
     * registry is empty and ready for the next class generation.
     *
     * @return a list of all registered entries in registration order
     */
    static List<Entry> drain() {
        List<Entry> result = new ArrayList<>(entries);
        entries.clear();
        return result;
    }
}

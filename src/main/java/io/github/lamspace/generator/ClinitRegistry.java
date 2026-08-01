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

    record Entry(Class<?> targetClass, Method method, String generatedInternal,
                 String methodFieldName, int index) {
    }

    private static final List<Entry> entries = new ArrayList<>();

    private ClinitRegistry() {
    }

    static void register(Class<?> targetClass, Method method,
                         String generatedInternal,
                         String methodFieldName, int index) {
        entries.add(new Entry(targetClass, method, generatedInternal,
                methodFieldName, index));
    }

    static List<Entry> drain() {
        List<Entry> result = new ArrayList<>(entries);
        entries.clear();
        return result;
    }
}

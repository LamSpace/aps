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

package io.github.lamspace.internal;

import java.lang.invoke.MethodHandles;

/**
 * Obtains a {@link MethodHandles.Lookup} with the highest available privilege
 * for a given target class. Tries full private access first; degrades
 * gracefully to a regular lookup if the module system denies it.
 */
public final class LookupManager {

    private LookupManager() {
        // static utility
    }

    /**
     * Returns a Lookup with the maximum available access for {@code targetClass}.
     * <p>
     * Attempts {@link MethodHandles#privateLookupIn(Class, MethodHandles.Lookup)}
     * first (full private access). If the target module is not open, falls back
     * to a regular {@link MethodHandles#lookup() public Lookup}.
     *
     * @param targetClass the class to obtain a Lookup for
     * @return a Lookup with as much access as the runtime permits
     */
    public static MethodHandles.Lookup getLookup(Class<?> targetClass) {
        try {
            return MethodHandles.privateLookupIn(targetClass, MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            // Module does not open the package — fall back to public access
            return MethodHandles.lookup();
        }
    }
}

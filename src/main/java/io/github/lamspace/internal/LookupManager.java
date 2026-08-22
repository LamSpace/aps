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
import java.util.logging.Logger;

/**
 * Obtains a {@link MethodHandles.Lookup} with private access to the given
 * target class.
 *
 * <p>Attempts {@link MethodHandles#privateLookupIn(Class, MethodHandles.Lookup)}
 * to obtain full private access. If the target class is in a strongly
 * encapsulated module whose package is not open, an
 * {@link IllegalArgumentException} is thrown with an actionable
 * {@code --add-opens} hint. Primitive and array types (rejected by
 * {@code privateLookupIn}) fall back to a public lookup.
 */
public final class LookupManager {

    private static final Logger LOGGER =
            Logger.getLogger(LookupManager.class.getName());

    private LookupManager() {
        // static utility
    }

    /**
     * Returns a Lookup with private access to {@code targetClass}; see the
     * class-level comment for the resolution and fallback strategy.
     *
     * @param targetClass the class to obtain a Lookup for
     * @return a Lookup with private access to the target class
     * @throws IllegalArgumentException if the target module does not open the
     *                                  target class's package
     */
    public static MethodHandles.Lookup getLookup(Class<?> targetClass) {
        try {
            return MethodHandles.privateLookupIn(targetClass,
                    MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            String moduleName = targetClass.getModule().getName();
            String packageName = targetClass.getPackageName();
            throw new IllegalArgumentException(
                    "Cannot access " + targetClass.getName() + " in module "
                            + moduleName + " (package " + packageName
                            + "): the package is not open to the unnamed "
                            + "module. Add --add-opens " + moduleName + "/"
                            + packageName + "=ALL-UNNAMED to the JVM "
                            + "arguments, or declare 'opens " + packageName
                            + ";' in the module's module-info.java.", e);
        } catch (IllegalArgumentException e) {
            // Primitive and array classes are rejected by privateLookupIn —
            // fall back to public lookup for these edge cases
            LOGGER.fine(() -> "privateLookupIn rejected "
                    + targetClass.getName() + ": " + e.getMessage());
            return MethodHandles.lookup();
        }
    }
}

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
 * Decides whether a method matches a Group's criteria.
 * Replaces {@link ClassFilter} in the multi-interceptor API.
 *
 * @see Group#of(MethodPredicate, Interceptor)
 */
@FunctionalInterface
public interface MethodPredicate {

    /**
     * Tests whether the given method matches this predicate.
     *
     * @param method a method declared by the target class
     * @return {@code true} if the method should be assigned to the
     *         associated Group's Interceptor
     */
    boolean test(Method method);
}

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

/**
 * Per-method metadata for dispatch generation.
 *
 * @param method          the intercepted method
 * @param staticFieldName the name of the static {@code Method} field in the
 *                        generated class (e.g., {@code "_method$0"})
 * @param methodHash      pre-computed {@code Method.hashCode()} used as
 *                        the dispatch discriminator via {@code ldc}
 */
record MethodInfo(Method method, String staticFieldName, int methodHash) {
    MethodInfo {
        if (method == null || staticFieldName == null) {
            throw new NullPointerException();
        }
    }
}

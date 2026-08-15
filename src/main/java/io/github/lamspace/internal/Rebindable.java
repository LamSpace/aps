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

import io.github.lamspace.Interceptor;

/**
 * Internal interface implemented by all generated proxy classes. Lets a live
 * proxy replace its bound interceptors without recreating the instance.
 *
 * <p>Not part of the public API — users call
 * {@link io.github.lamspace.AcceleratedProxy#rebind(Object, Interceptor)}
 * instead.
 */
public interface Rebindable {

    /**
     * Replaces the interceptors bound to this proxy. The array length must
     * equal the proxy's distinct interceptor count.
     *
     * @param interceptors the new interceptors, index-aligned with the
     *                     generated class's interceptor fields
     * @throws IllegalArgumentException if the array is null or its length
     *                                  differs from the expected count
     */
    void rebind(Interceptor[] interceptors);
}

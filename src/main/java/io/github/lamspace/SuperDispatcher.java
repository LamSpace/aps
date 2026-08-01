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

/**
 * Internal interface implemented by all generated class proxy subclasses.
 * Provides index-based super-method dispatch.
 *
 * <p>Users should prefer the static helper
 * {@link APS#invokeSuper(Object, int, Object[])} over casting to this
 * interface directly.
 */
public interface SuperDispatcher {

    /**
     * Invokes the superclass method at the given index in the dispatch table.
     *
     * @param index zero-based index of the method in the proxy's dispatch table
     * @param args  boxed arguments
     * @return the method's return value (null for void, boxed for primitives)
     * @throws Throwable any throwable from the superclass method
     */
    Object invokeSuper(int index, Object[] args) throws Throwable;
}

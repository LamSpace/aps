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
 * Internal interface implemented by all generated proxy classes.
 * Provides hashCode-based method dispatch for super-method invocation.
 *
 * <p>Not part of the public API — users call
 * {@link AcceleratedProxy#invokeSuper(Object, Method, Object[])} instead.
 */
public interface DispatchTarget {

    /**
     * Dispatches a super-method call to the correct branch via
     * a hashCode-driven switch on the given method.
     *
     * @param method the method to dispatch (used for hashCode lookup)
     * @param args   boxed arguments
     * @return the method's return value (null for void, boxed for primitives)
     * @throws Throwable any throwable from the dispatched method
     */
    Object dispatch(Method method, Object[] args) throws Throwable;
}

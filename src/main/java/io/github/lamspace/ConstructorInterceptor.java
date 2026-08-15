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

import java.lang.reflect.Constructor;

/**
 * Intercepts proxy construction, analogous to CGLib's {@code Enhancer}
 * constructor callback.
 *
 * <p>{@link #before} runs before the superclass constructor body and may
 * rewrite the constructor arguments or veto construction by throwing.
 * {@link #after} runs after the superclass constructor has completed and the
 * instance is fully initialized.
 *
 * <p>Only class proxies support constructor interception; interface proxies
 * have no superclass constructor to intercept.
 *
 * <pre>{@code
 *   Greeter proxy = AcceleratedProxy.proxy(Greeter.class, interceptor,
 *       (ctor, args) -> {
 *           System.out.println("before " + ctor);
 *           return args;
 *       });
 * }</pre>
 */
public interface ConstructorInterceptor {

    /**
     * Called before the superclass constructor runs. {@code this} is not yet
     * initialized, so no proxy instance is passed.
     *
     * @param ctor the superclass constructor that will run
     * @param args the initial constructor arguments, boxed
     * @return the arguments to pass to the superclass constructor; must be
     *         non-null with the same length as {@code ctor.getParameterCount()}
     * @throws Throwable to veto construction; a checked exception is surfaced
     *                   to the caller as {@code UndeclaredThrowableException}
     */
    Object[] before(Constructor<?> ctor, Object[] args) throws Throwable;

    /**
     * Called after the superclass constructor has run and the instance is fully
     * initialized. Observational only; the default is a no-op.
     *
     * @param proxy the proxy instance (now fully initialized)
     * @param ctor  the superclass constructor that ran
     * @param args  the arguments actually passed to the superclass constructor
     *              (i.e. the rewritten arguments)
     */
    default void after(Object proxy, Constructor<?> ctor, Object[] args) {
    }
}

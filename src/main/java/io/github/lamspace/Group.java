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

import java.util.Objects;

/**
 * Binds a {@link MethodPredicate} to an {@link Interceptor} for
 * method-group-based proxy configuration.
 *
 * <p>Groups are evaluated in declaration order with first-match-wins
 * semantics. Methods not matching any Group default to passthrough
 * (direct super call, zero interception overhead). Use
 * {@link #otherwise(Interceptor)} to provide an explicit catch-all.
 *
 * <pre>{@code
 *   Greeter proxy = OpenProxy.proxy(Greeter.class,
 *       Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
 *       Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
 *       Group.otherwise(fallbackInterceptor)
 *   );
 * }</pre>
 */
public final class Group {

    private final MethodPredicate predicate;
    private final Interceptor interceptor;
    private final boolean otherwise;

    private Group(MethodPredicate predicate, Interceptor interceptor,
                  boolean otherwise) {
        this.predicate = predicate;
        this.interceptor = interceptor;
        this.otherwise = otherwise;
    }

    /**
     * Creates a Group that assigns {@code interceptor} to methods where
     * {@code predicate.test(method)} returns {@code true}.
     *
     * @param predicate   method matching criteria; must not be null
     * @param interceptor the interceptor for matched methods; must not be null
     * @return a new Group
     */
    public static Group of(MethodPredicate predicate,
                           Interceptor interceptor) {
        Objects.requireNonNull(predicate, "predicate must not be null");
        Objects.requireNonNull(interceptor, "interceptor must not be null");
        return new Group(predicate, interceptor, false);
    }

    /**
     * Creates a catch-all Group that assigns {@code interceptor} to every
     * method not matched by any preceding Group.
     *
     * @param interceptor the fallback interceptor; must not be null
     * @return a new otherwise Group
     */
    public static Group otherwise(Interceptor interceptor) {
        Objects.requireNonNull(interceptor, "interceptor must not be null");
        return new Group(m -> true, interceptor, true);
    }

    /**
     * Returns this Group's method matcher.
     *
     * @return the method predicate bound to this Group
     */
    MethodPredicate predicate() {
        return predicate;
    }

    /**
     * Returns the interceptor assigned to methods this Group matches.
     *
     * @return the interceptor bound to this Group
     */
    Interceptor interceptor() {
        return interceptor;
    }

    /**
     * Returns {@code true} if this Group was created via
     * {@link #otherwise(Interceptor)}.
     *
     * @return whether this Group is a catch-all
     */
    boolean isOtherwise() {
        return otherwise;
    }
}

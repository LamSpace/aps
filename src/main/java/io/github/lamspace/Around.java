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

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative method-matching for an {@link Intercept} interceptor method.
 *
 * <p>Three match dimensions are AND-combined; within each dimension, multiple
 * values are OR-combined. A method matches when every non-empty dimension
 * matches. An empty dimension imposes no constraint.
 *
 * <ul>
 *   <li>{@code value}/{@code glob} — method-name glob ({@code *} matches any
 *       sequence, {@code ?} matches one character);</li>
 *   <li>{@code regex} — method-name regular expression (whole-name match);</li>
 *   <li>{@code annotatedWith} — annotation types the target method must carry
 *       (direct presence only).</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Around {

    /**
     * Single name-glob shorthand: {@code @Around("get*")}.
     *
     * @return the single name-glob pattern, or empty for none
     */
    String value() default "";

    /**
     * Method-name glob patterns. Empty = no glob constraint.
     *
     * @return the glob patterns; empty means no glob constraint
     */
    String[] glob() default {};

    /**
     * Method-name regex patterns. Empty = no regex constraint.
     *
     * @return the regex patterns; empty means no regex constraint
     */
    String[] regex() default {};

    /**
     * Annotation types the target method must carry. Empty = no constraint.
     *
     * @return the required annotation types; empty means no constraint
     */
    Class<? extends Annotation>[] annotatedWith() default {};
}

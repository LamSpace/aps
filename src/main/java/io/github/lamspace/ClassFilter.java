package io.github.lamspace;

import java.lang.reflect.Method;

/**
 * Decides whether a method should be intercepted.
 * Methods not accepted by the filter are NOT routed through the Callback —
 * they call the superclass implementation directly with zero interception overhead.
 */
@FunctionalInterface
public interface ClassFilter {

    /**
     * Decides whether the given method should be intercepted.
     *
     * @param method a method declared by the target class
     * @return {@code true} to route this method through the Callback,
     * {@code false} to skip interception
     */
    boolean accept(Method method);
}

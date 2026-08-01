package io.github.lamspace;

import java.lang.reflect.Method;

/**
 * Intercepts method calls on an interface proxy instance.
 * A single InterfaceCallback receives all method invocations for
 * interface proxies — mirroring the model of
 * {@link java.lang.reflect.InvocationHandler} but backed by
 * MethodHandle-based dispatch instead of reflection.
 *
 * <p>Unlike {@link Callback} (used for class proxies), there is no
 * {@code superHandle} parameter — interface methods have no super
 * implementation to delegate to.
 */
@FunctionalInterface
public interface InterfaceCallback {

    /**
     * Called for every method invocation on the interface proxy.
     *
     * @param proxy  the proxy instance
     * @param method the intercepted method (for metadata: name, annotations, etc.)
     * @param args   the method arguments, boxed; empty array for no-arg methods
     * @return the method's return value (null for void methods, boxed for primitives)
     * @throws Throwable any throwable the interceptor wishes to propagate
     */
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}

package io.github.lamspace;

import java.lang.invoke.MethodHandle;
import java.lang.reflect.Method;

/**
 * Intercepts method calls on a proxy instance.
 * A single Callback receives all method invocations, matching the model
 * of {@link java.lang.reflect.InvocationHandler} and CGLib's
 * {@code MethodInterceptor}.
 */
@FunctionalInterface
public interface Callback {

    /**
     * Called for every method invocation on the proxy.
     *
     * @param proxy       the proxy instance
     * @param method      the intercepted method (for metadata: name, annotations, etc.)
     * @param superHandle a MethodHandle bound to the superclass implementation;
     *                    call {@code superHandle.invoke(args)} to invoke the original
     *                    method, or omit to skip it
     * @param args        the method arguments, boxed; empty array for no-arg methods
     * @return the method's return value (null for void methods, boxed for primitives)
     * @throws Throwable any throwable the interceptor wishes to propagate
     */
    Object intercept(Object proxy, Method method, MethodHandle superHandle,
                     Object[] args) throws Throwable;
}

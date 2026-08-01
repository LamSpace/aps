package io.github.lamspace;

import java.lang.reflect.Method;

/**
 * Intercepts method calls on a class proxy instance.
 * A single Callback receives all method invocations, matching the model
 * of {@link java.lang.reflect.InvocationHandler} and CGLib's
 * {@code MethodInterceptor}.
 *
 * <p>To invoke the original superclass method, cast the proxy to the
 * generated class type and call {@code invokeSuper(index, args)}, or
 * use the helper pattern shown below:
 *
 * <pre>{@code
 *   Greeter proxy = APS.create(Greeter.class, (obj, method, index, args) -> {
 *       System.out.println("before " + method.getName());
 *       return APS.invokeSuper(obj, index, args);
 *   });
 * }</pre>
 */
@FunctionalInterface
public interface Callback {

    /**
     * Called for every method invocation on the proxy.
     *
     * @param proxy   the proxy instance
     * @param method  the intercepted method (for metadata: name, annotations, etc.)
     * @param index   zero-based index of the method in the proxy's dispatch table;
     *                pass to the proxy's {@code invokeSuper(int, Object[])} or
     *                to {@link APS#invokeSuper(Object, int, Object[])}
     * @param args    the method arguments, boxed; empty array for no-arg methods
     * @return the method's return value (null for void methods, boxed for primitives)
     * @throws Throwable any throwable the interceptor wishes to propagate
     */
    Object intercept(Object proxy, Method method, int index,
                     Object[] args) throws Throwable;
}

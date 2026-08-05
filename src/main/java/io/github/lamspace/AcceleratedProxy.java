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

import io.github.lamspace.generator.ClassGenerator;
import io.github.lamspace.generator.InterfaceGenerator;
import io.github.lamspace.internal.LookupManager;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;
import java.util.Arrays;

/**
 * Entry point for creating dynamic proxies.
 *
 * <p>All {@code proxy(...)} overloads generate a runtime proxy class
 * (subclass for concrete classes, implementation for interfaces), load it
 * via {@code MethodHandles.Lookup.defineHiddenClass(byte[], boolean)},
 * and route method calls through the provided {@link Interceptor}.
 *
 * <p>Generated proxy classes are cached by {@code {targetClass, filter,
 * constructorArgs}} using {@link WeakCache} so repeated proxy creation
 * for the same configuration reuses the generated class.
 *
 * <pre>{@code
 *   Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
 *       System.out.println("before " + method.getName());
 *       return AcceleratedProxy.invokeSuper(obj, method, args);
 *   });
 * }</pre>
 *
 * @see Interceptor
 * @see ClassFilter
 */
public final class AcceleratedProxy {

    private AcceleratedProxy() {
    }

    /**
     * Composite cache key for generated proxy classes.
     */
    private record CacheParams(Class<?> targetClass, ClassFilter filter,
                               Object[] constructorArgs) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheParams other)) return false;
            return targetClass == other.targetClass
                    && filter == other.filter
                    && Arrays.equals(constructorArgs, other.constructorArgs);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(targetClass);
            result = 31 * result + System.identityHashCode(filter);
            result = 31 * result + Arrays.hashCode(constructorArgs);
            return result;
        }
    }

    /**
     * Cache mapping {@code {targetClass, filter, constructorArgs}} to
     * generated proxy classes. Keys are weakly referenced so proxy classes
     * are eligible for GC when no longer in use.
     */
    private static final WeakCache<Class<?>, CacheParams, Class<?>> PROXY_CLASS_CACHE =
            new WeakCache<>(
                    (key, params) -> params, // subKeyFactory
                    AcceleratedProxy::generateProxyClass // valueFactory
            );

    /**
     * Generates and loads a proxy class for the given target and parameters.
     * Called by the cache on cache miss.
     */
    private static Class<?> generateProxyClass(Class<?> target,
                                                CacheParams params) {
        try {
            byte[] bytecode;

            if (target.isInterface()) {
                InterfaceGenerator generator = new InterfaceGenerator(target,
                        params.filter());
                bytecode = generator.generate();
            } else {
                ClassGenerator generator = new ClassGenerator(target,
                        params.filter(), params.constructorArgs());
                bytecode = generator.generate();
            }

            // Class loading: interface proxies use APS's own Lookup
            // (they live in io.github.lamspace package, even for restricted
            // modules like java.lang). Class proxies use LookupManager
            // for access to the target class's package.
            if (target.isInterface()) {
                return java.lang.invoke.MethodHandles.lookup()
                        .defineHiddenClass(bytecode, true).lookupClass();
            } else {
                return LookupManager.getLookup(target)
                        .defineHiddenClass(bytecode, true).lookupClass();
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate proxy class for " + target.getName(), e);
        }
    }

    /**
     * Invokes the original superclass method via the proxy's hashCode-based
     * dispatch. Convenience wrapper around the proxy's {@code dispatch} method.
     *
     * @param proxy  the proxy instance (must be an APS-generated proxy)
     * @param method the intercepted method, used to identify the dispatch target
     * @param args   boxed arguments to pass to the superclass method
     * @return the superclass method's return value
     * @throws Throwable any throwable from the superclass method
     */
    public static Object invokeSuper(Object proxy, Method method,
                                     Object[] args) throws Throwable {
        return ((DispatchTarget) proxy).dispatch(method, args);
    }

    /**
     * Creates a proxy for the given target. All non-final instance methods
     * (for classes) or all interface methods (for interfaces) are routed
     * through the interceptor.
     *
     * <p>Auto-detects the target type: if {@code target.isInterface()},
     * generates an interface proxy; otherwise generates a class proxy.
     *
     * @param target      the class or interface to proxy
     * @param interceptor invoked for every method call on the proxy
     * @param <T>         the proxy type
     * @return a proxy instance of type {@code T}
     * @throws IllegalArgumentException if the target cannot be proxied
     * @throws RuntimeException         if bytecode generation or class loading fails
     */
    public static <T> T proxy(Class<T> target, Interceptor interceptor) {
        return proxy(target, interceptor, null, new Object[0]);
    }

    /**
     * Creates a proxy for the given target with an optional method filter.
     *
     * @param target      the class or interface to proxy
     * @param interceptor invoked for every FILTERED method call on the proxy
     * @param filter      decides which methods pass through the interceptor;
     *                    {@code null} means all methods are intercepted
     * @param <T>         the proxy type
     * @return a proxy instance of type {@code T}
     */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> target, Interceptor interceptor,
                              ClassFilter filter) {
        return proxy(target, interceptor, filter, new Object[0]);
    }

    /**
     * Creates a proxy for the given target with optional method filter
     * and constructor arguments (for class proxies only).
     *
     * @param target          the class or interface to proxy
     * @param interceptor     invoked for every FILTERED method call on the proxy;
     *                        must not be {@code null}
     * @param filter          decides which methods pass through the interceptor;
     *                        {@code null} means all methods are intercepted
     * @param constructorArgs arguments to pass to the superclass constructor;
     *                        empty array (default) for the no-arg constructor
     * @param <T>             the proxy type
     * @return a proxy instance of type {@code T}
     */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> target, Interceptor interceptor,
                              ClassFilter filter, Object... constructorArgs) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        if (constructorArgs == null) {
            constructorArgs = new Object[0];
        }

        // Compute constructor argument types before the cache lookup —
        // this is deterministic from the target and arguments.
        Class<?>[] ctorArgTypes;
        if (target.isInterface()) {
            ctorArgTypes = new Class<?>[]{Interceptor.class};
        } else {
            ctorArgTypes = new Class<?>[1 + constructorArgs.length];
            ctorArgTypes[0] = Interceptor.class;
            for (int i = 0; i < constructorArgs.length; i++) {
                Object arg = constructorArgs[i];
                ctorArgTypes[i + 1] = (arg != null) ? arg.getClass()
                        : Object.class;
            }
        }

        try {
            CacheParams params = new CacheParams(target, filter,
                    constructorArgs);
            Class<?> proxyClass = PROXY_CLASS_CACHE.get(target, params);
            Constructor<?> ctor = proxyClass.getConstructor(ctorArgTypes);

            if (target.isInterface()) {
                return (T) ctor.newInstance(interceptor);
            } else {
                Object[] initArgs = new Object[1 + constructorArgs.length];
                initArgs[0] = interceptor;
                System.arraycopy(constructorArgs, 0, initArgs, 1,
                        constructorArgs.length);
                return (T) ctor.newInstance(initArgs);
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create proxy for " + target.getName(), e);
        }
    }
}

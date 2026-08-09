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
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Entry point for creating dynamic proxies.
 *
 * <p>All {@code proxy(...)} overloads generate a runtime proxy class
 * (subclass for concrete classes, implementation for interfaces), load it
 * via {@code MethodHandles.Lookup.defineHiddenClass(byte[], boolean)},
 * and route method calls through the provided {@link Interceptor}(s).
 *
 * <p>Multi-interceptor configuration uses {@link Group} declarations to
 * bind different method families to different Interceptor instances:
 *
 * <pre>{@code
 *   Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
 *       Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
 *       Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
 *       Group.otherwise(fallbackInterceptor)
 *   );
 * }</pre>
 *
 * <p>Generated proxy classes are cached by
 * {@code {targetClass, interceptors, mapping, constructorArgs}} using
 * {@link WeakCache} so repeated proxy creation for the same configuration
 * reuses the generated class.
 *
 * @see Interceptor
 * @see Group
 * @see MethodPredicate
 */
public final class AcceleratedProxy {

    private static final Logger LOGGER =
            Logger.getLogger(AcceleratedProxy.class.getName());

    private AcceleratedProxy() {
    }

    /**
     * Result of Group chain matching.
     */
    private record MatchResult(Interceptor[] interceptors,
                               MethodMapping mapping) {
    }

    /**
     * Composite cache key for generated proxy classes.
     * The proxy class structure depends only on the method→index mapping
     * and constructor args — not on the Interceptor instances themselves.
     * Two proxy() calls with the same Group config (same targets, same
     * predicate matching) but different Interceptor instances share the
     * same generated class.
     */
    private record CacheParams(Class<?> targetClass,
                               MethodMapping mapping,
                               Object[] constructorArgs) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheParams other)) return false;
            return targetClass == other.targetClass
                    && mapping.equals(other.mapping)
                    && Arrays.equals(constructorArgs,
                            other.constructorArgs);
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(targetClass);
            result = 31 * result + mapping.hashCode();
            result = 31 * result + Arrays.hashCode(constructorArgs);
            return result;
        }
    }

    /**
     * Cache mapping {@code {targetClass, interceptors, mapping,
     * constructorArgs}} to generated proxy classes. Keys are weakly
     * referenced so proxy classes are eligible for GC when no longer in use.
     */
    private static final WeakCache<Class<?>, CacheParams, Class<?>>
            PROXY_CLASS_CACHE = new WeakCache<>(
                    (key, params) -> params,
                    AcceleratedProxy::generateProxyClass
            );

    /**
     * Evaluates the Group chain against every proxyable method on the
     * target. Returns deduped interceptors and a stable-sorted
     * method-to-index mapping. Only proxyable methods are included —
     * static, final, and (for class targets) private methods are excluded.
     */
    private static MatchResult matchMethods(Class<?> target,
                                            Group[] groups) {
        Method[] rawMethods;
        if (target.isInterface()) {
            rawMethods = target.getMethods();
        } else {
            rawMethods = target.getDeclaredMethods();
        }

        // 1. Filter to proxyable methods (same criteria as the dispatchers)
        List<Method> proxyable = new ArrayList<>();
        for (Method m : rawMethods) {
            int mods = m.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                continue;
            }
            if (!target.isInterface() && Modifier.isPrivate(mods)) {
                continue;
            }
            proxyable.add(m);
        }
        Method[] methods = proxyable.toArray(new Method[0]);

        // 2. Stable sort for cross-JVM determinism
        Arrays.sort(methods,
                Comparator.comparing(Method::getName)
                        .thenComparing(m -> Arrays.toString(
                                m.getParameterTypes())));

        // 3. Match each method against the Group chain
        int[] indices = new int[methods.length];
        List<Interceptor> interceptorList = new ArrayList<>();
        Map<Interceptor, Integer> interceptorIndex = new IdentityHashMap<>();

        for (int i = 0; i < methods.length; i++) {
            Method m = methods[i];
            int matchedGroup = -1;

            for (int g = 0; g < groups.length; g++) {
                if (groups[g].predicate().test(m)) {
                    if (matchedGroup != -1
                            && !groups[g].isOtherwise()
                            && LOGGER.isLoggable(Level.WARNING)) {
                        final int first = matchedGroup;
                        final int second = g;
                        LOGGER.warning(() -> String.format(
                                "Method '%s' matches multiple Groups: "
                                        + "#%d and #%d. "
                                        + "Using first match (Group #%d).",
                                m.getName(), first, second, first));
                    }
                    if (matchedGroup == -1) {
                        matchedGroup = g;
                    }
                }
            }

            if (matchedGroup >= 0) {
                Interceptor interceptor =
                        groups[matchedGroup].interceptor();
                Integer idx = interceptorIndex.get(interceptor);
                if (idx == null) {
                    idx = interceptorList.size();
                    interceptorList.add(interceptor);
                    interceptorIndex.put(interceptor, idx);
                }
                indices[i] = idx;
            } else {
                indices[i] = -1; // passthrough
            }
        }

        return new MatchResult(
                interceptorList.toArray(new Interceptor[0]),
                new MethodMapping(indices));
    }

    /**
     * Generates and loads a proxy class for the given target and parameters.
     * Called by the cache on cache miss.
     */
    private static Class<?> generateProxyClass(Class<?> target,
                                                CacheParams params) {
        try {
            byte[] bytecode;
            MethodMapping mapping = params.mapping();
            int interceptorCount = mapping.interceptorCount();
            // Dummy array: generators only need .length, not the instances
            Interceptor[] dummy = new Interceptor[interceptorCount];

            if (target.isInterface()) {
                InterfaceGenerator generator = new InterfaceGenerator(target,
                        dummy, mapping);
                bytecode = generator.generate();
            } else {
                ClassGenerator generator = new ClassGenerator(target,
                        dummy, mapping, params.constructorArgs());
                bytecode = generator.generate();
            }

            if (target.isInterface()) {
                return java.lang.invoke.MethodHandles.lookup()
                        .defineHiddenClass(bytecode, true).lookupClass();
            } else {
                return LookupManager.getLookup(target)
                        .defineHiddenClass(bytecode, true).lookupClass();
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate proxy class for "
                            + target.getName(), e);
        }
    }

    /**
     * Invokes the original superclass method via the proxy's hashCode-based
     * dispatch. Convenience wrapper around the proxy's {@code dispatch}
     * method.
     *
     * @param proxy  the proxy instance (must be an APS-generated proxy)
     * @param method the intercepted method, used to identify the dispatch
     *               target
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
     * @param target      the class or interface to proxy
     * @param interceptor invoked for every method call on the proxy
     * @param <T>         the proxy type
     * @return a proxy instance of type {@code T}
     */
    public static <T> T proxy(Class<T> target, Interceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException(
                    "interceptor must not be null");
        }
        return proxy(target, new Object[0],
                Group.otherwise(interceptor));
    }

    /**
     * Creates a proxy with method-group-based interceptor assignment.
     * Groups are evaluated in declaration order (first-match-wins).
     * Methods not matching any Group call super directly (passthrough).
     *
     * @param target the class or interface to proxy
     * @param groups one or more Group bindings; must not be null or empty
     * @param <T>    the proxy type
     * @return a proxy instance of type {@code T}
     */
    public static <T> T proxy(Class<T> target, Group... groups) {
        return proxy(target, new Object[0], groups);
    }

    /**
     * Creates a proxy with method-group-based interceptor assignment
     * and constructor arguments (for class proxies only).
     *
     * @param target          the class or interface to proxy
     * @param constructorArgs arguments to pass to the superclass
     *                        constructor; empty array for no-arg
     *                        constructor
     * @param groups          one or more Group bindings; must not be null
     * @param <T>             the proxy type
     * @return a proxy instance of type {@code T}
     */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> target, Object[] constructorArgs,
                              Group... groups) {
        if (target == null) {
            throw new IllegalArgumentException(
                    "target must not be null");
        }
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException(
                    "groups must not be null or empty");
        }
        if (constructorArgs == null) {
            constructorArgs = new Object[0];
        }

        // 1. Match methods to interceptors
        MatchResult matchResult = matchMethods(target, groups);

        // 2. Cache lookup (keyed on mapping shape, not instances)
        CacheParams params = new CacheParams(target,
                matchResult.mapping(), constructorArgs);

        try {
            Class<?> proxyClass = PROXY_CLASS_CACHE.get(target, params);

            // 3. Build constructor argument array:
            //    [interceptors..., constructorArgs...]
            int interceptorCount = matchResult.interceptors().length;
            Object[] initArgs = new Object[interceptorCount
                    + constructorArgs.length];
            System.arraycopy(matchResult.interceptors(), 0, initArgs, 0,
                    interceptorCount);
            System.arraycopy(constructorArgs, 0, initArgs,
                    interceptorCount, constructorArgs.length);

            // 4. Build constructor parameter types
            Class<?>[] ctorArgTypes = new Class<?>[initArgs.length];
            for (int i = 0; i < interceptorCount; i++) {
                ctorArgTypes[i] = Interceptor.class;
            }
            for (int i = 0; i < constructorArgs.length; i++) {
                Object arg = constructorArgs[i];
                ctorArgTypes[interceptorCount + i] =
                        (arg != null) ? arg.getClass() : Object.class;
            }

            Constructor<?> ctor = proxyClass.getConstructor(ctorArgTypes);
            return (T) ctor.newInstance(initArgs);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create proxy for " + target.getName(), e);
        }
    }
}

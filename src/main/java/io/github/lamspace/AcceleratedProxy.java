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
import io.github.lamspace.generator.InterfaceMethodResolver;
import io.github.lamspace.generator.StaticMethodGenerator;
import io.github.lamspace.internal.LookupManager;
import io.github.lamspace.internal.Rebindable;

import java.lang.annotation.Annotation;
import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

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
                               Class<?>[] interfaces,
                               MethodMapping mapping,
                               Object[] constructorArgs,
                               boolean ctorIntercept) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheParams other)) return false;
            return targetClass == other.targetClass
                    && Arrays.equals(interfaces, other.interfaces)
                    && mapping.equals(other.mapping)
                    && Arrays.equals(argTypes(constructorArgs),
                    argTypes(other.constructorArgs))
                    && ctorIntercept == other.ctorIntercept;
        }

        @Override
        public int hashCode() {
            int result = targetClass != null
                    ? System.identityHashCode(targetClass) : 0;
            result = 31 * result + (interfaces != null
                    ? Arrays.hashCode(interfaces) : 0);
            result = 31 * result + mapping.hashCode();
            result = 31 * result + Arrays.hashCode(argTypes(constructorArgs));
            result = 31 * result + Boolean.hashCode(ctorIntercept);
            return result;
        }

        /**
         * Maps constructor arguments to their runtime types, with {@code null}
         * for null arguments (so two null args key the same, distinct from a
         * non-null {@code Object}).
         */
        private static Class<?>[] argTypes(Object[] args) {
            Class<?>[] types = new Class<?>[args.length];
            for (int i = 0; i < args.length; i++) {
                types[i] = (args[i] == null) ? null : args[i].getClass();
            }
            return types;
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
     * Evaluates the Group chain against every proxyable method of the given
     * interfaces. Merges the interfaces' methods (see
     * {@link InterfaceMethodResolver}), then matches each against the Group
     * chain. Returns deduped interceptors and a stable-sorted
     * method-to-index mapping.
     */
    private static MatchResult matchMethods(Class<?>[] interfaces,
                                            Group[] groups) {
        List<InterfaceMethodResolver.ResolvedMethod> resolved =
                InterfaceMethodResolver.resolve(interfaces);
        Method[] methods = new Method[resolved.size()];
        for (int i = 0; i < resolved.size(); i++) {
            methods[i] = resolved.get(i).canonical();
        }
        return matchMethods(methods, groups);
    }

    /**
     * Evaluates the Group chain against every proxyable method on the target
     * class. Only non-static, non-final, non-private methods are included.
     */
    private static MatchResult matchMethods(Class<?> target,
                                            Group[] groups) {
        Method[] rawMethods = target.getDeclaredMethods();
        List<Method> proxyable = new ArrayList<>();
        for (Method m : rawMethods) {
            int mods = m.getModifiers();
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)
                    || Modifier.isPrivate(mods)) {
                continue;
            }
            proxyable.add(m);
        }
        return matchMethods(proxyable.toArray(new Method[0]), groups);
    }

    /**
     * Sorts the methods for cross-JVM determinism and matches each against
     * the Group chain, returning deduped interceptors and the mapping.
     */
    private static MatchResult matchMethods(Method[] methods,
                                            Group[] groups) {
        Arrays.sort(methods,
                Comparator.comparing(Method::getName)
                        .thenComparing(m -> Arrays.toString(
                                m.getParameterTypes())));

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
    private static Class<?> generateProxyClass(Class<?> key,
                                               CacheParams params) {
        try {
            byte[] bytecode;
            MethodMapping mapping = params.mapping();
            int interceptorCount = mapping.interceptorCount();
            // Dummy array: generators only need .length, not the instances
            Interceptor[] dummy = new Interceptor[interceptorCount];

            if (params.interfaces() != null) {
                InterfaceGenerator generator = new InterfaceGenerator(
                        params.interfaces(), dummy, mapping);
                bytecode = generator.generate();
                return java.lang.invoke.MethodHandles.lookup()
                        .defineHiddenClass(bytecode, true).lookupClass();
            } else {
                Class<?> target = params.targetClass();
                ClassGenerator generator = new ClassGenerator(target,
                        dummy, mapping, params.ctorIntercept(),
                        params.constructorArgs());
                bytecode = generator.generate();
                return LookupManager.getLookup(target)
                        .defineHiddenClass(bytecode, true).lookupClass();
            }
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate proxy class", e);
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
     * Replaces the interceptor on an existing proxy instance with {@code
     * interceptor}, without recreating the instance. The proxy must be an
     * APS-generated single-interceptor proxy.
     *
     * @param proxy       the APS proxy instance
     * @param interceptor the new interceptor; must not be null
     * @throws IllegalArgumentException if {@code interceptor} is null or
     *                                  {@code proxy} is not an APS proxy
     */
    public static void rebind(Object proxy, Interceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        rebind(proxy, new Interceptor[]{interceptor});
    }

    /**
     * Replaces the interceptors on an existing proxy instance with {@code
     * interceptors}, without recreating the instance. The array length must
     * equal the proxy's distinct interceptor count (the number of deduped
     * interceptors it was created with).
     *
     * <p>This is a single-writer management operation. The stores are followed
     * by a full fence, but a caller that rebinds on one thread and invokes
     * methods on another must establish its own happens-before edge (a lock,
     * thread start, latch, or volatile flag).
     *
     * @param proxy        the APS proxy instance
     * @param interceptors the new interceptors, index-aligned with the
     *                     generated class's interceptor fields
     * @throws IllegalArgumentException if {@code proxy} is not an APS proxy or
     *                                  {@code interceptors} is null/ill-sized
     */
    public static void rebind(Object proxy, Interceptor[] interceptors) {
        if (!(proxy instanceof Rebindable rebindable)) {
            throw new IllegalArgumentException("not an APS-generated proxy");
        }
        rebindable.rebind(interceptors);
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
     * Creates a proxy implementing all given interfaces. The returned object
     * can be cast to each interface. Methods with the same signature and
     * return type across interfaces are merged into a single implementation.
     *
     * @param interfaces  the interfaces to implement; must be non-null,
     *                    non-empty, and contain only interfaces
     * @param interceptor invoked for every method call on the proxy
     * @return a proxy instance implementing every interface
     * @throws IllegalArgumentException if interfaces is invalid or a
     *                                  cross-interface method conflict is found
     */
    public static Object proxy(Class<?>[] interfaces,
                               Interceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException(
                    "interceptor must not be null");
        }
        return proxyInterfaces(interfaces, Group.otherwise(interceptor));
    }

    /**
     * Creates a proxy implementing all given interfaces with method-group-based
     * interceptor assignment.
     *
     * @param interfaces the interfaces to implement
     * @param groups     one or more Group bindings
     * @return a proxy instance implementing every interface
     */
    public static Object proxy(Class<?>[] interfaces, Group... groups) {
        return proxyInterfaces(interfaces, groups);
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

        if (target.isInterface()) {
            return (T) proxyInterfaces(new Class<?>[]{target}, groups);
        }

        // 1. Match methods to interceptors
        MatchResult matchResult = matchMethods(target, groups);

        // 2. Cache lookup (keyed on mapping shape, not instances)
        CacheParams params = new CacheParams(target, null,
                matchResult.mapping(), constructorArgs, false);

        try {
            Class<?> proxyClass = PROXY_CLASS_CACHE.get(target, params);
            return (T) instantiateProxy(proxyClass,
                    matchResult.interceptors(), null, constructorArgs);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create proxy for " + target.getName(), e);
        }
    }

    /**
     * Creates a class proxy with a single {@link Interceptor} and a
     * {@link ConstructorInterceptor} hook.
     *
     * @param target          the class to proxy
     * @param interceptor     the method interceptor; must not be null
     * @param ctorInterceptor the constructor interceptor; must not be null
     * @param <T>             the proxy type
     * @return a proxy instance of type {@code T}
     */
    public static <T> T proxy(Class<T> target,
                              Interceptor interceptor,
                              ConstructorInterceptor ctorInterceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException(
                    "interceptor must not be null");
        }
        return proxy(target, new Object[0], ctorInterceptor,
                Group.otherwise(interceptor));
    }

    /**
     * Creates a class proxy with method-group-based interceptor assignment
     * and a {@link ConstructorInterceptor} hook (no superclass constructor
     * arguments).
     *
     * @param target          the class to proxy
     * @param ctorInterceptor the constructor interceptor; must not be null
     * @param groups          one or more Group bindings
     * @param <T>             the proxy type
     * @return a proxy instance of type {@code T}
     */
    public static <T> T proxy(Class<T> target,
                              ConstructorInterceptor ctorInterceptor,
                              Group... groups) {
        return proxy(target, new Object[0], ctorInterceptor, groups);
    }

    /**
     * Creates a class proxy with method-group-based interceptor assignment,
     * constructor arguments, and a {@link ConstructorInterceptor} hook. The
     * superclass constructor is selected from {@code constructorArgs} types;
     * {@code ctorInterceptor.before} may rewrite the values.
     *
     * @param target          the class to proxy
     * @param constructorArgs arguments to pass to the superclass constructor
     * @param ctorInterceptor the constructor interceptor; must not be null
     * @param groups          one or more Group bindings
     * @param <T>             the proxy type
     * @return a proxy instance of type {@code T}
     */
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> target, Object[] constructorArgs,
                              ConstructorInterceptor ctorInterceptor,
                              Group... groups) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException(
                    "groups must not be null or empty");
        }
        if (ctorInterceptor == null) {
            throw new IllegalArgumentException(
                    "ctorInterceptor must not be null");
        }
        if (target.isInterface()) {
            throw new IllegalArgumentException(
                    "constructor interception is only supported "
                            + "for class proxies");
        }
        if (constructorArgs == null) {
            constructorArgs = new Object[0];
        }

        MatchResult matchResult = matchMethods(target, groups);
        CacheParams params = new CacheParams(target, null,
                matchResult.mapping(), constructorArgs, true);

        try {
            Class<?> proxyClass = PROXY_CLASS_CACHE.get(target, params);
            return (T) instantiateProxy(proxyClass,
                    matchResult.interceptors(), ctorInterceptor,
                    constructorArgs);
        } catch (InvocationTargetException e) {
            Throwable cause = e.getCause();
            sneakyThrow(cause);
            throw new RuntimeException("Failed to create proxy for "
                    + target.getName(), e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create proxy for " + target.getName(), e);
        }
    }

    /**
     * Creates a static-method proxy class. Returns a generated {@code Class}
     * whose {@code public static} methods shadow the target's public static
     * methods, routing them through the given interceptor with a {@code null}
     * proxy. Invoke a shadow via
     * {@code Class#getMethod(String, Class...).invoke(null, args)} or a
     * {@code MethodHandle}; static methods are compile-time bound, so
     * {@code Target.staticMethod()} is not interceptable — only calls made on
     * the returned class are.
     *
     * @param target      the class whose static methods to shadow; must not be
     *                    null and must not be an interface
     * @param interceptor the interceptor for every shadowed static method; must
     *                    not be null
     * @return a generated proxy class (fresh per call, not cached)
     */
    public static Class<?> proxyStatic(Class<?> target,
                                       Interceptor interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException(
                    "interceptor must not be null");
        }
        return proxyStatic(target, Group.otherwise(interceptor));
    }

    /**
     * Creates a static-method proxy class with method-group-based interceptor
     * assignment. Static methods are collected from the target and its
     * superclasses, then matched against the Groups first-match-wins; methods
     * matching no Group pass through to the original.
     *
     * @param target the class whose static methods to shadow; must not be null
     *               and must not be an interface
     * @param groups one or more Group bindings; must not be null or empty
     * @return a generated proxy class (fresh per call, not cached)
     */
    public static Class<?> proxyStatic(Class<?> target, Group... groups) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException(
                    "groups must not be null or empty");
        }
        if (target.isInterface()) {
            throw new IllegalArgumentException(
                    "static proxy requires a class, not an interface");
        }

        Method[] methods = collectStaticMethods(target);
        MatchResult matchResult = matchMethods(methods, groups);

        try {
            StaticMethodGenerator generator = new StaticMethodGenerator(
                    methods, matchResult.interceptors(),
                    matchResult.mapping());
            byte[] bytecode = generator.generate();
            Class<?> proxyClass = MethodHandles.lookup()
                    .defineHiddenClass(bytecode, true).lookupClass();

            Method bind = proxyClass.getMethod("__bindStatics",
                    Interceptor[].class);
            bind.invoke(null, (Object) matchResult.interceptors());
            return proxyClass;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create static proxy for " + target.getName(),
                    e);
        }
    }

    /**
     * Evicts all cached proxy classes whose cache-key class is {@code target},
     * forcing the next {@code proxy(...)} call for it to regenerate a fresh
     * class. Existing proxy instances are unaffected — they hold direct
     * references to their own hidden class and target class.
     *
     * <p>Cache-key asymmetry: class proxies key on the target; interface
     * proxies key on the first interface. So for an interface proxy, pass the
     * first interface.
     *
     * @param target the cache-key class to evict; must not be null
     */
    public static void evict(Class<?> target) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        PROXY_CLASS_CACHE.removeIf(k -> k == target);
    }

    /**
     * Evicts all cached proxy classes whose cache-key class was loaded by the
     * given {@link ClassLoader}. Intended for long-running frameworks that
     * hot-deploy classes under a dedicated loader and want deterministic
     * cleanup when that loader is retired.
     *
     * @param cl the class loader whose proxy classes to evict; must not be
     *           null
     */
    public static void evictClassLoader(ClassLoader cl) {
        if (cl == null) {
            throw new IllegalArgumentException("classLoader must not be null");
        }
        PROXY_CLASS_CACHE.removeIf(k -> k != null && k.getClassLoader() == cl);
    }

    /**
     * Collects the shadowable static methods of {@code target} and its
     * superclasses (up to but excluding {@code Object}): public, non-final,
     * static methods, deduplicated subclass-first by {@code name + parameter
     * types}, then stable-sorted to match {@link #matchMethods}.
     */
    private static Method[] collectStaticMethods(Class<?> target) {
        List<Method> collected = new ArrayList<>();
        Set<String> seen = new HashSet<>();
        for (Class<?> c = target; c != null && c != Object.class;
             c = c.getSuperclass()) {
            for (Method m : c.getDeclaredMethods()) {
                int mods = m.getModifiers();
                if (!Modifier.isStatic(mods) || !Modifier.isPublic(mods)
                        || Modifier.isFinal(mods)) {
                    continue;
                }
                String key = m.getName()
                        + Arrays.toString(m.getParameterTypes());
                if (seen.add(key)) {
                    collected.add(m);
                }
            }
        }
        Method[] methods = collected.toArray(new Method[0]);
        Arrays.sort(methods,
                Comparator.comparing(Method::getName)
                        .thenComparing(m -> Arrays.toString(
                                m.getParameterTypes())));
        return methods;
    }

    /**
     * Builds the generated proxy constructor's argument array
     * {@code [interceptors..., ctorInterceptor?, constructorArgs...]} and its
     * parameter types, then reflectively instantiates the proxy class. A
     * {@code null} {@code ctorInterceptor} omits the interception slot.
     */
    private static Object instantiateProxy(Class<?> proxyClass,
                                           Interceptor[] interceptors,
                                           ConstructorInterceptor ctorInterceptor,
                                           Object[] constructorArgs)
            throws Exception {
        int interceptorCount = interceptors.length;
        int ctorOffset = (ctorInterceptor == null) ? 0 : 1;

        Object[] initArgs = new Object[interceptorCount + ctorOffset
                + constructorArgs.length];
        System.arraycopy(interceptors, 0, initArgs, 0, interceptorCount);
        if (ctorInterceptor != null) {
            initArgs[interceptorCount] = ctorInterceptor;
        }
        System.arraycopy(constructorArgs, 0, initArgs,
                interceptorCount + ctorOffset, constructorArgs.length);

        Class<?>[] ctorArgTypes = new Class<?>[initArgs.length];
        for (int i = 0; i < interceptorCount; i++) {
            ctorArgTypes[i] = Interceptor.class;
        }
        if (ctorInterceptor != null) {
            ctorArgTypes[interceptorCount] = ConstructorInterceptor.class;
        }
        for (int i = 0; i < constructorArgs.length; i++) {
            Object arg = constructorArgs[i];
            ctorArgTypes[interceptorCount + ctorOffset + i] =
                    (arg != null) ? arg.getClass() : Object.class;
        }

        Constructor<?> ctor = proxyClass.getConstructor(ctorArgTypes);
        return ctor.newInstance(initArgs);
    }

    @SuppressWarnings("unchecked")
    private static <E extends Throwable> void sneakyThrow(Throwable t)
            throws E {
        throw (E) t;
    }

    /**
     * Creates a proxy implementing all given interfaces. Methods with the
     * same signature and return type are merged; ambiguous conflicts throw
     * {@link IllegalArgumentException} (see {@link InterfaceMethodResolver}).
     *
     * @param interfaces the interfaces to implement
     * @param groups     one or more Group bindings
     * @return a proxy instance implementing every interface
     */
    private static Object proxyInterfaces(Class<?>[] interfaces,
                                          Group... groups) {
        if (interfaces == null || interfaces.length == 0) {
            throw new IllegalArgumentException(
                    "interfaces must not be null or empty");
        }
        if (groups == null || groups.length == 0) {
            throw new IllegalArgumentException(
                    "groups must not be null or empty");
        }
        for (Class<?> itf : interfaces) {
            if (itf == null || !itf.isInterface()) {
                throw new IllegalArgumentException(
                        "interfaces must contain only interfaces");
            }
        }
        Class<?>[] copy = interfaces.clone();
        MatchResult matchResult = matchMethods(copy, groups);
        CacheParams params = new CacheParams(null, copy,
                matchResult.mapping(), new Object[0], false);
        try {
            Class<?> proxyClass = PROXY_CLASS_CACHE.get(copy[0], params);
            int interceptorCount = matchResult.interceptors().length;
            Object[] initArgs = new Object[interceptorCount];
            System.arraycopy(matchResult.interceptors(), 0, initArgs, 0,
                    interceptorCount);
            Class<?>[] ctorArgTypes = new Class<?>[interceptorCount];
            Arrays.fill(ctorArgTypes, Interceptor.class);
            Constructor<?> ctor = proxyClass.getConstructor(ctorArgTypes);
            return ctor.newInstance((Object[]) initArgs);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create proxy for interfaces", e);
        }
    }

    /**
     * Creates a proxy whose methods are matched to interceptors declaratively,
     * from the {@code @Around}-annotated methods of the given {@code @Intercept}
     * object.
     *
     * <p>Each {@code @Around} method must have signature
     * {@code (Object, Method, Object[]) -> reference}. Methods not matching any
     * {@code @Around} method passthrough (direct super call), consistent with
     * the programmatic {@code Group} API.
     *
     * @param target      the class or interface to proxy
     * @param interceptor an instance of a {@code @Intercept}-annotated class
     * @param <T>         the proxy type
     * @return a proxy instance of type {@code T}
     * @throws IllegalArgumentException if {@code interceptor} is invalid
     */
    public static <T> T intercept(Class<T> target, Object interceptor) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        return proxy(target, resolveAnnotationGroups(interceptor));
    }

    /**
     * Reflects over the {@code @Intercept} object and builds a {@code Group[]}
     * from its {@code @Around} methods, in deterministic (name-sorted) order.
     */
    private static Group[] resolveAnnotationGroups(Object interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        Class<?> interceptorClass = interceptor.getClass();
        if (!interceptorClass.isAnnotationPresent(Intercept.class)) {
            throw new IllegalArgumentException(
                    interceptorClass.getName() + " must be annotated with @Intercept");
        }
        Method[] methods = interceptorClass.getDeclaredMethods();
        Arrays.sort(methods,
                Comparator.comparing(Method::getName)
                        .thenComparing(m -> Arrays.toString(m.getParameterTypes())));
        List<Group> groups = new ArrayList<>();
        for (Method m : methods) {
            Around around = m.getAnnotation(Around.class);
            if (around == null) {
                continue;
            }
            validateAroundMethod(m);
            groups.add(Group.of(toPredicate(around), toInterceptor(interceptor, m)));
        }
        if (groups.isEmpty()) {
            throw new IllegalArgumentException(interceptorClass.getName()
                    + " must declare at least one @Around method");
        }
        return groups.toArray(new Group[0]);
    }

    /**
     * Validates the fixed {@code @Around} method contract.
     */
    private static void validateAroundMethod(Method m) {
        if (Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException(
                    "@Around method must not be static: " + m.getName());
        }
        Class<?>[] params = m.getParameterTypes();
        if (params.length != 3
                || params[0] != Object.class
                || params[1] != Method.class
                || params[2] != Object[].class) {
            throw new IllegalArgumentException("@Around method must have signature "
                    + "(Object, Method, Object[]): " + m.getName());
        }
        Class<?> ret = m.getReturnType();
        if (ret == void.class || ret.isPrimitive()) {
            throw new IllegalArgumentException("@Around method must return a "
                    + "reference type (not void or primitive): " + m.getName());
        }
    }

    /**
     * Builds a {@link MethodPredicate} from all three dimensions of
     * {@code around}, AND-combined across dimensions and OR within each.
     */
    private static MethodPredicate toPredicate(Around around) {
        String[] globs = buildGlobs(around);
        String[] regexes = around.regex();
        Class<? extends Annotation>[] annotations = around.annotatedWith();
        for (String regex : regexes) {
            if (regex.isEmpty()) {
                throw new IllegalArgumentException("@Around regex must not be empty");
            }
            try {
                Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "Invalid @Around regex: " + regex, e);
            }
        }
        return m -> {
            if (globs.length > 0 && !matchesAnyGlob(globs, m.getName())) {
                return false;
            }
            if (regexes.length > 0 && !matchesAnyRegex(regexes, m.getName())) {
                return false;
            }
            if (annotations.length > 0 && !hasAnyAnnotation(m, annotations)) {
                return false;
            }
            return true;
        };
    }

    private static String[] buildGlobs(Around around) {
        List<String> globs = new ArrayList<>();
        if (!around.value().isEmpty()) {
            globs.add(around.value());
        }
        globs.addAll(Arrays.asList(around.glob()));
        return globs.toArray(new String[0]);
    }

    private static boolean matchesAnyGlob(String[] globs, String name) {
        for (String glob : globs) {
            if (globMatches(glob, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String glob, String name) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return name.matches(regex.toString());
    }

    private static boolean matchesAnyRegex(String[] regexes, String name) {
        for (String regex : regexes) {
            if (name.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyAnnotation(Method m,
            Class<? extends Annotation>[] annotations) {
        for (Class<? extends Annotation> a : annotations) {
            if (m.isAnnotationPresent(a)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Binds the {@code @Around} instance method to an {@link Interceptor} via a
     * {@code LambdaMetafactory} call site (no per-call reflection). The instance
     * is captured through the factory type; {@code implMethod} stays a direct
     * method handle so the metafactory can crack it.
     */
    private static Interceptor toInterceptor(Object instance, Method m) {
        try {
            MethodHandles.Lookup lookup = LookupManager.getLookup(instance.getClass());
            MethodHandle implMethod = lookup.unreflect(m);
            MethodType samType = MethodType.methodType(Object.class,
                    Object.class, Method.class, Object[].class);
            MethodType factoryType = MethodType.methodType(Interceptor.class,
                    instance.getClass());
            return (Interceptor) LambdaMetafactory.metafactory(
                    lookup, "intercept",
                    factoryType,
                    samType,
                    implMethod,
                    samType)
                    .getTarget().invokeWithArguments(instance);
        } catch (Error e) {
            throw e;
        } catch (Throwable t) {
            String message = "Failed to bind @Around method: " + m.getName();
            if (t instanceof IllegalAccessException) {
                message += " (non-public @Around method in a closed module may require --add-opens)";
            }
            throw new IllegalArgumentException(message, t);
        }
    }
}

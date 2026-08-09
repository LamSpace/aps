# Multi-Interceptor / Method Grouping — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development
> (recommended) or superpowers:executing-plans to implement this plan task-by-task.
> Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace the single `Interceptor` + binary `ClassFilter` model with a `Group`-based
multi-interceptor API where each method family binds to a distinct Interceptor instance,
with zero hot-path overhead.

**Architecture:** New `Group` and `MethodPredicate` types define method→interceptor
bindings. `AcceleratedProxy` performs Group chain matching before cache lookup, producing a
`MethodMapping` (method → interceptor index) and a deduped `Interceptor[]`. Generators
store one field per distinct Interceptor; each method override directly `GETFIELD`s its
assigned field — no array indirection. `ClassFilter` is deleted.

**Tech Stack:** Java 24, ASM 9.x, JUnit 5, JMH

## Global Constraints

- Keep existing `proxy(Class<T>, Interceptor)` and `proxy(Class<T>, Interceptor, ClassFilter)` API working — delegate internally to Group model
- `dispatch()` method and `invokeSuper` path unchanged
- `WeakCache` unchanged
- All existing tests must pass without modification
- Zero hot-path performance degradation vs current single-Interceptor path
- `Interceptor` interface unchanged

---

## Phase 2a: Core API (no bytecode changes)

### Task 1: Create `MethodPredicate.java`

**Files:**
- Create: `src/main/java/io/github/lamspace/MethodPredicate.java`

**Interfaces:**
- Produces: `@FunctionalInterface MethodPredicate { boolean test(Method method); }`

- [ ] **Step 1: Write the file**

```java
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

import java.lang.reflect.Method;

/**
 * Decides whether a method matches a Group's criteria.
 * Replaces {@link ClassFilter} in the multi-interceptor API.
 *
 * @see Group#of(MethodPredicate, Interceptor)
 */
@FunctionalInterface
public interface MethodPredicate {

    /**
     * Tests whether the given method matches this predicate.
     *
     * @param method a method declared by the target class
     * @return {@code true} if the method should be assigned to the
     *         associated Group's Interceptor
     */
    boolean test(Method method);
}
```

- [ ] **Step 2: Compile check**

Run: `mvn compile -s /home/lam/repo/settings.xml`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/MethodPredicate.java
git commit -m "feat: add MethodPredicate functional interface"
```

---

### Task 2: Create `MethodMapping.java`

**Files:**
- Create: `src/main/java/io/github/lamspace/MethodMapping.java`

**Interfaces:**
- Produces: `final class MethodMapping { MethodMapping(int[] indices); int[] indices(); boolean equals(Object); int hashCode(); }`
- Consumed by: Task 4 (AcceleratedProxy.matchMethods), Task 6 (MethodDispatcher), Task 7 (InterfaceDispatcher)

- [ ] **Step 1: Write the file**

```java
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

import java.util.Arrays;

/**
 * Maps each method (by stable-sorted index) to its assigned Interceptor
 * index in the deduped {@code Interceptor[]} array, or {@code -1} for
 * passthrough. Internal type — not part of the public API.
 */
final class MethodMapping {

    private final int[] indices;

    /**
     * @param indices {@code indices[i]} = interceptor index, or -1 for passthrough
     */
    MethodMapping(int[] indices) {
        this.indices = Arrays.copyOf(indices, indices.length);
    }

    int[] indices() {
        return indices;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodMapping other)) return false;
        return Arrays.equals(indices, other.indices);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(indices);
    }

    @Override
    public String toString() {
        return "MethodMapping" + Arrays.toString(indices);
    }
}
```

- [ ] **Step 2: Compile check**

Run: `mvn compile -s /home/lam/repo/settings.xml`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/MethodMapping.java
git commit -m "feat: add MethodMapping internal type"
```

---

### Task 3: Create `Group.java`

**Files:**
- Create: `src/main/java/io/github/lamspace/Group.java`

**Interfaces:**
- Produces: `Group.of(MethodPredicate, Interceptor)`, `Group.otherwise(Interceptor)`, package-private `predicate()`, `interceptor()`, `isOtherwise()`
- Consumes: `MethodPredicate` (Task 1), `Interceptor` (existing)
- Consumed by: Task 4 (AcceleratedProxy.matchMethods)

- [ ] **Step 1: Write the file**

```java
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
 *   Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
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

    MethodPredicate predicate() {
        return predicate;
    }

    Interceptor interceptor() {
        return interceptor;
    }

    boolean isOtherwise() {
        return otherwise;
    }
}
```

- [ ] **Step 2: Compile check**

Run: `mvn compile -s /home/lam/repo/settings.xml`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/Group.java
git commit -m "feat: add Group for multi-interceptor method binding"
```

---

### Task 4: Refactor `AcceleratedProxy.java`

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (full rewrite of internals)

**Interfaces:**
- Consumes: `Group` (Task 3), `MethodMapping` (Task 2), `MethodPredicate` (Task 1), `Interceptor` (existing), `ClassGenerator` (existing, Task 8), `InterfaceGenerator` (existing, Task 9), `WeakCache` (existing), `LookupManager` (existing)
- Produces: `proxy(Class<T>, Interceptor)`, `proxy(Class<T>, Group...)`, `proxy(Class<T>, Object[], Group...)`, `invokeSuper(Object, Method, Object[])`

- [ ] **Step 1: Add Logger field**

At line 49 (inside class body, after `private AcceleratedProxy() {}`):

```java
    private static final java.util.logging.Logger LOGGER =
            java.util.logging.Logger.getLogger(AcceleratedProxy.class.getName());
```

- [ ] **Step 2: Add `MatchResult` private record**

Replace the import section and `CacheParams` record. Add after the Logger field:

```java
    /**
     * Result of Group chain matching: deduped interceptors and method mapping.
     */
    private record MatchResult(Interceptor[] interceptors,
                               MethodMapping mapping) {
    }
```

- [ ] **Step 3: Replace `CacheParams` record (lines 57–75)**

Replace with:

```java
    /**
     * Composite cache key for generated proxy classes.
     * Interceptors compared by reference equality (==).
     */
    private record CacheParams(Class<?> targetClass,
                               Interceptor[] interceptors,
                               MethodMapping mapping,
                               Object[] constructorArgs) {
        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof CacheParams other)) return false;
            if (targetClass != other.targetClass) return false;
            if (!mapping.equals(other.mapping)) return false;
            if (!java.util.Arrays.equals(constructorArgs,
                    other.constructorArgs)) return false;
            if (interceptors.length != other.interceptors.length) return false;
            for (int i = 0; i < interceptors.length; i++) {
                if (interceptors[i] != other.interceptors[i]) return false;
            }
            return true;
        }

        @Override
        public int hashCode() {
            int result = System.identityHashCode(targetClass);
            result = 31 * result + mapping.hashCode();
            result = 31 * result + java.util.Arrays.hashCode(constructorArgs);
            for (Interceptor i : interceptors) {
                result = 31 * result + System.identityHashCode(i);
            }
            return result;
        }
    }
```

- [ ] **Step 4: Add `matchMethods` private static method**

Add after the `PROXY_CLASS_CACHE` field (after line 86):

```java
    /**
     * Evaluates the Group chain against every proxyable method on the target.
     * Returns deduped interceptors and a stable-sorted method-to-index mapping.
     */
    /**
     * Evaluates the Group chain against every proxyable method on the target.
     * Returns deduped interceptors and a stable-sorted method-to-index
     * mapping. Only proxyable methods are included in the mapping — static,
     * final, and (for class targets) private methods are excluded, matching
     * the iteration behavior of {@code MethodDispatcher.dispatchMethods}.
     */
    private static MatchResult matchMethods(Class<?> target, Group[] groups) {
        java.lang.reflect.Method[] rawMethods;
        if (target.isInterface()) {
            rawMethods = target.getMethods();
        } else {
            rawMethods = target.getDeclaredMethods();
        }

        // 1. Filter to proxyable methods only (same criteria as
        //    MethodDispatcher / InterfaceDispatcher)
        java.util.List<java.lang.reflect.Method> proxyable =
                new java.util.ArrayList<>();
        for (java.lang.reflect.Method m : rawMethods) {
            int mods = m.getModifiers();
            if (java.lang.reflect.Modifier.isStatic(mods)
                    || java.lang.reflect.Modifier.isFinal(mods)) {
                continue;
            }
            if (!target.isInterface()
                    && java.lang.reflect.Modifier.isPrivate(mods)) {
                continue;
            }
            proxyable.add(m);
        }
        java.lang.reflect.Method[] methods =
                proxyable.toArray(new java.lang.reflect.Method[0]);

        // 2. Stable sort: name then parameter types for cross-JVM
        //    determinism (must match sort in dispatchMethods)
        java.util.Arrays.sort(methods,
                java.util.Comparator.comparing(
                        java.lang.reflect.Method::getName)
                        .thenComparing(m -> java.util.Arrays.toString(
                                m.getParameterTypes())));

        // 3. Match each method against the Group chain
        int[] indices = new int[methods.length];
        java.util.List<Interceptor> interceptorList =
                new java.util.ArrayList<>();
        java.util.Map<Interceptor, Integer> interceptorIndex =
                new java.util.IdentityHashMap<>();

        for (int i = 0; i < methods.length; i++) {
            java.lang.reflect.Method m = methods[i];
            int matchedGroup = -1;

            for (int g = 0; g < groups.length; g++) {
                if (groups[g].predicate().test(m)) {
                    if (matchedGroup != -1
                            && !groups[g].isOtherwise()) {
                        final int firstMatch = matchedGroup;
                        final int secondMatch = g;
                        LOGGER.warning(() -> String.format(
                                "Method '%s' matches multiple Groups: "
                                        + "#%d and #%d. "
                                        + "Using first match (Group #%d).",
                                m.getName(), firstMatch, secondMatch,
                                firstMatch));
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
```

- [ ] **Step 5: Rewrite `generateProxyClass` (lines 92–122)**

Replace with:

```java
    private static Class<?> generateProxyClass(Class<?> target,
                                                CacheParams params) {
        try {
            byte[] bytecode;

            if (target.isInterface()) {
                InterfaceGenerator generator = new InterfaceGenerator(target,
                        params.interceptors(), params.mapping());
                bytecode = generator.generate();
            } else {
                ClassGenerator generator = new ClassGenerator(target,
                        params.interceptors(), params.mapping(),
                        params.constructorArgs());
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
```

- [ ] **Step 6: Rewrite `proxy(Class, Interceptor)` (lines 154–156)**

Replace with:

```java
    public static <T> T proxy(Class<T> target, Interceptor interceptor) {
        return proxy(target, new Object[0],
                Group.otherwise(interceptor));
    }
```

- [ ] **Step 7: Rewrite `proxy(Class, Interceptor, ClassFilter)` (lines 168–172)**

Replace with:

```java
    @SuppressWarnings("unchecked")
    public static <T> T proxy(Class<T> target, Interceptor interceptor,
                              ClassFilter filter) {
        if (filter == null) {
            return proxy(target, interceptor);
        }
        return proxy(target, new Object[0],
                Group.of(filter::accept, interceptor));
    }
```

- [ ] **Step 8: Add new `proxy(Class, Group...)` overload**

Add after the two-arg proxy method:

```java
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
```

- [ ] **Step 9: Rewrite the full `proxy(Class, Interceptor, ClassFilter, Object...)` into `proxy(Class, Object[], Group...)` (lines 188–235)**

Replace everything from the old full proxy method to the end of the class with:

```java
    /**
     * Creates a proxy with method-group-based interceptor assignment
     * and constructor arguments (for class proxies only).
     *
     * @param target          the class or interface to proxy
     * @param constructorArgs arguments to pass to the superclass constructor;
     *                        empty array for no-arg constructor
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

        // 2. Cache lookup
        CacheParams params = new CacheParams(target,
                matchResult.interceptors(), matchResult.mapping(),
                constructorArgs);

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
            Class<?>[] ctorArgTypes =
                    new Class<?>[initArgs.length];
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
```

- [ ] **Step 10: Update Javadoc references from `ClassFilter` to `Group`**

Replace `@see ClassFilter` with `@see Group` in the class Javadoc.

- [ ] **Step 11: Compile check**

Run: `mvn compile -s /home/lam/repo/settings.xml`
Expected: **BUILD FAILURE** — `ClassGenerator` and `InterfaceGenerator` still reference `ClassFilter` in their constructors. This is expected; they will be fixed in Tasks 8–9.

- [ ] **Step 12: Verify existing tests still pass (after Phase 2a)**

The generators haven't changed yet, so tests should fail at compile time. We'll verify after Phase 2b.

- [ ] **Step 13: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java
git commit -m "refactor: redesign AcceleratedProxy API for multi-interceptor Group model"
```

---

## Phase 2b: Generator Adaptation

### Task 5: Refactor `MethodDispatcher.java`

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/MethodDispatcher.java`

**Interfaces:**
- Consumes: `MethodMapping` (Task 2), `Interceptor` (existing)
- Produces: `dispatchMethods(ClassWriter, Class<?>, String, MethodMapping, int, ClinitRegistry) -> List<String>`
- The old `CALLBACK_FIELD` constant is removed; field names are now `"_interceptor$" + index`

- [ ] **Step 1: Update imports (lines 19–20)**

Remove `import io.github.lamspace.ClassFilter;`. Add `import io.github.lamspace.MethodMapping;`.

- [ ] **Step 2: Remove `CALLBACK_FIELD` constant (line 39)**

Delete line: `private static final String CALLBACK_FIELD = "_callback";`

Add in its place:

```java
    private static final String INTERCEPTOR_FIELD_PREFIX = "_interceptor$";
```

- [ ] **Step 3: Change `dispatchMethods` signature (lines 56–59)**

Replace:

```java
    public static List<String> dispatchMethods(ClassWriter cw, Class<?> targetClass,
                                               String generatedInternal,
                                               ClassFilter filter,
                                               ClinitRegistry registry) {
```

With:

```java
    public static List<String> dispatchMethods(ClassWriter cw, Class<?> targetClass,
                                               String generatedInternal,
                                               MethodMapping mapping,
                                               int interceptorCount,
                                               ClinitRegistry registry) {
```

- [ ] **Step 4: Add method sorting before the iteration loop**

After the opening of `dispatchMethods`, add sorting to match `matchMethods` order:

```java
        // Stable sort for cross-JVM determinism
        java.lang.reflect.Method[] methods = targetClass.getDeclaredMethods();
        java.util.Arrays.sort(methods,
                java.util.Comparator.comparing(
                        java.lang.reflect.Method::getName)
                        .thenComparing(m -> java.util.Arrays.toString(
                                m.getParameterTypes())));

        for (java.lang.reflect.Method method : methods) {
            int mods = method.getModifiers();
            ...
```

The old `for (Method method : targetClass.getDeclaredMethods())` loop body stays the same; only the iteration source changes from `targetClass.getDeclaredMethods()` to the sorted `methods` array.

- [ ] **Step 5: Change the `shouldIntercept` logic (line 69)**

Replace:

```java
            boolean shouldIntercept = (filter == null) || filter.accept(method);
```

With:

```java
            int interceptorIndex = mapping.indices()[index];
            boolean shouldIntercept = interceptorIndex >= 0;
```

- [ ] **Step 5: Change `generateOverride` call (lines 79–80)**

Replace:

```java
            generateOverride(cw, method, generatedInternal, shouldIntercept,
                    methodFieldName, index);
```

With:

```java
            generateOverride(cw, method, generatedInternal, shouldIntercept,
                    interceptorIndex, methodFieldName);
```

- [ ] **Step 6: Change `generateOverride` signature (lines 94–98)**

Replace:

```java
    private static void generateOverride(ClassWriter cw, Method method,
                                         String generatedInternal,
                                         boolean shouldIntercept,
                                         String methodFieldName,
                                         int methodIndex) {
```

With:

```java
    private static void generateOverride(ClassWriter cw, Method method,
                                         String generatedInternal,
                                         boolean shouldIntercept,
                                         int interceptorIndex,
                                         String methodFieldName) {
```

- [ ] **Step 7: Change the interceptor field load (lines 147–149)**

Replace:

```java
        // 1. Load interceptor field
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                CALLBACK_FIELD, Type.getDescriptor(Interceptor.class));
```

With:

```java
        // 1. Load interceptor field
        String fieldName = INTERCEPTOR_FIELD_PREFIX + interceptorIndex;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                fieldName, Type.getDescriptor(Interceptor.class));
```

- [ ] **Step 8: Update Javadoc for `dispatchMethods` (lines 44–56)**

Replace the Javadoc to reference `MethodMapping` instead of `ClassFilter`.

- [ ] **Step 9: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/MethodDispatcher.java
git commit -m "refactor: MethodDispatcher uses per-group interceptor fields"
```

---

### Task 6: Refactor `InterfaceDispatcher.java`

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java`

The changes mirror Task 5 exactly but for the interface proxy path.

- [ ] **Step 1: Update imports**

Remove `import io.github.lamspace.ClassFilter;`. Add `import io.github.lamspace.MethodMapping;`.

- [ ] **Step 2: Remove `CALLBACK_FIELD` constant, add `INTERCEPTOR_FIELD_PREFIX`**

Replace `private static final String CALLBACK_FIELD = "_callback";` with:

```java
    private static final String INTERCEPTOR_FIELD_PREFIX = "_interceptor$";
```

- [ ] **Step 3: Change `dispatchMethods` signature (lines 55–58)**

Replace:

```java
    static List<String> dispatchMethods(ClassWriter cw, Class<?> interfaceClass,
                                        String generatedInternal,
                                        ClassFilter filter,
                                        ClinitRegistry registry) {
```

With:

```java
    static List<String> dispatchMethods(ClassWriter cw, Class<?> interfaceClass,
                                        String generatedInternal,
                                        MethodMapping mapping,
                                        int interceptorCount,
                                        ClinitRegistry registry) {
```

- [ ] **Step 4: Add method sorting before the iteration loop**

After the opening of `dispatchMethods`, add sorting to match `matchMethods` order:

```java
        // Stable sort for cross-JVM determinism
        java.lang.reflect.Method[] methods = interfaceClass.getMethods();
        java.util.Arrays.sort(methods,
                java.util.Comparator.comparing(
                        java.lang.reflect.Method::getName)
                        .thenComparing(m -> java.util.Arrays.toString(
                                m.getParameterTypes())));

        for (java.lang.reflect.Method method : methods) {
            int mods = method.getModifiers();
            ...
```

The old `for (Method method : interfaceClass.getMethods())` loop body stays the same; only the iteration source changes.

- [ ] **Step 5: Change the `shouldIntercept` logic (line 68)**

Replace:

```java
            boolean shouldIntercept = (filter == null) || filter.accept(method);
```

With:

```java
            int interceptorIndex = mapping.indices()[index];
            boolean shouldIntercept = interceptorIndex >= 0;
```

- [ ] **Step 5: Change `generateImplementation` call (lines 79–80)**

Replace:

```java
            generateImplementation(cw, method, generatedInternal,
                    shouldIntercept, methodFieldName);
```

With:

```java
            generateImplementation(cw, method, generatedInternal,
                    shouldIntercept, interceptorIndex, methodFieldName);
```

- [ ] **Step 6: Change `generateImplementation` signature (lines 94–98)**

Replace:

```java
    private static void generateImplementation(ClassWriter cw, Method method,
                                               String generatedInternal,
                                               boolean shouldIntercept,
                                               String methodFieldName) {
```

With:

```java
    private static void generateImplementation(ClassWriter cw, Method method,
                                               String generatedInternal,
                                               boolean shouldIntercept,
                                               int interceptorIndex,
                                               String methodFieldName) {
```

- [ ] **Step 7: Change the interceptor field load (lines 148–151)**

Replace:

```java
        // 1. Load interceptor: this._callback
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                CALLBACK_FIELD, Type.getDescriptor(Interceptor.class));
```

With:

```java
        // 1. Load interceptor: this._interceptor$N
        String fieldName = INTERCEPTOR_FIELD_PREFIX + interceptorIndex;
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                fieldName, Type.getDescriptor(Interceptor.class));
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java
git commit -m "refactor: InterfaceDispatcher uses per-group interceptor fields"
```

---

### Task 7: Refactor `ClassGenerator.java`

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/ClassGenerator.java`

**Interfaces:**
- Consumes: `MethodMapping` (Task 2), `Interceptor` (existing), `MethodDispatcher` (Task 5)
- Produces: Constructor `ClassGenerator(Class<?>, Interceptor[], MethodMapping, Object...)`, `generate()`, `constructorArgs()`

- [ ] **Step 1: Update imports**

Remove `import io.github.lamspace.ClassFilter;`. Add `import io.github.lamspace.MethodMapping;`.

- [ ] **Step 2: Change fields (lines 52–53)**

Replace:

```java
    private final Class<?> targetClass;
    private final ClassFilter filter;
```

With:

```java
    private final Class<?> targetClass;
    private final Interceptor[] interceptors;
    private final MethodMapping mapping;
```

- [ ] **Step 3: Rewrite constructors (lines 59–79)**

Replace both constructors with:

```java
    /**
     * Creates a generator for the given target class.
     *
     * @param targetClass     the class to proxy
     * @param interceptors    deduped interceptor instances
     * @param mapping         method → interceptor index mapping
     */
    public ClassGenerator(Class<?> targetClass, Interceptor[] interceptors,
                          MethodMapping mapping) {
        this(targetClass, interceptors, mapping, new Object[0]);
    }

    /**
     * Creates a generator for the given target class with constructor
     * arguments.
     *
     * @param targetClass     the class to proxy
     * @param interceptors    deduped interceptor instances
     * @param mapping         method → interceptor index mapping
     * @param constructorArgs arguments to pass to the superclass constructor
     */
    public ClassGenerator(Class<?> targetClass, Interceptor[] interceptors,
                          MethodMapping mapping,
                          Object... constructorArgs) {
        this.targetClass = targetClass;
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
        this.constructorArgs = (constructorArgs == null)
                ? new Object[0] : constructorArgs;
    }
```

- [ ] **Step 4: Rewrite `constructorArgs()` (lines 87–95)**

Replace with:

```java
    public Class<?>[] constructorArgs() {
        Class<?>[] all = new Class<?>[interceptors.length
                + constructorArgs.length];
        for (int i = 0; i < interceptors.length; i++) {
            all[i] = Interceptor.class;
        }
        for (int i = 0; i < constructorArgs.length; i++) {
            Object arg = constructorArgs[i];
            all[interceptors.length + i] = (arg != null)
                    ? arg.getClass() : Object.class;
        }
        return all;
    }
```

- [ ] **Step 5: Update `generate()` — change field declarations and MethodDispatcher call**

In `generate()` (line 103+):

Replace the field declaration (lines 118–121):

```java
        // -- Interceptor field --
        String callbackDesc = Type.getDescriptor(Interceptor.class);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "_callback", callbackDesc, null, null);
```

With:

```java
        // -- Interceptor fields (one per distinct interceptor) --
        String interceptorDesc = Type.getDescriptor(Interceptor.class);
        for (int i = 0; i < interceptors.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    "_interceptor$" + i, interceptorDesc, null, null);
        }
```

Replace the `generateConstructor` call with new version that handles M interceptors:

```java
        // -- Constructor: stores interceptors, delegates to super() --
        generateConstructor(cw, generatedInternal, targetInternal,
                interceptorDesc);
```

Replace the MethodDispatcher call (lines 128–129):

```java
        List<String> dispatched = MethodDispatcher.dispatchMethods(
                cw, targetClass, generatedInternal, mapping,
                interceptors.length, registry);
```

- [ ] **Step 6: Rewrite `generateConstructor` (currently a private method, adapt to multi-field)**

Replace the `generateConstructor` method:

```java
    private void generateConstructor(ClassWriter cw, String generatedInternal,
                                     String targetInternal,
                                     String interceptorDesc) {
        // Build descriptor: (LInterceptor;...LInterceptor;[superArgs])V
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < interceptors.length; i++) {
            desc.append(interceptorDesc);
        }
        for (Object arg : constructorArgs) {
            Class<?> type = (arg != null) ? arg.getClass() : Object.class;
            desc.append(Type.getDescriptor(type));
        }
        desc.append(")V");

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                desc.toString(), null, null);
        mv.visitCode();

        // super(superArgs...)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        int slot = 1 + interceptors.length;
        for (Object arg : constructorArgs) {
            Class<?> type = (arg != null) ? arg.getClass() : Object.class;
            mv.visitVarInsn(BytecodeUtils.loadOpcode(type), slot);
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL, targetInternal,
                "<init>",
                Type.getConstructorDescriptor(
                        resolveSuperConstructor()), false);

        // this._interceptor$i = arg(i+1)
        for (int i = 0; i < interceptors.length; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1 + i);
            mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal,
                    "_interceptor$" + i, interceptorDesc);
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }

    private Constructor<?> resolveSuperConstructor() {
        for (Constructor<?> ctor : targetClass.getDeclaredConstructors()) {
            Class<?>[] paramTypes = ctor.getParameterTypes();
            if (paramTypes.length == constructorArgs.length) {
                boolean match = true;
                for (int i = 0; i < paramTypes.length; i++) {
                    Class<?> expected = paramTypes[i];
                    Object arg = constructorArgs[i];
                    if (arg == null) {
                        if (expected.isPrimitive()) {
                            match = false;
                            break;
                        }
                    } else if (!expected.isAssignableFrom(arg.getClass())) {
                        match = false;
                        break;
                    }
                }
                if (match) {
                    return ctor;
                }
            }
        }
        throw new IllegalArgumentException(
                "No matching super constructor found for "
                        + targetClass.getName());
    }
```

- [ ] **Step 7: Update Javadoc**

Update the class Javadoc to reference `Interceptor[]` and `MethodMapping` instead of `ClassFilter`.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/ClassGenerator.java
git commit -m "refactor: ClassGenerator uses Interceptor[] + MethodMapping"
```

---

### Task 8: Refactor `InterfaceGenerator.java`

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java`

- [ ] **Step 1: Update imports**

Remove `import io.github.lamspace.ClassFilter;`. Add `import io.github.lamspace.MethodMapping;`.

- [ ] **Step 2: Change fields (lines 47–48)**

Replace:

```java
    private final Class<?> interfaceClass;
    private final ClassFilter filter;
```

With:

```java
    private final Class<?> interfaceClass;
    private final Interceptor[] interceptors;
    private final MethodMapping mapping;
```

- [ ] **Step 3: Rewrite constructor (lines 57–60)**

Replace:

```java
    public InterfaceGenerator(Class<?> interfaceClass, ClassFilter filter) {
        this.interfaceClass = interfaceClass;
        this.filter = filter;
    }
```

With:

```java
    public InterfaceGenerator(Class<?> interfaceClass,
                              Interceptor[] interceptors,
                              MethodMapping mapping) {
        this.interfaceClass = interfaceClass;
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
    }
```

- [ ] **Step 4: Update `generate()` — field declarations (lines 84–87)**

Replace:

```java
        // -- Interceptor field --
        String callbackDesc = Type.getDescriptor(Interceptor.class);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "_callback", callbackDesc, null, null);
```

With:

```java
        // -- Interceptor fields (one per distinct interceptor) --
        String interceptorDesc = Type.getDescriptor(Interceptor.class);
        for (int i = 0; i < interceptors.length; i++) {
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    "_interceptor$" + i, interceptorDesc, null, null);
        }
```

- [ ] **Step 5: Update constructor generation call (line 90)**

Replace:

```java
        generateConstructor(cw, generatedInternal, callbackDesc);
```

With:

```java
        generateConstructor(cw, generatedInternal, interceptorDesc);
```

- [ ] **Step 6: Update InterfaceDispatcher call (lines 93–95)**

Replace:

```java
        InterfaceDispatcher.dispatchMethods(cw, interfaceClass,
                generatedInternal, filter, registry);
```

With:

```java
        InterfaceDispatcher.dispatchMethods(cw, interfaceClass,
                generatedInternal, mapping, interceptors.length, registry);
```

- [ ] **Step 7: Rewrite `generateConstructor` (lines 121–138)**

Replace with multi-interceptor version:

```java
    private void generateConstructor(ClassWriter cw, String generatedInternal,
                                     String interceptorDesc) {
        // Build descriptor: (LInterceptor;...LInterceptor;)V
        StringBuilder desc = new StringBuilder("(");
        for (int i = 0; i < interceptors.length; i++) {
            desc.append(interceptorDesc);
        }
        desc.append(")V");

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                desc.toString(), null, null);
        mv.visitCode();
        // super()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        // this._interceptor$i = arg(i+1)
        for (int i = 0; i < interceptors.length; i++) {
            mv.visitVarInsn(Opcodes.ALOAD, 0);
            mv.visitVarInsn(Opcodes.ALOAD, 1 + i);
            mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal,
                    "_interceptor$" + i, interceptorDesc);
        }
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 1 + interceptors.length);
        mv.visitEnd();
    }
```

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceGenerator.java
git commit -m "refactor: InterfaceGenerator uses Interceptor[] + MethodMapping"
```

---

### Task 9: Delete `ClassFilter.java` and Clean Up

**Files:**
- Delete: `src/main/java/io/github/lamspace/ClassFilter.java`
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (update Javadoc, remove ClassFilter imports if any remain)

- [ ] **Step 1: Delete ClassFilter.java**

```bash
git rm src/main/java/io/github/lamspace/ClassFilter.java
```

- [ ] **Step 2: Clean up remaining ClassFilter references**

Search for any remaining `ClassFilter` imports or references in source files:

```bash
grep -r "ClassFilter" src/main/java/ --include="*.java"
```

Expected: No output. If any references remain, update them.

- [ ] **Step 3: Compile check**

Run: `mvn compile -s /home/lam/repo/settings.xml`
Expected: BUILD SUCCESS

- [ ] **Step 4: Run all existing tests**

Run: `mvn test -s /home/lam/repo/settings.xml`
Expected: All existing tests pass. If any fail, investigate and fix before proceeding.

- [ ] **Step 5: Commit**

```bash
git commit -m "refactor: remove ClassFilter, subsumed by Group model"
```

---

## Phase 2c: Test Coverage

### Task 10: Write `GroupMatchingTest.java`

**Files:**
- Create: `src/test/java/io/github/lamspace/GroupMatchingTest.java`

- [ ] **Step 1: Write the test file**

```java
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

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

/**
 * Tests Group matching engine semantics.
 */
class GroupMatchingTest {

    interface Sample {
        String getName();
        void setName(String name);
        int getAge();
        String toString();
        boolean equals(Object obj);
        int hashCode();
    }

    private final Interceptor interceptorA = (proxy, method, args) -> null;
    private final Interceptor interceptorB = (proxy, method, args) -> null;
    private final Interceptor interceptorC = (proxy, method, args) -> null;

    @Test
    void firstMatchWins() {
        // Both groups match "getName" — first one wins
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().startsWith("get"), interceptorA),
                Group.of(m -> m.getName().startsWith("get"), interceptorB));

        assertNotNull(proxy);
        // interceptorA should handle getName, not interceptorB
        assertEquals("from A", interceptorA.intercept(proxy,
                Sample.class.getMethods()[0], new Object[0]));
    }

    @Test
    void noMatchDefaultsToPassthrough() {
        // Only match "get*" — setName should passthrough
        AtomicBoolean setNameCalled = new AtomicBoolean(false);

        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().startsWith("get"),
                        (p, method, args) -> {
                            return AcceleratedProxy.invokeSuper(p, method, args);
                        }));

        // getName goes through interceptor — works via invokeSuper
        assertNotNull(proxy.getName());

        // setName is passthrough — no interceptor involved
        proxy.setName("test");
        // Passthrough means it calls super.toString() which is Object.toString()
        // For interface proxy, passthrough non-Object methods throw AbstractMethodError
    }

    @Test
    void otherwiseFallback() {
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().equals("getName"), interceptorA),
                Group.otherwise(interceptorB));

        assertNotNull(proxy);
    }

    @Test
    void emptyGroupsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(Sample.class, new Group[0]));
    }

    @Test
    void nullGroupsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(Sample.class, (Group[]) null));
    }

    @Test
    void groupOfRejectsNullPredicate() {
        assertThrows(NullPointerException.class, () ->
                Group.of(null, interceptorA));
    }

    @Test
    void groupOfRejectsNullInterceptor() {
        assertThrows(NullPointerException.class, () ->
                Group.of(m -> true, null));
    }

    @Test
    void otherwiseRejectsNullInterceptor() {
        assertThrows(NullPointerException.class, () ->
                Group.otherwise(null));
    }

    @Test
    void sharedInterceptorInstanceCreatesOneField() throws Exception {
        // Same interceptor instance used in two groups → deduped to 1 field
        Interceptor shared = (proxy, method, args) -> null;
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().startsWith("get"), shared),
                Group.of(m -> m.getName().startsWith("set"), shared));

        assertNotNull(proxy);
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -s /home/lam/repo/settings.xml -Dtest=GroupMatchingTest`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/GroupMatchingTest.java
git commit -m "test: add Group matching engine tests"
```

---

### Task 11: Write `DuplicateMatchWarningTest.java`

**Files:**
- Create: `src/test/java/io/github/lamspace/DuplicateMatchWarningTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;
import java.util.logging.*;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateMatchWarningTest {

    interface OverlapTarget {
        String getUserName();
        void setUserName(String name);
        int getAge();
    }

    @Test
    void duplicateMatchLogsWarning() {
        Logger logger = Logger.getLogger(
                AcceleratedProxy.class.getName());
        TestHandler handler = new TestHandler();
        handler.setLevel(Level.WARNING);
        logger.addHandler(handler);

        Interceptor a = (p, m, args) -> null;
        Interceptor b = (p, m, args) -> null;

        AcceleratedProxy.proxy(OverlapTarget.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("getUser"), b));

        boolean hasWarning = handler.records().stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING
                        && r.getMessage().contains("getUserName")
                        && r.getMessage().contains("multiple Groups"));
        assertTrue(hasWarning,
                "Should log WARNING for overlapping predicates");

        logger.removeHandler(handler);
    }

    @Test
    void otherwiseDoesNotTriggerWarning() {
        Logger logger = Logger.getLogger(
                AcceleratedProxy.class.getName());
        TestHandler handler = new TestHandler();
        handler.setLevel(Level.WARNING);
        logger.addHandler(handler);

        Interceptor a = (p, m, args) -> null;
        Interceptor fallback = (p, m, args) -> null;

        // get* matches both Group #0 and the otherwise — but
        // otherwise should not generate a warning
        AcceleratedProxy.proxy(OverlapTarget.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.otherwise(fallback));

        boolean hasWarning = handler.records().stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING
                        && r.getMessage().contains("multiple Groups"));
        assertFalse(hasWarning,
                "otherwise Group should not trigger duplicate warning");

        logger.removeHandler(handler);
    }

    @Test
    void distinctPredicatesNoWarning() {
        Logger logger = Logger.getLogger(
                AcceleratedProxy.class.getName());
        TestHandler handler = new TestHandler();
        handler.setLevel(Level.WARNING);
        logger.addHandler(handler);

        Interceptor a = (p, m, args) -> null;
        Interceptor b = (p, m, args) -> null;

        AcceleratedProxy.proxy(OverlapTarget.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("set"), b));

        boolean hasWarning = handler.records().stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING
                        && r.getMessage().contains("multiple Groups"));
        assertFalse(hasWarning,
                "Non-overlapping predicates should not generate warning");

        logger.removeHandler(handler);
    }

    /** Collects log records for assertion. */
    private static class TestHandler extends Handler {
        private final java.util.List<LogRecord> records =
                new java.util.ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            records.add(record);
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        java.util.List<LogRecord> records() {
            return records;
        }
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -s /home/lam/repo/settings.xml -Dtest=DuplicateMatchWarningTest`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/DuplicateMatchWarningTest.java
git commit -m "test: add duplicate match warning tests"
```

---

### Task 12: Write `MultiInterceptorClassProxyTest.java`

**Files:**
- Create: `src/test/java/io/github/lamspace/MultiInterceptorClassProxyTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import static org.junit.jupiter.api.Assertions.*;

class MultiInterceptorClassProxyTest {

    public static class Greeter {
        public String getGreeting() { return "hello"; }
        public void setGreeting(String g) { /* no-op */ }
        public String toString() { return "Greeter"; }
    }

    @Test
    void getterAndSetterUseDifferentInterceptors() {
        AtomicReference<String> getterCalled = new AtomicReference<>();
        AtomicReference<String> setterCalled = new AtomicReference<>();

        Interceptor getterInterceptor = (proxy, method, args) -> {
            getterCalled.set(method.getName());
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        };
        Interceptor setterInterceptor = (proxy, method, args) -> {
            setterCalled.set(method.getName());
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        };

        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
                Group.of(m -> m.getName().startsWith("set"), setterInterceptor));

        assertEquals("hello", proxy.getGreeting());
        assertEquals("getGreeting", getterCalled.get());
        assertNull(setterCalled.get());

        proxy.setGreeting("hi");
        assertEquals("setGreeting", setterCalled.get());
    }

    @Test
    void passthroughMethodBypassesInterceptor() {
        AtomicInteger interceptorCalls = new AtomicInteger(0);
        Interceptor counting = (proxy, method, args) -> {
            interceptorCalls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        };

        // Only intercept setGreeting — getGreeting and toString passthrough
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("set"), counting));

        // passthrough getter
        assertEquals("hello", proxy.getGreeting());
        assertEquals(0, interceptorCalls.get());

        // intercepted setter
        proxy.setGreeting("test");
        assertEquals(1, interceptorCalls.get());

        // passthrough toString
        proxy.toString();
        assertEquals(1, interceptorCalls.get());
    }

    @Test
    void invokeSuperWorksInAnyGroup() {
        Interceptor a = (proxy, method, args) ->
                AcceleratedProxy.invokeSuper(proxy, method, args);
        Interceptor b = (proxy, method, args) ->
                AcceleratedProxy.invokeSuper(proxy, method, args);

        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().equals("getGreeting"), a),
                Group.otherwise(b));

        assertEquals("hello", proxy.getGreeting());
        proxy.setGreeting("x");
        assertEquals("Greeter", proxy.toString());
    }

    @Test
    void sharedInterceptorDedup() throws Exception {
        Interceptor shared = (proxy, method, args) ->
                AcceleratedProxy.invokeSuper(proxy, method, args);

        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), shared),
                Group.of(m -> m.getName().startsWith("set"), shared));

        // Both getter and setter work correctly
        assertEquals("hello", proxy.getGreeting());
        proxy.setGreeting("world");
    }

    @Test
    void statefulInterceptorPerGroup() {
        AtomicInteger getterCount = new AtomicInteger(0);
        AtomicInteger setterCount = new AtomicInteger(0);

        Interceptor getter = (proxy, method, args) -> {
            getterCount.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        };
        Interceptor setter = (proxy, method, args) -> {
            setterCount.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        };

        Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), getter),
                Group.of(m -> m.getName().startsWith("set"), setter));

        proxy.getGreeting();
        proxy.getGreeting();
        proxy.setGreeting("a");

        assertEquals(2, getterCount.get());
        assertEquals(1, setterCount.get());
    }

    @Test
    void cacheHitWithSameGroups() {
        Interceptor a = (p, m, args) -> null;
        Interceptor b = (p, m, args) -> null;

        Greeter p1 = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("set"), b));

        Greeter p2 = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("set"), b));

        // Same config → same proxy class
        assertEquals(p1.getClass(), p2.getClass());
    }

    @Test
    void oldApiStillWorks() {
        AtomicInteger calls = new AtomicInteger(0);
        Interceptor interceptor = (proxy, method, args) -> {
            calls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        };

        // Old single-interceptor API
        Greeter proxy = AcceleratedProxy.proxy(Greeter.class, interceptor);

        proxy.getGreeting();
        proxy.setGreeting("x");
        assertEquals(2, calls.get());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -s /home/lam/repo/settings.xml -Dtest=MultiInterceptorClassProxyTest`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/MultiInterceptorClassProxyTest.java
git commit -m "test: add multi-interceptor class proxy tests"
```

---

### Task 13: Write `MultiInterceptorInterfaceProxyTest.java`

**Files:**
- Create: `src/test/java/io/github/lamspace/MultiInterceptorInterfaceProxyTest.java`

- [ ] **Step 1: Write the test file**

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;
import java.util.concurrent.atomic.AtomicInteger;
import static org.junit.jupiter.api.Assertions.*;

class MultiInterceptorInterfaceProxyTest {

    interface Calculator {
        int add(int a, int b);
        int subtract(int a, int b);
        int multiply(int a, int b);
    }

    static class CalculatorImpl implements Calculator {
        public int add(int a, int b) { return a + b; }
        public int subtract(int a, int b) { return a - b; }
        public int multiply(int a, int b) { return a * b; }
    }

    @Test
    void differentGroupsForDifferentOperations() {
        AtomicInteger addCount = new AtomicInteger(0);
        AtomicInteger mulCount = new AtomicInteger(0);

        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().equals("add"),
                        (p, method, args) -> {
                            addCount.incrementAndGet();
                            return CalculatorImpl.class
                                    .getMethod(method.getName(),
                                            method.getParameterTypes())
                                    .invoke(new CalculatorImpl(), args);
                        }),
                Group.of(m -> m.getName().equals("multiply"),
                        (p, method, args) -> {
                            mulCount.incrementAndGet();
                            return CalculatorImpl.class
                                    .getMethod(method.getName(),
                                            method.getParameterTypes())
                                    .invoke(new CalculatorImpl(), args);
                        }));

        assertEquals(5, proxy.add(2, 3));
        assertEquals(1, addCount.get());
        assertEquals(0, mulCount.get());

        assertEquals(6, proxy.multiply(2, 3));
        assertEquals(1, addCount.get());
        assertEquals(1, mulCount.get());
    }

    @Test
    void passthroughMethodThrowsAbstractMethodError() {
        // Only intercept "add" — subtract should throw
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().equals("add"),
                        (p, method, args) -> 42));

        assertEquals(42, proxy.add(1, 2));

        assertThrows(AbstractMethodError.class, () ->
                proxy.subtract(5, 3));
    }

    @Test
    void otherwiseCoversAllRemainingMethods() {
        AtomicInteger defaultCount = new AtomicInteger(0);

        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().equals("add"),
                        (p, method, args) -> 42),
                Group.otherwise((p, method, args) -> {
                    defaultCount.incrementAndGet();
                    return 0;
                }));

        proxy.subtract(5, 3);
        proxy.multiply(2, 3);
        assertEquals(2, defaultCount.get());
    }

    @Test
    void oldApiStillWorksForInterfaces() {
        AtomicInteger calls = new AtomicInteger(0);

        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (p, method, args) -> {
                    calls.incrementAndGet();
                    return 0;
                });

        proxy.add(1, 2);
        proxy.subtract(5, 3);
        proxy.multiply(2, 3);
        assertEquals(3, calls.get());
    }
}
```

- [ ] **Step 2: Run tests**

Run: `mvn test -s /home/lam/repo/settings.xml -Dtest=MultiInterceptorInterfaceProxyTest`
Expected: All tests pass.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/MultiInterceptorInterfaceProxyTest.java
git commit -m "test: add multi-interceptor interface proxy tests"
```

---

### Task 14: Verify Full Test Suite

- [ ] **Step 1: Run all tests**

Run: `mvn test -s /home/lam/repo/settings.xml`
Expected: All tests pass, including:
- `AcceleratedProxyInterfaceProxyTest`
- `AcceleratedProxyClassProxyTest`
- `WeakCacheTest`
- `DispatchGeneratorTest` (in generator/)
- `LookupManagerTest` (in internal/)
- `GroupMatchingTest`
- `DuplicateMatchWarningTest`
- `MultiInterceptorClassProxyTest`
- `MultiInterceptorInterfaceProxyTest`

- [ ] **Step 2: Fix any failures**

If any existing test fails, investigate and fix before proceeding.

- [ ] **Step 3: Commit** (if fixes were needed)

```bash
git commit -m "fix: ensure full test suite passes with Group model"
```

---

## Phase 2d: Documentation & Roadmap

### Task 15: Update Documentation

**Files:**
- Modify: `CLAUDE.md` (project instructions)
- Modify: `docs/aps-future-roadmap.md` (mark Phase 2 as in-progress or completed)

- [ ] **Step 1: Update roadmap status**

In `docs/aps-future-roadmap.md`, update the Phase 2 row to reflect implementation status.

- [ ] **Step 2: Update CLAUDE.md** if needed (architecture overview changes)

- [ ] **Step 3: Commit**

```bash
git add docs/aps-future-roadmap.md
git commit -m "docs: update roadmap for Phase 2 multi-interceptor implementation"
```

---

## Appendix: File Manifest

| File | Path | Status |
|------|------|--------|
| `MethodPredicate.java` | `src/main/java/io/github/lamspace/` | New |
| `MethodMapping.java` | `src/main/java/io/github/lamspace/` | New |
| `Group.java` | `src/main/java/io/github/lamspace/` | New |
| `AcceleratedProxy.java` | `src/main/java/io/github/lamspace/` | Refactored |
| `ClassFilter.java` | `src/main/java/io/github/lamspace/` | **Deleted** |
| `MethodDispatcher.java` | `src/main/java/io/github/lamspace/generator/` | Refactored |
| `InterfaceDispatcher.java` | `src/main/java/io/github/lamspace/generator/` | Refactored |
| `ClassGenerator.java` | `src/main/java/io/github/lamspace/generator/` | Refactored |
| `InterfaceGenerator.java` | `src/main/java/io/github/lamspace/generator/` | Refactored |
| `DispatchGenerator.java` | `src/main/java/io/github/lamspace/generator/` | **Unchanged** |
| `WeakCache.java` | `src/main/java/io/github/lamspace/` | **Unchanged** |
| `Interceptor.java` | `src/main/java/io/github/lamspace/` | **Unchanged** |
| `DispatchTarget.java` | `src/main/java/io/github/lamspace/` | **Unchanged** |
| `GroupMatchingTest.java` | `src/test/java/io/github/lamspace/` | New |
| `DuplicateMatchWarningTest.java` | `src/test/java/io/github/lamspace/` | New |
| `MultiInterceptorClassProxyTest.java` | `src/test/java/io/github/lamspace/` | New |
| `MultiInterceptorInterfaceProxyTest.java` | `src/test/java/io/github/lamspace/` | New |

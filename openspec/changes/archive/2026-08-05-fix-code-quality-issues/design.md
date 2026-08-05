## Context

The APS codebase has 17 source files with a clean architecture: public API (`AcceleratedProxy`, `Interceptor`, `ClassFilter`), bytecode generators (`ClassGenerator`, `InterfaceGenerator`, `MethodDispatcher`, `InterfaceDispatcher`, `DispatchGenerator`), utilities (`BytecodeUtils`, `ClinitRegistry`, `WeakCache`), and internal infrastructure (`LookupManager`, `DispatchTarget`). The core dispatch mechanism (hashCode switch + INVOKESPECIAL) is well-tested with 41 passing integration tests and JMH benchmarks. See proposal.md for motivation.

Two specs (`aps-core`, `aps-unified-proxy`) both require "Proxy class caching: SHALL cache generated proxy classes keyed by `{targetClass, filter}`." `WeakCache` is fully implemented but not wired into `AcceleratedProxy.proxy()`.

## Goals / Non-Goals

**Goals:**
- Integrate WeakCache so repeated `proxy(target, interceptor, filter)` calls reuse the generated class
- Fix null constructor argument edge case in `findConstructor()`
- Make proxy generation thread-safe (fix `ClinitRegistry` shared mutable state)
- Rename test classes to match current API names
- Add unit tests for previously untested internal components

**Non-Goals:**
- Changing the public API (no new methods, no signature changes)
- Performance optimization beyond caching
- Adding new proxy features (multi-interface, static method, etc.)
- Changing the existing spec requirements

## Decisions

### 1. WeakCache Integration Strategy

**Decision:** Use `WeakCache<Class<?>, CacheParams, Class<?>>` as a static field in `AcceleratedProxy`, keyed by a composite `CacheParams` record of `{targetClass, filter, constructorArgs}`.

**Cache key design:**
```java
record CacheParams(Class<?> targetClass, ClassFilter filter, Object[] constructorArgs) {
    @Override
    public boolean equals(Object o) {
        // filter compared by identity (FunctionalInterface instances are
        // typically lambdas — identity comparison is the safest default)
        // constructorArgs use Arrays.equals for value comparison
    }
}
```

**Caching flow:** Wrap the current bytecode generation + class loading in a `WeakCache.get(key, k -> bytecode)` call. The cache returns the existing class if present; otherwise generates, loads, caches, and returns.

**Alternatives considered:**
- `ConcurrentHashMap` with manual cleanup: simpler but risks ClassLoader leaks. `WeakCache` (copied from JDK's `java.lang.reflect.WeakCache`) uses `WeakReference` for both keys and values, ensuring GC-friendly behavior.
- Caching only by `{targetClass, filter}` without `constructorArgs`: would incorrectly reuse classes when constructor args differ (different superclass constructor).

### 2. Null Constructor Argument Fix

**Decision:** In `findConstructor()`, when `constructorArgs[i]` is `null`, accept any reference-type parameter (not primitive). The null value itself propagates correctly through `Constructor.newInstance()`.

**Change in `findConstructor()`:** Modify the matching logic so that `wrap(existing[i]).isAssignableFrom(wrap(paramTypes[i]))` is skipped when `constructorArgs[i] == null` and `existing[i]` is a reference type. Primitive parameters still reject null.

**Change in `constructorArgs()`:** Keep the `Object.class` fallback for type metadata — it correctly identifies non-primitive slots.

**Alternatives considered:**
- Requiring users to pass explicit `Class<?>[]` parameter types: adds API surface, breaks the simple `proxy(Class, Interceptor, null, "arg")` pattern.
- Inferring type from constructor parameter types at match time (current approach with relaxed null check): simpler, no API change, handles the common case.

### 3. ClinitRegistry Thread Safety

**Decision:** Convert `ClinitRegistry` from static state to instance-based. Each `ClassGenerator.generate()` and `InterfaceGenerator.generate()` call creates its own `ClinitRegistry` instance. The `register()` and `drain()` methods become instance methods.

**Change:**
- Remove `static` from `entries` list, `register()`, and `drain()`
- `ClassGenerator` and `InterfaceGenerator` each instantiate `new ClinitRegistry()` in their `generate()` method
- Pass the instance to `MethodDispatcher.dispatchMethods()` / `InterfaceDispatcher.dispatchMethods()`

**Alternatives considered:**
- `synchronized` on static methods: simpler code change but introduces lock contention. Since the registry is short-lived (one per generation call), instance-based is cleaner and avoids any lock overhead.

### 4. LookupManager Fallback Logging

**Decision:** Add a `java.util.logging.Logger` warning when `privateLookupIn` fails and the fallback is used. The existing `IllegalAccessException` catch behavior is preserved.

**No API change** — purely internal diagnostic improvement.

### 5. Test Class Rename

**Decision:** Rename files and class names:
- `APSClassProxyTest` → `AcceleratedProxyClassProxyTest` (file already named this, class inside mismatches)
- `APSInterfaceProxyTest` → `AcceleratedProxyInterfaceProxyTest` (same)

### 6. New Unit Tests

**Decision:** Add focused unit tests in `src/test/java/io/github/lamspace/`:
- `WeakCacheTest.java` — test put/get, cache hit, GC eviction, containsValue, size
- `ClinitRegistryTest.java` — test register/drain lifecycle, isolation between instances
- `LookupManagerTest.java` — test privateLookupIn success path, fallback path
- `BytecodeUtilsTest.java` — test pushInt value ranges, loadOpcode for each type, boxPrimitive/unboxPrimitive round-trips
- `DispatchGeneratorTest.java` — test methodDispatchHash determinism, collision detection, resolveHashes

## Risks / Trade-offs

- **[Risk] Caching by `{targetClass, filter, constructorArgs}` may not match all use cases:** Two `ClassFilter` lambdas that are semantically identical but different instances won't match. → **Mitigation:** Document that filter identity matters. This is the standard behavior for `FunctionalInterface`-based caching (same as `java.lang.reflect.Proxy`).
- **[Risk] `ClinitRegistry` instance migration may miss call sites:** `MethodDispatcher` and `InterfaceDispatcher` both call `ClinitRegistry.register()`. → **Mitigation:** Compile-time safety — changing the method signatures from static to instance will cause compilation errors at all call sites, making them easy to find and fix.
- **[Risk] Constructor null fix may allow ambiguous matches:** If a class has two constructors `Foo(String)` and `Foo(Integer)` and the user passes `null`, the first match wins. → **Mitigation:** This is the same ambiguity that exists in Java reflection generally. Document the limitation.

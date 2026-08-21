# OpenProxy Phase 2: Multi-Interceptor / Method Grouping — Design Spec

**Date:** 2026-08-09 **Status:** Design approved **Phase:** 2 — Feature Extension

## 1. Motivation

Currently OpenProxy uses a single `Interceptor` + `ClassFilter` (binary accept/reject) per proxy instance. All intercepted methods route through the same `Interceptor`, forcing users to write manual `if-else` chains inside `intercept()` to distinguish method families.

This feature addresses three goals with equal priority:

- **Code organization** — eliminate manual dispatch boilerplate inside `intercept()`.
- **Performance** — allow lightweight interceptors for high-frequency methods while heavy interceptors handle only a few methods.
- **Reusability** — enable the same group-template (e.g., "all `get*` → cache, all
  `set*` → validate+notify") to be reused across proxy classes.

## 2. API Design

### 2.1 New Types

```java
// Replaces ClassFilter — the predicate in Group.of()
@FunctionalInterface
public interface MethodPredicate {
    boolean test(Method method);
}

// Immutable binding: predicate → interceptor
public final class Group {

    // Match methods where predicate.test(m) == true → use this interceptor
    public static Group of(MethodPredicate predicate, Interceptor interceptor);

    // Catch-all for methods not matched by any preceding Group
    public static Group otherwise(Interceptor interceptor);
}
```

### 2.2 AcceleratedProxy — Redesigned API

```java
public final class AcceleratedProxy {

    // Single interceptor (equivalent to Group.otherwise(interceptor))
    public static <T> T proxy(Class<T> target, Interceptor interceptor);

    // Multi-group
    public static <T> T proxy(Class<T> target, Group... groups);

    // Multi-group with constructor arguments
    public static <T> T proxy(Class<T> target, Object[] constructorArgs,
                              Group... groups);

    // Unchanged
    public static Object invokeSuper(Object proxy, Method method, Object[] args);
}
```

### 2.3 Usage Example

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
        Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
        Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
        Group.otherwise(fallbackInterceptor)
);

// With default passthrough — unmatched methods bypass interception
Greeter proxy2 = AcceleratedProxy.proxy(Greeter.class,
        Group.of(m -> m.getName().startsWith("get"), cacheInterceptor)
        // setName, toString etc. → direct super call, zero overhead
);
```

### 2.4 Semantics

| Rule                        | Behavior                                                                                                                                                      |
|-----------------------------|---------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **First-Match-Wins**        | Groups evaluated in declaration order; first predicate returning `true` binds the method to that Group's interceptor                                          |
| **Default passthrough**     | Methods not matching any Group call super implementation directly (no interception overhead)                                                                  |
| **`otherwise` optional**    | The catch-all is not required; unmatched methods default to passthrough                                                                                       |
| **Duplicate match warning** | If a method matches multiple Groups, a `WARNING` is logged at `proxy()` creation time identifying the method and the conflicting Group indices                |
| **Interceptor dedup**       | Distinct Interceptor instances (by reference equality) are stored in separate fields; multiple methods sharing the same Interceptor reference share one field |

### 2.5 Backward Compatibility

The old single-Interceptor API is preserved and delegates to the new Group model internally:

```java
// Old: proxy(target, interceptor)
// → Internally: proxy(target, Group.otherwise(interceptor))
// All methods intercepted by one interceptor.

// Old: proxy(target, interceptor, filter)
// → Internally: proxy(target, Group.of(filter::accept, interceptor))
// filter.accept(m)==true → intercept; false → passthrough. Same semantics.
```

### 2.6 What Is Removed

- `ClassFilter` — its binary accept/reject role is fully subsumed by Group predicates plus the default-passthrough semantic. The old `proxy(target, interceptor, filter)`
  API is still available; `filter::accept` is internally converted to a
  `MethodPredicate`.

## 3. Bytecode Generation

### 3.1 Storage Model

Each generated proxy class stores **one field per distinct Interceptor** (deduped by reference equality). Method overrides directly `GETFIELD` their assigned field — no array, no index lookup, no indirection.

```
Current (single Interceptor):          New (multi Interceptor, 3 groups):

class Foo$$Proxy extends Foo {         class Foo$$Proxy extends Foo {
    final Interceptor _callback;           final Interceptor _i0;  // group 0
                                           final Interceptor _i1;  // group 1
                                           // no _i2 — unmatched methods
    String getName() {
        return _callback.intercept(        String getName() {
            this, _m0, args);                  return _i0.intercept(
    }                                              this, _m0, args);
                                               }
    void setName(String n) {
        _callback.intercept(                void setName(String n) {
            this, _m1, args);                   return _i1.intercept(
    }                                              this, _m1, args);
                                               }
    void untouched() {                     void untouched() {
        return super.untouched();              return super.untouched();
    }                                      }
}                                       }
```

### 3.2 Hot Path — Zero Degradation

The per-method call sequence is bytecode-identical to the current design except for the field name. After JIT C2 compilation, both resolve to a single register-offset load:

```
ALOAD 0
GETFIELD _i0              ← same opcode as current GETFIELD _callback
ALOAD 0
GETSTATIC _method$N
... box args ...
INVOKEINTERFACE intercept
```

No array load (`AALOAD`), no bounds check, no indirection overhead.

### 3.3 Passthrough Methods — Zero Overhead Preserved

Unmatched methods generate the same direct super call as the current
`ClassFilter.accept() == false` path:

```java
// Class proxy
ALOAD 0
        ...args ...
INVOKESPECIAL super.

method(args)

// Interface proxy (non-Object method)
NEW AbstractMethodError
DUP
LDC "Method ... is not intercepted"
INVOKESPECIAL AbstractMethodError.<init>
ATHROW
```

### 3.4 Constructor

```java
// Generated constructor — M = distinct interceptor count
<init>(
        Interceptor i0, Interceptor
i1,...,
Interceptor iM-1,Object...superArgs){
        super(superArgs);
        this._i0 =i0;
    this._i1 =i1;
    ...
            this._iM-1=iM-1;
        }
```

Parameter count equals the number of distinct Interceptor instances. Typical usage has 3–5 Groups, far below the JVM 255-parameter limit.

### 3.5 `dispatch()` Method — Unchanged

The hash-based `dispatch(Method, Object[])` for `invokeSuper` is not affected — Interceptor selection is orthogonal to super-method dispatch.

### 3.6 Generation Pipeline Changes

```
Current:                               New:

1. Iterate methods + filter.accept()   1. Iterate methods + Group chain matching
   → binary intercept/non-intercept        → map to interceptor index or -1 (passthrough)
                                           → detect duplicates → log WARNING
2. Generate overrides with _callback   2. Generate overrides with _iN (or passthrough)
3. Generate dispatch (unchanged)       3. Generate dispatch (unchanged)
4. Generate <clinit> (unchanged)       4. Generate <clinit> (unchanged)
5. Generate constructor (1 param)      5. Generate constructor (M params)
```

## 4. Cache Strategy

### 4.1 Revised CacheParams

```java
private record CacheParams(
        Class<?> targetClass,
        Interceptor[] interceptors,    // deduped; compared by reference equality (==)
        MethodMapping mapping,         // method → interceptor index
        Object[] constructorArgs
) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheParams other)) return false;
        return targetClass == other.targetClass
                && Arrays.equals(constructorArgs, other.constructorArgs)
                && mapping.equals(other.mapping)
                && interceptors.length == other.interceptors.length
                && IntStream.range(0, interceptors.length)
                .allMatch(i -> interceptors[i] == other.interceptors[i]);
    }

    @Override
    public int hashCode() {
        int result = System.identityHashCode(targetClass);
        result = 31 * result + mapping.hashCode();
        result = 31 * result + Arrays.hashCode(constructorArgs);
        for (Interceptor i : interceptors) {
            result = 31 * result + System.identityHashCode(i);
        }
        return result;
    }
}
```

`Interceptor` is `@FunctionalInterface` — lambda and method-reference instances use `Object.equals()` (identity). The explicit `==` comparison in `equals()`
ensures correct behavior even for named implementations that might override
`equals()`. Two distinct Group configurations with semantically equivalent but non-identical Interceptor instances produce different CacheParams → different proxy classes, which is the correct and intentional semantics.

### 4.2 MethodMapping (Internal)

```java
final class MethodMapping {
    final int[] indices;  // indices[i] = interceptor index, -1 = passthrough
    // equals/hashCode based on Arrays.equals/hashCode(indices)
}
```

`indices[i]` is indexed by a stable sort order of `targetClass.getMethods()` (sorted by `Method.getName()` then parameter type names to ensure cross-JVM determinism).

### 4.3 Lookup Flow

```
proxy(target, groups)
  → 1. Stable-sort target.getMethods()
  → 2. For each method, evaluate Group chain (first-match-wins)
       → Build Interceptor[] (dedup) + MethodMapping
       → Detect duplicate matches → log WARNING
  → 3. Construct CacheParams(target, interceptors, mapping, constructorArgs)
  → 4. PROXY_CLASS_CACHE.get(target, params)
       → Hit → construct instance with interceptors
       → Miss → generateProxyClass() → cache → construct instance
```

Group matching executes BEFORE the cache lookup. Matching cost is negligible (O (methods × groups) predicate calls, < 1µs), while ensuring exact cache semantics.

## 5. Performance Impact

### 5.1 Hot Path — Zero Degradation

The intercepted method call sequence uses the same `GETFIELD` + `INVOKEINTERFACE`
sequence as the current design. The only difference is the field index in the constant pool. JIT-compiled code is identical in structure and latency.

### 5.2 Passthrough Path — Zero Degradation

Unmatched methods generate the same `INVOKESPECIAL super.xxx()` as `ClassFilter`-rejected methods today. No change.

### 5.3 One-Time Costs

| Cost                                 | Magnitude                                          | Hot path?              |
|--------------------------------------|----------------------------------------------------|------------------------|
| Group chain matching                 | < 1µs per `proxy()` call                           | No — once per creation |
| Constructor (extra PUTFIELDs)        | ~1ns per extra field                               | No — once per instance |
| Object size (extra Interceptor refs) | 8 bytes per distinct Interceptor (compressed OOPs) | N/A — per instance     |
| Class file size                      | ~10 bytes per extra field + ~20 bytes constructor  | N/A                    |

### 5.4 JMH Benchmark Plan

| Benchmark                         | Comparison                                        | Expected                       |
|-----------------------------------|---------------------------------------------------|--------------------------------|
| Single-interceptor throughput     | Current API vs new API (equivalent config)        | ±2%                            |
| Multi-group throughput (3 groups) | New API vs single-interceptor + manual if-else    | Equivalent                     |
| Passthrough latency               | Current ClassFilter(false) vs new Group unmatched | Identical                      |
| `proxy()` creation time           | Current vs new (warm cache)                       | Within measurement noise       |
| `invokeSuper` dispatch            | Current vs new                                    | Identical (dispatch unchanged) |

## 6. Implementation Plan

### 6.1 File Change Summary

| File                       | Action                                                              | Effort |
|----------------------------|---------------------------------------------------------------------|--------|
| `MethodPredicate.java`     | **New** — `@FunctionalInterface`                                    | Small  |
| `Group.java`               | **New** — immutable binding, matching engine, duplicate detection   | Medium |
| `MethodMapping.java`       | **New** — internal int[] + equals/hashCode                          | Small  |
| `ClassFilter.java`         | **Delete** — subsumed by MethodPredicate + passthrough              | Small  |
| `AcceleratedProxy.java`    | **Refactor** — new API overloads, CacheParams, match-before-cache   | Medium |
| `ClassGenerator.java`      | **Refactor** — Interceptor[] + MethodMapping instead of ClassFilter | Medium |
| `InterfaceGenerator.java`  | **Refactor** — same as ClassGenerator                               | Medium |
| `MethodDispatcher.java`    | **Refactor** — per-method field name resolution                     | Medium |
| `InterfaceDispatcher.java` | **Refactor** — same as MethodDispatcher                             | Medium |
| `DispatchGenerator.java`   | **Unchanged**                                                       | —      |
| `WeakCache.java`           | **Unchanged**                                                       | —      |
| `LookupManager.java`       | **Unchanged**                                                       | —      |
| Tests (6 files)            | **New/Extend** — see §7                                             | Large  |

### 6.2 Implementation Order

```
Phase 2a: Core API (no bytecode changes)
  ├── 1. Add MethodPredicate.java
  ├── 2. Add Group.java (matching engine + duplicate detection)
  ├── 3. Add MethodMapping.java
  ├── 4. Refactor AcceleratedProxy CacheParams
  └── 5. Add new proxy() overloads, delegate old API
        → Verify: compiles, all existing tests pass

Phase 2b: Generator adaptation
  ├── 6. Refactor MethodDispatcher → field-per-group
  ├── 7. Refactor InterfaceDispatcher → field-per-group
  ├── 8. Refactor ClassGenerator → Interceptor[] + MethodMapping
  ├── 9. Refactor InterfaceGenerator → same
  └── 10. Delete ClassFilter.java
        → Verify: proxy classes load, functional tests pass

Phase 2c: Test coverage
  ├── 11. GroupMatchingTest
  ├── 12. MultiInterceptorClassProxyTest
  ├── 13. MultiInterceptorInterfaceProxyTest
  ├── 14. GeneratedClassStructureTest
  ├── 15. DuplicateMatchWarningTest
  └── 16. MultiInterceptorScenarioTest
        → Verify: all green

Phase 2d: Performance verification
  ├── 17. MultiInterceptorBenchmark (JMH)
  └── 18. Comparison report vs current version
        → Verify: throughput within ±2%
```

### 6.3 Effort Estimate

| Phase     | Content                            | Estimate      |
|-----------|------------------------------------|---------------|
| 2a        | Core API                           | 1–2 days      |
| 2b        | Generator refactor                 | 2–3 days      |
| 2c        | Tests                              | 2–3 days      |
| 2d        | JMH benchmarks                     | 0.5 day       |
| Docs      | Javadoc, CLAUDE.md, roadmap update | 0.5 day       |
| **Total** |                                    | **7–11 days** |

## 7. Test Strategy

### 7.1 Unit: Group Matching Engine

```
GroupMatchingTest:
  ├── firstMatchWins              — overlapping predicates → order decides
  ├── noMatchDefaults             — unmatched → passthrough
  ├── otherwiseFallback           — Group.otherwise() as safety net
  ├── duplicateMatchWarning       — overlap → WARNING in log
  ├── noWarningForDistinct        — non-overlapping → silent
  ├── otherwiseNoWarning          — otherwise + overlap → no warning for otherwise
  ├── emptyGroups                 — empty array → all passthrough
  └── allMethodsMatched           — otherwise covers all → no passthrough
```

### 7.2 Unit: Class Proxy

```
MultiInterceptorClassProxyTest:
  ├── getterSetterGroups          — get/set → different interceptors
  ├── passthroughMethods          — unmatched → super call, no interceptor invoked
  ├── invokeSuperInGroup          — invokeSuper works within any group's interceptor
  ├── interceptorOrder            — Group declaration order == actual dispatch order
  ├── sharedInterceptor           — same instance reused → dedup verified
  ├── statefulInterceptor         — per-group state isolated
  └── cacheHitWithSameGroups      — identical config → same proxy class
```

### 7.3 Unit: Interface Proxy

```
MultiInterceptorInterfaceProxyTest:
  ├── getterSetterGroups          — mirror of class proxy test
  ├── passthroughThrowsError      — unmatched non-Object method → AbstractMethodError
  ├── invokeSuperObjectMethods    — equals/hashCode/toString → dispatch
  └── otherwiseOnInterface        — Group.otherwise() catches all methods
```

### 7.4 Unit: Bytecode Verification

```
GeneratedClassStructureTest:
  ├── fieldCountMatchesDistinctInterceptors — M fields for M distinct interceptors
  ├── noArrayFieldForInterceptors           — no Interceptor[] field
  ├── passthroughMethodNoInterceptor        — no GETFIELD before INVOKESPECIAL
  ├── directFieldAccessPerMethod            — GETFIELD _iN, no AALOAD
  └── constructorParamCount                 — params = distinct interceptor count
```

### 7.5 Integration: End-to-End Scenarios

```
MultiInterceptorScenarioTest:
  ├── loggingAndTransaction        — logger group + tx group coexist
  ├── cachingAndValidation         — cache (get) + validate (set), distinct groups
  ├── layeredInterfaces            — interface hierarchy with multi-group
  └── mixedInheritance             — parent/child methods → different groups
```

### 7.6 Regression

- All existing tests (`AcceleratedProxyInterfaceProxyTest`,
  `AcceleratedProxyClassProxyTest`, `WeakCacheTest`, `DispatchGeneratorTest`,
  `LookupManagerTest`) must remain green without modification.
- Existing single-Interceptor API delegates to new internals; behavior unchanged.

### 7.7 Performance: JMH Benchmarks

```
MultiInterceptorBenchmark:
  ├── singleInterceptorThroughput  — current vs new (equivalent config)
  ├── multiGroupThroughput         — 3 groups vs manual if-else in single interceptor
  ├── passthroughThroughput        — unmatched method latency
  └── proxyCreationOverhead        — creation time with group matching
```

## 8. Risks & Mitigations

| Risk                                             | Severity | Mitigation                                                                                        |
|--------------------------------------------------|----------|---------------------------------------------------------------------------------------------------|
| `Method.getMethods()` order differs across JVMs  | Medium   | Stable-sort methods by `getName()` + parameter type names before building `MethodMapping.indices` |
| Duplicate detection overhead in production       | Low      | Only execute when logger level ≤ WARNING; gate behind `Logger.isLoggable()`                       |
| Constructor parameter explosion                  | Low      | Dedup by reference equality; practical max ~10 Groups; 255-param JVM limit is unreachable         |
| Cache key collision from different Group configs | Low      | `MethodMapping.equals()` based on `Arrays.equals(indices)`, deterministic                         |
| `Interceptor` identity comparison confusion      | Low      | Document clearly: two lambdas with identical code are distinct; use named instances for sharing   |

## 9. Decisions Log

| Decision                                       | Rationale                                                                                                                                                                     |
|------------------------------------------------|-------------------------------------------------------------------------------------------------------------------------------------------------------------------------------|
| **First-Match-Wins**                           | Simple, predictable, fastest — no chain-of-responsibility complexity. Overlaps handled by warning.                                                                            |
| **Default passthrough**                        | Consistent with "whitelist" Group mental model. If user doesn't declare interception for a method, it's not intercepted. `Group.otherwise()` available as explicit catch-all. |
| **Remove ClassFilter**                         | Fully subsumed by Group predicates + passthrough. Cleaner API surface.                                                                                                        |
| **Per-distinct-interceptor fields (no array)** | Zero hot-path overhead vs current single-interceptor design. `GETFIELD` directly, no `AALOAD`.                                                                                |
| **Dedup by reference equality**                | Practical: most use cases have 3–5 Groups, users naturally reuse Interceptor instances. Two distinct instances with identical behavior is intentional, not accidental.        |
| **Match before cache**                         | Matching cost < 1µs; enables precise cache semantics based on mapping results.                                                                                                |
| **Duplicate match = WARNING, not exception**   | First-match-wins resolves it deterministically. WARNING helps users catch unintended overlaps without being punitive.                                                         |
| **Old single-Interceptor API preserved**       | Internal delegation to Group model; no migration burden for existing code.                                                                                                    |

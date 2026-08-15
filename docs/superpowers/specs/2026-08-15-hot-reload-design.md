# APS Phase 3: Hot Reload / Hot Swap — Design Spec

**Date:** 2026-08-15 **Status:** Awaiting review **Phase:** 3 — Advanced Features

## 1. Motivation

Roadmap item 6 (`热加载/热替换`) covers two related runtime-lifecycle needs for long-running framework scenarios:

1. **Class hot-reload** — when a framework hot-deploys a target class via a new `ClassLoader` (Tomcat webapp reload, OSGi, plugin systems), `proxy()`
   should transparently generate a fresh proxy for the new `Class` object, while old instances keep working on the old proxy class.
2. **Interceptor hot-swap** — a framework holding a proxy instance (e.g. a long-lived singleton service) wants to replace its `Interceptor` logic at runtime without recreating the instance, which would break held references.

The roadmap's `热加载挑战` note states the hard constraint: a hidden class cannot be modified once defined, so reload means generating a *new* class name, and old instances keep the old class while new instances use the new one.

Key realization from the current design: **capability 1 already holds structurally.** The cache key `CacheParams` compares `targetClass` by identity (`targetClass == other.targetClass`, §2.1), and both generators mint a unique class name per generation via an `AtomicInteger` counter. A hot-deployed class is a *different* `Class` object, so it already misses the cache and gets a fresh proxy. What is missing is (a) a deterministic way to *evict* cache entries — the weak references give lazy GC,
not eager cleanup — and (b) tests and docs that pin the old-instance/new-instance isolation guarantee down. Capability 2 is the genuinely new work: the interceptor fields are `final`, so they cannot be rebound today.

**Success criteria**

1. `AcceleratedProxy.evict(Class<?>)` and `evictClassLoader(ClassLoader)`
   deterministically drop proxy-class cache entries; a subsequent `proxy()`
   call for an evicted target regenerates a fresh class.
2. Old proxy instances keep working after `evict`; new instances use a freshly generated class.
3. `AcceleratedProxy.rebind(Object, Interceptor)` and
   `rebind(Object, Interceptor[])` replace the interceptors on an existing class or interface proxy instance; the proxy object identity is unchanged.
4. The instance/interface hot path — method overrides, `dispatch`, constructor,
   `<clinit>` — is byte-for-byte unchanged; only the interceptor fields drop
   `final` and a `rebind` method + `Rebindable` interface are added.
5. Unit/integration tests, a JMH parity check, and updated roadmap + READMEs.

## 2. Design

### 2.1 Why reload already works (no generation change)

`AcceleratedProxy.CacheParams` is structure-only and identity-keyed:

```java
return targetClass ==other.targetClass &&Arrays.

equals(interfaces, …)
        &&mapping.

equals(…) && … ;
```

and `generateProxyClass` mints a unique hidden-class name per generation (`ClassGenerator`/`InterfaceGenerator` `COUNTER`). So the sequence
"old class in loader A → reload same-named class in loader B → `proxy()`"
already yields two independent proxy classes, and each hidden class is defined in the lookup of *its* target (`LookupManager.getLookup(target)` for class proxies), so the old proxy is pinned to loader A and the new proxy to loader B. This feature formalizes that fact with a lifecycle API; it adds no new generation logic.

### 2.2 Eviction API

Add one package-private method to `WeakCache`:

```java
void removeIf(Predicate<? super K> predicate)
```

- `expungeStaleEntries()` first, then iterate the outer `map.keySet()`.
- For each key: skip the `NULL_KEY` sentinel (APS never caches a null key); unwrap the weak `CacheKey` to the raw `K`; if `predicate.test(key)`, call
  `CacheKey.expungeFrom(map, reverseMap)` (which already removes the
  `valuesMap` and its reverse-map entries).
- `ConcurrentHashMap`'s weakly-consistent iterator makes this safe under concurrent `get`.

Public entry points on `AcceleratedProxy`:

```java
public static void evict(Class<?> target)

public static void evictClassLoader(ClassLoader cl)
```

- `evict` → `removeIf(k -> k == target)`.
- `evictClassLoader` → `removeIf(k -> k != null && k.getClassLoader() == cl)`.
- Both reject null args with `IllegalArgumentException`.

**Cache-key asymmetry (documented):** class proxies key on `target`; interface proxies key on the *first* interface (`proxyInterfaces` passes `copy[0]`). So
`evict` addresses the cache-key class — the target for class proxies, the first interface for (multi-)interface proxies. Eviction is idempotent: evicting a class with no cached entry is a no-op.

### 2.3 Interceptor rebind — bytecode layout change

New internal interface (mirrors the `DispatchTarget` pattern; public so hidden classes in arbitrary packages can implement it):

```java
// io.github.lamspace.internal.Rebindable
public interface Rebindable {
    void rebind(Interceptor[] interceptors);
}
```

Both `ClassGenerator` and `InterfaceGenerator`:

1. Drop `ACC_FINAL` on the `_interceptor$i` fields (keep `ACC_PRIVATE`). The fields remain plain, non-`volatile`.
2. Add `io/github/lamspace/internal/Rebindable` to the implemented interfaces (alongside `DispatchTarget`).
3. Emit a `public void rebind(Interceptor[])` method whose body (via a shared
   `BytecodeUtils.generateRebind` helper) is:

```
if (interceptors == null)              throw new IllegalArgumentException("interceptors must not be null");
if (interceptors.length != N)          throw new IllegalArgumentException("interceptor count mismatch: expected N");
this._interceptor$0 = interceptors[0];
…
this._interceptor$(N-1) = interceptors[N-1];
VarHandle.fullFence();                 // INVOKESTATIC java/lang/invoke/VarHandle.fullFence()V
```

`N` is the distinct-interceptor count baked in at generation time. The method descriptor is `([Lio/github/lamspace/Interceptor;)V`.
`MethodDispatcher`, `InterfaceDispatcher`, and `DispatchGenerator` are untouched, so the override bodies (`GETFIELD` + `invokeinterface`) remain byte-identical.

### 2.4 Rebind API

```java
public static void rebind(Object proxy, Interceptor interceptor)

public static void rebind(Object proxy, Interceptor[] interceptors)
```

- The single-interceptor overload delegates
  `rebind(proxy, new Interceptor[]{interceptor})` after a null check.
- Both: if `proxy` is not an `instanceof Rebindable`, throw
  `IllegalArgumentException("not an APS-generated proxy")`.
- Length mismatch is thrown from the generated `rebind` body (only the class knows `N`).
- `ConstructorInterceptor` is deliberately **not** rebindable: it runs only during construction (`before`/`after` around `super()`) and is never stored as an instance field (see `ClassGenerator.generateInterceptedConstructor`), so there is nothing to swap once the instance exists.

### 2.5 Concurrency contract (plain + fullFence)

- Interceptor fields remain plain (non-`volatile`, non-`final`) → hot-path reads are an ordinary `GETFIELD`, zero added cost, and the override bytecode is unchanged from today.
- `rebind` performs the `N` stores then a `VarHandle.fullFence()` (store-store
    + store-load). The fence orders the writes and gives a strong practical visibility barrier, but it is **not** by itself a JMM happens-before edge to unsynchronized readers.
- Contract: `rebind` is a **single-writer** management operation. A caller that rebinds on one thread and invokes methods on another **must establish happens-before itself** (a lock, `Thread.start`, `CountDownLatch`, or a
  `volatile` flag they control), exactly like any safe publication of a mutable field. For the common single-interceptor case the swap is additionally trivially atomic (one field store).

### 2.6 Files touched

| File                                | Change                                                                                                     |
|-------------------------------------|------------------------------------------------------------------------------------------------------------|
| `WeakCache.java`                    | Add `removeIf(Predicate<? super K>)` (§2.2).                                                               |
| `internal/Rebindable.java`          | **New** — `void rebind(Interceptor[])`.                                                                    |
| `generator/BytecodeUtils.java`      | Add `generateRebind(MethodVisitor, String internalName, int count, String interceptorDesc)` helper (§2.3). |
| `generator/ClassGenerator.java`     | Drop `FINAL` on interceptor fields; implement `Rebindable`; emit `rebind` (§2.3).                          |
| `generator/InterfaceGenerator.java` | Same (§2.3).                                                                                               |
| `AcceleratedProxy.java`             | Add `evict`, `evictClassLoader`, and two `rebind` overloads (§2.2, §2.4).                                  |

No change to `MethodDispatcher`, `InterfaceDispatcher`, `DispatchGenerator`,
`MethodMapping`, `DispatchTarget`, `Interceptor`, `Group`, `MethodPredicate`,
`ConstructorInterceptor`, `LookupManager`, `StaticMethodGenerator`,
`ClinitRegistry`, `InterfaceMethodResolver`, `MethodInfo`.

## 3. Error handling

1. `evict(null)`, `evictClassLoader(null)`, `rebind(proxy, null)`,
   `rebind(null, …)` → `IllegalArgumentException`.
2. `rebind` on a non-APS object → `IllegalArgumentException("not an
   APS-generated proxy")`.
3. `rebind` with a wrong-length `Interceptor[]` →
   `IllegalArgumentException("interceptor count mismatch: expected N")` thrown from the generated body.
4. A `null` *element* inside a correctly-sized array is not validated: the array length, not element nullity, is checked. A `null` slot `NPE`s on the next call — consistent with `proxy()`'s non-null interceptor discipline but documented as caller responsibility.

## 4. Testing

New `src/test/java/io/github/lamspace/HotReloadTest.java`,
`src/test/java/io/github/lamspace/RebindClassProxyTest.java`, and
`src/test/java/io/github/lamspace/RebindInterfaceProxyTest.java`; extend
`src/test/java/io/github/lamspace/WeakCacheTest.java` for `removeIf`.

| #  | Scenario                      | Coverage                                                                                                                           |
|----|-------------------------------|------------------------------------------------------------------------------------------------------------------------------------|
| 1  | `evict` regenerates           | `evict(target)` then `proxy()` returns a *different* proxy class (identity) that still works                                       |
| 2  | Old instance survives evict   | instance created before `evict` keeps intercepting after eviction                                                                  |
| 3  | `evictClassLoader` scoping    | removes entries for the given loader, leaves others untouched                                                                      |
| 4  | Eviction idempotent           | evicting a class with no entry is a no-op                                                                                          |
| 5  | Eviction null args            | `evict(null)` / `evictClassLoader(null)` → `IllegalArgumentException`                                                              |
| 6  | Two-classloader isolation     | deferred — child-loader targets need JPMS `privateLookupIn` (item 8); identity-keyed regeneration is covered by #1–#3              |
| 7  | `WeakCache.removeIf`          | removes matching keys only; next `get` re-evaluates `valueFactory`; empty cache is a no-op; predicate never sees the null sentinel |
| 8  | Single class-proxy rebind     | `rebind(proxy, interceptor)` → subsequent calls use the new interceptor, old not called                                            |
| 9  | Single interface-proxy rebind | same as #8 on an interface proxy                                                                                                   |
| 10 | Multi-interceptor rebind      | `rebind(proxy, Interceptor[])` with correct length replaces each index (class and interface proxies)                              |
| 11 | Length mismatch               | wrong-length array → `IllegalArgumentException`                                                                                    |
| 12 | Null / non-proxy              | null array, null proxy → `IllegalArgumentException`; non-APS object → `IllegalArgumentException`                                   |
| 13 | `invokeSuper` after rebind    | super-method dispatch still works with the new interceptor                                                                         |
| 14 | Passthrough unaffected        | a method matched by no `Group` never touches interceptors, rebind or not                                                           |
| 15 | Per-instance isolation        | rebind one of two instances of the same class; the other is unaffected                                                             |
| 16 | Convenience overload          | `rebind(proxy, interceptor)` ≡ `rebind(proxy, new Interceptor[]{interceptor})`                                                     |
| 17 | Repeated rebind               | rebinding twice in a row replaces cleanly, no stale field                                                                          |
| 18 | Regression: instance path     | `proxy()` class + interface + constructor-interception tests still pass unchanged                                                  |
| 19 | Passthrough-only (0-interceptor) rebind | a proxy whose methods all passthrough rejects a non-empty array; an empty array is a no-op                               |

## 5. Benchmark

**Conclusion: zero impact on the instance/interface hot path.** The override bodies are emitted by `MethodDispatcher`/`InterfaceDispatcher`/`DispatchGenerator`, none of which change (§2.6), so the `GETFIELD`-then-`invokeinterface` sequence is byte-identical. The only classfile deltas are: `ACC_FINAL` cleared on the interceptor fields (no machine-code effect), one added interface, and one added
`rebind` method (never called on the hot path).

Verification is therefore procedural:

1. Run the existing JMH suite before/after —
   `ProxyBenchmark`, `ConstructorInterceptionBenchmark`,
   `StaticMethodProxyBenchmark` — and require identical ns/op within noise.
2. Add an informational `RebindBenchmark` (or extend an existing one) reporting ns/op for a single `rebind` call. This is a management operation, not the hot path, so the number is reported in `docs/benchmark-results.md` (and
   `_cn`) but does not gate — the parity requirement in (1) is the gate.

## 6. Documentation changes

- `docs/aps-future-roadmap.md`: mark Phase 3 item 6 `热加载/热替换` as 已完成; add `### 类热重载（已完成）` and `### 拦截器热替换（已完成）` subsections with API examples; rewrite the `### 热加载挑战` note to state the resolution (identity-keyed cache + per-generation class names + the evict/rebind API).
- `README.md` / `README_CN.md`: add "Hot reload / hot swap" feature bullets and a Quick Start example (`evict` for classloader reload; `rebind` for swapping an interceptor on a live instance).
- Javadoc on `evict`, `evictClassLoader`, `rebind` (both overloads) and
  `Rebindable`, matching the existing detailed style, including the §2.5 concurrency contract and the §2.2 cache-key asymmetry.
- `docs/benchmark-results.md` / `docs/benchmark-results_cn.md`: add the informational `rebind` numbers.
- `docs/migration-guide.md`: note that `rebind` is purely additive; CGLib has no direct post-construction callback swap, and hot-reload-via-classloader behaves analogously to CGLib proxies (a new proxy per reloaded class).

## 7. Deliberate decisions

1. **Explicit eviction API, not automatic.** Weak references already bound the leak lazily; `evict`/`evictClassLoader` add determinism for frameworks that want eager cleanup. No GC hooks or classloader listeners are added.
2. **No "force regenerate the same `Class`".** For an unchanged `Class` + unchanged mapping, regenerating yields byte-identical code (only the name changes) — pure metaspace churn with no behavioral change. Reload is driven by the `Class` *identity* changing, which the cache already keys on.
3. **Rebind is index-preserving, not group re-matching.** The method→index mapping is baked into the class; re-running `Group`s could change it. Rebind only swaps the interceptor *instances* at fixed indices.
4. **Plain + `fullFence()` over `volatile`.** Preserves the "direct `GETFIELD`, no indirection, parity with `reflect.Proxy`" identity at the cost of pushing happens-before to the caller (§2.5).
5. **`ConstructorInterceptor` not rebindable.** It is construction-only state, never an instance field.
6. **Separate `Rebindable` interface, not `DispatchTarget` extension.**
   `DispatchTarget` stays single-purpose (super-method dispatch).
7. **Length-only validation in `rebind`.** Element nullity is caller responsibility, matching the existing `proxy()` discipline.

## 8. Out of scope

- Same-`Class` bytecode redefinition / `Instrumentation`-based hot swap (JVMTI/agent territory, not subclass generation).
- Rebinding `ConstructorInterceptor` (construction-only).
- Re-matching `Group`s on rebind (would change the mapping baked into the class).
- Hot reload/rebind of static-method proxies (`proxyStatic` is uncached by design; static state is class-global).
- JPMS `--add-opens` handling (separate roadmap item 8).
- Auto-eviction on classloader GC (already lazy via weak references; no GC hooks added).

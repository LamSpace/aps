## Context

APS caches generated proxy classes in a `WeakCache` keyed by `CacheParams`, which compares the target `Class` by identity and the method→interceptor mapping by value — never the interceptor instances themselves. Interceptors are bound per-instance as `private final _interceptor$i` fields (one field per distinct interceptor) that the method overrides read directly via `GETFIELD` (no array indirection). Both `ClassGenerator` and `InterfaceGenerator` mint a unique class name per generation via an `AtomicInteger` counter and define the class with `MethodHandles.Lookup.defineHiddenClass`.

Two consequences drive this design: (1) the cache is keyed by `Class` identity, so a fresh `Class` object (e.g. a reloaded class when APS is co-located with it) is a different cache key and yields a fresh proxy — what's missing is a deterministic way to *evict* entries; (2) the `final` interceptor fields make in-place rebinding impossible today. Note: a target loaded by a *child* `ClassLoader` (APS in a shared parent) is **not** proxiable by the current design — `MethodHandles.privateLookupIn` cannot grant `MODULE` access across class loaders, so `defineHiddenClass` fails. That is the JPMS concern deferred to roadmap item 8.

## Goals / Non-Goals

**Goals:**

- Deterministic cache eviction (`evict` / `evictClassLoader`) without affecting live instances.
- In-place interceptor rebinding (`rebind`) for both class and interface proxies.
- Zero regression to the hot path — the override/dispatch bytecode must stay byte-identical.

**Non-Goals:**

- Force-regenerating a proxy for an *unchanged* `Class` (identical structure yields identical bytecode; only the name changes).
- `Instrumentation`/JVMTI-based redefinition of a class's method set.
- Rebinding `ConstructorInterceptor` (construction-only, not an instance field).
- Hot reload/rebind of static-method proxies (`proxyStatic` is uncached by design).
- JPMS `--add-opens` handling (separate roadmap item).

## Decisions

### 1. Eviction = `WeakCache.removeIf` + two `AcceleratedProxy` entry points

Add a package-private `WeakCache.removeIf(Predicate<? super K>)` that sweeps the outer map, unwraps each weak `CacheKey`, and calls the existing `CacheKey.expungeFrom(map, reverseMap)` for matching keys (skipping the `NULL_KEY` sentinel). Expose `evict(Class)` → `removeIf(k -> k == target)` and `evictClassLoader(ClassLoader)` → `removeIf(k -> k != null && k.getClassLoader() == cl)`.

- *Alternative — rely on weak references alone:* correct but lazy; frameworks can't force cleanup. Rejected because the whole point is determinism.
- *Alternative — a separate registry tracking class→proxy:* duplicates state the cache already holds. Rejected.

### 2. Rebind = index-preserving array, not group re-matching

`rebind(Object, Interceptor[])` replaces the interceptors at fixed indices; the length must equal the distinct-interceptor count baked into the generated class. The method→index mapping is compiled into the class, so re-running `Group`s could change it and is unsafe.

- *Alternative — `rebind(Object, Group...)` re-matches:* changes the mapping, which the class can't accommodate. Rejected.

### 3. Plain fields + `VarHandle.fullFence()` (not `volatile`)

The interceptor fields drop `final` but stay plain. `rebind` writes each field then emits a `VarHandle.fullFence()`. Hot-path reads remain an ordinary `GETFIELD` with zero added cost.

- *Alternative — `volatile` fields:* gives atomic single-field swap and JMM visibility, but adds an acquire read on every method call, deviating from the "direct `GETFIELD`, parity with `reflect.Proxy`" identity. Rejected.
- *Trade-off:* `fullFence()` orders the writes but is not itself a happens-before edge to unsynchronized readers; the caller must establish one. Documented in the `rebind` Javadoc and the spec.

### 4. Separate `Rebindable` interface, not `DispatchTarget` extension

A new `io.github.lamspace.internal.Rebindable` (public, so hidden classes in arbitrary packages can implement it) carries `void rebind(Interceptor[])`. `DispatchTarget` stays single-purpose (super-method dispatch).

- *Alternative — add `rebind` to `DispatchTarget`:* muddies a single-responsibility interface. Rejected.

### 5. Length-only validation in the generated `rebind` body

The generated method checks the array is non-null and its length equals the expected count, throwing `IllegalArgumentException` otherwise. Element nullity is caller responsibility (matches the existing `proxy()` non-null discipline).

## Risks / Trade-offs

- [Rebind visibility is not JMM-guaranteed without caller synchronization] → `fullFence()` gives ordering + a strong practical visibility barrier; the single-writer + happens-before contract is documented on `rebind`.
- [Cache-key asymmetry: interface proxies key on the first interface] → `evict(iface)` only targets the first interface; documented in the `evict` Javadoc and the spec.
- [Dropping `final` removes a safety invariant] → mitigated by the single-writer contract and the fact that rebind only mutates the interceptor fields, never the dispatch/`Method` static fields.
- [Regeneration churn in metaspace] → already bounded by weak keys/values; eviction is opt-in and never automatic.
- [Cross-classloader targets (child loader) are not proxiable] → documented as out of scope; requires the JPMS `privateLookupIn`/`--add-opens` strategy (roadmap item 8).

## Migration Plan

Purely additive — no existing API changes signature or behavior. `evict`, `evictClassLoader`, and `rebind` are new; the hot-path emitters are untouched. Rollback is a plain revert of the change.

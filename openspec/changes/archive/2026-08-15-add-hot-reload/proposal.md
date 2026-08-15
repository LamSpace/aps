## Why

Long-running framework scenarios (webapp/plugin/DI containers) need two runtime-lifecycle capabilities APS lacks today: a deterministic way to drop cached proxy classes when a target is hot-deployed under a new `ClassLoader`, and the ability to swap an interceptor on a live proxy instance without recreating it (the per-interceptor fields are currently `final`, so they cannot be rebound).

## What Changes

- Add `AcceleratedProxy.evict(Class<?>)` and `evictClassLoader(ClassLoader)` to deterministically drop proxy-class cache entries, so the next `proxy(...)` call for an evicted target regenerates a fresh class.
- Add `WeakCache.removeIf(Predicate)` to make eviction possible (the weak references today only give lazy GC, not eager cleanup).
- Add `AcceleratedProxy.rebind(Object, Interceptor)` and `rebind(Object, Interceptor[])` to replace the interceptors on a live proxy instance without recreating it.
- Generated proxy classes (class and interface) implement a new internal `Rebindable` interface and emit a `rebind(Interceptor[])` method; the per-interceptor instance fields drop `final` (stay plain, non-`volatile`).
- No change to the hot-path emitters (`MethodDispatcher`, `InterfaceDispatcher`, `DispatchGenerator`); the override/dispatch bytecode stays byte-identical.

## Capabilities

### New Capabilities

- `hot-reload`: deterministic proxy-class eviction (`evict` / `evictClassLoader`) and in-place interceptor rebinding (`rebind`) for both class and interface proxies.

### Modified Capabilities

<!-- none -->

## Impact

- **Code**: `WeakCache`, `AcceleratedProxy`, `ClassGenerator`, `InterfaceGenerator`, `BytecodeUtils`; new `internal/Rebindable`.
- **Not touched**: `MethodDispatcher`, `InterfaceDispatcher`, `DispatchGenerator` (hot path unchanged), `DispatchTarget`, `Interceptor`, `Group`, `ConstructorInterceptor`, `LookupManager`.
- **Tests**: new `HotReloadTest`, `RebindClassProxyTest`, `RebindInterfaceProxyTest`; `WeakCacheTest` extended; new JMH `RebindBenchmark`.
- **Docs**: `docs/aps-future-roadmap.md`, `README.md`, `README_CN.md`, `docs/migration-guide.md`, `docs/benchmark-results.md` (+ `_cn`).

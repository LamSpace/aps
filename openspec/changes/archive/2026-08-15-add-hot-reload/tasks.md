## 1. WeakCache eviction primitive

- [x] 1.1 Add `WeakCache.removeIf(Predicate<? super K>)` — sweep the outer map, skip the `NULL_KEY` sentinel, unwrap each weak `CacheKey`, and call `CacheKey.expungeFrom(map, reverseMap)` for matching keys (design decision 1)
- [x] 1.2 Extend `WeakCacheTest` with `removeIf` cases: removes matching keys only, re-evaluates on next `get`, empty-cache no-op, skips the null sentinel

## 2. Class hot-reload (evict)

- [x] 2.1 Add `AcceleratedProxy.evict(Class<?>)` and `evictClassLoader(ClassLoader)` (both reject null with `IllegalArgumentException`)
- [x] 2.2 Add `HotReloadTest`: evict forces regeneration, old instance survives eviction, `evictClassLoader` evicts that loader / ignores an unrelated loader, eviction is idempotent, null args rejected
- [x] 2.3 Cross-classloader isolation — deferred to JPMS item 8 (child-loader targets need `privateLookupIn`/`--add-opens`); identity-keyed regeneration is covered by 2.2

## 3. Interceptor rebind — class proxies

- [x] 3.1 Create `io.github.lamspace.internal.Rebindable` with `void rebind(Interceptor[])`
- [x] 3.2 Add `BytecodeUtils.generateRebind(...)` — emit the `rebind` body: null check, length check, per-index `PUTFIELD`, then `VarHandle.fullFence()`
- [x] 3.3 Modify `ClassGenerator`: drop `ACC_FINAL` on `_interceptor$i`, add `Rebindable` to the implemented interfaces, emit the `rebind` method
- [x] 3.4 Add `AcceleratedProxy.rebind(Object, Interceptor)` and `rebind(Object, Interceptor[])` (reject non-proxy with `IllegalArgumentException`)
- [x] 3.5 Add `RebindClassProxyTest`: single swap, index-preserving multi-swap, length mismatch, null/non-proxy rejection, `invokeSuper` after rebind, per-instance isolation, convenience-overload equivalence, repeated rebind, passthrough method unaffected

## 4. Interceptor rebind — interface proxies

- [x] 4.1 Modify `InterfaceGenerator`: drop `ACC_FINAL` on `_interceptor$i`, add `Rebindable` to the implemented interfaces, emit the `rebind` method
- [x] 4.2 Add `RebindInterfaceProxyTest`: single swap, per-instance isolation, null/non-proxy rejection

## 5. Benchmark + performance verification

- [x] 5.1 Add `RebindBenchmark` (informational ns/op for a single `rebind`)
- [x] 5.2 Run JMH parity: `ProxyBenchmark`, `ConstructorInterceptionBenchmark`, `StaticMethodProxyBenchmark` must be unchanged within noise (hot path byte-identical)

## 6. Documentation

- [x] 6.1 Update `docs/aps-future-roadmap.md` — mark item 6 已完成, add 类热重载 / 拦截器热替换 subsections, rewrite the 热加载挑战 note
- [x] 6.2 Update `README.md` and `README_CN.md` — feature bullets + Quick Start snippet
- [x] 6.3 Update `docs/migration-guide.md` — note the additive nature and CGLib comparison
- [x] 6.4 Update `docs/benchmark-results.md` (+ `_cn`) — add the `RebindBenchmark` number

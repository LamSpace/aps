## Context

OpenProxy currently uses a single `Interceptor` field (`_callback`) in every generated proxy class. A `ClassFilter` (binary accept/reject) controls which methods route through the interceptor versus calling super directly. The matching decision happens inside `generateProxyClass` — after the cache lookup determines no cached class exists.

The change introduces multiple `Interceptor` instances per proxy, each bound to a subset of methods via `Group` declarations. This requires changes across the API layer (matching engine, cache key), the generator layer (field layout, constructor), and the dispatcher layer (per-method field references).

See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**
- Multiple `Interceptor` instances per proxy, each handling a distinct method family
- Zero hot-path overhead vs current single-Interceptor design (no array indirection)
- Backward compatible — existing `proxy(Class, Interceptor)` and `proxy(Class, Interceptor, ClassFilter)` preserved
- Deterministic cache key across JVM instances (stable method sort order)

**Non-Goals:**
- Chain-of-responsibility (multiple interceptors per single method call)
- Annotation-driven API (`@Intercept`) — Phase 3
- Hot-reload or dynamic reconfiguration of groups

## Decisions

### 1. Per-distinct-interceptor fields (no array)

**Choice:** Each distinct `Interceptor` gets its own instance field (`_interceptor$0`, `_interceptor$1`, ...). Each method override directly `GETFIELD`s its assigned field.

**Rationale:** The hot path (`GETFIELD` + `INVOKEINTERFACE intercept`) is bytecode-identical to the current single-Interceptor design. An `Interceptor[]` array approach would add one `AALOAD` per call — negligible in practice (~0.3ns) but avoidable entirely.

**Alternative considered:** Single `Interceptor[]` array with compile-time-constant indices per method. Rejected because the direct-field approach costs nothing extra in class file size (3-5 extra fields, ~50 bytes) and eliminates even the minimal array access overhead.

### 2. Dedup by reference equality

**Choice:** Two `Group.of(p1, interceptorX)` and `Group.of(p2, interceptorX)` with the same `interceptorX` instance share a single field.

**Rationale:** Users naturally reuse interceptor instances. Reference equality (`==`) is deterministic and doesn't rely on `Interceptor.equals()` being overridden (unlikely for lambdas). Two instances with identical behavior but different references are treated as distinct — this is intentional, not accidental.

### 3. Match before cache lookup

**Choice:** Group chain matching executes BEFORE the `WeakCache.get()` call. `CacheParams` includes the deduped `Interceptor[]` and `MethodMapping`.

**Rationale:** Matching cost is negligible (< 1µs: O(methods × groups) predicate calls). Running it before cache lookup means the cache key captures the exact matching result, guaranteeing cache correctness without a separate matching-result cache.

**Alternative considered:** Cache the match result separately, keep cache key as `{target, groups}`. Rejected — adds complexity (two caches) for no measurable benefit.

### 4. First-match-wins with WARNING, not exception

**Choice:** Duplicate method matches produce a `WARNING` log message but proceed with first-match-wins.

**Rationale:** `IllegalArgumentException` on overlap would be punitive — overlapping predicates are a legitimate pattern (e.g., `startsWith("get")` and `startsWith("getUser")`) where declaration order provides unambiguous intent. A WARNING helps users catch unintended overlaps without blocking compilation or startup.

### 5. Stable method sort for cache key determinism

**Choice:** Methods are sorted by `getName()` then `parameterTypes` before building `MethodMapping.indices`. Both `matchMethods` and `dispatchMethods` use identical sort order.

**Rationale:** `Class.getDeclaredMethods()` and `Class.getMethods()` return order is JVM-implementation-dependent. Without stable sorting, the same Group configuration could produce different `MethodMapping` instances across JVM restarts, causing cache misses.

### 6. ClassFilter removed, old API preserved via delegation

**Choice:** `ClassFilter.java` is deleted. `proxy(target, interceptor)` becomes `proxy(target, Group.otherwise(interceptor))`. `proxy(target, interceptor, filter)` becomes `proxy(target, Group.of(filter::accept, interceptor))`.

**Rationale:** Clean API surface with no duplicated concepts. The old signatures remain at the `AcceleratedProxy` level, so existing code compiles without changes.

## Risks / Trade-offs

- **[Risk]** Constructor parameter count grows with distinct Interceptor count → **Mitigation**: Reference dedup keeps practical count at 3-5 params; JVM limit is 255.
- **[Risk]** `Method.getMethods()` iteration order differs across JVM versions → **Mitigation**: Stable sort applied in both `matchMethods` and `dispatchMethods`.
- **[Risk]** Duplicate-match WARNING adds overhead to `proxy()` call → **Mitigation**: Gate detection behind `Logger.isLoggable(Level.WARNING)` so production deployments with higher log thresholds skip the check entirely.

## 1. Core API Types (no bytecode changes)

- [x] 1.1 Create `MethodPredicate.java` — `@FunctionalInterface` with `boolean test(Method method)`
- [x] 1.2 Create `MethodMapping.java` — internal `int[]` wrapper with `equals`/`hashCode` for cache key
- [x] 1.3 Create `Group.java` — `Group.of(MethodPredicate, Interceptor)` and `Group.otherwise(Interceptor)` factories, package-private accessors
- [x] 1.4 Refactor `AcceleratedProxy` — new `MatchResult` record, rewrite `CacheParams` with `Interceptor[]` + `MethodMapping`, add `matchMethods()` with stable sort + Group chain evaluation + duplicate warning, add `proxy(Class, Group...)` and `proxy(Class, Object[], Group...)` overloads, delegate old `proxy(Class, Interceptor)` and `proxy(Class, Interceptor, ClassFilter)` to Group model
- [x] 1.5 Verify compilation succeeds (generator compile errors expected — Phase 2 will fix)

## 2. Bytecode Generator Adaptation

- [x] 2.1 Refactor `MethodDispatcher` — stable-sort methods before iteration, accept `MethodMapping` + `interceptorCount` instead of `ClassFilter`, generate `GETFIELD _interceptor$N` per method instead of `GETFIELD _callback`
- [x] 2.2 Refactor `InterfaceDispatcher` — same changes as MethodDispatcher for the interface path
- [x] 2.3 Refactor `ClassGenerator` — accept `Interceptor[]` + `MethodMapping` instead of `ClassFilter`, generate one `_interceptor$N` field per distinct interceptor, generate constructor with M interceptor parameters + super args, resolve super constructor by parameter type matching
- [x] 2.4 Refactor `InterfaceGenerator` — accept `Interceptor[]` + `MethodMapping`, generate per-interceptor fields, generate multi-parameter constructor
- [x] 2.5 Delete `ClassFilter.java`, clean up remaining imports and references project-wide

## 3. Tests — Matching Engine

- [x] 3.1 Write `GroupMatchingTest` — first-match-wins, default passthrough, `otherwise()` fallback, empty/nulls rejected, `Group.of`/`otherwise` null validation, shared interceptor dedup
- [x] 3.2 Write `DuplicateMatchWarningTest` — overlapping predicates log WARNING with method name and group indices, `otherwise` does not trigger warning, distinct predicates produce no warning

## 4. Tests — Class & Interface Proxies

- [x] 4.1 Write `MultiInterceptorClassProxyTest` — getter/setter different interceptors, passthrough bypasses interceptor, `invokeSuper` works in any group, interceptor order matches declaration, shared interceptor dedup, stateful interceptors don't leak across groups, cache hit with same config, old API still works
- [x] 4.2 Write `MultiInterceptorInterfaceProxyTest` — different operations use different interceptors, passthrough throws `AbstractMethodError`, `otherwise` catches remaining, old API preserved

## 5. Verification

- [x] 5.1 Run full test suite — all existing tests pass (71 tests, minor ClassFilter→Group API migrations in existing test files)
- [x] 5.2 Run JMH benchmark — multi-group vs single-interceptor within ±2%, passthrough identical to direct

## 6. Documentation

- [x] 6.1 Update `docs/openproxy-future-roadmap.md` — mark Phase 2 as completed
- [x] 6.2 Update Javadoc on `AcceleratedProxy` — replace `ClassFilter` references with `Group`

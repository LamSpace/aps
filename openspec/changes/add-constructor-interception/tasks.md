## 1. ConstructorInterceptor interface + vertical slice

- [x] 1.1 Create `io.github.lamspace.ConstructorInterceptor` (`before`/`after` as specced)
- [x] 1.2 Add `ctorIntercept` flag + constructors + `superParamTypes()` helper to `ClassGenerator`
- [x] 1.3 Emit the `_ctor$` static field and `<clinit>` `getDeclaredConstructor` lookup in `ClassGenerator`
- [x] 1.4 Emit the intercepted constructor (box args → `before` → unbox → super → `after`) in `ClassGenerator`
- [x] 1.5 Add `ctorIntercept` to `CacheParams` and pass `params.ctorIntercept()` in `generateProxyClass`
- [x] 1.6 Add `proxy(Class<T>, Object[], ConstructorInterceptor, Group...)` + `sneakyThrow` veto unwrap in `AcceleratedProxy`
- [x] 1.7 Write `ConstructorInterceptionTest` covering ordering, correct ctor/args, `after` receives `this`, default `after`
- [x] 1.8 Run full suite (`mvn -s /home/lam/repo/settings.xml test`) — no regressions

## 2. Argument rewriting

- [x] 2.1 Test `before` rewrites values across int/long/double/boolean/String/null
- [x] 2.2 Test `before` returning the same array passes through unchanged

## 3. Construction veto

- [x] 3.1 Test `before` throwing `RuntimeException` propagates as-is
- [x] 3.2 Test `before` throwing a checked exception surfaces as `UndeclaredThrowableException`

## 4. Cache correctness + convenience overloads

- [x] 4.1 Add `proxy(Class<T>, Interceptor, ConstructorInterceptor)` and `proxy(Class<T>, ConstructorInterceptor, Group...)`
- [x] 4.2 Test different interceptor instances share the generated class but run their own hooks
- [x] 4.3 Test intercepted vs non-intercepted proxies use distinct classes
- [x] 4.4 Test convenience overload matches `Group.otherwise(interceptor)` equivalent; null ctor interceptor and interface target rejected

## 5. Benchmark

- [x] 5.1 Add `ConstructorInterceptionBenchmark` (direct / plain proxy / intercepted proxy)
- [x] 5.2 Run it and the existing `ProxyBenchmark`; confirm no regression on non-intercepted path

## 6. Documentation

- [x] 6.1 Update `docs/aps-future-roadmap.md` (mark 构造器拦截 已完成 + subsection)
- [x] 6.2 Update `README.md` and `README_CN.md` (feature bullet + Quick Start)
- [x] 6.3 Record benchmark numbers in `docs/benchmark-results.md` and `_cn`

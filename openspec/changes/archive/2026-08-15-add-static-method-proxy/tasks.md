## 1. Static method proxy core

- [x] 1.1 Create `io.github.lamspace.generator.StaticMethodGenerator` (emits `extends Object` hidden class: static interceptor fields, static `Method` fields + `<clinit>`, `__bindStatics(Interceptor[])`, and per-method intercepted/passthrough shadows)
- [x] 1.2 Add `proxyStatic(Class<?>, Group...)` and `proxyStatic(Class<?>, Interceptor)` overloads plus `collectStaticMethods` (declared + inherited `public static` non-`final`, subclass-first dedup) to `AcceleratedProxy`
- [x] 1.3 Write `StaticMethodProxyTest` covering passthrough, interception with null proxy + correct `Method`/args, call-original, primitive/reference/void return types
- [x] 1.4 Run `mvn -s /home/lam/repo/settings.xml test` — no regressions

## 2. Edge-case coverage

- [x] 2.1 Test overloaded statics dispatch by parameter types
- [x] 2.2 Test inherited and redeclared static methods are shadowed correctly
- [x] 2.3 Test `final`/`private` static methods are not shadowed
- [x] 2.4 Test Group matching binds different interceptors, and a shared interceptor across groups routes correctly
- [x] 2.5 Test `RuntimeException` propagates and checked exception surfaces as `UndeclaredThrowableException`
- [x] 2.6 Test `MethodHandle` invocation, distinct classes per call, convenience overload, and null/empty/interface argument rejection

## 3. Benchmark

- [x] 3.1 Add `StaticMethodProxyBenchmark` (direct / reflection floor / proxy reflection / proxy MethodHandle)
- [x] 3.2 Run it and re-run `ProxyBenchmark`; confirm instance/interface numbers are unchanged

## 4. Documentation

- [x] 4.1 Update `docs/openproxy-future-roadmap.md` (mark 静态方法代理 已完成 + subsection)
- [x] 4.2 Update `README.md` and `README_CN.md` (feature bullet + Quick Start)
- [x] 4.3 Record benchmark numbers in `docs/benchmark-results.md` and `_cn`

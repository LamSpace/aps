## 1. InterfaceMethodResolver

- [x] 1.1 Write `src/test/java/io/github/lamspace/generator/InterfaceMethodResolverTest.java` covering: merge same-signature-same-return, Object-method dedup, one-default-plus-abstract default owner, differing-return rejection, double-default rejection, parent/child dedup, single-interface sort order, non-interface rejection
- [x] 1.2 Run `mvn -s /home/lam/repo/settings.xml test -Dtest=InterfaceMethodResolverTest` and confirm it fails (class absent)
- [x] 1.3 Implement `src/main/java/io/github/lamspace/generator/InterfaceMethodResolver.java` with `resolve(Class<?>[])` returning `List<ResolvedMethod>` (canonical, owner, variants, defaultOwner)
- [x] 1.4 Run the test and confirm all 8 pass
- [x] 1.5 Commit

## 2. Wire the resolver into the interface path (N == 1 refactor)

- [x] 2.1 Add `defaultOwner` component to `MethodInfo` (with a 3-arg convenience constructor)
- [x] 2.2 Update `DispatchGenerator.generateDispatch` to read the owner from `info.defaultOwner()` and drop the `interfaceInternalName` parameter; update `ClassGenerator` call site
- [x] 2.3 Update `InterfaceDispatcher.dispatchMethods` to iterate the resolved method list (no re-sort/filter)
- [x] 2.4 Update `InterfaceGenerator` to take `Class<?>[]`, resolve methods, and emit multi-interface `implements`
- [x] 2.5 Update `AcceleratedProxy`: `CacheParams` gains `interfaces`; array-based `matchMethods`; `proxyInterfaces` helper; `generateProxyClass` branches on `params.interfaces()`; route single-interface targets through `proxyInterfaces`
- [x] 2.6 Run `mvn -s /home/lam/repo/settings.xml test` and confirm the full existing suite passes (especially `AcceleratedProxyInterfaceProxyTest`, `DefaultMethodInvocationTest`, `MultiInterceptorInterfaceProxyTest`, `AcceleratedProxyClassProxyTest`)
- [x] 2.7 Commit

## 3. Public multi-interface API and end-to-end tests

- [x] 3.1 Add `Object proxy(Class<?>[] interfaces, Interceptor)` and `Object proxy(Class<?>[] interfaces, Group... groups)` overloads to `AcceleratedProxy`
- [x] 3.2 Write `src/test/java/io/github/lamspace/MultiInterfaceProxyTest.java` covering the spec scenarios: multi-interface castability, shared-signature merge, one-default-plus-abstract, double-default rejection, differing-return rejection, three interfaces, parent/child dedup, Object-method behavior, Group chain across interfaces, cache reuse, invokeSuper hash routing through both interfaces, invalid-input rejection, single-interface equivalence
- [x] 3.3 Run `mvn -s /home/lam/repo/settings.xml test -Dtest=MultiInterfaceProxyTest` and confirm it fails before the overloads exist
- [x] 3.4 Run the test and confirm all pass
- [x] 3.5 Run the full suite and confirm all pass
- [x] 3.6 Commit

## 4. Documentation and benchmark verification

- [x] 4.1 Mark Phase 3 item 2 (`多接口代理`) as 已完成 in `docs/openproxy-future-roadmap.md` and add a `### 多接口代理（已完成）` subsection
- [x] 4.2 Add a "Multi-interface proxy" feature bullet and Quick Start example to `README.md` and `README_CN.md`
- [x] 4.3 Add a `java.lang.reflect.Proxy` → OpenProxy multi-interface mapping note to `docs/migration-guide.md`
- [x] 4.4 Run the existing JMH suite and confirm interface/class numbers are unchanged vs `docs/benchmark-results.md`
- [x] 4.5 Commit

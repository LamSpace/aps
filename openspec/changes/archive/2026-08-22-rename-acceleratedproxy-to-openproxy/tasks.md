## 1. Baseline (before any code change)

- [x] 1.1 Run `mvn test` and record the total test count and all-green status — **baseline: 219 tests, all green (2026-08-22)**
- [x] 1.2 Run the 4 JMH benchmarks (`ProxyBenchmark`, `RebindBenchmark`, `ConstructorInterceptionBenchmark`, `StaticMethodProxyBenchmark`) and record baseline numbers — **baseline: 96 results, zero failures, saved to `/tmp/jmh-baseline.json` + `/tmp/jmh-baseline.log` (2026-08-22)**

## 2. Core rename (src/main)

- [x] 2.1 `git mv AcceleratedProxy.java OpenProxy.java`; rename class declaration, private constructor, logger reference, `OpenProxy::generateProxyClass` method reference, and the class-javadoc example
- [x] 2.2 Replace the generated-class marker `"$$AcceleratedProxy$$"` with `"$$OpenProxy$$"` in `ClassGenerator`, `InterfaceGenerator`, `StaticMethodGenerator`
- [x] 2.3 Replace the remaining javadoc/comment references in `Group`, `Interceptor`, `ConstructorInterceptor`, `DispatchTarget`, `Intercept`, `Rebindable`, `package-info`, `InterfaceMethodResolver`, `MethodDispatcher`
- [x] 2.4 Verify: `mvn -q compile` green and `javadoc -Xdoclint:all -package` zero warnings

## 3. Tests and benchmarks

- [x] 3.1 Replace all `AcceleratedProxy` references in the 21 non-renamed test/benchmark files
- [x] 3.2 Rename `AcceleratedProxyClassProxyTest` → `OpenProxyClassProxyTest` and `AcceleratedProxyInterfaceProxyTest` → `OpenProxyInterfaceProxyTest` (git mv + class name)
- [x] 3.3 Add one assertion (new or existing generated-class test): proxy class name contains `$$OpenProxy$$` (added `generatedClassNameShouldCarryOpenProxyMarker` in `OpenProxyClassProxyTest`)
- [x] 3.4 Run `mvn test` — same test count as baseline 1.1 (plus any added assertion), all green — **220 = 219 baseline + 1 new marker assertion, all green**

## 4. Documentation

- [x] 4.1 Replace references in `README.md` and `README_CN.md`
- [x] 4.2 Replace references in `docs/guide/` (20 files) and `docs/migration-guide.md`; add a short "AcceleratedProxy → OpenProxy rename" note to the migration guide
- [x] 4.3 Edit `openspec/specs/openproxy-unified-proxy/spec.md` Purpose directly: `AcceleratedProxy.proxy()` → `OpenProxy.proxy()` (deltas cannot carry Purpose changes)

## 5. Final verification

- [x] 5.1 Grep sweep: zero `AcceleratedProxy` occurrences outside `docs/superpowers/` and `openspec/changes/archive/` — code/README/guide/benchmarks clean; remaining hits are expected: the 8 main specs (replaced by delta sync at archive), the migration-guide rename note (must name the old class), and this change's own artifacts (archived later)
- [x] 5.2 Re-run the 4 JMH benchmarks; compare with baseline 1.2 — OpenProxy items within noise, ranking vs CGLib/JDK unchanged — **96/96 identical set, zero failures; OpenProxy scores within noise (median Δ +2.7%); 8/9 OpenProxy-vs-CGLib pairs preserved; the one flip (voidOp) is driven entirely by CGLib-side variance (6.1→3.8 ns) while OpenProxy stayed flat (4.12→4.20 ns). Post results: `/tmp/jmh-post.json`**
- [x] 5.3 `openspec validate rename-acceleratedproxy-to-openproxy` passes

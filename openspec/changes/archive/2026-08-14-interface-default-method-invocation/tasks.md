## 1. Default method fast path

- [x] 1.1 Write failing integration test `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java` for directly-declared (`greet()`, `add(int,int)`, `run()`) and inherited (`inheritedGreet()`) defaults
- [x] 1.2 Run it and confirm it fails with `AbstractMethodError`
- [x] 1.3 Add `interfaceInternalName` parameter to `DispatchGenerator.generateDispatch` and update the two callers (`InterfaceGenerator`, `ClassGenerator`)
- [x] 1.4 Emit `INVOKESPECIAL` (`itf = true`) against the target interface for every `method.isDefault()` case
- [x] 1.5 Run full test suite (`mvn -s /home/lam/repo/settings.xml -q test`) and confirm all pass
- [x] 1.6 Commit

## 2. Edge cases and regression

- [x] 2.1 Add tests: non-default still throws, interceptor replacement, argument modification before `invokeSuper`, `Object` methods still dispatch, exception propagation from a default method
- [x] 2.2 Run full test suite and confirm all pass (including existing `AcceleratedProxyInterfaceProxyTest` and `AcceleratedProxyClassProxyTest`)
- [x] 2.3 Commit

## 3. Benchmark and report

- [x] 3.1 Add `DefaultGreeter` interface, `DefaultMethodState`, and `i_default_greet` / `i_default_inherited` / `i_jp_default_greet` / `i_jp_default_inherited` benchmarks to `ProxyBenchmark.java`
- [x] 3.2 Build classpath (`mvn -s /home/lam/repo/settings.xml -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt`)
- [x] 3.3 Run JMH and capture ns/op scores
- [x] 3.4 Update `docs/benchmark-results.md` and `docs/benchmark-results_cn.md` with a new "Interface Default Method Invocation" section and updated date
- [x] 3.5 Commit

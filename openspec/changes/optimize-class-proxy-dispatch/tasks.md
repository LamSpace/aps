## 1. Core: Callback interface and generated proxy changes

- [ ] 1.1 Update `Callback.intercept` signature from `(Object, Method, MethodHandle, Object[])` to `(Object, Method, int, Object[])`
- [ ] 1.2 Update `MethodDispatcher` to pass index instead of bound handle in override bytecode
- [ ] 1.3 Update `ClassGenerator` to generate `MethodHandle[]` static field, `invokeSuper()` method, and updated `<clinit>` type-erase logic
- [ ] 1.4 Update `ClinitRegistry` to track per-method index assignments for the dispatch table

## 2. Interface proxy (unchanged behavior)

- [ ] 2.1 Verify `InterfaceCallback.intercept` signature is unaffected (still `(Object, Method, Object[])`)
- [ ] 2.2 Run existing interface proxy tests to confirm no regression

## 3. Tests and benchmarks

- [ ] 3.1 Update `APSFunctionalTest.java` to use new `(proxy, method, index, args)` callback signature
- [ ] 3.2 Update `ClassGeneratorTest.java` to verify `MethodHandle[]` generation and `invokeSuper`
- [ ] 3.3 Update `ProxyBenchmark.java` class proxy scenarios to use new callback signature
- [ ] 3.4 Run full JMH benchmark suite and update `docs/benchmark-results.md` with v1.2 results
- [ ] 3.5 Update the v1.0 → v1.1 improvement table to include v1.1 → v1.2 (index dispatch) comparison

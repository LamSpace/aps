## 1. Fix secondary hash formula

- [x] 1.1 Update `DispatchGenerator.resolveHashes()` to use `Arrays.hashCode(m.getParameterTypes())` as the secondary hash discriminator instead of `m.getName().hashCode()`
- [x] 1.2 Add a fallback incrementing counter loop in `resolveHashes()` for the theoretical triple-collision case

## 2. Wire resolveHashes into class proxy generation

- [x] 2.1 In `ClassGenerator.generate()`, replace the per-entry `DispatchGenerator.computeHash()` call with a single `DispatchGenerator.resolveHashes(methods)` call that processes all methods at once
- [x] 2.2 Construct `MethodInfo` objects using the resolved hash map instead of `computeHash()`

## 3. Wire resolveHashes into interface proxy generation

- [x] 3.1 In `InterfaceGenerator.generate()`, apply the same `resolveHashes()` wiring as in `ClassGenerator.generate()`
- [x] 3.2 Verify the change is a no-op for interface proxies (interface method dispatch branches throw `AbstractMethodError`, so collision-free hashes are less critical but still correct)

## 4. Add overloaded method tests

- [x] 4.1 Add an `OverloadedTarget` test class to `AcceleratedProxyClassProxyTest` with multiple `greet()` overloads (different parameter types and counts)
- [x] 4.2 Add a test that creates a proxy for `OverloadedTarget`, calls each overloaded variant through the interceptor, and verifies each dispatches to the correct superclass method
- [x] 4.3 Add a test that verifies `dispatch(method, args)` routes to the correct branch for every overloaded method

## 5. Validation

- [x] 5.1 Run the full test suite (`mvn test`) to verify no regressions
- [x] 5.2 Run JMH benchmarks to confirm no performance regression in the dispatch hot path

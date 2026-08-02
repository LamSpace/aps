## 1. New API Types

- [x] 1.1 Create `Interceptor.java` — unified `@FunctionalInterface` with `Object intercept(Object proxy, Method method, Object[] args) throws Throwable`
- [x] 1.2 Create `DispatchTarget.java` — package-private interface with `Object dispatch(Method method, Object[] args) throws Throwable`

## 2. Dispatch Mechanism

- [x] 2.1 Create `DispatchGenerator.java` — generates `dispatch()` method bytecode with hashCode-driven if-else chain using `Method.hashCode()` embedded as `ldc` constants
- [x] 2.2 Verify Method.hashCode () collisions are detected at generation time with secondary discriminator fallback

## 3. Generator Updates

- [x] 3.1 Update `MethodDispatcher.java` — change callback type from `Callback` to `Interceptor`, remove `int index` parameter from bytecode, drop `ClinitRegistry` hash field registration (hash values use `ldc`)
- [x] 3.2 Update `InterfaceDispatcher.java` — change callback type from `InterfaceCallback` to `Interceptor`
- [x] 3.3 Update `ClassGenerator.java` — remove `MethodHandle[] _handles` array, remove `SuperDispatcher` interface, add `DispatchTarget` interface, add `dispatch()` generation, remove `invokeSuper()` generation, update `<clinit>` to remove MethodHandle binding logic, drain `ClinitRegistry` entries before both dispatch and clinit generation
- [x] 3.4 Update `InterfaceGenerator.java` — add `DispatchTarget` to implemented interfaces, change callback type to `Interceptor`, add `dispatch()` generation (Object methods → super call, interface methods → AbstractMethodError)

## 4. Entry Point

- [x] 4.1 Replace `APS.create()` and `APS.createInterface()` with `APS.proxy()` — auto-detects class vs interface, generates appropriate proxy, unified class loading via `LookupManager.getLookup(target).defineHiddenClass()`
- [x] 4.2 Update `APS.invokeSuper()` — change signature from `(Object proxy, int index, Object[] args)` to `(Object proxy, Method method, Object[] args)`, cast to `DispatchTarget.dispatch(method, args)`
- [x] 4.3 Add `WeakCache` — proxy class cache keyed by `{targetClass, filter}`, integrate into `APS.proxy()`

## 5. Cleanup

- [x] 5.1 Delete `Callback.java`
- [x] 5.2 Delete `InterfaceCallback.java`
- [x] 5.3 Delete `SuperDispatcher.java`
- [x] 5.4 Delete `HiddenClassLoader.java` (no longer needed after class loading unification)
- [x] 5.5 Verify main source compilation passes with no remaining references to deleted types

## 6. Tests

- [x] 6.1 Update `APSFunctionalTest.java` — migrate from `APS.create`/`Callback` to `APS.proxy`/`Interceptor`, update `invokeSuper` calls
- [x] 6.2 Update `APSInterfaceFunctionalTest.java` — migrate from `APS.createInterface`/`InterfaceCallback` to `APS.proxy`/`Interceptor`
- [x] 6.3 Create `APSUnifiedTest.java` — tests for `proxy()` with both class and interface, `invokeSuper` dispatch behavior (class methods succeed, interface methods throw `AbstractMethodError`), `ClassFilter` integration, cache reuse

## 7. Benchmarks

- [x] 7.1 Update `ProxyBenchmark.java` — migrate all benchmark states from old API (`create`/`createInterface`/`Callback`/`InterfaceCallback`) to new API (`proxy`/`Interceptor`), update `invokeSuper` calls
- [x] 7.2 Run full benchmark suite and verify class proxy scenarios show expected improvements (passthrough ~3x, primitive return ~5x, void method ~4x)
- [x] 7.3 Update `docs/benchmark-results.md` with new numbers

## 8. Documentation

- [x] 8.1 Update `README.md` — API examples, performance table, migration guide

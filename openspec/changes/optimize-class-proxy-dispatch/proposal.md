## Why

APS class proxy is 3-30× slower than CGLib. The root cause is `bindTo(this)` on every method invocation, which allocates a `BoundMethodHandle` (~10-20ns) and adds one level of indirection to the super-method dispatch path. Replacing `bindTo` with an index-based MethodHandle dispatch table eliminates this allocation entirely, bringing APS closer to CGLib while retaining the MethodHandle architecture.

## What Changes

- **BREAKING**: Change `Callback.intercept(Object, Method, MethodHandle, Object[])` to `Callback.intercept(Object, Method, int, Object[])` — the third parameter changes from a pre-bound `MethodHandle` to an integer index
- Add `invokeSuper(int index, Object[] args)` method to generated class proxies, enabling users to invoke the super implementation by index instead of holding a per-call handle
- Pre-compute a static `MethodHandle[]` array in `<clinit>` with all handles type-erased to `(Object, Object[])Object` for uniform dispatch
- Update all benchmarks, tests, and documentation to use the new Callback API

## Capabilities

### New Capabilities

- `class-proxy-index-dispatch`: Index-based MethodHandle dispatch table for class proxies, replacing per-call `bindTo(this)` with a static lookup array and a generated `invokeSuper(int, Object[])` method

### Modified Capabilities

- `aps-core`: Change `Callback.intercept` signature from `(Object, Method, MethodHandle, Object[])` to `(Object, Method, int, Object[])`; generated proxies must provide `invokeSuper(int, Object[])` for super method dispatch

## Impact

- `Callback.java` — interface signature change (**BREAKING**)
- `MethodDispatcher.java` — override bytecode generation: pass index instead of bound handle
- `ClassGenerator.java` — add `invokeSuper()` method generation and `MethodHandle[]` static field
- `ClinitRegistry.java` — update to track index assignments
- `APS.java` — API surface unchanged (`APS.create()` signature stays the same)
- `APSFunctionalTest.java`, `ProxyBenchmark.java` — update to new Callback API
- `docs/benchmark-results.md` — re-run and update benchmarks

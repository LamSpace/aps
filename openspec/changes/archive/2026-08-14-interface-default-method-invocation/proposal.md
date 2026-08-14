## Why

For interface proxies, `AcceleratedProxy.invokeSuper(proxy, method, args)` throws `AbstractMethodError` for every non-`Object` interface method — including `default` methods that have a concrete implementation. This forces interceptors to re-implement the default behavior by hand, which defeats the purpose of `default` methods as a "fall through to the interface's own logic" escape hatch.

## What Changes

- `invokeSuper` on a `default` method — whether **declared directly** on the target interface or **inherited** from a parent interface — now invokes that default implementation via a generated `INVOKESPECIAL` against the target interface (zero `MethodHandle` overhead).
- `invokeSuper` on a **non-default** interface method continues to throw `AbstractMethodError` (unchanged).
- Class-proxy super calls, `Object`-method dispatch, and existing interception behavior are unchanged.

No public API changes: `AcceleratedProxy.invokeSuper`, `AcceleratedProxy.proxy`, and `DispatchTarget.dispatch` keep their signatures.

## Capabilities

### New Capabilities

<!-- none -->

### Modified Capabilities

- `aps-interface-proxy`: the "Super invocation on interface proxy" requirement changes — default methods now invoke their default implementation instead of throwing `AbstractMethodError`; non-default interface methods still throw.

## Impact

- **Code**: `DispatchGenerator.generateDispatch` (dispatch branch logic + new `interfaceInternalName` parameter), `InterfaceGenerator` / `ClassGenerator` (updated call sites).
- **Tests**: new `DefaultMethodInvocationTest` (integration); existing `AcceleratedProxyInterfaceProxyTest` semantics for non-default methods remain valid.
- **Benchmarks**: `ProxyBenchmark` gains interface default-method scenarios; `docs/benchmark-results*.md` updated.
- **Dependencies**: none added.

## Why

`AcceleratedProxy.proxy()` accepts a single target — one class or one interface — so a single proxy object cannot be used through multiple interfaces. `java.lang.reflect.Proxy.newProxyInstance` supports this via its `Class<?>[]` parameter, and users migrating from it (or wanting one object that is simultaneously a `Greeter` and an `Auditable`) have no equivalent in OpenProxy.

## What Changes

- New `AcceleratedProxy.proxy(Class<?>[] interfaces, Interceptor)` and `AcceleratedProxy.proxy(Class<?>[] interfaces, Group... groups)` overloads returning `Object`; the returned object can be cast to each interface.
- Cross-interface method merging: methods with the same signature (name + parameter types) and same return type collapse into one implementation.
- Deterministic conflict rejection at `proxy()` time: same signature with differing return types throws `IllegalArgumentException`; two `default` implementations for the same signature from distinct interfaces throw `IllegalArgumentException`; one `default` + one abstract merge, with `invokeSuper` calling the `default`.
- Internally the interface path is unified onto `Class<?>[]` — the single-interface proxy becomes the `N == 1` case, and its generated class and behavior are unchanged.
- Class proxies are untouched.

No breaking changes: all existing `proxy(Class<T>, ...)` overloads and their `T` return type are preserved.

## Capabilities

### New Capabilities

- `multi-interface-proxy`: runtime proxy classes that implement multiple interfaces, including cross-interface method merging and conflict rejection.

### Modified Capabilities

<!-- none: single-interface and class-proxy behavior are unchanged -->

## Impact

- **Code**: `AcceleratedProxy` (new overloads, `CacheParams` gains an `interfaces` field, array-based `matchMethods`), new `generator/InterfaceMethodResolver`, `InterfaceGenerator`/`InterfaceDispatcher`/`DispatchGenerator`/`MethodInfo` (resolved-method list + per-method default owner).
- **Tests**: new `InterfaceMethodResolverTest` (unit) and `MultiInterfaceProxyTest` (integration); existing suite must stay green (`N == 1` byte-identity).
- **Benchmarks**: no change expected (merging/conflict detection runs at creation time, outside the JMH loop); a before/after run is the verification gate.
- **Dependencies**: none added.

## Why

APS can intercept every non-final *instance* method, but static methods are invisible to it: `MethodDispatcher` skips `static` members and the generated subclass's dispatch machinery is instance-bound. A small but real set of consumers — reflective test harnesses, plugin/DI frameworks that look up a class and invoke its static methods, and log-injection wrappers obtained via `MethodHandle` — need to route static calls through the same `Interceptor` contract. Static methods are compile-time bound (`INVOKESTATIC`), so this change returns a decorated `Class` the caller invokes reflectively or via `MethodHandle` rather than transparently intercepting `Target.staticMethod()`.

## What Changes

- Add `AcceleratedProxy.proxyStatic(Class<?> target, Group... groups)` returning a generated proxy `Class<?>`, plus a single-interceptor convenience overload `proxyStatic(Class<?> target, Interceptor interceptor)`.
- A new `StaticMethodGenerator` (ASM) emits a hidden class (`extends Object`, in `io.github.lamspace`) declaring one `public static` shadow per collected static method; matched methods route through the existing `Interceptor` with `proxy == null`, unmatched methods pass through via a direct `INVOKESTATIC` to the declaring class.
- Static methods are collected from the target and its superclasses (subclass-first dedup by `name + parameterTypes`): `public static`, non-`final`, non-`private` methods.
- Reuses `Interceptor` as-is; the original static method is invoked from the interceptor via `method.invoke(null, args)`.
- Static proxies are **uncached** (static interceptor fields are class-global state), and the existing `proxy()` path — instance and interface — is byte-for-byte unchanged.

## Capabilities

### New Capabilities

- `static-method-proxy`: a generated proxy class whose `public static` methods shadow a target class's static methods and route them through the `Interceptor` (with `proxy == null`), returned as a `Class<?>` for reflective or `MethodHandle` invocation.

### Modified Capabilities

<!-- No existing capability's requirements change; this is purely additive. -->

## Impact

- **Code:** new `io.github.lamspace.generator.StaticMethodGenerator`; changes to `io.github.lamspace.AcceleratedProxy` (two `proxyStatic` overloads, `collectStaticMethods`). No change to the existing generators, dispatchers, cache, or `invokeSuper`.
- **API:** two new additive `proxyStatic(...)` entry points returning `Class<?>`; no breaking changes to existing signatures.
- **Tests / benchmarks:** new `StaticMethodProxyTest` and a JMH `StaticMethodProxyBenchmark`; existing class/interface proxy numbers must not regress (procedural guard — no instance-path file is touched).
- **Docs:** `README.md`/`README_CN.md`, `docs/aps-future-roadmap.md`, `docs/benchmark-results.md`/`_cn`.

## Context

APS currently has two parallel proxy generation paths: `ClassGenerator` for class proxies and `InterfaceGenerator` for interface proxies, each using a different callback interface (`Callback` vs `InterfaceCallback`). The class proxy `invokeSuper` path goes through a type-erased `MethodHandle.invoke()` (via `_handles[index].invoke(this, args)` with `asSpreader` + `asType` erasure), which adds ~10ns of overhead compared to a direct `super.method(args)` call. The interface proxy path is already
close to `java.lang.reflect.Proxy` performance.

See proposal.md for full motivation and spec files for behavioral requirements.

## Goals / Non-Goals

**Goals:**

- Single `APS.proxy()` entry point for both classes and interfaces
- Single `Interceptor` callback interface replacing `Callback` and `InterfaceCallback`
- Replace type-erased `MethodHandle` dispatch with hashCode-based switch using direct `INVOKESPECIAL` super calls
- Eliminate `MethodHandle[]` array, `asSpreader`, and `asType` from the codebase
- Unify class loading to one code path

**Non-Goals:**

- Interface `default` method invocation (requires `findSpecial`, deferred)
- Static/final method interception (out of scope)
- Multi-interface proxy support

## Decisions

### Decision 1: Hash function — `Method.hashCode()` over `System.identityHashCode()`

`Method.hashCode()` computes as `getDeclaringClass().getName().hashCode() ^ getName().hashCode()`. This is deterministic and stable across JVM runs, allowing hash values to be pre-computed at bytecode generation time and embedded as `ldc` constants in the `dispatch()` method bytecode. No `GETSTATIC` loads needed in the hot path.

`System.identityHashCode()` was considered but rejected because it cannot be pre-computed at bytecode generation time — the hash is only known at `<clinit>` runtime, requiring either static field storage (`GETSTATIC` per comparison) or computing it at dispatch call time on the `Method` parameter.

Collision risk: within a single proxy class, two methods with the same name can only exist via overloading (different parameters), so `getName().hashCode()` is already unique per-method within the declaring class. Cross-class collisions are irrelevant since dispatch only handles one target class. If a collision does occur, `Method.hashCode()` XOR with declaring class name is extremely unlikely to collide for different method names in the same class — in that case, a secondary discriminator
(`method.getName()`) is appended to the dispatch chain.

### Decision 2: `DispatchTarget` as package-private internal interface

`DispatchTarget` is not part of the public API. Users never cast to it — they call `APS.invokeSuper(proxy, method, args)` which internally casts to `DispatchTarget`. This keeps the public API surface minimal (just `Interceptor` + `APS.proxy()` + `APS.invokeSuper()`).

### Decision 3: One `dispatch()` method vs per-method fields

Newproxy uses per-method `volatile MethodHandle` fields with DCL lazy initialization for interface methods. For APS, the class proxy case only needs direct `super.method(args)` calls — no MethodHandle needed. A single `dispatch()` method with a hashCode switch handles all methods in one place, avoiding per-method field overhead and thread-safety concerns.

### Decision 4: Interface proxy dispatch behavior

For interface proxies, `dispatch()` handles `Object.equals/hashCode/toString` with direct `super` calls (like class proxies). All other methods throw `AbstractMethodError` — consistent with the fact that interfaces have no super implementation. Interface `default` method support is deferred.

### Decision 5: Cache key design

Cache key is `{targetClass, filter}`. The `ClassLoader` is also part of the key hierarchy (for correct class visibility), following the `WeakCache<ClassLoader, CacheKey, Class<?>>` pattern from `java.lang.reflect.Proxy`. `null` filter means "intercept all methods" and is treated as a distinct cache entry.

## Risks / Trade-offs

- **Method.hashCode () collision** → Mitigation: Collision detection at bytecode generation time with secondary discriminator fallback. Extremely unlikely within a single class's method set.
- **Generated class count increase** → With caching (keyed by `{targetClass, filter}`), repeated `proxy()` calls for the same configuration reuse the same class. Different filter instances produce different classes — same behavior as before.
- **Breakage of existing users** → All public API types (`Callback`, `InterfaceCallback`, `create()`, `createInterface()`) are being removed. This is intentional — the design is a clean break, not a deprecation cycle. Migration path is documented in proposal.md.

## Migration Plan

1. Create new types (`Interceptor`, `DispatchTarget`) alongside old types
2. Create `DispatchGenerator` and `WeakCache`
3. Update generators (`ClassGenerator`, `InterfaceGenerator`, `MethodDispatcher`, `InterfaceDispatcher`)
4. Update `APS.java` with `proxy()` entry point
5. Remove old types (`Callback`, `InterfaceCallback`, `SuperDispatcher`, `HiddenClassLoader`)
6. Update tests and benchmarks to new API
7. Run full benchmark suite to verify performance improvements

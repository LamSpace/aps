## Context

`ClassGenerator.generateConstructor` already emits a generated subclass constructor that calls `super(args...)` and stores the interceptor fields; `AcceleratedProxy` threads `constructorArgs` through to the superclass constructor and caches generated classes in `WeakCache` keyed by a `CacheParams` record. The JVM constraint that shapes this feature: **`this` is unavailable until `super()` returns**, so a pre-`super()` hook cannot touch the proxy instance. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**

- A `ConstructorInterceptor` hook that runs `before` and `after` the superclass constructor for class proxies.
- `before` may rewrite arguments or veto; `after` is observational.
- Zero change to the non-intercepted constructor bytecode.

**Non-Goals:**

- Interface-proxy constructor interception (no superclass constructor to intercept).
- Factory-style control (replacing the created instance) or switching the superclass constructor overload from inside `before`.
- Constructor interception for the annotation-driven `intercept()` API.
- Hot-load/hot-replace of interception (separate roadmap item).

## Decisions

1. **Two-method interface with `java.lang.reflect.Constructor`.** `before`/`after` carry asymmetric capability (before can't see `this`, can rewrite/veto; after can see `this`, can't rewrite). Passing the reflected `Constructor` lets callers distinguish overloads; the lookup is a one-time `<clinit>` reflection, the same pattern `generateClinit` already uses for `Method` objects.

2. **`ConstructorInterceptor` instance passed via constructor, not baked into bytecode.** The generated class stays instance-agnostic, so caching is preserved exactly like the existing `Interceptor` instances. The cache key gains only `boolean ctorIntercept`, never the instance.

3. **`this`-before-`super()` via local slot + static `_ctor$` field.** The `ConstructorInterceptor` argument is read from its local-variable slot (a field store before `super()` is forbidden by the verifier); the `Constructor` object is a `static final` field populated in `<clinit>`.

4. **Checked exceptions from `before` wrapped in `UndeclaredThrowableException`.** The generated constructor cannot declare arbitrary checked exceptions, so the `before` call site emits the same try/catch shape `InterfaceDispatcher` uses (rethrow `RuntimeException`/`Error`, wrap `Exception`). `proxy()` unwraps `InvocationTargetException` and rethrows the cause so veto surfaces with fidelity.

5. **Constructor overload fixed at generation time.** The superclass constructor is selected from the initial argument types (existing `findConstructor`). `before` rewrites values, not the overload; the generated unboxing casts each rewritten element to the selected constructor's declared parameter type.

## Risks / Trade-offs

- **[Bytecode correctness] The unboxing path (rewritten `Object[]` → typed super args) is the highest-risk code.** → Mitigation: reuse `BytecodeUtils.boxPrimitive`/`unboxPrimitive`/`loadOpcode`; cover with tests across all primitive/boxed/null types.
- **[Regression] Any change to the shared `generateConstructor` could alter the non-intercepted path.** → Mitigation: the non-intercepted branch is copied verbatim; JMH guard asserts `proxy(target, interceptor)` numbers are unchanged.
- **[Veto fidelity] Unwrapping `InvocationTargetException` diverges from the existing "wrap everything in RuntimeException" behavior.** → Mitigation: scoped to the interception path only; checked exceptions still surface as `UndeclaredThrowableException`, so no `throws` clause is added to `proxy()`.
- **[Contract violation] `before` returning `null` or a wrong-length array fails with a raw NPE/`ArrayIndexOutOfBoundsException`.** → Mitigation: documented contract, no per-instance guard bytecode (avoids taxing the hot path for an excluded case).

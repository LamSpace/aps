## Why

Multi-interceptor configuration today requires programmatic `Group.of(MethodPredicate, Interceptor)` lambdas. This is explicit but verbose, and a project that reuses the same interception pattern across many proxy classes must repeat the `MethodPredicate` lambdas everywhere. This change adds a declarative, annotation-driven alternative that reduces boilerplate while compiling down to the exact same `Group` pipeline.

## What Changes

- Add `@Intercept` (type-level) and `@Around` (method-level) annotations with `RUNTIME` retention.
- Add `AcceleratedProxy.intercept(Class<T>, Object)` entry point that reflects over an `@Intercept`-annotated object and builds a `Group[]` from its `@Around` methods.
- `@Around` supports three orthogonal match dimensions — method-name glob (`value`/`glob`), method-name regex (`regex`), and method-annotation (`annotatedWith`) — AND-combined across dimensions, OR within each.
- `@Around` method contract: instance method, params exactly `(Object, Method, Object[])`, reference return type.
- Unmatched methods passthrough (direct super call), consistent with the programmatic `Group` API.
- Invalid configurations (missing `@Intercept`, no `@Around`, wrong signature, `static`/`void`/primitive, invalid regex) fail fast at `intercept()` time with `IllegalArgumentException`.
- No change to the existing proxy API, generators, cache, or hot path — the annotation-driven config and an equivalent hand-written `Group` config produce the same `MethodMapping` and share the same generated class.

## Capabilities

### New Capabilities

- `annotation-driven-api`: Declarative `@Intercept`/`@Around` method matching that compiles down to the existing `Group`/`Interceptor` pipeline.

### Modified Capabilities

<!-- none — the Group / multi-interceptor-grouping API is unchanged -->

## Impact

- **Code:** two new annotation types (`Intercept.java`, `Around.java`); new `intercept()` method plus private resolver/predicate/adapter helpers in `AcceleratedProxy.java`.
- **API:** new public types in `io.github.lamspace`; existing `proxy()` overloads and `Group`/`Interceptor`/`MethodPredicate` unchanged.
- **Performance:** zero impact on existing paths (byte-identical); annotation-driven steady-state ≈ hand-written lambda via a `LambdaMetafactory` adapter call site.
- **Tests/docs:** new `AnnotationDrivenApiTest`; new benchmark in `ProxyBenchmark`; roadmap + README updates.

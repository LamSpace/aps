## Why

Currently APS proxies route ALL intercepted methods through a single `Interceptor` instance. Users must write manual `if-else` chains inside `intercept()` to distinguish method families (getters vs setters vs business logic). This is boilerplate, error-prone, and forces heavyweight interceptors onto lightweight methods. CGLib's `CallbackFilter` + `Callback[]` proves the "method group → interceptor" pattern is both mature and useful — APS should offer a cleaner, zero-overhead version.

## What Changes

- **Add** `MethodPredicate` functional interface — replaces `ClassFilter` as the method matching primitive
- **Add** `Group` immutable type — binds a `MethodPredicate` to an `Interceptor` with `Group.of()` and `Group.otherwise()` factory methods
- **Add** `MethodMapping` internal type — compact method-to-interceptor index mapping for cache keys and bytecode generation
- **Add** new `AcceleratedProxy.proxy(Class<T>, Group...)` and `proxy(Class<T>, Object[], Group...)` overloads for multi-interceptor creation
- **Modify** `AcceleratedProxy` internals — Group chain matching (first-match-wins) executes before cache lookup; `CacheParams` key changes from `{targetClass, filter}` to `{targetClass, interceptors[], mapping}`
- **Modify** `ClassGenerator` and `InterfaceGenerator` — accept `Interceptor[]` + `MethodMapping` instead of `ClassFilter`; generate one instance field per distinct Interceptor (deduped by reference equality) and a constructor with M interceptor parameters
- **Modify** `MethodDispatcher` and `InterfaceDispatcher` — per-method overrides `GETFIELD` a group-specific field (`_interceptor$N`) instead of a single `_callback`; stable-sort methods for cross-JVM determinism
- **Remove** `ClassFilter` interface — fully subsumed by `MethodPredicate` + default passthrough semantics
- **BREAKING** — none. The old `proxy(Class<T>, Interceptor)` and `proxy(Class<T>, Interceptor, ClassFilter)` API delegates internally to the new Group model; existing code compiles and behaves identically

## Capabilities

### New Capabilities

- `multi-interceptor-grouping`: Method-group-based interceptor assignment where each method family (e.g., getters, setters) binds to a distinct `Interceptor` instance via `Group.of(predicate, interceptor)`. First-match-wins evaluation order, default passthrough for unmatched methods, duplicate-match warnings, and zero hot-path overhead (direct field access, no array indirection).

### Modified Capabilities

- `aps-unified-proxy`: The proxy class caching key changes from `{targetClass, filter}` to `{targetClass, interceptors[], mapping}`. The `ClassFilter` interface is removed; method filtering moves to `MethodPredicate` within `Group` declarations. `AcceleratedProxy.proxy()` internals perform Group chain matching before cache lookup.

## Impact

- **API surface**: `AcceleratedProxy` gains 2 new overloads; existing 3 overloads preserved (internal delegation)
- **Bytecode generation**: `ClassGenerator`, `InterfaceGenerator`, `MethodDispatcher`, `InterfaceDispatcher` refactored; `DispatchGenerator` unchanged
- **Caching**: `WeakCache` unchanged; `CacheParams` record redefined with new key fields
- **Tests**: 4 new test classes; all 5 existing test classes continue to pass without modification
- **Performance**: Zero degradation on hot path — `GETFIELD _interceptor$N` is bytecode-identical to current `GETFIELD _callback`
- **Dependencies**: No new external dependencies

## Context

See `proposal.md` for motivation. Relevant current state:

- `AcceleratedProxy.proxy(Class<T> target, ...)` accepts one target; `generateProxyClass` branches on `target.isInterface()` into `InterfaceGenerator` (implements one interface + `DispatchTarget`) or `ClassGenerator` (subclasses the target).
- `matchMethods` gathers one target's methods, stable-sorts by `(name, parameter types)`, and matches them against the `Group` chain, producing a `MethodMapping` (`indices[]` aligned with the sorted method order).
- `InterfaceGenerator`/`InterfaceDispatcher` independently re-sort `interfaceClass.getMethods()`; the dispatch `dispatch(Method, Object[])` is a hashCode switch where each `Method` maps to a handler (`INVOKESPECIAL Object` for Object methods, `INVOKESPECIAL <target-interface>` for default methods, `AbstractMethodError` otherwise).
- Proxy classes are cached by `WeakCache` keyed on `{targetClass, mapping, constructorArgs}`.

The binding constraint: the `MethodMapping.indices[]` produced by `matchMethods` must stay aligned with the method implementations emitted by `InterfaceDispatcher`. Today that alignment is achieved by both sides re-sorting `getMethods()` with the same comparator.

## Goals / Non-Goals

**Goals:**

- One generated proxy class implements N interfaces; single-interface (`N == 1`) produces a byte-identical class to today, so existing tests and JMH numbers are unaffected.
- One source of truth for the merged, sorted method list, so `matchMethods` and `InterfaceGenerator` can't drift.
- Merged methods dispatch correctly through either interface's `Method` object.

**Non-Goals:**

- Class + interface proxying (`extends` a class while also `implements` interfaces).
- Covariant return-type merging (differing return types always throw).
- "Most-specific-interface-wins" for default conflicts (two defaults always throw).

## Decisions

### Decision 1: Unify the interface path onto `Class<?>[]` internally

`InterfaceGenerator`, `matchMethods`, `generateProxyClass`, and the cache key all accept `Class<?>[]`; the existing single-interface public overloads wrap a length-1 array. The class path is left untouched — it differs semantically (`extends` vs `implements`, `LookupManager.getLookup` vs `MethodHandles.lookup`, super constructor args).

- **Alternative considered**: a separate `MultiInterfaceGenerator` parallel to `InterfaceGenerator`. Rejected — it duplicates the field/constructor/dispatch/clinit generation and leaves two code paths to keep in sync.
- **Alternative considered**: one `Class<?>[]` pipeline for both classes and interfaces. Rejected — classes and interfaces have irreconcilable generation semantics, so forcing them together adds an `isInterface` branch at every layer for no benefit.

### Decision 2: `InterfaceMethodResolver` is the single source of truth for the method set

A new `InterfaceMethodResolver.resolve(Class<?>[])` returns the merged, deduplicated, conflict-checked, sorted method list as `ResolvedMethod(canonical, owner, variants, defaultOwner)`. Both `AcceleratedProxy.matchMethods` and `InterfaceGenerator.generate` call it, which is what guarantees `mapping.indices[]` aligns with emitted methods — replacing today's fragile "two independent re-sorts."

- `canonical` — first `Method` object for a signature (generated implementation + `intercept` argument).
- `owner` — the array interface that yielded `canonical` (the `<clinit>` `getMethod` target, preserving `N == 1` bytecode for inherited methods).
- `variants` — all distinct `Method` objects for the signature (dispatch hash routing).
- `defaultOwner` — the array interface holding the `default`, or `null` (the `INVOKESPECIAL` owner).

- **Alternative considered**: recompute the merge in both `matchMethods` and `InterfaceGenerator`. Rejected — it re-introduces the drift risk the resolver exists to eliminate.

### Decision 3: Per-method default owner + per-variant dispatch routing

`MethodInfo` gains a `Class<?> defaultOwner` component (null for class proxies). `DispatchGenerator.generateDispatch` drops its single `interfaceInternalName` parameter and, for the interface branch, emits `INVOKESPECIAL <defaultOwner>.<method>` (ASM `itf = true`) when `defaultOwner != null`, else `AbstractMethodError`.

For a merged method, `generate()` emits one dispatch branch per `variant` `Method` object, each carrying the same `defaultOwner`. This routes every hash (A's `Method` and B's `Method` have different `Method.hashCode()` values because the declaring class differs) to the same handler. Since `INVOKESPECIAL` requires the named interface to be a direct superinterface, and every array interface is a direct superinterface of the generated class, `defaultOwner` (the array interface that exposed the default) is always valid — the same rule the existing default-method fast path relies on.

- **Alternative considered**: keying dispatch on name + parameter types instead of `Method.hashCode()`. Rejected — it would change the hot path for all existing proxies and break the `N == 1` byte-identity requirement.

### Decision 4: Conflict policy — merge the unambiguous, reject the ambiguous

Same signature + same return type merges. Same signature + differing return type throws `IllegalArgumentException`; two `default`s from distinct interfaces throw `IllegalArgumentException`; one `default` + one abstract merges with `invokeSuper` calling the default. See the spec's "Cross-interface conflict rejection" and "Super invocation on merged methods" requirements.

- **Alternative considered**: resolve covariant return types to the most-specific type. Rejected — rare, and it forces the generated method's return descriptor to the subtype plus an extra `CHECKCAST`, for little value.
- **Alternative considered**: most-specific-interface-wins for default conflicts. Rejected — requires interface-hierarchy analysis for an edge case; callers pass the more specific interface instead.

## Risks / Trade-offs

- **[`N == 1` byte-identity could regress]** → The refactor (Decision 1–3) must not change single-interface output; the full existing suite (including `DefaultMethodInvocationTest`) is the regression gate, plus a before/after JMH run as the benchmark gate.
- **[Multi-interface returns `Object`, losing generic type safety]** → Inherent to multiple interfaces (same as `java.lang.reflect.Proxy`); documented in the API and README.
- **[Creation-time cost grows]** → Merging/dedup/conflict detection runs at `proxy()` time, outside the JMH hot loop; negligible and not benchmarked.
- **[Pathological re-declared `Object` method across two interfaces]** → Not specially handled; the merged method follows the first interface in the array (same edge case as the existing single-interface path). Out of scope.

## Migration Plan

Additive: new overloads only, no existing signatures change. No migration or rollback steps required.

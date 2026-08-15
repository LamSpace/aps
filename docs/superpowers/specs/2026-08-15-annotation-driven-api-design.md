# APS Phase 3: Annotation-Driven API — Design Spec

**Date:** 2026-08-15 **Status:** Design approved **Phase:** 3 — Advanced Features

## 1. Motivation

Multi-interceptor configuration currently requires programmatic `Group` +
`MethodPredicate` lambdas:

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
        Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
        Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
        Group.otherwise(fallbackInterceptor));
```

This is explicit but verbose, and a project that reuses the same interception
pattern across many proxy classes must repeat the `MethodPredicate` lambdas
everywhere. This feature adds a declarative, annotation-driven alternative that
compiles down to the *same* `Group` pipeline — reducing boilerplate without
touching the generated-bytecode hot path.

**Success criteria**

1. `@Intercept` (type-level) and `@Around` (method-level) annotations exist with
   `RUNTIME` retention.
2. `AcceleratedProxy.intercept(Class<T>, Object)` produces a proxy whose matched
   methods route to the annotated methods; unmatched methods passthrough (same
   as the programmatic `Group` default).
3. `@Around` supports three orthogonal match dimensions — method-name glob
   (`value`/`glob`), method-name regex (`regex`), and method-annotation
   (`annotatedWith`) — AND-combined across dimensions, OR within each.
4. Annotation-driven config and an equivalent hand-written `Group` config produce
   the **same** `MethodMapping` (hence the same generated class and cache entry).
5. Steady-state call latency of annotation-driven ≈ programmatic (adapter is a
   `LambdaMetafactory` call site, not per-call reflection).
6. All invalid configurations fail fast at `intercept()` time with a clear
   `IllegalArgumentException`.
7. The existing proxy API and JMH numbers are byte-identical (zero regression).
8. Unit tests, an updated roadmap, and updated README documentation.

## 2. Design

### 2.1 Public API

Two new annotations and one new entry point, all in `io.github.lamspace`:

```java
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Intercept {}

@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Around {
    /** Single name-glob shorthand: @Around("get*") == @Around(glob = "get*"). */
    String value() default "";

    /** Method-name glob patterns, e.g. "get*", "*ById". Empty = no glob constraint. */
    String[] glob() default {};

    /** Method-name regex patterns, e.g. "get[A-Z].*". Empty = no regex constraint. */
    String[] regex() default {};

    /** Annotation types the target method must carry. Empty = no annotation constraint. */
    Class<? extends Annotation>[] annotatedWith() default {};
}
```

```java
public static <T> T intercept(Class<T> target, Object interceptor)
```

`intercept` reflects over `interceptor.getClass()`, builds a `Group[]` from its
`@Around` methods, and delegates to the existing `proxy(target, groups)`. Because
`proxy(Class<T>, Group...)` already routes both class and interface targets, no
special-casing is needed. There is no multi-interface `intercept(Class<?>[], ...)`
in v1 (see §8).

### 2.2 Resolution pipeline

`intercept()` performs, in order:

1. Validate `target`/`interceptor` non-null, and that `interceptor.getClass()` is
   annotated `@Intercept`.
2. Collect `@Around` methods from `interceptor.getClass().getDeclaredMethods()`,
   sorted by method name (then parameter types) for cross-JVM determinism —
   matching the determinism guarantee already made by `matchMethods`.
3. Validate each `@Around` method's signature (§2.3).
4. For each method, build a `Group.of(predicate, adapter)` (§2.4, §2.5).
5. Delegate to `proxy(target, groups)`.

Group order is the sorted `@Around` order, and the existing first-match-wins
semantics (with the duplicate-match warning) apply unchanged.

### 2.3 `@Around` method contract

Each `@Around` method must be an instance method with the exact
`Interceptor`-shaped signature and a reference return type:

```java
@Around("get*")
Object measure(Object proxy, Method method, Object[] args) { ... }
```

- Parameters must be exactly `(Object, Method, Object[])` — no narrower types, no
  fewer/more parameters.
- Return type must be a reference type (not `void`, not primitive); a subtype
  return is widened to `Object` by the adapter.

Anything else is rejected at `intercept()` time (§3). This fixed contract is what
lets the adapter be a zero-adaptation `LambdaMetafactory` call site.

### 2.4 Predicate construction

Each `@Around` maps to a `MethodPredicate` combining the three dimensions with
AND across dimensions and OR within each:

```
match(m) =
     (value=="" && glob empty)  ? true : (glob(value, m.name) || any(glob[], m.name))
  && (regex empty)               ? true : any(regex[], m.name)
  && (annotatedWith empty)       ? true : any(annotatedWith[] present on m)
```

- **Glob**: `*` → `.*`, `?` → `.`, everything else `Pattern.quote`d, then
  `String.matches` (whole-name match).
- **Regex**: `Pattern.compile(pattern).matcher(name).matches()` (whole-name
  match, not `find`).
- **annotatedWith**: `method.isAnnotationPresent(type)` — direct presence only,
  no meta-annotation / `@Inherited` traversal.

### 2.5 Adapter construction

The adapter binds the annotated instance method to the `Interceptor` SAM via
`LambdaMetafactory` (a single call site created at `intercept()` time), **not**
`Method.invoke`. Because the `@Around` method signature is already
`(Object, Method, Object[]) -> Object`, the metafactory needs no argument
dropping or adaptation; the JIT can inline the call site, giving steady-state
latency equivalent to a hand-written lambda.

Access is obtained through a `MethodHandles.Lookup` for the interceptor class
(`privateLookupIn`), mirroring the approach already used by
`internal.LookupManager` for class proxies. Public `@Around` methods always work;
non-public methods in a named module may require the module to be open, and a
lookup failure surfaces as a clear error pointing at `--add-opens` (consistent
with the JPMS roadmap item).

### 2.6 Files touched

| File                  | Change                                                                       |
|-----------------------|------------------------------------------------------------------------------|
| `Intercept.java`      | **New** — type-level marker annotation.                                      |
| `Around.java`         | **New** — method-level matching annotation.                                  |
| `AcceleratedProxy.java` | **New** `intercept(Class<T>, Object)`; private resolver/predicate/adapter helpers (alongside `matchMethods`). |

No change to `ClassGenerator`, `InterfaceGenerator`, any `Dispatcher`,
`MethodMapping`, `WeakCache`, `Interceptor`, `Group`, `MethodPredicate`,
`DispatchTarget`, or the public `invokeSuper` signature.

## 3. Error handling

All raised at `intercept()` time as `IllegalArgumentException` unless noted:

1. `target == null` or `interceptor == null`.
2. `interceptor.getClass()` not annotated `@Intercept`.
3. No `@Around`-annotated method declared.
4. An `@Around` method whose parameters are not exactly
   `(Object, Method, Object[])`.
5. An `@Around` method that is `static` (a static method has no receiver to bind
   to the interceptor instance).
6. An `@Around` method with a `void` or primitive return type.
7. An empty `regex` string, or a `regex` that fails to compile
   (`PatternSyntaxException` is wrapped, naming the pattern).
8. A lookup failure for the `@Around` method (non-public method in a closed
   named module) → `IllegalArgumentException` suggesting `--add-opens`.

## 4. Testing

New `src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java`.

| #  | Scenario                                              | Coverage                                                                 |
|----|-------------------------------------------------------|--------------------------------------------------------------------------|
| 1  | Single `@Around("get*")`                              | matched methods route to the annotated method; unmatched passthrough     |
| 2  | Multiple `@Around` methods, distinct patterns         | each routes to its own method; Group order is deterministic              |
| 3  | `@Around(glob = {"get*", "is*"})`                     | OR within the glob dimension                                             |
| 4  | `@Around(regex = "get[A-Z].*")`                       | regex dimension matches                                                  |
| 5  | `@Around(annotatedWith = Tx.class)`                   | annotation dimension; absent annotation → passthrough                    |
| 6  | Combined `glob` + `annotatedWith`                     | AND across dimensions                                                    |
| 7  | Adapter passes `(proxy, method, args)` correctly      | annotated method receives the right arguments; can call `invokeSuper`    |
| 8  | Subtype return type (e.g. `String` return)            | widened to `Object` and returned correctly                               |
| 9  | Interface target                                      | `intercept(Interface.class, instance)` works like `proxy(Interface, …)` |
| 10 | Class target with constructor args                    | class proxy path is unaffected                                           |
| 11 | Equivalence to programmatic `Group`                   | same `MethodMapping` and same call results                               |
| 12 | Negative: null target / null interceptor              | throws                                                                    |
| 13 | Negative: class not `@Intercept`                      | throws                                                                    |
| 14 | Negative: no `@Around` method                         | throws                                                                    |
| 15 | Negative: wrong parameter signature / `void` return   | throws                                                                    |
| 16 | Negative: `static` `@Around` method                   | throws                                                                    |
| 17 | Negative: empty or invalid regex                      | throws                                                                    |
| 18 | Overlapping patterns (two `@Around` match one method) | first-match-wins; warning path is exercised                              |

## 5. Benchmark

**Conclusion: no impact on existing numbers, and annotation-driven ≈
programmatic.** The generated proxy class, `dispatch()` hash switch, and
`INVOKESPECIAL` fast path are identical — `intercept()` differs from a
hand-written `Group` config only in *how the `Group[]` is produced*, and the
resulting `MethodMapping` is the same, so the two hit the same `WeakCache` entry
and the same bytecode. The one-time resolution cost (reflection + metafactory +
`Group` construction) runs at `intercept()` call time, outside any measured loop.

The adapter's per-call path is `GETFIELD interceptor → INVOKEINTERFACE intercept`,
where the interceptor is the `LambdaMetafactory` call site — no
`Method.invoke`, so it inlines like a hand-written lambda.

Guard (procedural, not theoretical): run the existing JMH suite before and after
and require class- and interface-proxy numbers unchanged. Add a new
"annotation-driven vs. equivalent programmatic Group" pair (same target, same
matching) and require parity within the established noise band (≈±1–5%, per the
existing single-fork measurement range).

## 6. Documentation changes

- `docs/aps-future-roadmap.md`: mark Phase 3 item 3 **注解驱动 API** as 已完成
  and add a `### 注解驱动 API（已完成）` subsection (API example + the three match
  dimensions + the fixed `@Around` signature).
- `README.md` and `README_CN.md`: add an "Annotation-Driven API" feature bullet
  and a Quick Start example.

## 7. Deliberate decisions

1. **Thin sugar layer, not generator integration.** The adapter is a
   `LambdaMetafactory` call site, so annotation-driven interception already
   reaches hand-written-lambda parity; inlining annotated bodies into generated
   bytecode would buy nothing and would couple the feature to the generator.
2. **Fixed `@Around` signature.** No flexible argument omission (`(args)`, `()`,
   narrower first param) — that would require per-method signature validation
   plus argument-dropping adaptation for marginal ergonomic gain.
3. **Reference return types only.** Primitive returns are rejected rather than
   auto-boxed, keeping the metafactory adaptation-free.
4. **Three dimensions, AND-combined.** glob/regex/annotatedWith coexist and
   combine with AND-across / OR-within, per the agreed scope; an AspectJ-style
   pointcut language is out of scope.
5. **No meta-annotation / `@Inherited` traversal.** `annotatedWith` checks direct
   presence only.

## 8. Out of scope

- AspectJ-style pointcut expressions (parameter/return-type predicates, logical
  combinators).
- Multi-interface `intercept(Class<?>[], ...)`.
- Composing annotation-driven groups with hand-written `Group`s in a single call.
- Flexible `@Around` argument signatures and primitive-return boxing.
- Memoizing the reflection result across `intercept()` calls (the expensive part
  — bytecode generation — is already cached by `WeakCache`).

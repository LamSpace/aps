## Purpose

Let proxy creators declare interception rules as annotations on an interceptor object instead of writing programmatic `Group.of(MethodPredicate, Interceptor)` lambdas, so the same rule set can be reused across many proxy classes.

## Requirements

### Requirement: Annotation-driven interception entry point

The system SHALL provide `OpenProxy.intercept(Class<T> target, Object interceptor)` that creates a proxy by reflecting over the `@Around`-annotated methods of `interceptor` and delegating to the existing `proxy(Class<T>, Group...)` pipeline.

#### Scenario: Intercept a class with an annotated interceptor

- **WHEN** the user calls `OpenProxy.intercept(Greeter.class, interceptor)` where `interceptor` is an instance of a class annotated `@Intercept` with an `@Around("get*")` method
- **THEN** the returned proxy routes methods matching `get*` through that `@Around` method
- **AND** methods not matching `get*` pass through to the superclass directly

#### Scenario: Intercept an interface

- **WHEN** the user calls `OpenProxy.intercept(SomeInterface.class, interceptor)`
- **THEN** the system produces an interface proxy with the same matching behavior as `proxy(SomeInterface.class, groups)`

### Requirement: Declarative method-name glob matching

The system SHALL support method-name glob matching via the `@Around` `value` and `glob` elements, where `*` matches any sequence of characters and `?` matches one character.

#### Scenario: Single glob shorthand

- **WHEN** an `@Around` method is annotated `@Around("get*")`
- **THEN** it matches target methods whose name starts with "get"

#### Scenario: Multiple globs are OR-combined

- **WHEN** an `@Around` method is annotated `@Around(glob = {"get*", "is*"})`
- **THEN** it matches target methods whose name starts with either "get" or "is"

### Requirement: Regex and method-annotation matching

The system SHALL support method-name regular-expression matching via the `@Around` `regex` element (whole-name match) and method-annotation matching via the `@Around` `annotatedWith` element (direct presence only).

#### Scenario: Regex matches the method name

- **WHEN** an `@Around` method is annotated `@Around(regex = "get[A-Z].*")`
- **THEN** it matches methods named `getGreeting` but not `get123`

#### Scenario: annotatedWith matches annotated methods only

- **WHEN** an `@Around` method is annotated `@Around(annotatedWith = Tx.class)`
- **THEN** it matches target methods directly annotated with `@Tx`
- **AND** methods without `@Tx` are not matched

### Requirement: Dimension combination semantics

The system SHALL AND-combine the glob, regex, and `annotatedWith` dimensions of a single `@Around` annotation, and OR-combine multiple values within a single dimension. An empty dimension imposes no constraint.

#### Scenario: Dimensions AND-combine

- **WHEN** an `@Around` method is annotated `@Around(value = "get*", annotatedWith = Tx.class)`
- **THEN** it matches a method only when the method name starts with "get" AND the method carries `@Tx`
- **AND** a method named `getPlain` without `@Tx` is not matched

### Requirement: `@Around` method contract

Each `@Around` method SHALL be a non-static instance method whose parameter types are exactly `(Object, Method, Object[])` and whose return type is a reference type (not `void` or primitive).

#### Scenario: Adapter receives proxy, method, and arguments

- **WHEN** an intercepted method is invoked on the proxy
- **THEN** the `@Around` method receives the proxy instance, the `Method`, and the boxed arguments
- **AND** may call `OpenProxy.invokeSuper(proxy, method, args)` to invoke the original method

#### Scenario: Subtype return is widened

- **WHEN** an `@Around` method declares a reference return type such as `String`
- **THEN** its return value is returned to the caller and widened to `Object` at the interceptor boundary

### Requirement: Fail-fast validation

The system SHALL reject invalid configurations at `intercept()` time with `IllegalArgumentException`.

#### Scenario: Null target or interceptor

- **WHEN** the user calls `intercept(null, interceptor)` or `intercept(Target.class, null)`
- **THEN** the system throws `IllegalArgumentException`

#### Scenario: Interceptor class not annotated

- **WHEN** the `interceptor` object's class is not annotated `@Intercept`
- **THEN** the system throws `IllegalArgumentException`

#### Scenario: No @Around method

- **WHEN** the `interceptor` object's class declares no `@Around`-annotated method
- **THEN** the system throws `IllegalArgumentException`

#### Scenario: Invalid @Around signature

- **WHEN** an `@Around` method is `static`, has parameters other than `(Object, Method, Object[])`, or has a `void` or primitive return type
- **THEN** the system throws `IllegalArgumentException`

#### Scenario: Invalid regex

- **WHEN** an `@Around` `regex` value is empty or fails to compile
- **THEN** the system throws `IllegalArgumentException`

### Requirement: Equivalence with programmatic Group API

An annotation-driven configuration and an equivalent hand-written `Group` configuration SHALL produce the same `MethodMapping` and therefore the same generated proxy class.

#### Scenario: Same generated class

- **WHEN** the user creates one proxy via `intercept(Target.class, interceptor)` with `@Around("get*")`
- **AND** another proxy via `proxy(Target.class, Group.of(m -> m.getName().startsWith("get"), ...))`
- **THEN** both proxies are instances of the same generated class

### Requirement: Deterministic matching order

When an interceptor class declares multiple `@Around` methods, the system SHALL evaluate them in a deterministic name-sorted order with first-match-wins semantics.

#### Scenario: Overlapping patterns use deterministic order

- **WHEN** an interceptor class declares an `@Around("get*")` method and an `@Around("getGreeting")` method
- **THEN** a `getGreeting` call is routed to the method whose name sorts first, regardless of declaration order in source

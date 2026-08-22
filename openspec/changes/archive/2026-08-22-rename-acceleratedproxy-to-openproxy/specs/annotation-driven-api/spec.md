## MODIFIED Requirements

### Requirement: Annotation-driven interception entry point

The system SHALL provide `OpenProxy.intercept(Class<T> target, Object interceptor)` that creates a proxy by reflecting over the `@Around`-annotated methods of `interceptor` and delegating to the existing `proxy(Class<T>, Group...)` pipeline.

#### Scenario: Intercept a class with an annotated interceptor

- **WHEN** the user calls `OpenProxy.intercept(Greeter.class, interceptor)` where `interceptor` is an instance of a class annotated `@Intercept` with an `@Around("get*")` method
- **THEN** the returned proxy routes methods matching `get*` through that `@Around` method
- **AND** methods not matching `get*` pass through to the superclass directly

#### Scenario: Intercept an interface

- **WHEN** the user calls `OpenProxy.intercept(SomeInterface.class, interceptor)`
- **THEN** the system produces an interface proxy with the same matching behavior as `proxy(SomeInterface.class, groups)`

### Requirement: `@Around` method contract

Each `@Around` method SHALL be a non-static instance method whose parameter types are exactly `(Object, Method, Object[])` and whose return type is a reference type (not `void` or primitive).

#### Scenario: Adapter receives proxy, method, and arguments

- **WHEN** an intercepted method is invoked on the proxy
- **THEN** the `@Around` method receives the proxy instance, the `Method`, and the boxed arguments
- **AND** may call `OpenProxy.invokeSuper(proxy, method, args)` to invoke the original method

#### Scenario: Subtype return is widened

- **WHEN** an `@Around` method declares a reference return type such as `String`
- **THEN** its return value is returned to the caller and widened to `Object` at the interceptor boundary

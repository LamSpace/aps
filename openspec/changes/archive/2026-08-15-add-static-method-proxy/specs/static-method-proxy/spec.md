## Purpose

Lets users route a target class's public static methods through an interceptor by returning a generated proxy class whose static methods shadow them, for reflective or `MethodHandle` invocation.

## ADDED Requirements

### Requirement: Static proxy entry point

The system SHALL provide `AcceleratedProxy.proxyStatic(Class<?> target, Group... groups)` returning a generated proxy `Class<?>`, and a convenience overload `proxyStatic(Class<?> target, Interceptor interceptor)`.

#### Scenario: Convenience overload

- **WHEN** the user calls `proxyStatic(target, interceptor)`
- **THEN** it behaves identically to `proxyStatic(target, Group.otherwise(interceptor))`

#### Scenario: Null or empty arguments rejected

- **WHEN** the user passes a null target, a null interceptor, or null/empty groups
- **THEN** the system throws `IllegalArgumentException`

#### Scenario: Interface target rejected

- **WHEN** the user passes an interface as `target`
- **THEN** the system throws `IllegalArgumentException`

### Requirement: Shadow public static methods

The returned class SHALL declare one `public static` method for each `public static`, non-`final` method declared by the target or inherited from its superclasses; when a subclass redeclares an inherited static method, the subclass declaration SHALL take precedence.

#### Scenario: Declared method shadowed

- **WHEN** the target declares a public static method `add(int, int)`
- **THEN** the returned class declares a shadow `add(int, int)` reachable via `getMethod("add", int.class, int.class)`

#### Scenario: Inherited method shadowed

- **WHEN** the target inherits a public static method `inherited()` from a superclass
- **THEN** the returned class declares a shadow `inherited()` whose original is the superclass method

#### Scenario: Redeclared method shadows parent

- **WHEN** the target redeclares an inherited public static method
- **THEN** the returned class's shadow routes to the target's own declaration, not the parent's

#### Scenario: Final and private static methods not shadowed

- **WHEN** the target declares a `final` static method or a `private` static method
- **THEN** the returned class does not declare a shadow for it (`getMethod` throws `NoSuchMethodException`)

### Requirement: Interception with null proxy

A shadow whose method matches a `Group` SHALL invoke the interceptor with a null proxy argument, the target's reflected `Method`, and the boxed arguments.

#### Scenario: Interceptor receives null proxy and correct metadata

- **WHEN** a matched shadow is invoked reflectively
- **THEN** the interceptor's `proxy` argument is null, the `method` argument has the shadowed method's name, and the `args` argument holds the boxed invocation arguments

### Requirement: Call the original static method

The interceptor SHALL be able to invoke the original static method by calling `method.invoke(null, args)` on the `Method` it receives, and that call SHALL return the original method's result.

#### Scenario: Original invoked via method.invoke

- **WHEN** the interceptor calls `method.invoke(null, args)` and returns the result
- **THEN** the shadowed call returns the original static method's result

### Requirement: Passthrough for unmatched methods

A shadow whose method matches no `Group` SHALL invoke the original static method directly and return its result without invoking any interceptor.

#### Scenario: Unmatched method passes through

- **WHEN** a shadow's method matches no `Group` and is invoked
- **THEN** the original static method runs, its result is returned, and no interceptor is called

### Requirement: Return types and overloads

Shadows SHALL return `void`, primitive, and reference types correctly, and distinct overloads SHALL be distinguished by parameter types.

#### Scenario: Return type round-trip

- **WHEN** shadows returning `void`, `int`, `long`, `double`, `boolean`, and a reference type are invoked
- **THEN** `void` returns null and each other type returns the correct boxed value

#### Scenario: Overload dispatch

- **WHEN** the target has overloaded static methods with the same name but different parameter types
- **THEN** invoking each overload on the returned class dispatches to the matching original

### Requirement: Exception propagation

A `RuntimeException` or `Error` thrown by the interceptor SHALL propagate to the caller; a checked exception thrown by the interceptor SHALL surface as `UndeclaredThrowableException`.

#### Scenario: RuntimeException propagates

- **WHEN** the interceptor throws an `IllegalStateException`
- **THEN** the reflective invocation throws `InvocationTargetException` whose cause is that `IllegalStateException`

#### Scenario: Checked exception wrapped

- **WHEN** the interceptor throws a checked `Exception`
- **THEN** the reflective invocation throws `InvocationTargetException` whose cause is an `UndeclaredThrowableException` wrapping that `Exception`

### Requirement: Distinct proxy classes

Each `proxyStatic` call SHALL return a distinct proxy class, independent of the interceptors bound by any other call.

#### Scenario: Two calls return distinct classes

- **WHEN** `proxyStatic` is called twice for the same target
- **THEN** the two returned `Class` objects are not the same, and each routes through its own interceptors

### Requirement: Non-regression

Creating proxies via the existing `proxy()` API SHALL be unaffected by this change.

#### Scenario: Existing proxy behavior unchanged

- **WHEN** the user creates an instance or interface proxy via the existing `proxy(...)` API
- **THEN** its behavior is identical to before this change

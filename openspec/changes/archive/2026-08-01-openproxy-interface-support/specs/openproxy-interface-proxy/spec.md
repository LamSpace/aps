## Purpose

Enable runtime proxy implementations of Java interfaces using MethodHandle-based dispatch, complementing the existing class proxy support and providing a reflection-free alternative to `java.lang.reflect.Proxy`.

## ADDED Requirements

### Requirement: Interface proxy creation

The system SHALL generate a runtime class that `extends Object` and `implements` the target interface, routing all non-static, non-final method calls through a user-provided `InterfaceCallback` handler.

#### Scenario: Basic interface proxy creation

- **WHEN** user calls `AcceleratedProxy.createInterface(TargetInterface.class, callback)`
- **THEN** system returns a proxy instance implementing `TargetInterface`
- **AND** any method call on the proxy invokes `callback.intercept(proxy, method, args)`
- **AND** the callback receives three arguments: the proxy instance, the `java.lang.reflect.Method`, and the boxed argument array
- **AND** no `superHandle` MethodHandle is provided (interface methods have no super implementation)

#### Scenario: Non-interface class rejected

- **WHEN** user calls `AcceleratedProxy.createInterface(SomeClass.class, callback)` where `SomeClass` is a concrete class
- **THEN** system throws `IllegalArgumentException` with a message indicating the class is not an interface

#### Scenario: Null arguments rejected

- **WHEN** user calls `AcceleratedProxy.createInterface(null, callback)` or `AcceleratedProxy.createInterface(TargetInterface.class, null)`
- **THEN** system throws `IllegalArgumentException`

### Requirement: Interface callback contract

The system SHALL use a dedicated `InterfaceCallback` functional interface distinct from the class-proxy `Callback`, reflecting the absence of a super-call MethodHandle parameter.

#### Scenario: InterfaceCallback signature

- **WHEN** user implements `InterfaceCallback.intercept(Object proxy, Method method, Object[] args)`
- **THEN** the proxy receives arguments in the `args` array with primitives boxed to their wrapper types
- **AND** the callback returns `Object` — boxed wrapper for primitives, `null` for void methods

### Requirement: All interface methods intercepted

The system SHALL intercept all public instance methods declared by the interface and its superinterfaces, including default methods. Static and final methods SHALL be excluded.

#### Scenario: Default method interception

- **WHEN** an interface declares a `default` method with an implementation
- **AND** a proxy is created for that interface
- **THEN** calls to the default method SHALL route through the callback rather than executing the default implementation

#### Scenario: Inherited Object methods intercepted

- **WHEN** a proxy is created for any interface
- **THEN** calls to non-final Object methods (toString, hashCode, equals) SHALL route through the callback

#### Scenario: Final Object methods excluded

- **WHEN** a proxy is created for any interface
- **THEN** final Object methods (getClass, notify, notifyAll, wait overloads) SHALL NOT be intercepted
- **AND** they execute their normal Object implementation

### Requirement: Method filtering for interfaces

The system SHALL support an optional `ClassFilter` that determines which methods pass through the callback. Methods not accepted by the filter SHALL throw `AbstractMethodError` when called — there is no super implementation to fall back to.

#### Scenario: Filtered method throws AbstractMethodError

- **WHEN** user creates a proxy with `AcceleratedProxy.createInterface(MultiMethod.class, callback, m -> m.getName().startsWith("get"))`
- **AND** a method not matching the filter is called
- **THEN** the method throws `AbstractMethodError`

#### Scenario: Unfiltered proxy intercepts all methods

- **WHEN** user creates a proxy with `AcceleratedProxy.createInterface(TargetInterface.class, callback)` (no filter)
- **THEN** all eligible interface methods are routed through the callback

### Requirement: Primitive type handling (interface)

The system SHALL correctly box primitive arguments into `Object[]` for callback delivery and unbox the `Object` return value back to the expected primitive type for interface proxies.

#### Scenario: Primitive argument boxing in interface callback

- **WHEN** an interface method with primitive parameters (e.g., `int add(int a, int b)`) is called on a proxy
- **THEN** the callback receives `args` containing `Integer` objects for each primitive parameter

#### Scenario: Primitive return unboxing in interface callback

- **WHEN** a callback returns an `Integer` for an interface method declared to return `int`
- **THEN** the caller receives the unboxed `int` value without `ClassCastException`

### Requirement: Exception propagation (interface)

The system SHALL propagate unchecked exceptions thrown by the callback directly to the caller. Checked exceptions SHALL be wrapped in `java.lang.reflect.UndeclaredThrowableException`.

#### Scenario: RuntimeException from interface callback

- **WHEN** `InterfaceCallback.intercept` throws a `RuntimeException`
- **THEN** the caller receives that exact `RuntimeException`

#### Scenario: Checked exception from interface callback

- **WHEN** `InterfaceCallback.intercept` throws a checked `Exception` not declared by the interface method
- **THEN** the caller receives an `UndeclaredThrowableException` wrapping the original exception

### Requirement: Hidden class loading (interface)

The system SHALL use `MethodHandles.Lookup.defineHiddenClass(byte[], true)` to load generated interface proxy classes, consistent with class-proxy behavior, avoiding custom ClassLoader usage and ensuring proxy classes are eligible for garbage collection.

#### Scenario: Interface proxy class is garbage collectable

- **WHEN** all references to an interface proxy instance and its class are dropped
- **THEN** the proxy class SHALL be eligible for GC without ClassLoader retention

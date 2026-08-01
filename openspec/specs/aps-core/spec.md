## Purpose

A high-performance, MethodHandle-based dynamic proxy engine for Java that proxies concrete classes at runtime, offering
a drop-in replacement for CGLib with better call-site performance.

## Requirements

### Requirement: Proxy class creation

The system SHALL generate a runtime subclass of any non-final concrete class and route all non-final instance method
calls through a user-provided single Callback handler.

#### Scenario: Basic proxy creation and interception

- **WHEN** user calls `APS.create(TargetClass.class, callback)`
- **THEN** system returns a proxy instance of type `TargetClass`
- **AND** any method call on the proxy invokes `callback.intercept(proxy, method, superHandle, args)`

#### Scenario: Super method invocation via MethodHandle

- **WHEN** callback calls `superHandle.invoke(args)`
- **THEN** the original superclass method executes with the provided arguments
- **AND** the return value is returned to the callback
- **AND** no `java.lang.reflect.Method.invoke` is used in the dispatch path

### Requirement: MethodHandle super-call binding

The system SHALL pre-compute and cache a `java.lang.invoke.MethodHandle` for each proxyable method, bound to the
superclass implementation using `MethodHandles.Lookup.findSpecial`.

#### Scenario: MethodHandle is available in callback

- **WHEN** a proxy method is invoked
- **THEN** the callback receives a non-null MethodHandle that can invoke the superclass method
- **AND** repeated calls to the same method on the same proxy instance reuse the same MethodHandle

### Requirement: Hidden class loading

The system SHALL use `MethodHandles.Lookup.defineHiddenClass(byte[], true)` to load generated proxy classes, avoiding
custom ClassLoader usage and ensuring proxy classes are eligible for garbage collection.

#### Scenario: Proxy class is garbage collectable

- **WHEN** all references to a proxy instance and its class are dropped
- **THEN** the proxy class SHALL be eligible for GC without ClassLoader retention

### Requirement: Method filtering

The system SHALL support an optional ClassFilter that determines which methods pass through the Callback. Methods not
accepted by the filter SHALL call the superclass implementation directly with zero interception overhead.

#### Scenario: Filtered method skips interception

- **WHEN** user creates a proxy with
  `APS.create(TargetClass.class, callback, method -> method.getName().startsWith("get"))`
- **AND** a method not matching the filter is called (e.g., `setValue`)
- **THEN** the method executes the superclass implementation directly without invoking the callback

#### Scenario: Unfiltered proxy intercepts all methods

- **WHEN** user creates a proxy with `APS.create(TargetClass.class, callback)` (no filter)
- **THEN** all non-final instance method calls are routed through the callback

### Requirement: Primitive type handling

The system SHALL correctly box primitive arguments into Object[] for callback delivery and unbox the Object return value
back to the expected primitive type.

#### Scenario: Primitive argument boxing

- **WHEN** a method with primitive parameters (e.g., `int add(int a, int b)`) is called on a proxy
- **THEN** the callback receives `args` containing `Integer` objects for each primitive parameter

#### Scenario: Primitive return unboxing

- **WHEN** a callback returns an `Integer` for a method declared to return `int`
- **THEN** the caller receives the unboxed `int` value without ClassCastException

### Requirement: Void method handling

The system SHALL handle methods with void return type by discarding the callback's return value.

#### Scenario: Void method invocation

- **WHEN** a void method is called on a proxy
- **THEN** the callback receives and executes the call
- **AND** the caller receives no return value regardless of what the callback returns

### Requirement: Exception propagation

The system SHALL propagate unchecked exceptions thrown by the callback directly to the caller. Checked exceptions SHALL
be wrapped in `java.lang.reflect.UndeclaredThrowableException`.

#### Scenario: RuntimeException from callback

- **WHEN** callback throws a RuntimeException
- **THEN** the caller receives that exact RuntimeException

#### Scenario: Checked exception from callback

- **WHEN** callback throws a checked Exception not declared by the intercepted method
- **THEN** the caller receives an UndeclaredThrowableException wrapping the original exception

### Requirement: Access control with graceful degradation

The system SHALL attempt to obtain full private access to the target class via `MethodHandles.privateLookupIn`. If the
target module is not open, the system SHALL fall back to a regular public Lookup without failing.

#### Scenario: Private access succeeds

- **WHEN** the target class's package is open for reflection
- **THEN** the system uses a full-access Lookup enabling `findSpecial` to all non-public methods

#### Scenario: Private access denied with fallback

- **WHEN** the target class's module does not open its package
- **THEN** the system falls back to `MethodHandles.lookup()` and continues operation with the available access level

### Requirement: No-default-constructor support

The system SHALL support proxying classes that lack a no-argument constructor by accepting constructor arguments at
proxy creation time.

#### Scenario: Proxy class with constructor arguments

- **WHEN** user calls `APS.create(BeanWithArgs.class, callback, null, "arg1", 42)`
- **THEN** the system finds a matching constructor on the target class
- **AND** generates a proxy constructor that delegates the arguments to super ()
- **AND** returns a properly initialized proxy instance

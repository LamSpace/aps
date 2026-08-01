## MODIFIED Requirements

### Requirement: Proxy class creation

The system SHALL generate a runtime subclass of any non-final concrete class and route all non-final instance method calls through a user-provided single Callback handler.

#### Scenario: Basic proxy creation and interception

- **WHEN** user calls `APS.create(TargetClass.class, callback)`
- **THEN** system returns a proxy instance of type `TargetClass`
- **AND** any method call on the proxy invokes `callback.intercept(proxy, method, index, args)`

#### Scenario: Super method invocation via index

- **WHEN** callback calls `((GeneratedProxy) proxy).invokeSuper(index, args)` or uses the helper method to invoke super
- **THEN** the original superclass method executes with the provided arguments
- **AND** the return value is returned to the callback
- **AND** no `java.lang.reflect.Method.invoke` is used in the dispatch path

### Requirement: MethodHandle super-call binding

The system SHALL pre-compute and cache a `java.lang.invoke.MethodHandle` for each proxyable method, bound to the superclass implementation using `MethodHandles.Lookup.findSpecial`, and store them in a static array indexed by method position.

#### Scenario: MethodHandle is available in callback

- **WHEN** a proxy method is invoked
- **THEN** the callback receives a method index that can be used with the proxy's `invokeSuper` method to call the superclass implementation
- **AND** repeated calls to the same method on the same proxy instance use the same index

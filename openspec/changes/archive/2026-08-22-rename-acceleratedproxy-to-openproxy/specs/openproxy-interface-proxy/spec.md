## MODIFIED Requirements

### Requirement: Interface proxy creation

The system SHALL generate a runtime class that `extends Object` and `implements` the target interface and `DispatchTarget`, routing all non-static, non-final method calls through a user-provided `Interceptor` handler.

#### Scenario: Basic interface proxy creation

- **WHEN** user calls `OpenProxy.proxy(TargetInterface.class, interceptor)`
- **THEN** system returns a proxy instance implementing `TargetInterface`
- **AND** any method call on the proxy invokes `interceptor.intercept(proxy, method, args)`
- **AND** the callback receives three arguments: the proxy instance, the `java.lang.reflect.Method`, and the boxed argument array

#### Scenario: Null arguments rejected

- **WHEN** user calls `OpenProxy.proxy(null, interceptor)` or `OpenProxy.proxy(TargetInterface.class, null)`
- **THEN** system throws `IllegalArgumentException`

### Requirement: Super invocation on interface proxy

The system SHALL implement `DispatchTarget.dispatch(Method, Object[])` on interface proxy classes. Non-default interface methods SHALL throw `AbstractMethodError`. Default interface methods SHALL invoke their default implementation. Object methods (`equals`, `hashCode`, `toString`) SHALL call `super` directly via `INVOKESPECIAL`.

#### Scenario: invokeSuper on non-default interface method throws

- **WHEN** user calls `OpenProxy.invokeSuper(interfaceProxy, method, args)` where `method` is a non-default interface method
- **THEN** system throws `AbstractMethodError`

#### Scenario: invokeSuper on directly-declared default method

- **WHEN** user calls `OpenProxy.invokeSuper(interfaceProxy, method, args)` where `method` is a default method declared directly on the target interface
- **THEN** the interface's default implementation executes and returns its result

#### Scenario: invokeSuper on inherited default method

- **WHEN** user calls `OpenProxy.invokeSuper(interfaceProxy, method, args)` where `method` is a default method inherited from a parent interface
- **THEN** the inherited default implementation executes and returns its result

#### Scenario: exception from default method propagates

- **WHEN** a default method invoked via `OpenProxy.invokeSuper` throws
- **THEN** the thrown exception propagates to the caller unchanged

#### Scenario: invokeSuper on Object method succeeds

- **WHEN** user calls `OpenProxy.invokeSuper(interfaceProxy, method, args)` where `method` is `toString`, `hashCode`, or `equals`
- **THEN** the corresponding `Object` method executes and returns the result

### Requirement: Method filtering for interfaces

The system SHALL support an optional `ClassFilter` that determines which methods pass through the callback. Methods not accepted by the filter SHALL throw `AbstractMethodError` when called — there is no super implementation to fall back to.

#### Scenario: Filtered method throws AbstractMethodError

- **WHEN** user creates a proxy with `OpenProxy.proxy(MultiMethod.class, callback, m -> m.getName().startsWith("get"))`
- **AND** a method not matching the filter is called
- **THEN** the method throws `AbstractMethodError`

#### Scenario: Unfiltered proxy intercepts all methods

- **WHEN** user creates a proxy with `OpenProxy.proxy(TargetInterface.class, callback)` (no filter)
- **THEN** all eligible interface methods are routed through the callback

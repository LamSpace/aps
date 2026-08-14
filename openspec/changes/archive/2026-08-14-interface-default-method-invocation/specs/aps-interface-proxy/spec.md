## MODIFIED Requirements

### Requirement: Super invocation on interface proxy

The system SHALL implement `DispatchTarget.dispatch(Method, Object[])` on interface proxy classes. Non-default interface methods SHALL throw `AbstractMethodError`. Default interface methods SHALL invoke their default implementation. Object methods (`equals`, `hashCode`, `toString`) SHALL call `super` directly via `INVOKESPECIAL`.

#### Scenario: invokeSuper on non-default interface method throws

- **WHEN** user calls `AcceleratedProxy.invokeSuper(interfaceProxy, method, args)` where `method` is a non-default interface method
- **THEN** system throws `AbstractMethodError`

#### Scenario: invokeSuper on directly-declared default method

- **WHEN** user calls `AcceleratedProxy.invokeSuper(interfaceProxy, method, args)` where `method` is a default method declared directly on the target interface
- **THEN** the interface's default implementation executes and returns its result

#### Scenario: invokeSuper on inherited default method

- **WHEN** user calls `AcceleratedProxy.invokeSuper(interfaceProxy, method, args)` where `method` is a default method inherited from a parent interface
- **THEN** the inherited default implementation executes and returns its result

#### Scenario: exception from default method propagates

- **WHEN** a default method invoked via `AcceleratedProxy.invokeSuper` throws
- **THEN** the thrown exception propagates to the caller unchanged

#### Scenario: invokeSuper on Object method succeeds

- **WHEN** user calls `AcceleratedProxy.invokeSuper(interfaceProxy, method, args)` where `method` is `toString`, `hashCode`, or `equals`
- **THEN** the corresponding `Object` method executes and returns the result

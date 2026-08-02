## MODIFIED Requirements

### Requirement: Interface proxy creation

The system SHALL generate a runtime class that `extends Object` and `implements` the target interface and `DispatchTarget`, routing all non-static, non-final method calls through a user-provided `Interceptor` handler.

#### Scenario: Basic interface proxy creation

- **WHEN** user calls `AcceleratedProxy.proxy(TargetInterface.class, interceptor)`
- **THEN** system returns a proxy instance implementing `TargetInterface`
- **AND** any method call on the proxy invokes `interceptor.intercept(proxy, method, args)`
- **AND** the callback receives three arguments: the proxy instance, the `java.lang.reflect.Method`, and the boxed argument array

#### Scenario: Non-interface class rejected

- **WHEN** user calls `AcceleratedProxy.proxy(SomeClass.class, interceptor)` where `SomeClass` is a concrete class
- **THEN** system treats it as a class proxy (extends SomeClass) rather than rejecting it

#### Scenario: Null arguments rejected

- **WHEN** user calls `AcceleratedProxy.proxy(null, interceptor)` or `AcceleratedProxy.proxy(TargetInterface.class, null)`
- **THEN** system throws `NullPointerException`

### Requirement: Interface callback contract

The system SHALL use the unified `Interceptor` functional interface (same as class proxies) with signature `Object intercept(Object proxy, Method method, Object[] args) throws Throwable`.

#### Scenario: Interceptor signature for interfaces

- **WHEN** user implements `Interceptor.intercept(Object proxy, Method method, Object[] args)`
- **THEN** the proxy receives arguments in the `args` array with primitives boxed to their wrapper types
- **AND** the callback returns `Object` — boxed wrapper for primitives, `null` for void methods

### Requirement: Super invocation on interface proxy

The system SHALL implement `DispatchTarget.dispatch(Method, Object[])` on interface proxy classes. Non-Object interface methods SHALL throw `AbstractMethodError`. Object methods (`equals`, `hashCode`, `toString`) SHALL call `super` directly via `INVOKESPECIAL`.

#### Scenario: invokeSuper on interface method throws

- **WHEN** user calls `AcceleratedProxy.invokeSuper(interfaceProxy, method, args)` where `method` is an interface method
- **THEN** system throws `AbstractMethodError`

#### Scenario: invokeSuper on Object method succeeds

- **WHEN** user calls `AcceleratedProxy.invokeSuper(interfaceProxy, method, args)` where `method` is `toString`, `hashCode`, or `equals`
- **THEN** the corresponding `Object` method executes and returns the result

## REMOVED Requirements

### Requirement: Interface callback contract (separate InterfaceCallback)

**Reason**: The separate `InterfaceCallback` interface is replaced by the unified `Interceptor` interface, which serves both class and interface proxies with the same signature.

**Migration**: Replace `InterfaceCallback` with `Interceptor`. The method signature `Object intercept(Object proxy, Method method, Object[] args)` is identical — only the interface name changes.

## Purpose

Enable runtime proxy implementations of Java interfaces using MethodHandle-based dispatch, complementing the existing class proxy support and providing a reflection-free alternative to `java.lang.reflect.Proxy`.

### Requirement: Interface proxy creation

The system SHALL generate a runtime class that `extends Object` and `implements` the target interface and `DispatchTarget`, routing all non-static, non-final method calls through a user-provided `Interceptor` handler.

#### Scenario: Basic interface proxy creation

- **WHEN** user calls `APS.proxy(TargetInterface.class, interceptor)`
- **THEN** system returns a proxy instance implementing `TargetInterface`
- **AND** any method call on the proxy invokes `interceptor.intercept(proxy, method, args)`
- **AND** the callback receives three arguments: the proxy instance, the `java.lang.reflect.Method`, and the boxed argument array

#### Scenario: Null arguments rejected

- **WHEN** user calls `APS.proxy(null, interceptor)` or `APS.proxy(TargetInterface.class, null)`
- **THEN** system throws `IllegalArgumentException`

### Requirement: Unified Interceptor callback

The system SHALL use the unified `Interceptor` functional interface (shared with class proxies) with signature `Object intercept(Object proxy, Method method, Object[] args) throws Throwable`.

#### Scenario: Interceptor signature for interfaces

- **WHEN** user implements `Interceptor.intercept(Object proxy, Method method, Object[] args)`
- **THEN** the proxy receives arguments in the `args` array with primitives boxed to their wrapper types
- **AND** the callback returns `Object` — boxed wrapper for primitives, `null` for void methods

### Requirement: Super invocation on interface proxy

The system SHALL implement `DispatchTarget.dispatch(Method, Object[])` on interface proxy classes. Non-Object interface methods SHALL throw `AbstractMethodError`. Object methods (`equals`, `hashCode`, `toString`) SHALL call `super` directly via `INVOKESPECIAL`.

#### Scenario: invokeSuper on interface method throws

- **WHEN** user calls `APS.invokeSuper(interfaceProxy, method, args)` where `method` is an interface method
- **THEN** system throws `AbstractMethodError`

#### Scenario: invokeSuper on Object method succeeds

- **WHEN** user calls `APS.invokeSuper(interfaceProxy, method, args)` where `method` is `toString`, `hashCode`, or `equals`
- **THEN** the corresponding `Object` method executes and returns the result

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

- **WHEN** user creates a proxy with `APS.proxy(MultiMethod.class, callback, m -> m.getName().startsWith("get"))`
- **AND** a method not matching the filter is called
- **THEN** the method throws `AbstractMethodError`

#### Scenario: Unfiltered proxy intercepts all methods

- **WHEN** user creates a proxy with `APS.proxy(TargetInterface.class, callback)` (no filter)
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

- **WHEN** `Interceptor.intercept` throws a `RuntimeException`
- **THEN** the caller receives that exact `RuntimeException`

#### Scenario: Checked exception from interface callback

- **WHEN** `Interceptor.intercept` throws a checked `Exception` not declared by the interface method
- **THEN** the caller receives an `UndeclaredThrowableException` wrapping the original exception

### Requirement: Hidden class loading (interface)

The system SHALL use `MethodHandles.Lookup.defineHiddenClass(byte[], true)` to load generated interface proxy classes, consistent with class-proxy behavior, avoiding custom ClassLoader usage and ensuring proxy classes are eligible for garbage collection.

#### Scenario: Interface proxy class is garbage collectable

- **WHEN** all references to an interface proxy instance and its class are dropped
- **THEN** the proxy class SHALL be eligible for GC without ClassLoader retention

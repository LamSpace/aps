## MODIFIED Requirements

### Requirement: Proxy class creation

The system SHALL generate a runtime subclass of any non-final concrete class and route all non-final instance method calls through a user-provided single `Interceptor` handler.

#### Scenario: Basic proxy creation and interception

- **WHEN** user calls `APS.proxy(TargetClass.class, interceptor)`
- **THEN** system returns a proxy instance of type `TargetClass`
- **AND** any method call on the proxy invokes `interceptor.intercept(proxy, method, args)`

#### Scenario: Super method invocation via invokeSuper

- **WHEN** callback calls `APS.invokeSuper(proxy, method, args)`
- **THEN** the original superclass method executes via direct `INVOKESPECIAL` (no reflection, no MethodHandle)
- **AND** the return value is returned to the callback

### Requirement: HashCode-based super-call dispatch

The system SHALL generate a `dispatch(Method, Object[])` method with a hashCode-driven if-else chain that routes each method to its corresponding `super.method(args)` call. No `MethodHandle[]` array or `MethodHandle.invoke()` is used.

#### Scenario: Super call is direct INVOKESPECIAL

- **WHEN** `dispatch(method, args)` is invoked
- **THEN** the system computes `method.hashCode()` and matches the pre-computed hash constant embedded as an `ldc` bytecode instruction
- **AND** the matched branch calls `super.method(args)` directly with type-specific parameter unboxing
- **AND** repeated super calls to the same method reuse the same direct call site

### Requirement: Hidden class loading

The system SHALL use a single code path based on `LookupManager.getLookup(target).defineHiddenClass(bytecode, true)` for loading both class and interface proxy bytecode types, avoiding custom ClassLoader usage and ensuring proxy classes are eligible for garbage collection.

#### Scenario: Proxy class is garbage collectable

- **WHEN** all references to a proxy instance and its class are dropped
- **THEN** the proxy class SHALL be eligible for GC without ClassLoader retention

### Requirement: Method filtering

The system SHALL support an optional `ClassFilter` that determines which methods pass through the `Interceptor`. Methods not accepted by the filter SHALL call the superclass implementation directly with zero interception overhead.

#### Scenario: Filtered method skips interception

- **WHEN** user creates a proxy with `APS.proxy(TargetClass.class, interceptor, method -> method.getName().startsWith("get"))`
- **AND** a method not matching the filter is called (e.g., `setValue`)
- **THEN** the method executes the superclass implementation directly without invoking the interceptor

#### Scenario: Unfiltered proxy intercepts all methods

- **WHEN** user creates a proxy with `APS.proxy(TargetClass.class, interceptor)` (no filter)
- **THEN** all non-final instance method calls are routed through the interceptor

### Requirement: Proxy class caching

The system SHALL cache generated proxy classes keyed by `{targetClass, filter}` to avoid re-generating bytecode for the same proxy configuration.

#### Scenario: Repeated proxy creation reuses class

- **WHEN** user calls `APS.proxy(SomeClass.class, interceptor)` twice
- **THEN** the system reuses the previously generated proxy class

## REMOVED Requirements

### Requirement: MethodHandle super-call binding

**Reason**: Replaced by hashCode-based `dispatch()` method that uses direct `INVOKESPECIAL` super calls instead of pre-computed `MethodHandle[]` array with type erasure. This eliminates the MethodHandle dispatch overhead entirely.

**Migration**: No user-facing migration required. The `Interceptor` callback no longer receives a MethodHandle — use `APS.invokeSuper(proxy, method, args)` instead.

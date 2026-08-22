## MODIFIED Requirements

### Requirement: Unified proxy creation entry point

The system SHALL provide a single `OpenProxy.proxy(Class<T> target, Interceptor interceptor)` method that auto-detects whether `target` is a class or interface and generates the appropriate proxy type.

#### Scenario: Class proxy via unified entry

- **WHEN** user calls `OpenProxy.proxy(TargetClass.class, interceptor)`
- **THEN** system returns a proxy instance that extends `TargetClass` and implements `DispatchTarget`
- **AND** all non-final instance method calls are routed through `interceptor.intercept(proxy, method, args)`

#### Scenario: Interface proxy via unified entry

- **WHEN** user calls `OpenProxy.proxy(TargetInterface.class, interceptor)`
- **THEN** system returns a proxy instance that implements `TargetInterface` and `DispatchTarget`
- **AND** all interface method calls are routed through `interceptor.intercept(proxy, method, args)`

#### Scenario: Null target rejected

- **WHEN** user calls `OpenProxy.proxy(null, interceptor)`
- **THEN** system throws `NullPointerException`

#### Scenario: Null interceptor rejected

- **WHEN** user calls `OpenProxy.proxy(TargetClass.class, null)`
- **THEN** system throws `NullPointerException`

### Requirement: Method-based super invocation

The system SHALL provide `OpenProxy.invokeSuper(Object proxy, Method method, Object[] args)` that dispatches to the original superclass method through a hashCode-driven switch.

#### Scenario: Invoke super on class method

- **WHEN** user calls `OpenProxy.invokeSuper(proxy, method, args)` on a class proxy
- **AND** `method` corresponds to a method inherited from the target class
- **THEN** the superclass method executes via direct `INVOKESPECIAL` (no reflection, no MethodHandle)
- **AND** the result is returned to the caller

#### Scenario: Invoke super on interface method throws error

- **WHEN** user calls `OpenProxy.invokeSuper(proxy, method, args)` on an interface proxy
- **AND** `method` corresponds to a non-Object interface method
- **THEN** system throws `AbstractMethodError`

#### Scenario: Invoke super on Object method from interface proxy

- **WHEN** user calls `OpenProxy.invokeSuper(proxy, method, args)` on an interface proxy
- **AND** `method` is `equals`, `hashCode`, or `toString` from `java.lang.Object`
- **THEN** the Object method executes via direct `INVOKESPECIAL`

### Requirement: Proxy class caching

The system SHALL cache generated proxy classes keyed by the `{targetClass, mapping, constructorArgs}` tuple to avoid re-generating bytecode for the same proxy configuration. The `mapping` captures the method-to-interceptor-index assignment produced by Group chain matching.

#### Scenario: Cache hit returns existing class

- **WHEN** user calls `OpenProxy.proxy(SomeClass.class, group1, group2)` twice with identical Group configurations and constructor arguments
- **THEN** the system reuses the previously generated proxy class instead of generating new bytecode
- **AND** both proxy instances use the same class

#### Scenario: Different group configs produce different classes

- **WHEN** user creates two proxies for the same class with different Group declarations (different predicates, different interceptors, or different declaration order)
- **THEN** the system generates two distinct proxy classes

#### Scenario: Different constructor args produce different classes

- **WHEN** user creates two class proxies with the same Group declarations but different constructor arguments
- **THEN** the system generates two distinct proxy classes

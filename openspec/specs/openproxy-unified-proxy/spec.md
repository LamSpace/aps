## Purpose

Unified dynamic proxy API that creates proxies for both concrete classes and Java interfaces through a single entry point (`AcceleratedProxy.proxy()`) with a single `Interceptor` callback interface, using hashCode-based super-method dispatch for class proxies.

## ADDED Requirements

### Requirement: Unified proxy creation entry point

The system SHALL provide a single `AcceleratedProxy.proxy(Class<T> target, Interceptor interceptor)` method that auto-detects whether `target` is a class or interface and generates the appropriate proxy type.

#### Scenario: Class proxy via unified entry

- **WHEN** user calls `AcceleratedProxy.proxy(TargetClass.class, interceptor)`
- **THEN** system returns a proxy instance that extends `TargetClass` and implements `DispatchTarget`
- **AND** all non-final instance method calls are routed through `interceptor.intercept(proxy, method, args)`

#### Scenario: Interface proxy via unified entry

- **WHEN** user calls `AcceleratedProxy.proxy(TargetInterface.class, interceptor)`
- **THEN** system returns a proxy instance that implements `TargetInterface` and `DispatchTarget`
- **AND** all interface method calls are routed through `interceptor.intercept(proxy, method, args)`

#### Scenario: Null target rejected

- **WHEN** user calls `AcceleratedProxy.proxy(null, interceptor)`
- **THEN** system throws `NullPointerException`

#### Scenario: Null interceptor rejected

- **WHEN** user calls `AcceleratedProxy.proxy(TargetClass.class, null)`
- **THEN** system throws `NullPointerException`

### Requirement: Unified Interceptor callback

The system SHALL use a single `Interceptor` functional interface for both class and interface proxies with signature `Object intercept(Object proxy, Method method, Object[] args) throws Throwable`.

#### Scenario: Interceptor signature

- **WHEN** user implements `Interceptor.intercept(Object proxy, Method method, Object[] args)`
- **THEN** `proxy` is the proxy instance, `method` is the `java.lang.reflect.Method` being intercepted, and `args` is the boxed argument array
- **AND** the callback returns `Object` — boxed wrapper for primitive return types, `null` for void

#### Scenario: No index parameter

- **WHEN** a proxy method is invoked
- **THEN** the Interceptor receives exactly three arguments (proxy, method, args)
- **AND** method identification for super-call dispatch uses `Method.hashCode()` internally, not an explicit index

### Requirement: Method-based super invocation

The system SHALL provide `AcceleratedProxy.invokeSuper(Object proxy, Method method, Object[] args)` that dispatches to the original superclass method through a hashCode-driven switch.

#### Scenario: Invoke super on class method

- **WHEN** user calls `AcceleratedProxy.invokeSuper(proxy, method, args)` on a class proxy
- **AND** `method` corresponds to a method inherited from the target class
- **THEN** the superclass method executes via direct `INVOKESPECIAL` (no reflection, no MethodHandle)
- **AND** the result is returned to the caller

#### Scenario: Invoke super on interface method throws error

- **WHEN** user calls `AcceleratedProxy.invokeSuper(proxy, method, args)` on an interface proxy
- **AND** `method` corresponds to a non-Object interface method
- **THEN** system throws `AbstractMethodError`

#### Scenario: Invoke super on Object method from interface proxy

- **WHEN** user calls `AcceleratedProxy.invokeSuper(proxy, method, args)` on an interface proxy
- **AND** `method` is `equals`, `hashCode`, or `toString` from `java.lang.Object`
- **THEN** the Object method executes via direct `INVOKESPECIAL`

### Requirement: HashCode-based dispatch

The system SHALL generate a `dispatch(Method, Object[])` method in every proxy class that uses a hashCode-driven if-else chain to route super-method invocations, replacing the previous `MethodHandle[]` array approach.

#### Scenario: Direct super call via hashCode switch

- **WHEN** `dispatch(method, args)` is called on a class proxy
- **THEN** the system computes `method.hashCode()` and routes to the matching branch
- **AND** each branch calls `super.method(args)` directly (INVOKESPECIAL) with type-specific parameter unboxing
- **AND** no `MethodHandle.invoke()`, `Method.invoke()`, or array indexing is used in the hot path

#### Scenario: Hash collision detection at generation time

- **WHEN** two methods produce the same `Method.hashCode()` value during bytecode generation
- **THEN** the system SHALL detect the collision and append a secondary discriminator to the dispatch chain
- **AND** the generated class compiles and dispatches correctly for both methods

#### Scenario: Overloaded methods dispatch correctly

- **WHEN** the target class declares overloaded methods with the same name but different parameter types (e.g., `void foo(String)` and `void foo(int)`)
- **THEN** the secondary discriminator SHALL produce distinct dispatch hashes for each overload
- **AND** calling `dispatch(method, args)` with the `Method` object for each overload SHALL route to the correct branch
- **AND** each branch invokes the correct superclass method with the correct parameter types

### Requirement: Proxy class caching

The system SHALL cache generated proxy classes keyed by the `{targetClass, mapping, constructorArgs}` tuple to avoid re-generating bytecode for the same proxy configuration. The `mapping` captures the method-to-interceptor-index assignment produced by Group chain matching.

#### Scenario: Cache hit returns existing class

- **WHEN** user calls `AcceleratedProxy.proxy(SomeClass.class, group1, group2)` twice with identical Group configurations and constructor arguments
- **THEN** the system reuses the previously generated proxy class instead of generating new bytecode
- **AND** both proxy instances use the same class

#### Scenario: Different group configs produce different classes

- **WHEN** user creates two proxies for the same class with different Group declarations (different predicates, different interceptors, or different declaration order)
- **THEN** the system generates two distinct proxy classes

#### Scenario: Different constructor args produce different classes

- **WHEN** user creates two class proxies with the same Group declarations but different constructor arguments
- **THEN** the system generates two distinct proxy classes

### Requirement: Unified hidden class loading

The system SHALL use a single code path for loading both class and interface proxy bytecode via `Lookup.defineHiddenClass()`.

#### Scenario: Both proxy types use defineHiddenClass

- **WHEN** a proxy class is generated (for either class or interface)
- **THEN** the bytecode is loaded via `Lookup.defineHiddenClass(bytecode, true)`
- **AND** no custom ClassLoader is used

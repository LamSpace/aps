## Purpose

Provide runtime proxy classes that implement multiple Java interfaces at once, merging shared method signatures and rejecting ambiguous cross-interface conflicts.

### Requirement: Multi-interface proxy creation

The system SHALL generate a single runtime proxy class that implements all given interfaces. `AcceleratedProxy.proxy(Class<?>[] interfaces, Interceptor)` and `AcceleratedProxy.proxy(Class<?>[] interfaces, Group...)` SHALL return an `Object` that can be cast to each interface in `interfaces`, with every non-static, non-final method call routed through the interceptor.

#### Scenario: Proxy usable through each interface

- **WHEN** user calls `proxy(new Class<?>[]{A.class, B.class}, interceptor)` and casts the result to `A` and to `B`
- **THEN** both casts succeed and reference the same object
- **AND** method calls through either interface invoke `interceptor.intercept(proxy, method, args)`

#### Scenario: Shared signature intercepted once

- **WHEN** two interfaces declare a method with the same signature and return type
- **THEN** a single implementation serves calls through either interface, both routed through the interceptor

### Requirement: Cross-interface method merging

The system SHALL merge methods that share the same signature (name + parameter types) and the same return type into one implementation, rather than generating duplicate implementations.

#### Scenario: Same signature and return type merge

- **WHEN** two interfaces declare `String foo(String)` with the same return type
- **THEN** the proxy exposes one implementation of `foo`, callable through either interface with equivalent behavior

### Requirement: Cross-interface conflict rejection

The system SHALL reject ambiguous interface combinations at `proxy()` time by throwing `IllegalArgumentException`.

#### Scenario: Differing return types rejected

- **WHEN** two interfaces declare methods with the same signature but different return types
- **THEN** `proxy(...)` throws `IllegalArgumentException`

#### Scenario: Competing default methods rejected

- **WHEN** two interfaces each declare a `default` method with the same signature
- **THEN** `proxy(...)` throws `IllegalArgumentException`

### Requirement: Super invocation on merged methods

For a merged method, `AcceleratedProxy.invokeSuper(proxy, method, args)` SHALL resolve the correct super implementation: when exactly one interface provides a `default`, it invokes that default; when every declaration is abstract, it throws `AbstractMethodError`.

#### Scenario: One default plus abstract invokes the default

- **WHEN** a merged method is `default` in one interface and abstract in another
- **THEN** `invokeSuper(proxy, method, args)` invokes the `default` implementation

#### Scenario: All-abstract merged method throws

- **WHEN** a merged method is abstract in every interface
- **THEN** `invokeSuper(proxy, method, args)` throws `AbstractMethodError`

#### Scenario: Routing is independent of which interface's Method is passed

- **WHEN** a merged method has a `default` in one interface
- **AND** `invokeSuper` is called with the `Method` object from either interface
- **THEN** both calls invoke the same `default` implementation

### Requirement: Invalid interface arrays rejected

The system SHALL throw `IllegalArgumentException` when the interfaces argument is null, empty, or contains an element that is not an interface.

#### Scenario: Null, empty, or non-interface input rejected

- **WHEN** user calls `proxy(null, interceptor)`, `proxy(new Class<?>[0], interceptor)`, or passes an array containing a non-interface `Class`
- **THEN** the system throws `IllegalArgumentException`

### Requirement: Single-interface behavior preserved

The system SHALL preserve existing single-interface proxy behavior: a proxy created for one interface through the new `Class<?>[]` overloads behaves identically to one created through the existing single-target overload.

#### Scenario: Single interface via the array overload

- **WHEN** user calls `proxy(new Class<?>[]{A.class}, interceptor)`
- **THEN** the resulting proxy behaves the same as `proxy(A.class, interceptor)`

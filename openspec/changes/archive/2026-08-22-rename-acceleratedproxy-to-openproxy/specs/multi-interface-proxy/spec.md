## MODIFIED Requirements

### Requirement: Multi-interface proxy creation

The system SHALL generate a single runtime proxy class that implements all given interfaces. `OpenProxy.proxy(Class<?>[] interfaces, Interceptor)` and `OpenProxy.proxy(Class<?>[] interfaces, Group...)` SHALL return an `Object` that can be cast to each interface in `interfaces`, with every non-static, non-final method call routed through the interceptor.

#### Scenario: Proxy usable through each interface

- **WHEN** user calls `proxy(new Class<?>[]{A.class, B.class}, interceptor)` and casts the result to `A` and to `B`
- **THEN** both casts succeed and reference the same object
- **AND** method calls through either interface invoke `interceptor.intercept(proxy, method, args)`

#### Scenario: Shared signature intercepted once

- **WHEN** two interfaces declare a method with the same signature and return type
- **THEN** a single implementation serves calls through either interface, both routed through the interceptor

### Requirement: Super invocation on merged methods

For a merged method, `OpenProxy.invokeSuper(proxy, method, args)` SHALL resolve the correct super implementation: when exactly one interface provides a `default`, it invokes that default; when every declaration is abstract, it throws `AbstractMethodError`.

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

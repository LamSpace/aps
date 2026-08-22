## MODIFIED Requirements

### Requirement: Proxy class creation

The system SHALL generate a runtime subclass of any non-final concrete class and route all non-final instance method calls through a user-provided single `Interceptor` handler.

#### Scenario: Basic proxy creation and interception

- **WHEN** user calls `OpenProxy.proxy(TargetClass.class, interceptor)`
- **THEN** system returns a proxy instance of type `TargetClass`
- **AND** any method call on the proxy invokes `interceptor.intercept(proxy, method, args)`

#### Scenario: Super method invocation via invokeSuper

- **WHEN** callback calls `OpenProxy.invokeSuper(proxy, method, args)`
- **THEN** the original superclass method executes via direct `INVOKESPECIAL` (no reflection, no MethodHandle)
- **AND** the return value is returned to the callback

### Requirement: Method filtering

The system SHALL support an optional `ClassFilter` that determines which methods pass through the `Interceptor`. Methods not accepted by the filter SHALL call the superclass implementation directly with zero interception overhead.

#### Scenario: Filtered method skips interception

- **WHEN** user creates a proxy with
  `OpenProxy.proxy(TargetClass.class, interceptor, method -> method.getName().startsWith("get"))`
- **AND** a method not matching the filter is called (e.g., `setValue`)
- **THEN** the method executes the superclass implementation directly without invoking the interceptor

#### Scenario: Unfiltered proxy intercepts all methods

- **WHEN** user creates a proxy with `OpenProxy.proxy(TargetClass.class, interceptor)` (no filter)
- **THEN** all non-final instance method calls are routed through the interceptor

### Requirement: Proxy class caching

The system SHALL cache generated proxy classes keyed by `{targetClass, filter}` to avoid re-generating bytecode for the same proxy configuration.

#### Scenario: Repeated proxy creation reuses class

- **WHEN** user calls `OpenProxy.proxy(SomeClass.class, interceptor)` twice
- **THEN** the system reuses the previously generated proxy class

### Requirement: No-default-constructor support

The system SHALL support proxying classes that lack a no-argument constructor by accepting constructor arguments at proxy creation time, unboxing boxed primitive arguments to the superclass constructor's declared parameter types.

#### Scenario: Proxy class with constructor arguments

- **WHEN** user calls `OpenProxy.proxy(BeanWithArgs.class, new Object[]{"arg1", 42}, groups)` for a `BeanWithArgs(String, int)` constructor
- **THEN** the system finds a matching constructor on the target class
- **AND** generates a proxy constructor that unboxes the boxed `Integer` argument to `int` and delegates the arguments to `super(...)`
- **AND** returns a properly initialized proxy instance

#### Scenario: Proxy class with reference-only arguments

- **WHEN** user calls `OpenProxy.proxy(Bean.class, new Object[]{"name"}, groups)` for a `Bean(String)` constructor
- **THEN** the system delegates the `String` argument to `super(...)`
- **AND** returns a properly initialized proxy instance

#### Scenario: Null constructor argument

- **WHEN** user calls `OpenProxy.proxy(Bean.class, new Object[]{null}, groups)` for a `Bean(String)` constructor
- **THEN** the system delegates `null` to `super(...)`
- **AND** returns a properly initialized proxy instance

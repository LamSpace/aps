## MODIFIED Requirements

### Requirement: No-default-constructor support

The system SHALL support proxying classes that lack a no-argument constructor by accepting constructor arguments at proxy creation time, unboxing boxed primitive arguments to the superclass constructor's declared parameter types.

#### Scenario: Proxy class with constructor arguments

- **WHEN** user calls `AcceleratedProxy.proxy(BeanWithArgs.class, new Object[]{"arg1", 42}, groups)` for a `BeanWithArgs(String, int)` constructor
- **THEN** the system finds a matching constructor on the target class
- **AND** generates a proxy constructor that unboxes the boxed `Integer` argument to `int` and delegates the arguments to `super(...)`
- **AND** returns a properly initialized proxy instance

#### Scenario: Proxy class with reference-only arguments

- **WHEN** user calls `AcceleratedProxy.proxy(Bean.class, new Object[]{"name"}, groups)` for a `Bean(String)` constructor
- **THEN** the system delegates the `String` argument to `super(...)`
- **AND** returns a properly initialized proxy instance

#### Scenario: Null constructor argument

- **WHEN** user calls `AcceleratedProxy.proxy(Bean.class, new Object[]{null}, groups)` for a `Bean(String)` constructor
- **THEN** the system delegates `null` to `super(...)`
- **AND** returns a properly initialized proxy instance

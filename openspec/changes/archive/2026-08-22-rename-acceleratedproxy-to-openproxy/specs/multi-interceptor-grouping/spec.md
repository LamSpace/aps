## MODIFIED Requirements

### Requirement: Method group binding via Group API

The system SHALL provide a `Group` type with factory methods `Group.of(MethodPredicate, Interceptor)` and `Group.otherwise(Interceptor)` that bind method matching criteria to interceptor instances for use with `OpenProxy.proxy()`.

#### Scenario: Declare getter and setter groups

- **WHEN** user calls `OpenProxy.proxy(Target.class, Group.of(p1, interceptorA), Group.of(p2, interceptorB))`
- **AND** `p1` matches methods whose names start with "get"
- **AND** `p2` matches methods whose names start with "set"
- **THEN** getter methods are routed through `interceptorA`
- **AND** setter methods are routed through `interceptorB`

#### Scenario: Null predicate rejected

- **WHEN** user calls `Group.of(null, interceptor)`
- **THEN** the system throws `NullPointerException`

#### Scenario: Null interceptor rejected

- **WHEN** user calls `Group.of(predicate, null)` or `Group.otherwise(null)`
- **THEN** the system throws `NullPointerException`

### Requirement: Multi-group proxy creation API

The system SHALL provide `OpenProxy.proxy(Class<T>, Group...)` and `OpenProxy.proxy(Class<T>, Object[], Group...)` overloads that accept one or more Group declarations.

#### Scenario: Create proxy with groups

- **WHEN** user calls `OpenProxy.proxy(Target.class, group1, group2)`
- **THEN** the system matches all proxyable methods against the groups in declaration order
- **AND** creates a proxy instance with per-group interceptor routing

#### Scenario: Empty groups rejected

- **WHEN** user calls `OpenProxy.proxy(Target.class)` with an empty `Group[]` array
- **THEN** the system throws `IllegalArgumentException`

#### Scenario: Null groups rejected

- **WHEN** user calls `OpenProxy.proxy(Target.class, (Group[]) null)`
- **THEN** the system throws `IllegalArgumentException`

## Purpose

Allow proxy creators to bind different `Interceptor` instances to different method families (e.g., getters vs setters) through declarative `Group` declarations, eliminating manual dispatch boilerplate inside `intercept()`.

## ADDED Requirements

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

### Requirement: First-match-wins evaluation

The system SHALL evaluate `Group` declarations in the order they are passed to `proxy()`. The first Group whose predicate returns `true` for a given method binds that method to its Interceptor. Subsequent matching Groups for the same method are ignored.

#### Scenario: Overlapping predicates use declaration order

- **WHEN** `Group.of(m -> true, interceptorA)` is declared before `Group.otherwise(interceptorB)`
- **THEN** all methods are routed through `interceptorA`
- **AND** `interceptorB` is never invoked

### Requirement: Duplicate match warning

The system SHALL log a `WARNING`-level message when a method matches more than one Group's predicate (excluding `Group.otherwise()`), identifying the method name and the conflicting Group indices.

#### Scenario: Overlap between two non-otherwise Groups

- **WHEN** `Group.of(m -> m.getName().startsWith("get"), a)` and `Group.of(m -> m.getName().startsWith("getUser"), b)` both match `getUserName()`
- **THEN** the system logs a WARNING containing the method name and the two Group indices
- **AND** the first declared Group's interceptor (`a`) handles the method

#### Scenario: Overlap with otherwise does not warn

- **WHEN** `Group.of(m -> m.getName().startsWith("get"), a)` and `Group.otherwise(b)` both match `getName()`
- **THEN** no duplicate-match WARNING is logged

### Requirement: Default passthrough for unmatched methods

The system SHALL route any method not matching any declared Group (and not caught by `Group.otherwise()`) directly to the superclass implementation without interception overhead.

#### Scenario: Class proxy — unmatched method calls super directly

- **WHEN** a class proxy is created with only `Group.of(m -> m.getName().startsWith("get"), interceptor)`
- **AND** a setter method is invoked on the proxy
- **THEN** the setter executes the superclass implementation directly
- **AND** no Interceptor instance is involved

#### Scenario: Interface proxy — unmatched method throws error

- **WHEN** an interface proxy is created with only `Group.of(m -> m.getName().startsWith("get"), interceptor)`
- **AND** a non-getter, non-Object method is invoked on the proxy
- **THEN** the system throws `AbstractMethodError`

### Requirement: Interceptor deduplication by reference equality

The system SHALL store one instance field per distinct `Interceptor` reference (compared by `==`) in generated proxy classes, sharing the same field across all methods assigned to the same Interceptor instance.

#### Scenario: Same interceptor instance shared across groups

- **WHEN** the same `Interceptor` instance is used in two different `Group.of()` declarations
- **THEN** the generated proxy class contains exactly one field for that Interceptor
- **AND** all methods in both groups reference the same field

#### Scenario: Distinct instances produce distinct fields

- **WHEN** two `Group.of()` declarations use different `Interceptor` instances (by reference)
- **THEN** the generated proxy class contains two distinct fields

### Requirement: MethodPredicate replaces ClassFilter

The system SHALL provide a `MethodPredicate` functional interface with signature `boolean test(Method method)` as the method matching primitive for `Group.of()`. The `ClassFilter` interface is removed.

#### Scenario: MethodPredicate used in Group.of

- **WHEN** user declares `Group.of(m -> m.getAnnotation(Log.class) != null, interceptor)`
- **THEN** the predicate receives each proxyable method and returns whether it matches

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

### Requirement: Zero hot-path overhead

The system SHALL generate proxy method overrides that access their assigned Interceptor via a direct instance field reference (`GETFIELD _interceptor$N`), with no array lookup, no index computation, and no additional indirection beyond the current single-Interceptor design.

#### Scenario: Intercepted method bytecode structure

- **WHEN** a multi-group proxy's intercepted method is invoked
- **THEN** the bytecode sequence is: `ALOAD 0`, `GETFIELD _interceptor$N`, method-argument setup, `INVOKEINTERFACE Interceptor.intercept`
- **AND** no `AALOAD`, `CHECKCAST`, or bounds-check bytecodes appear in the interceptor resolution path

## MODIFIED Requirements

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

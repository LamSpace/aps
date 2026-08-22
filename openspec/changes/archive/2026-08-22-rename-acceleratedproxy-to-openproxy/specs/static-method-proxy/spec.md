## MODIFIED Requirements

### Requirement: Static proxy entry point

The system SHALL provide `OpenProxy.proxyStatic(Class<?> target, Group... groups)` returning a generated proxy `Class<?>`, and a convenience overload `proxyStatic(Class<?> target, Interceptor interceptor)`.

#### Scenario: Convenience overload

- **WHEN** the user calls `proxyStatic(target, interceptor)`
- **THEN** it behaves identically to `proxyStatic(target, Group.otherwise(interceptor))`

#### Scenario: Null or empty arguments rejected

- **WHEN** the user passes a null target, a null interceptor, or null/empty groups
- **THEN** the system throws `IllegalArgumentException`

#### Scenario: Interface target rejected

- **WHEN** the user passes an interface as `target`
- **THEN** the system throws `IllegalArgumentException`

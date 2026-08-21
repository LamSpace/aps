## MODIFIED Requirements

### Requirement: Access control with graceful degradation

The system SHALL attempt to obtain full private access to the target class via `MethodHandles.privateLookupIn`. If the target module is not open, the system SHALL fail fast by throwing `IllegalArgumentException` with an actionable `--add-opens` hint, instead of falling back to a public lookup. Primitive and array types (rejected by `privateLookupIn`) SHALL fall back to a public lookup.

#### Scenario: Private access succeeds

- **WHEN** the target class's package is open for reflection
- **THEN** the system uses a full-access Lookup enabling access to all non-public methods

#### Scenario: Strongly encapsulated module denied with actionable error

- **WHEN** the target class's module does not open its package
- **THEN** the system throws `IllegalArgumentException`
- **AND** the message contains an actionable `--add-opens <module>/<package>=ALL-UNNAMED` hint
- **AND** the original `IllegalAccessException` is retained as the cause

#### Scenario: Primitive or array type falls back

- **WHEN** the target class is a primitive or array type (rejected by `privateLookupIn` with `IllegalArgumentException`)
- **THEN** the system falls back to a public lookup without failing

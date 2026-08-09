## MODIFIED Requirements

### Requirement: Proxy class caching

The system SHALL cache generated proxy classes keyed by the `{targetClass, interceptors[], mapping, constructorArgs}` tuple to avoid re-generating bytecode for the same proxy configuration. Interceptors in the key are compared by reference equality (`==`). The `mapping` captures the method-to-interceptor-index assignment produced by Group chain matching.

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

## REMOVED Requirements

### Requirement: ClassFilter method filtering

**Reason**: Method filtering is now handled by `MethodPredicate` within `Group.of()` declarations. The binary accept/reject model is replaced by first-match-wins Group chain matching with default passthrough.

**Migration**: Replace `AcceleratedProxy.proxy(target, interceptor, filter)` with `AcceleratedProxy.proxy(target, Group.of(filter::accept, interceptor))`. The old API signature is preserved and delegates internally to the Group model — no code changes required.

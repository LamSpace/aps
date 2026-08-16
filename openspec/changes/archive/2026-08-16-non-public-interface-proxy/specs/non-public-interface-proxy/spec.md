## Purpose

Support proxying package-private (non-`public`) interfaces by defining the generated hidden class in the interface's own package, while leaving the all-public path byte-for-byte unchanged.

## ADDED Requirements

### Requirement: Non-public interface proxying

The system SHALL generate a proxy that implements a package-private interface, defining the generated hidden class in that interface's package so the JVM access rule is satisfied.

#### Scenario: Package-private interface proxied

- **WHEN** user calls `proxy(SecretService.class, interceptor)` where `SecretService` is package-private
- **THEN** the returned proxy implements `SecretService`
- **AND** method calls route through `interceptor.intercept(proxy, method, args)`

#### Scenario: invokeSuper on a package-private default method

- **WHEN** a package-private interface declares a `default` method
- **AND** the interceptor calls `invokeSuper(proxy, method, args)`
- **THEN** the `default` implementation runs

### Requirement: Mixed public and non-public interfaces

The system SHALL proxy an array mixing `public` interfaces (any package) with non-public interfaces that share a single package; that shared package becomes the generated class's package.

#### Scenario: Public plus package-private interface

- **WHEN** user calls `proxy(new Class<?>[]{PublicMarker.class, SecretService.class}, interceptor)`
- **THEN** the proxy implements both interfaces and routes both through the interceptor

### Requirement: Cross-package non-public rejection

The system SHALL throw `IllegalArgumentException` when the interface array contains non-public interfaces from different packages.

#### Scenario: Different-package non-public interfaces rejected

- **WHEN** two non-public interfaces reside in different packages
- **THEN** `proxy(...)` throws `IllegalArgumentException`

### Requirement: All-public path unchanged

The system SHALL keep the all-public interface path byte-for-byte unchanged: the generated class stays in `io.github.lamspace` and is defined with `MethodHandles.lookup()`, so public JDK interfaces in strongly-encapsulated modules remain proxyable without `--add-opens`.

#### Scenario: Public JDK interface still proxied

- **WHEN** user calls `proxy(java.util.function.Function.class, interceptor)`
- **THEN** the proxy works without any `--add-opens` JVM argument

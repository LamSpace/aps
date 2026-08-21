## Purpose

Lets long-running frameworks deterministically drop cached OpenProxy proxy classes for a hot-deployed target, and swap the interceptor on a live proxy instance without recreating it.

## ADDED Requirements

### Requirement: Deterministic proxy-class eviction

The library SHALL provide `AcceleratedProxy.evict(Class<?>)` and `AcceleratedProxy.evictClassLoader(ClassLoader)`. Evicting a target SHALL remove its cached generated proxy classes so that the next `proxy(...)` call for that target generates a fresh class. Eviction MUST NOT affect proxy instances that were already created — they keep working on the class they were built from.

#### Scenario: Evict forces regeneration

- **WHEN** a proxy for a target class is created, then `evict(target)` is called
- **THEN** the next `proxy(...)` call for the same target returns an instance of a different generated class

#### Scenario: EvictClassLoader scopes by class loader

- **WHEN** proxy classes exist for targets loaded by two different class loaders, and `evictClassLoader(loaderA)` is called
- **THEN** only the proxy classes keyed by classes from `loaderA` are regenerated on the next `proxy(...)`; entries for the other loader remain cached

#### Scenario: Existing instances survive eviction

- **WHEN** a proxy instance exists and its target is evicted
- **THEN** invoking methods on that instance still works and still routes through its original interceptor

#### Scenario: Eviction rejects null arguments

- **WHEN** `evict(null)` or `evictClassLoader(null)` is called
- **THEN** an `IllegalArgumentException` is thrown

### Requirement: In-place interceptor rebinding

The library SHALL provide `AcceleratedProxy.rebind(Object, Interceptor)` and `AcceleratedProxy.rebind(Object, Interceptor[])`. Rebinding MUST replace the interceptors bound to an existing proxy instance without recreating the instance or changing its generated class. The array form MUST require a length equal to the proxy's distinct interceptor count and SHALL reject a mismatched length with `IllegalArgumentException`. Rebinding a non-proxy object SHALL throw `IllegalArgumentException`. Rebinding is a single-writer operation: a caller that rebinds on one thread and invokes methods on another MUST establish its own happens-before edge for the swap to be visible.

#### Scenario: Rebind swaps the interceptor on a class proxy

- **WHEN** a class proxy is created with interceptor A, then `rebind(proxy, B)` is called
- **THEN** subsequent method calls route through B, and the proxy's class and identity are unchanged

#### Scenario: Rebind swaps the interceptor on an interface proxy

- **WHEN** an interface proxy is created with interceptor A, then `rebind(proxy, B)` is called
- **THEN** subsequent method calls route through B

#### Scenario: Rebind preserves interceptor indices

- **WHEN** a proxy with N distinct interceptors is rebound with an N-length array
- **THEN** each method routes through the new interceptor at the same index it had before

#### Scenario: Rebind rejects a mismatched array length

- **WHEN** `rebind(proxy, interceptors)` is called with an array whose length differs from the proxy's distinct interceptor count
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: Rebind rejects non-proxy objects

- **WHEN** `rebind` is called on an object that is not an OpenProxy-generated proxy (including `null`)
- **THEN** an `IllegalArgumentException` is thrown

#### Scenario: Rebind is per-instance

- **WHEN** two proxy instances share the same generated class and only one is rebound
- **THEN** only the rebound instance's behavior changes; the other instance is unaffected

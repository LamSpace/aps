## Why

Interface proxying currently supports public interfaces only — the generated hidden class is defined in OpenProxy's own package (`io.github.lamspace`), so it cannot implement a package-private interface (the same restriction as `java.lang.reflect.Proxy`). This closes Phase 3 item 10 by defining the hidden class in the interface's own package when a non-public interface is present.

## What Changes

- Support proxying package-private (non-`public`) interfaces through the existing `proxy(...)` entry points.
- When the interface array contains a non-public interface, define the generated hidden class in that interface's package via a package-access `Lookup` (`privateLookupIn`); all non-public interfaces must share one package, otherwise `IllegalArgumentException`.
- Leave the all-public path byte-for-byte unchanged (same generated name, `MethodHandles.lookup()`), so public JDK interfaces in strongly-encapsulated modules keep working without `--add-opens`.
- No change to cache-key semantics (the first interface remains the weak cache key).

## Capabilities

### New Capabilities

- `non-public-interface-proxy`: proxying package-private interfaces by defining the generated class in the interface's package, rejecting non-public interfaces that span multiple packages, and preserving the all-public path.

### Modified Capabilities

None — the all-public interface path is behaviorally and byte-for-byte unchanged, so no existing spec's requirements change.

## Impact

- **Code:** `AcceleratedProxy.java` (new private `nonPublicAnchor` helper + wiring in `proxyInterfaces` and `generateProxyClass`), `InterfaceGenerator.java` (new `packagePrefix` constructor argument).
- **API:** No public API change; `proxy(Class<?>[], …)` and `proxy(Class<T>, …)` gain the ability to accept package-private interfaces.
- **Tests:** new `pkgprivate`/`otherpkg` test packages for the non-public cases, plus a public-JDK-interface regression test.
- **Docs:** roadmap, README (EN/CN), a new openspec capability spec, and Javadoc.

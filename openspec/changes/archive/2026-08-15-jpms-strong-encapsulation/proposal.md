## Why

Proxying a class in a strongly encapsulated module (any package not `open`, including `java.base` packages such as `java.util`) currently fails with a misleading error: `Failed to generate proxy class` → `… not in same package as lookup class`. The silent fallback in `LookupManager` never works — the generated class name is always in the target's package while the fallback lookup is in `io.github.lamspace.internal`, so `defineHiddenClass` always rejects the name-package mismatch, giving the user no hint about `--add-opens`.

## What Changes

- `LookupManager.getLookup` no longer silently falls back to `MethodHandles.lookup()` when `privateLookupIn` is denied by strong encapsulation; it throws `IllegalArgumentException` with an actionable `--add-opens <module>/<package>=ALL-UNNAMED` hint.
- `AcceleratedProxy.generateProxyClass` re-throws `IllegalArgumentException` unchanged so the hint reaches `proxy(...)` callers as the direct cause instead of being buried under `Failed to generate proxy class`.
- Primitive/array targets keep their existing graceful fallback (a type rejection, not a module denial).
- Roadmap item 8 is marked done; a new roadmap item for non-public interface proxying is added; the README documents the `--add-opens` requirement.

## Capabilities

### New Capabilities

<!-- none — this change modifies existing access-control behavior, not new capabilities -->

### Modified Capabilities

- `openproxy-core`: The "Access control with graceful degradation" requirement changes from graceful fallback to fail-fast with an actionable `--add-opens` error for strongly encapsulated modules.

## Impact

- `src/main/java/io/github/lamspace/internal/LookupManager.java` — fail-fast on `IllegalAccessException`; Javadoc updated.
- `src/main/java/io/github/lamspace/AcceleratedProxy.java` — re-throw `IllegalArgumentException` in `generateProxyClass`.
- Tests: `internal/LookupManagerTest.java` (correct the `String.class` premise + add a fail-fast test), new `JpmsStrongEncapsulationTest.java`.
- Docs: `docs/openproxy-future-roadmap.md`, `README.md`, `README_CN.md`.
- No public API change, no hot-path change. Behavior change only for strongly encapsulated module targets (which previously always failed with a confusing error anyway).

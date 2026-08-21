## Context

Class proxies are defined as hidden classes via `LookupManager.getLookup(target)` → `MethodHandles.privateLookupIn(target, lookup)` → `Lookup.defineHiddenClass(...)`. `privateLookupIn` grants private access to the target's package. When the target lives in a strongly encapsulated module (package not `open`), `privateLookupIn` throws `IllegalAccessException`; the current code swallows it and returns `MethodHandles.lookup()` (package `io.github.lamspace.internal`). Because the generated class name is always `<target-package>.<SimpleName>$$AcceleratedProxy$$N`, `defineHiddenClass` then rejects the name-package mismatch ("not in same package as lookup class"). See proposal.md for the motivation.

## Goals / Non-Goals

**Goals:**

- Fail fast with an actionable `--add-opens` hint when `privateLookupIn` is denied by strong encapsulation.
- Surface that error to `proxy(...)` callers as the direct cause, not buried under a generic wrapper.
- Preserve behavior for classpath (unnamed-module) classes and open named modules, and for primitive/array edge cases.

**Non-Goals:**

- Non-public interface proxying (deferred to roadmap item 10).
- OpenProxy's own `module-info.java` / `Automatic-Module-Name` (Maven Central item).
- Cross-ClassLoader hot deployment (a separate concern; `--add-opens` does not address it).
- A "smart" hybrid that falls back to a public lookup only when the target is public with accessible members.

## Decisions

1. **Fail-fast, not best-effort fallback.** The fallback never worked — it always fails at `defineHiddenClass` with a name-package mismatch. Replacing it with a throw yields a predictable, actionable error. Alternative considered: keep the fallback and improve the downstream error message — rejected because the failure point (`defineHiddenClass`) is far from the cause (`privateLookupIn` denial), making a targeted message awkward.
2. **`IllegalArgumentException`, not a new exception type.** No new public API surface; consistent with the library's existing API-misuse convention. The message carries the actionable content. Alternative: a dedicated `ModuleAccessException` — rejected as over-engineering for a single failure site.
3. **Class proxies only.** Interface proxies keep using `MethodHandles.lookup()` and support `public` interfaces only (same as `java.lang.reflect.Proxy`). Non-public interface support is deferred.
4. **Keep primitive/array fallback.** `privateLookupIn` rejects primitives/arrays with `IllegalArgumentException` (a type rejection, not a module denial), and these are never valid proxy targets; the existing graceful fallback keeps `LookupManager` a safe utility.

## Risks / Trade-offs

- [Proxying any `java.base` class now requires `--add-opens`] → This is the intended, documented behavior; previously such classes always failed with a confusing error, so nothing working is lost. The README documents the requirement.
- [The `IllegalArgumentException` is re-thrown from `generateProxyClass`, which also affects the "no matching constructor" path] → That path's `IllegalArgumentException` now surfaces one wrapping level shallower (clearer, not a break); no test asserted the old double-wrap.

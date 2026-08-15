# APS Phase 3: JPMS Strong Encapsulation — Design Spec

**Date:** 2026-08-15 **Status:** Pending review **Phase:** 3 — Advanced Features

## 1. Motivation

Class proxies are defined as hidden classes via
`LookupManager.getLookup(target)` → `MethodHandles.privateLookupIn(target,
lookup)` → `Lookup.defineHiddenClass(...)`. `privateLookupIn` grants the lookup
private access to the target's package so the generated subclass can (a) be
defined in the target's runtime package and (b) access non-public
constructors/members.

When the target class lives in a strongly encapsulated module — any package that
is not `open`, including `java.base` packages such as `java.util` —
`privateLookupIn` throws `IllegalAccessException`. The current code swallows that
and silently falls back to `MethodHandles.lookup()` (the APS package
`io.github.lamspace.internal`). Because the generated class name is always
`<target-package>.<SimpleName>$$AcceleratedProxy$$N`, `defineHiddenClass` then
rejects the name-package mismatch, so the user sees:

```text
Failed to generate proxy class
  → java/util/ArrayList$$AcceleratedProxy$$0 not in same package as lookup class
```

with no hint about the real cause (`--add-opens`).

This item replaces the silent fallback with a **fail-fast + actionable error**, so
a denied module access surfaces as a clear `--add-opens` instruction instead of a
misleading name-package error.

**Success criteria**

1. `LookupManager.getLookup` throws `IllegalArgumentException` (with an
   actionable `--add-opens` hint) when `privateLookupIn` is denied by strong
   encapsulation, instead of silently falling back.
2. Primitive and array types (rejected by `privateLookupIn` with
   `IllegalArgumentException`, not a module-access denial) still fall back to a
   public lookup.
3. `AcceleratedProxy.generateProxyClass` re-throws the `IllegalArgumentException`
   unchanged, so the hint is the direct cause of the `proxy(...)` failure rather
   than buried under `Failed to generate proxy class`.
4. Proxying a strongly encapsulated class (e.g. `java.util.ArrayList`) fails fast
   with the `--add-opens` hint in the cause chain.
5. Classpath (unnamed-module) classes and open named modules are unaffected —
   byte-identical behavior, zero performance change.
6. The existing test suite passes with one test premise corrected (`String.class`
   is itself a strongly encapsulated class, not a "standard" class).
7. Roadmap item 8 is marked done, a new roadmap item for non-public interface
   proxying is added, and the README documents the JPMS behavior.

## 2. Design

### 2.1 Current behavior

`internal/LookupManager.getLookup(Class<?>)`:

1. `privateLookupIn(targetClass, MethodHandles.lookup())` — full private access.
2. On `IllegalAccessException`: logs a warning, returns `MethodHandles.lookup()`
   (APS package).
3. On `IllegalArgumentException` (primitive/array): logs fine, returns
   `MethodHandles.lookup()`.

`AcceleratedProxy.generateProxyClass` calls `getLookup(target).defineHiddenClass(...)`
for class proxies and wraps any `Exception` in `RuntimeException("Failed to
generate proxy class")`.

### 2.2 New behavior

`getLookup` branches become:

- `privateLookupIn` succeeds → return it (unchanged).
- `IllegalAccessException` → throw `IllegalArgumentException`:

  ```text
  Cannot access <name> in module <module> (package <pkg>): the package is not
  open to the unnamed module. Add --add-opens <module>/<pkg>=ALL-UNNAMED to the
  JVM arguments, or declare 'opens <pkg>;' in the module's module-info.java.
  ```

  The original `IllegalAccessException` is retained as the cause.
- `IllegalArgumentException` (primitive/array) → unchanged fallback.

`generateProxyClass` adds a `catch (IllegalArgumentException e) { throw e; }`
before the existing `catch (Exception e)` so the actionable error is not wrapped.

### 2.3 Module name / package derivation

`targetClass.getModule().getName()` and `targetClass.getPackageName()` supply the
module and package in the hint. `IllegalAccessException` from `privateLookupIn`
only occurs for named modules (the unnamed module is always open), so the module
name is non-null on that path — no null-guard is needed.

### 2.4 Files touched

| File                                     | Change                                                                 |
|------------------------------------------|------------------------------------------------------------------------|
| `internal/LookupManager.java`            | Fail-fast `IllegalArgumentException` on `IllegalAccessException`; Javadoc updated. |
| `AcceleratedProxy.java`                  | `generateProxyClass`: re-throw `IllegalArgumentException`.             |
| `internal/LookupManagerTest.java`        | Correct `String.class` → classpath class; add fail-fast test.          |
| `JpmsStrongEncapsulationTest.java` (new) | e2e `proxy(ArrayList.class)` fails with actionable cause.              |

No change to `ClassGenerator`, `InterfaceGenerator`, the dispatchers, `WeakCache`,
`Interceptor`, `Group`, `MethodPredicate`, or the public API surface (no new
public types).

## 3. Error handling

- **Strong encapsulation (module access denied):** `IllegalArgumentException` with
  `--add-opens` hint (cause = original `IllegalAccessException`). Surfaced through
  `proxy(...)` as the direct cause of `RuntimeException("Failed to create proxy
  for X")`.
- **Primitive/array target:** unchanged graceful fallback (these are not valid
  proxy targets; `getLookup` is effectively never called with them on the proxy
  path).
- **No matching constructor (`findConstructor`):** unchanged
  `IllegalArgumentException`, now also surfaces as a direct cause (one less
  wrapping level). This is an incidental clarity improvement, not a behavior break
  — no test asserts the old double-wrap.

## 4. Testing

- `LookupManagerTest`:
  - `shouldReturnNonNullLookupForStandardClass` — change `String.class` to
    `LookupManager.class` (a classpath class; `String` is itself strongly
    encapsulated).
  - New `shouldThrowActionableErrorForStronglyEncapsulatedClass` —
    `getLookup(java.util.ArrayList.class)` throws `IllegalArgumentException` with
    `--add-opens` in the message.
  - Keep the `int.class` / `String[].class` fallback tests and
    `shouldReturnLookupForInnerClass` unchanged.
- New `JpmsStrongEncapsulationTest`:
  - `proxyStronglyEncapsulatedClassFailsWithActionableHint` —
    `AcceleratedProxy.proxy(ArrayList.class, (o, m, a) -> null)` throws
    `RuntimeException` whose direct cause is `IllegalArgumentException` containing
    `--add-opens`.

`java.util.ArrayList` is the chosen trigger: it is a public, non-final class in a
non-open `java.base` package (`java.util`), so it deterministically reaches the
`privateLookupIn` denial without depending on a `jdk.internal` name or a
dynamically compiled named module.

## 5. Performance

No impact. `privateLookupIn` runs once per proxy-class generation (cache miss),
never on the call path. The `dispatch`/`invokeSuper` hot path is generated
bytecode and is byte-identical before/after. The behavior change is confined to
the denial branch, which previously produced a broken proxy anyway.

## 6. Documentation changes

- `docs/aps-future-roadmap.md`: mark item 8 已完成 with a
  `### JPMS 强封装模块（已完成）` subsection; add item 10 `非 public 接口代理`
  (table row + short subsection) for the deferred interface-side work.
- `README.md` / `README_CN.md`: add a "JPMS / Strong Encapsulation" section (the
  `--add-opens` requirement + an example error).

## 7. Deliberate decisions

1. **Fail-fast, not best-effort fallback.** The silent fallback never works — the
   generated class name is always in the target's package, so `defineHiddenClass`
   rejects the name-package mismatch ("not in same package as lookup class").
   Fail-fast with a `--add-opens` hint replaces that misleading error with a
   predictable, actionable one. Consequence: proxying any `java.base` class (e.g.
   `ArrayList`) now clearly tells the user to add `--add-opens`.
2. **`IllegalArgumentException`, not a new exception type.** No new public API
   surface; consistent with the library's existing API-misuse convention. The
   message carries the actionable content.
3. **Class proxies only.** Interface proxies still use `MethodHandles.lookup()`
   and support `public` interfaces only (same as `java.lang.reflect.Proxy`).
   Non-public interface support is deferred to roadmap item 10.
4. **Keep primitive/array fallback.** `IllegalArgumentException` from
   `privateLookupIn` for primitive/array is a type rejection, not a module denial,
   and these are never valid proxy targets; preserving the existing graceful
   fallback keeps `LookupManager` a safe utility.

## 8. Out of scope

- Non-public interface proxying (deferred to roadmap item 10).
- APS's own `module-info.java` / `Automatic-Module-Name` (belongs to the Maven
  Central release item).
- Cross-ClassLoader hot deployment (a separate open concern, not addressed by
  `--add-opens`).
- A "smart" hybrid that falls back to a public lookup only when the target is
  public with accessible members (introspecting access flags is complex and
  brittle for marginal benefit).

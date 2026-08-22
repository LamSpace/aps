## Why

An audit of every Java file under `src/main/` found that while the public API surface is well documented, the documentation as a whole is not standard-compliant or complete: ~35 private/package-private helper methods have no JavaDoc at all, several documented methods are missing mandatory `@param`/`@return` tags, `WeakCache` carries unresolvable `{@link}`/`@see` references to the JDK's package-private `java.lang.reflect.WeakCache` plus a misused `@author` tag, and `AcceleratedProxy`'s class- and field-level comments describe a proxy-class cache key (`{targetClass, interceptors, mapping, constructorArgs}`) that contradicts the actual `CacheParams` key (which deliberately excludes interceptor instances and includes `interfaces`/`ctorIntercept`). The project is pre-1.0 (`0.1.0-SNAPSHOT`, published coordinates `io.github.lamspace:openproxy`), so the comments should be complete, accurate, and pass `javadoc -Xdoclint` cleanly before release.

## What Changes

Comments only — **no code behavior changes**. Per the audit:

- **Regenerate/add missing JavaDoc** on undocumented methods in 10 classes: `AcceleratedProxy` (6 private helpers), `ClassGenerator` (8), `StaticMethodGenerator` (8), `InterfaceGenerator` (3), `MethodDispatcher` (3), `InterfaceDispatcher` (2), `WeakCache` (7+ inner-class members), `BytecodeUtils` (1), `InterfaceMethodResolver` (1), `Group` (3 package-private accessors).
- **Fix invalid references** in `WeakCache`: remove `{@link java.lang.reflect.WeakCache}` and `@see java.lang.reflect.WeakCache` (package-private JDK class — javadoc cannot resolve it), and replace the non-standard `@author copied from ...` sentence with a plain-text attribution note.
- **Fix stale/inaccurate descriptions** in `AcceleratedProxy`: the class javadoc and `PROXY_CLASS_CACHE` field javadoc both claim interceptors are part of the cache key; the `CacheParams` contract says the opposite.
- **Complete incomplete tag sets**: `BytecodeUtils.pushClassConstant` (missing `@param`s), `DispatchGenerator.methodDispatchHash` (missing `@param`/`@return`), `DispatchGenerator.emitDispatchBody` (missing `@param`s).
- **De-duplicate** `LookupManager`'s verbatim class/method doc repetition.
- **Verify** with `javadoc -Xdoclint:all` over `src/main/` and require zero warnings.

### Audit summary — classes needing comment regeneration

| Class | Issue category |
|---|---|
| `AcceleratedProxy` | 6 undocumented private methods; 2 stale cache-key descriptions |
| `WeakCache` | Unresolvable `@link`/`@see` to package-private JDK class; misused `@author`; undocumented `expungeStaleEntries` + inner-class constructors/helpers; expunge list omits `removeIf` |
| `Group` | 3 undocumented package-private accessors |
| `ClassGenerator` | 8 undocumented private methods |
| `InterfaceGenerator` | 3 undocumented private methods |
| `InterfaceDispatcher` | 2 undocumented private methods |
| `MethodDispatcher` | 3 undocumented private methods |
| `StaticMethodGenerator` | 8 undocumented private methods |
| `BytecodeUtils` | 1 undocumented private method; `pushClassConstant` missing `@param` tags |
| `DispatchGenerator` | `methodDispatchHash` and `emitDispatchBody` missing `@param`/`@return` tags |
| `InterfaceMethodResolver` | 1 undocumented private method (`signatureKey`) |
| `MethodInfo` | Secondary + compact record constructors undocumented |
| `LookupManager` | Method javadoc verbatim-duplicates class javadoc (minor cleanup) |

Classes already compliant (no change needed): `Around`, `Intercept`, `Interceptor`, `ConstructorInterceptor`, `DispatchTarget`, `MethodPredicate`, `MethodMapping`*, `ClinitRegistry`, `internal/Rebindable`, and all three `package-info.java` files. (*`MethodMapping`'s `equals`/`hashCode`/`toString` overrides are exempt by convention.)

## Capabilities

### New Capabilities

None.

### Modified Capabilities

None — this change modifies comments only; no spec-level behavior changes. `skip_specs: true` is set in this change's `.openspec.yaml`.

## Impact

- **Code**: 13 files under `src/main/java/io/github/lamspace/` (all listed in the audit table). Comment edits only; no signature, logic, or formatting changes outside comment blocks.
- **APIs**: none.
- **Build/deps**: none. Verification uses an ad-hoc `javadoc -Xdoclint:all` invocation with the ASM jar on the classpath; no `pom.xml` change.
- **Risk**: negligible — comments do not compile to bytecode. The only verification gate is doclint-clean output plus an unchanged `mvn compile`.

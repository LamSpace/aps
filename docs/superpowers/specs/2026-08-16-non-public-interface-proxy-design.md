# OpenProxy Phase 3: Non-Public Interface Proxy — Design Spec

**Date:** 2026-08-16 **Status:** Awaiting review **Phase:** 3 — Advanced Features

## 1. Motivation

Interface proxying currently defines the generated hidden class with
`MethodHandles.lookup()` and hardcodes the class name in OpenProxy's own package
(`io.github.lamspace`). A JVM class can implement a package-private interface
only if it lives in that interface's package, so today's proxy can implement
**public** interfaces only — the same restriction as `java.lang.reflect.Proxy`.
The roadmap's Phase 3 item 10 ("非 public 接口代理") lifts this: proxy a
package-private (non-`public`) interface by defining the hidden class in the
interface's own package via a package-access `Lookup`.

**Hard constraint that shapes everything:** a package-private interface is
accessible only from its own runtime package, and a hidden class can live in
exactly one package. Therefore (a) every non-`public` interface in the input
array must share a single package, and (b) the generated class's name must use
that package. There is a second, subtler constraint: the public path must not
regress. Item 8 already documents that interface proxying works for public
interfaces — including `public` JDK interfaces in strongly-encapsulated modules
such as `java.util.function.Function` — via the public lookup. Switching that
path to `privateLookupIn` would make `proxy(Function.class, …)` start throwing
an `--add-opens` error (the package is not open). So the private lookup must be
applied **only when a non-`public` interface is present**, never unconditionally.

**Success criteria**

1. `AcceleratedProxy.proxy(Class<?>[], …)` and the single-interface
   `proxy(Class<T>, …)` entry proxy a package-private interface, routing its
   methods through the `Interceptor` exactly as today.
2. `AcceleratedProxy.invokeSuper` on a package-private interface's `default`
   method invokes the default implementation (`INVOKESPECIAL`, no `MethodHandle`).
3. Mixed arrays — one or more `public` interfaces plus one package-private
   interface in the same package — work; the package-private interface fixes the
   hidden class's package.
4. Two or more non-`public` interfaces in **different** packages fail fast with
   an `IllegalArgumentException` (same discipline as the existing return-type /
   default conflicts).
5. The all-`public` path is **byte-for-byte unchanged**: same generated name,
   same `MethodHandles.lookup()`, zero regression for public JDK interfaces.
6. Unit/integration tests, an updated roadmap, updated README docs, and Javadoc.

## 2. Design

### 2.1 Core rule: the anchor package

A single private helper resolves the *anchor* — the first non-`public`
interface in the array, or `null` when all interfaces are `public` — and
validates the single-package constraint:

```java
/** First non-public interface, or null if all are public.
 *  Throws if non-public interfaces span multiple packages. */
private static Class<?> nonPublicAnchor(Class<?>[] interfaces) {
    Class<?> anchor = null;
    for (Class<?> itf : interfaces) {
        if (!Modifier.isPublic(itf.getModifiers())) {
            if (anchor == null) {
                anchor = itf;
            } else if (!anchor.getPackageName().equals(itf.getPackageName())) {
                throw new IllegalArgumentException(
                    "cannot proxy non-public interfaces from different "
                    + "packages: " + anchor.getName() + " and " + itf.getName());
            }
        }
    }
    return anchor;
}
```

`Modifier.isPublic` is the correct discriminator: a package-private top-level
or nested interface has no `PUBLIC` bit; a `public` interface (top-level or
nested) does. (Caveat: a `public` nested interface whose enclosing class is
package-private reports the `PUBLIC` bit but is effectively package-private,
so it is misclassified as public and fails later at class-definition time —
out of scope, see §7.) This helper is called twice for different reasons
(§2.3, §2.5); it is a cheap idempotent scan and is the single source of truth
for the rule.

### 2.2 Lookup selection

In `AcceleratedProxy.generateProxyClass`, the interface branch currently does:

```java
return java.lang.invoke.MethodHandles.lookup()
        .defineHiddenClass(bytecode, true).lookupClass();
```

It becomes:

```java
Class<?>[] interfaces = params.interfaces();
Class<?> anchor = nonPublicAnchor(interfaces);
MethodHandles.Lookup lookup = (anchor == null)
        ? MethodHandles.lookup()
        : LookupManager.getLookup(anchor);
return lookup.defineHiddenClass(bytecode, true).lookupClass();
```

- **All public** (`anchor == null`): `MethodHandles.lookup()` — identical to
  today, so public JDK interfaces in strongly-encapsulated modules keep working.
- **Non-public present**: `LookupManager.getLookup(anchor)` reuses the existing
  `privateLookupIn` path (no `LookupManager` change). For an anchor in the
  unnamed module — the common case — `privateLookupIn` succeeds unconditionally.
  For an anchor in a named module whose package is not open, `getLookup` throws
  the existing actionable `--add-opens` `IllegalArgumentException`, which is
  exactly right (you genuinely cannot access that package otherwise).

### 2.3 Generated class naming

`InterfaceGenerator` hardcodes the package prefix today:

```java
String generatedInternal = "io/github/lamspace/" + baseName + "$$AcceleratedProxy$$" + COUNTER.getAndIncrement();
```

The constructor gains a `packagePrefix` parameter (callers: `AcceleratedProxy`
only) and `generate()` uses it:

```java
String generatedInternal = packagePrefix + baseName + "$$AcceleratedProxy$$" + COUNTER.getAndIncrement();
```

`generateProxyClass` computes the prefix from the anchor, mirroring
`ClassGenerator`'s existing "same package as the target" derivation:

```java
String packagePrefix = "io/github/lamspace/";
if (anchor != null) {
    String pkg = anchor.getPackageName();
    packagePrefix = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
}
```

- **All public**: `packagePrefix == "io/github/lamspace/"` → the generated name
  is byte-identical to today (§5).
- **Non-public present**: the name is `P/Base$$AcceleratedProxy$$N` where `P` is
  the anchor's package — `Base` is the single interface's simple name, or
  `MultiInterface` for multi-interface arrays. This satisfies the JVM rule: the
  hidden class is defined in the lookup class's package, so the name must match
  the anchor's package.

`baseName` derivation is unchanged (single → simple name; multi → `MultiInterface`).

### 2.4 Conflict rules

New rule, checked by `nonPublicAnchor` (surfaced in `proxyInterfaces`, §3):

- **Non-public interfaces from different packages → `IllegalArgumentException`.**

Existing rules (unchanged, still enforced by `InterfaceMethodResolver.resolve`):

- Same signature + different return types → `IllegalArgumentException`.
- Two `default` implementations → `IllegalArgumentException`.
- Same signature + same return type → merged.
- One `default` + abstract → merged, `default` is the `invokeSuper` target.

A `public` interface may live in any package; it is accessible from the anchor's
package by definition.

### 2.5 Cache key semantics (resolves the roadmap "待完善" note)

No change. The weak cache key remains the first interface (`copy[0]`), and
`CacheParams` already carries the full interface array (order-sensitive). The
generated package is now a pure function of that array, so two identical arrays
share one cache entry and one generated class; two arrays differing only in the
first interface (or order) are distinct entries but each is self-consistent.
`evict`/`evictClassLoader` keying is unaffected. The roadmap's open "缓存键语义"
question therefore needs no code change — only documentation.

### 2.6 Files touched

| File                              | Change                                                                                                                            |
|-----------------------------------|-----------------------------------------------------------------------------------------------------------------------------------|
| `AcceleratedProxy.java`           | **New** private `nonPublicAnchor(Class<?>[])`; call it in `proxyInterfaces` (validation) and in `generateProxyClass` (package + lookup). |
| `generator/InterfaceGenerator.java` | Constructor gains a `packagePrefix` parameter; `generate()` uses it instead of the hardcoded `io/github/lamspace/`.                |
| `internal/LookupManager.java`     | **No change** — reuses `getLookup` as-is.                                                                                          |

No change to `ClassGenerator`, `InterfaceMethodResolver`, `InterfaceDispatcher`,
`DispatchGenerator`, `MethodDispatcher`, `MethodMapping`, `WeakCache`, `Group`,
`Interceptor`, `DispatchTarget`, `Rebindable`, or `invokeSuper`.

### 2.7 Why the rest already works

`InterfaceMethodResolver.resolve` and the `<clinit>` `Method` resolution both use
`Class.getMethods()` / `Class.getMethod()`, which return only `public` methods —
and interface methods (abstract and `default`) are implicitly `public`, even in a
package-private interface. So method collection, the `intercept` `Method`
argument, and `<clinit>` resolution need **no change**. Likewise, `INVOKESPECIAL`
to a package-private `default` method is legal once the proxy lives in the same
package. The generated class's `extends Object` + `implements DispatchTarget,
Rebindable` remain valid: both OpenProxy interfaces are `public`.

## 3. Error handling

1. Different-package non-`public` interfaces → `IllegalArgumentException`
   ("cannot proxy non-public interfaces from different packages: A and B").
   Surfaced **unwrapped**: `nonPublicAnchor` is invoked in `proxyInterfaces`
   *before* the cache, matching how the return-type/default conflicts already
   escape `matchMethods`.
2. Non-`public` anchor in a closed named module → `IllegalArgumentException`
   with the `--add-opens` hint (existing `LookupManager` behavior, re-thrown
   as-is by `generateProxyClass`).
3. All other failure modes (null/empty interfaces, non-interface element,
   null/empty groups, null interceptor) → unchanged.

## 4. Testing

### 4.1 New files

- `src/test/java/io/github/lamspace/pkgprivate/SecretService.java` — a
  package-private interface with an abstract method and a `default` method:

  ```java
  package io.github.lamspace.pkgprivate;
  interface SecretService {
      String greet(String name);
      default String shout(String s) { return s.toUpperCase(); }
  }
  ```

- `src/test/java/io/github/lamspace/otherpkg/OtherSecretService.java` — a second
  package-private interface in a *different* package (used only via
  `Class.forName` for the conflict test).

- `src/test/java/io/github/lamspace/pkgprivate/NonPublicInterfaceProxyTest.java`
  — package-private test class (JUnit 5 supports it), same package as
  `SecretService` so it can reference the type directly. The `PublicMarker`
  interface referenced in scenario 3 is a `public` interface declared in this
  test file (or in `io.github.lamspace`), used to exercise the mixed array.

### 4.2 Functional scenarios (verification)

| # | Scenario                                      | Assertion                                                                                                    |
|---|-----------------------------------------------|--------------------------------------------------------------------------------------------------------------|
| 1 | Proxy a package-private interface            | `proxy(SecretService.class, (o,m,a) -> "hi " + a[0])` → `greet("x")` returns `"hi x"`; interceptor saw the right `Method` + args |
| 2 | `invokeSuper` on a package-private `default` | interceptor calls `invokeSuper(o, m, a)`; `shout("hi")` returns `"HI"` (default impl, not recursive)          |
| 3 | Mixed public + package-private (same package)| `proxy(new Class[]{PublicMarker.class, SecretService.class}, …)` works; both interfaces' methods dispatch      |
| 4 | Non-public interfaces in different packages  | `proxy(new Class[]{SecretService.class, Class.forName("…otherpkg.OtherSecretService")}, …)` → `IllegalArgumentException` |
| 5 | Return types                                 | `void`/primitive/reference round-trip through the package-private interface                                  |
| 6 | Non-matching method passthrough              | a method matching no `Group` bypasses the interceptor (direct super call)                                    |
| 7 | Cache identity                                | two `proxy(SecretService.class, …)` calls share one generated class (same behavior, not a duplicate class)    |
| 8 | `evict` + re-proxy                            | `evict(SecretService.class)` then re-`proxy` produces a fresh working class (hot-reload keying intact)        |

### 4.3 Regression scenarios

| # | Scenario                                                        | Assertion                                                                                     |
|---|-----------------------------------------------------------------|-----------------------------------------------------------------------------------------------|
| 9 | Public JDK interface still works (no `--add-opens`)             | `proxy(java.util.function.Function.class, …)` intercepts `apply`; does **not** throw `--add-opens` (proves the public path stayed on `MethodHandles.lookup()`) |
| 10| Existing interface/class/static/constructor suites stay green   | full `mvn test` passes, especially `AcceleratedProxyInterfaceProxyTest`, `MultiInterfaceProxyTest`, `DefaultMethodInvocationTest`, `JpmsStrongEncapsulationTest`, `HotReloadTest`, `RebindInterfaceProxyTest` |
| 11| Public single-interface bytecode unchanged                      | all-public `InterfaceGenerator` output is byte-for-byte identical (name derivation and lookup unchanged — §2.3); guarded procedurally by the full-suite run |

### 4.4 Manual verification

The functional tests are the verification; the library has no runtime. For a
manual smoke check, run `NonPublicInterfaceProxyTest` and confirm case 9 does
not require any `--add-opens`. Benchmark suite is not affected (no hot-path
file changed) and needs no update.

## 5. Documentation changes

- `docs/openproxy-future-roadmap.md`: mark Phase 3 item 10 **非 public 接口代理** as
  已完成; add a `### 非 public 接口代理（已完成）` subsection describing the
  anchor-package rule, the different-package conflict, and that the all-public
  path is unchanged; fold in the resolved "缓存键语义" note.
- `README.md` / `README_CN.md`: add a "Non-public interface proxy" feature
  bullet and a short example (package-private interface + `proxy`).
- `openspec/specs/`: add `openspec/specs/non-public-interface-proxy/spec.md`
  with requirement scenarios mirroring §4 (matching the per-feature openspec
  convention used by the other Phase 3 items).
- Javadoc: update `proxy(Class<?>[], …)` and the `AcceleratedProxy` class-level
  doc to state non-public interface support and the single-package constraint;
  update `InterfaceGenerator` Javadoc for the new `packagePrefix` parameter.
- `docs/migration-guide.md`: no entry — purely additive, and CGLib/JDK proxies
  cannot proxy package-private interfaces either.

## 6. Deliberate decisions

1. **On-demand private lookup, not the literal "always `privateLookupIn` first
   interface".** The roadmap sentence describes the *end state* (use
   `LookupManager` so the class lands in the interface's package); taking it
   literally would regress public interfaces in strongly-encapsulated modules
   (item 8's interface path). The private lookup is applied only when a
   non-`public` interface is present.
2. **Anchor = first non-public interface's package; all non-public interfaces
   must share it.** Derived from the JVM rule that a package-private interface
   is accessible only from its own package, and a hidden class lives in one
   package. Public interfaces stay unconstrained.
3. **All-public path byte-for-byte unchanged.** `packagePrefix` defaults to
   `io/github/lamspace/` and the lookup stays `MethodHandles.lookup()`; the only
   generator diff is a parameterized prefix that resolves to the same literal.
4. **Cache key unchanged.** The full interface array already lives in
   `CacheParams`, so the generated package is a pure function of the existing
   key material; no new key semantics are needed.
5. **Validation lives in `proxyInterfaces` (before the cache).** This keeps the
   different-package error an unwrapped `IllegalArgumentException`, consistent
   with the return-type/default conflicts already thrown from `matchMethods`.
6. **`LookupManager` unchanged.** `getLookup` already implements exactly the
   right semantics (`privateLookupIn` + `--add-opens` fast-fail + primitive/array
   fallback); the non-public anchor is a normal class from its perspective.
7. **Scope is package-private only.** Private/protected *nested* interfaces are
   out of scope; in the unnamed module `privateLookupIn` succeeds regardless of
   the target's own modifier, so they fail later at `defineHiddenClass` with an
   `IllegalAccessError` (an `Error`, not wrapped) — loud failure, never silent
   misbehavior.

## 7. Out of scope

- Private or `protected` nested interfaces (require nestmate access; not
  "package-private").
- Proxying a non-`public` interface in a named module whose package is not open
  to the unnamed module (needs `--add-opens`, same as item 8 for classes).
- Package-private interfaces in the default (unnamed) package (untested edge;
  the prefix derivation handles it but it is not a target).
- Any change to the existing multi-interface signature / return-type / `default`
  conflict rules.
- Non-public *static* method proxying (item 5 territory).

# Non-Public Interface Proxy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let `AcceleratedProxy.proxy(...)` proxy package-private (non-`public`) interfaces by defining the generated hidden class in the interface's own package.

**Architecture:** A private `nonPublicAnchor(Class<?>[])` helper finds the first non-`public` interface (or `null` if all are public) and validates that all non-`public` interfaces share one package. `generateProxyClass` derives the generated class's package prefix and the `Lookup` from that anchor; `InterfaceGenerator` takes the package prefix as a constructor argument. The all-public path is byte-for-byte unchanged.

**Tech Stack:** Java 25, ASM 9.7.1 (`org.objectweb.asm`), JUnit 5 (Jupiter), Maven. Generated classes use `MethodHandles.Lookup.defineHiddenClass`.

**Spec:** `docs/superpowers/specs/2026-08-16-non-public-interface-proxy-design.md`

## Global Constraints

- Java source/target level: **25** (pom.xml `<source>25</source>`).
- All `mvn` commands MUST include `-s /home/lam/repo/settings.xml`.
- ASM bytecode level already `Opcodes.V24`; do not change it.
- The all-public interface path MUST stay byte-for-byte identical (same name, `MethodHandles.lookup()`).
- Commit message style: `feat:` / `test:` / `docs:` (conventional commits, matching repo history).
- Work on a feature branch off `master` (e.g. `git switch -c feat/non-public-interface-proxy`) before the first commit.

---

### Task 1: Core — proxy a package-private interface

**Files:**
- Create: `src/test/java/io/github/lamspace/pkgprivate/SecretService.java`
- Create: `src/test/java/io/github/lamspace/pkgprivate/NonPublicInterfaceProxyTest.java`
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (add helper; wire into `proxyInterfaces` ~line 762 and `generateProxyClass` ~line 261)
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java` (add `packagePrefix` field + constructor param; use it in `generate()` ~line 79)

**Interfaces:**
- Produces: `private static Class<?> nonPublicAnchor(Class<?>[] interfaces)`; new `InterfaceGenerator(Class<?>[], Interceptor[], MethodMapping, String packagePrefix)` constructor.

- [ ] **Step 1: Create the package-private test interface**

Create `src/test/java/io/github/lamspace/pkgprivate/SecretService.java`:

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace.pkgprivate;

/**
 * Package-private interface used to exercise non-public interface proxying.
 * Deliberately non-{@code public}: the generated proxy must be defined into
 * this package to implement it.
 */
interface SecretService {

    String greet(String name);

    default String shout(String s) {
        return s.toUpperCase();
    }
}
```

- [ ] **Step 2: Write the failing test**

Create `src/test/java/io/github/lamspace/pkgprivate/NonPublicInterfaceProxyTest.java`:

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace.pkgprivate;

import io.github.lamspace.AcceleratedProxy;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class NonPublicInterfaceProxyTest {

    @Test
    void proxiesPackagePrivateInterface() {
        SecretService proxy = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> "hi " + a[0]);

        assertEquals("hi bob", proxy.greet("bob"));
    }

    @Test
    void invokeSuperCallsPackagePrivateDefaultMethod() {
        SecretService proxy = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> AcceleratedProxy.invokeSuper(o, m, a));

        assertEquals("HELLO", proxy.shout("hello"));
    }
}
```

- [ ] **Step 3: Run the test to confirm it fails**

Run: `mvn -s /home/lam/repo/settings.xml -Dtest=NonPublicInterfaceProxyTest test`

Expected: FAIL — `AcceleratedProxy.proxy(SecretService.class, …)` throws (today the hidden class is defined in `io.github.lamspace` and cannot implement the package-private `SecretService`). A `RuntimeException` wrapping `IllegalAccessError`/`VerifyError` is acceptable; the point is the proxy is not created.

- [ ] **Step 4: Add the `nonPublicAnchor` helper to `AcceleratedProxy.java`**

Insert immediately after the `matchMethods(Class<?>[] interfaces, Group[] groups)` method (the overload ending around line 169, before the class-target `matchMethods` overload):

```java
    /**
     * Returns the first non-{@code public} interface in {@code interfaces}, or
     * {@code null} if all are public. Validates that all non-public interfaces
     * share a single package (a JVM class can only be defined in one package,
     * and a package-private interface is accessible only from its own package).
     *
     * @param interfaces the interfaces to proxy
     * @return the anchor non-public interface, or {@code null}
     * @throws IllegalArgumentException if non-public interfaces span packages
     */
    private static Class<?> nonPublicAnchor(Class<?>[] interfaces) {
        Class<?> anchor = null;
        for (Class<?> itf : interfaces) {
            if (!Modifier.isPublic(itf.getModifiers())) {
                if (anchor == null) {
                    anchor = itf;
                } else if (!anchor.getPackageName().equals(
                        itf.getPackageName())) {
                    throw new IllegalArgumentException(
                            "cannot proxy non-public interfaces from different "
                                    + "packages: " + anchor.getName()
                                    + " and " + itf.getName());
                }
            }
        }
        return anchor;
    }
```

`Modifier` is already imported (line 30). No new import.

- [ ] **Step 5: Validate early in `proxyInterfaces`**

In `AcceleratedProxy.proxyInterfaces` (the method ending ~line 780), change:

```java
        Class<?>[] copy = interfaces.clone();
        MatchResult matchResult = matchMethods(copy, groups);
```

to:

```java
        Class<?>[] copy = interfaces.clone();
        nonPublicAnchor(copy); // fail fast on cross-package non-public interfaces
        MatchResult matchResult = matchMethods(copy, groups);
```

- [ ] **Step 6: Wire anchor → package + lookup in `generateProxyClass`**

In `generateProxyClass` (~line 261), replace the interface branch:

```java
            if (params.interfaces() != null) {
                InterfaceGenerator generator = new InterfaceGenerator(
                        params.interfaces(), dummy, mapping);
                bytecode = generator.generate();
                return java.lang.invoke.MethodHandles.lookup()
                        .defineHiddenClass(bytecode, true).lookupClass();
            } else {
```

with:

```java
            if (params.interfaces() != null) {
                Class<?>[] interfaces = params.interfaces();
                Class<?> anchor = nonPublicAnchor(interfaces);
                // All-public interfaces keep the historical OpenProxy package; a
                // non-public anchor places the class in that interface's
                // package so it can implement a package-private interface.
                String packagePrefix = "io/github/lamspace/";
                if (anchor != null) {
                    String pkg = anchor.getPackageName();
                    packagePrefix = pkg.isEmpty() ? "" : pkg.replace('.', '/') + "/";
                }
                InterfaceGenerator generator = new InterfaceGenerator(
                        interfaces, dummy, mapping, packagePrefix);
                bytecode = generator.generate();
                MethodHandles.Lookup lookup = (anchor == null)
                        ? MethodHandles.lookup()
                        : LookupManager.getLookup(anchor);
                return lookup.defineHiddenClass(bytecode, true).lookupClass();
            } else {
```

- [ ] **Step 7: Add the `packagePrefix` parameter to `InterfaceGenerator`**

In `InterfaceGenerator.java`:

1. Add a field after `private final MethodMapping mapping;` (line 45):

```java
    private final String packagePrefix;
```

2. Change the constructor (lines 54-60) to accept and store it:

```java
    /**
     * Creates a generator for the given interfaces.
     *
     * @param interfaces    the interfaces to implement
     * @param interceptors  deduped interceptor instances
     * @param mapping       method → interceptor index mapping
     * @param packagePrefix internal-name package prefix for the generated
     *                      class (e.g. {@code "io/github/lamspace/"} or
     *                      {@code "com/example/pkg/"})
     */
    public InterfaceGenerator(Class<?>[] interfaces,
                              Interceptor[] interceptors,
                              MethodMapping mapping,
                              String packagePrefix) {
        this.interfaces = interfaces.clone();
        this.interceptors = interceptors.clone();
        this.mapping = mapping;
        this.packagePrefix = packagePrefix;
    }
```

3. In `generate()` (~line 79), change:

```java
        String generatedInternal = "io/github/lamspace/" + baseName
                + "$$AcceleratedProxy$$" + COUNTER.getAndIncrement();
```

to:

```java
        String generatedInternal = packagePrefix + baseName
                + "$$AcceleratedProxy$$" + COUNTER.getAndIncrement();
```

- [ ] **Step 8: Run the test to confirm it passes**

Run: `mvn -s /home/lam/repo/settings.xml -Dtest=NonPublicInterfaceProxyTest test`

Expected: PASS — both `proxiesPackagePrivateInterface` and `invokeSuperCallsPackagePrivateDefaultMethod` pass.

- [ ] **Step 9: Run the full suite as a first regression check**

Run: `mvn -s /home/lam/repo/settings.xml test`

Expected: PASS — no existing test regresses (in particular `AcceleratedProxyInterfaceProxyTest`, `MultiInterfaceProxyTest`, `DefaultMethodInvocationTest`, `JpmsStrongEncapsulationTest`, `HotReloadTest`, `RebindInterfaceProxyTest`).

- [ ] **Step 10: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java \
        src/main/java/io/github/lamspace/generator/InterfaceGenerator.java \
        src/test/java/io/github/lamspace/pkgprivate/SecretService.java \
        src/test/java/io/github/lamspace/pkgprivate/NonPublicInterfaceProxyTest.java
git commit -m "feat: proxy package-private interfaces by defining the proxy in the interface's package"
```

---

### Task 2: Conflict + mixed-array + cache/evict scenarios

**Files:**
- Create: `src/test/java/io/github/lamspace/otherpkg/OtherSecretService.java`
- Modify: `src/test/java/io/github/lamspace/pkgprivate/NonPublicInterfaceProxyTest.java` (add tests)

**Interfaces:**
- Consumes: `nonPublicAnchor` behavior from Task 1 (the cross-package `IllegalArgumentException` and the mixed-array package selection).

- [ ] **Step 1: Create a second package-private interface in a different package**

Create `src/test/java/io/github/lamspace/otherpkg/OtherSecretService.java`:

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace.otherpkg;

/** Package-private interface in a second package, for the cross-package conflict test. */
interface OtherSecretService {

    int compute();
}
```

- [ ] **Step 2: Add the four scenario tests**

In `NonPublicInterfaceProxyTest.java`, first change the static import at the top from:

```java
import static org.junit.jupiter.api.Assertions.assertEquals;
```

to:

```java
import static org.junit.jupiter.api.Assertions.*;
```

(the new tests use `assertThrows`, `assertSame`, and `assertNotSame` too). Then append the following inside the class body:

```java
    /** Public interface in the test package, mixed with a package-private one. */
    public interface PublicMarker {
        String mark();
    }

    @Test
    void mixedPublicAndPackagePrivateInterfaces() {
        Object proxy = AcceleratedProxy.proxy(
                new Class<?>[]{PublicMarker.class, SecretService.class},
                (o, m, a) -> "x");

        assertEquals("x", ((PublicMarker) proxy).mark());
        assertEquals("x", ((SecretService) proxy).greet("ignored"));
    }

    @Test
    void nonPublicInterfacesInDifferentPackagesThrow() throws Exception {
        Class<?> other = Class.forName(
                "io.github.lamspace.otherpkg.OtherSecretService");

        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(
                        new Class<?>[]{SecretService.class, other},
                        (o, m, a) -> null));
    }

    @Test
    void cachesGeneratedClassPerInterface() {
        SecretService a = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> "x");
        SecretService b = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> "y");

        assertSame(a.getClass(), b.getClass());
    }

    @Test
    void evictAndReproxy() {
        SecretService first = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> "a");
        Class<?> cls = first.getClass();

        AcceleratedProxy.evict(SecretService.class);
        SecretService second = AcceleratedProxy.proxy(SecretService.class,
                (o, m, a) -> "b");

        assertNotSame(cls, second.getClass());
        assertEquals("b", second.greet("x"));
    }
```

- [ ] **Step 3: Run the test class**

Run: `mvn -s /home/lam/repo/settings.xml -Dtest=NonPublicInterfaceProxyTest test`

Expected: PASS — all six tests green. (These verify behavior already implemented in Task 1; they should pass on the first run. If `nonPublicInterfacesInDifferentPackagesThrow` fails because the exception is wrapped, re-read the spec §3: the validation must run in `proxyInterfaces` before the cache so the `IllegalArgumentException` is unwrapped — confirm Step 5 of Task 1 is in place.)

- [ ] **Step 4: Commit**

```bash
git add src/test/java/io/github/lamspace/otherpkg/OtherSecretService.java \
        src/test/java/io/github/lamspace/pkgprivate/NonPublicInterfaceProxyTest.java
git commit -m "test: cover mixed-array, cross-package conflict, cache and evict for non-public interface proxy"
```

---

### Task 3: Public JDK interface regression guard

**Files:**
- Create: `src/test/java/io/github/lamspace/PublicJdkInterfaceProxyTest.java`

**Interfaces:**
- Consumes: the all-public lookup path (unchanged from before this feature). This test locks in that `MethodHandles.lookup()` is still used when no non-public interface is present.

- [ ] **Step 1: Write the regression test**

Create `src/test/java/io/github/lamspace/PublicJdkInterfaceProxyTest.java`:

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.util.function.Function;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PublicJdkInterfaceProxyTest {

    @Test
    void proxiesPublicJdkInterfaceWithoutAddOpens() {
        // Guards the all-public path: a public java.base interface must stay
        // proxyable via MethodHandles.lookup() — no --add-opens required. If
        // the private lookup were applied unconditionally, this would throw an
        // IllegalArgumentException carrying an --add-opens hint.
        Function<String, String> proxy = AcceleratedProxy.proxy(
                Function.class, (o, m, a) -> "intercepted:" + a[0]);

        assertEquals("intercepted:abc", proxy.apply("abc"));
    }
}
```

- [ ] **Step 2: Run the test**

Run: `mvn -s /home/lam/repo/settings.xml -Dtest=PublicJdkInterfaceProxyTest test`

Expected: PASS.

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/PublicJdkInterfaceProxyTest.java
git commit -m "test: guard the all-public interface path against a private-lookup regression"
```

---

### Task 4: Documentation + final regression gate

**Files:**
- Modify: `docs/openproxy-future-roadmap.md`
- Modify: `README.md`
- Modify: `README_CN.md`
- Create: `openspec/specs/non-public-interface-proxy/spec.md`
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (Javadoc on `proxy(Class<?>[], …)`)

**Interfaces:**
- Consumes: nothing new; documents Task 1-3 behavior.

- [ ] **Step 1: Update the roadmap**

In `docs/openproxy-future-roadmap.md`:

1. Change item 10's row (line 47) from:

```
| 10   | P3     | **非 public 接口代理**         | 接口代理对首个接口走 `privateLookupIn`，支持代理包级私有的非 public 接口 |
```

to:

```
| 10   | P3     | **非 public 接口代理**（已完成） | 存在非 public 接口时用 `privateLookupIn` 把隐藏类定义到该接口所在包，支持包级私有接口；全 public 路径不变 |
```

2. Replace the entire `### 非 public 接口代理` section (lines 138-142) with:

```markdown
### 非 public 接口代理（已完成）

- 当接口集合中存在非 public 接口时，用 `LookupManager.getLookup` 获取该接口包的私有 lookup，把隐藏类定义到该接口所在包，从而支持代理包级私有的非 public 接口
- 所有非 public 接口必须位于同一包，跨包时抛 `IllegalArgumentException`；public 接口可位于任意包
- 全 public 接口时仍走 `MethodHandles.lookup()`，隐藏类留在 `io.github.lamspace` 包——public JDK 接口（如 `java.util.function.Function`）照常可用，字节码不变
- 缓存键语义不变：仍以首个接口为弱键，`CacheParams` 已含完整接口数组，生成包名是该数组的纯函数
```

- [ ] **Step 2: Update the README**

In `README.md`:

1. Add a feature bullet immediately after the "Static method proxy" bullet (line 24):

```markdown
- **Non-public interface proxy** — package-private interfaces are proxied by defining the generated class in the interface's own package (the all-public path is unchanged)
```

2. Add a Quick Start subsection immediately before `### Hot Reload / Hot Swap` (line 147):

````markdown
### Non-Public Interface Proxy

```java
// package-private interface in your package
interface SecretService {
    String greet(String name);
    default String shout(String s) { return s.toUpperCase(); }
}

SecretService proxy = AcceleratedProxy.proxy(SecretService.class, (obj, method, args) ->
        AcceleratedProxy.invokeSuper(obj, method, args));

proxy.greet("world");   // routed through the interceptor
proxy.shout("hi");      // default method — invokeSuper calls the default impl
```
````

- [ ] **Step 3: Update the Chinese README**

In `README_CN.md`, mirror Step 2 in Chinese:

1. Feature bullet (after the static-method-proxy bullet):

```markdown
- **非 public 接口代理** — 包级私有接口通过在接口自身包内定义代理类来代理（全 public 路径保持不变）
```

2. A `### 非 Public 接口代理` Quick Start subsection (placed before the hot-reload section):

````markdown
### 非 Public 接口代理

```java
// 你包内的包级私有接口
interface SecretService {
    String greet(String name);
    default String shout(String s) { return s.toUpperCase(); }
}

SecretService proxy = AcceleratedProxy.proxy(SecretService.class, (obj, method, args) ->
        AcceleratedProxy.invokeSuper(obj, method, args));

proxy.greet("world");   // 经拦截器路由
proxy.shout("hi");      // default 方法 — invokeSuper 调用默认实现
```
````

- [ ] **Step 4: Add the openspec capability spec**

Create `openspec/specs/non-public-interface-proxy/spec.md`:

```markdown
## Purpose

Support proxying package-private (non-`public`) interfaces by defining the
generated hidden class in the interface's own package, while leaving the
all-public path byte-for-byte unchanged.

### Requirement: Non-public interface proxying

The system SHALL generate a proxy that implements a package-private interface,
defining the generated hidden class in that interface's package so the JVM
access rule is satisfied.

#### Scenario: Package-private interface proxied

- **WHEN** user calls `proxy(SecretService.class, interceptor)` where `SecretService` is package-private
- **THEN** the returned proxy implements `SecretService`
- **AND** method calls route through `interceptor.intercept(proxy, method, args)`

#### Scenario: invokeSuper on a package-private default method

- **WHEN** a package-private interface declares a `default` method
- **AND** the interceptor calls `invokeSuper(proxy, method, args)`
- **THEN** the `default` implementation runs

### Requirement: Mixed public and non-public interfaces

The system SHALL proxy an array mixing `public` interfaces (any package) with
non-public interfaces that share a single package; that shared package becomes
the generated class's package.

#### Scenario: Public plus package-private interface

- **WHEN** user calls `proxy(new Class<?>[]{PublicMarker.class, SecretService.class}, interceptor)`
- **THEN** the proxy implements both interfaces and routes both through the interceptor

### Requirement: Cross-package non-public rejection

The system SHALL throw `IllegalArgumentException` when the interface array
contains non-public interfaces from different packages.

#### Scenario: Different-package non-public interfaces rejected

- **WHEN** two non-public interfaces reside in different packages
- **THEN** `proxy(...)` throws `IllegalArgumentException`

### Requirement: All-public path unchanged

The system SHALL keep the all-public interface path byte-for-byte unchanged:
the generated class stays in `io.github.lamspace` and is defined with
`MethodHandles.lookup()`, so public JDK interfaces in strongly-encapsulated
modules remain proxyable without `--add-opens`.

#### Scenario: Public JDK interface still proxied

- **WHEN** user calls `proxy(java.util.function.Function.class, interceptor)`
- **THEN** the proxy works without any `--add-opens` JVM argument
```

- [ ] **Step 5: Update the `proxy(Class<?>[], …)` Javadoc**

In `AcceleratedProxy.java`, extend the Javadoc of `proxy(Class<?>[] interfaces, Interceptor interceptor)` (the method around line 377-396) with a sentence noting the new capability. Add after the existing summary paragraph:

```java
     * <p>Package-private (non-{@code public}) interfaces are supported: the
     * generated class is defined in the package shared by all non-public
     * interfaces, so all non-public interfaces must share one package (or
     * {@link IllegalArgumentException} is thrown).
```

- [ ] **Step 6: Full-suite regression gate**

Run: `mvn -s /home/lam/repo/settings.xml test`

Expected: PASS — the entire suite (all prior feature tests plus the new `NonPublicInterfaceProxyTest` and `PublicJdkInterfaceProxyTest`) is green.

- [ ] **Step 7: Commit**

```bash
git add docs/openproxy-future-roadmap.md README.md README_CN.md \
        openspec/specs/non-public-interface-proxy/spec.md \
        src/main/java/io/github/lamspace/AcceleratedProxy.java
git commit -m "docs: document non-public interface proxy in roadmap, README, openspec, and Javadoc"
```

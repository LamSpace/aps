# JPMS Strong Encapsulation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Replace `LookupManager`'s silent fallback to a wrong-package lookup with a fail-fast `IllegalArgumentException` carrying an actionable `--add-opens` hint, so proxying a class in a strongly encapsulated module fails clearly instead of with a misleading name-package error.

**Architecture:** `LookupManager.getLookup` already attempts `MethodHandles.privateLookupIn` first; the change turns its `IllegalAccessException` branch from a logged fallback into a throw. `AcceleratedProxy.generateProxyClass` then re-throws `IllegalArgumentException` unchanged so the hint reaches the caller as the direct cause of `proxy(...)`'s `RuntimeException`, rather than being buried under `Failed to generate proxy class`. The hot-path emitters are untouched.

**Tech Stack:** Java 25, ASM 9.7.1, JUnit 5.11.4.

**Spec:** `docs/superpowers/specs/2026-08-15-jpms-strong-encapsulation-design.md`

## Global Constraints

- All `mvn` commands MUST use `-s /home/lam/repo/settings.xml`.
- Java source/target 25; generated classes use `Opcodes.V24`.
- Do **not** modify `ClassGenerator`, `InterfaceGenerator`, `MethodDispatcher`, `InterfaceDispatcher`, `DispatchGenerator`, `WeakCache`, `Interceptor`, `Group`, or `MethodPredicate` — the hot path must stay byte-identical.
- No new public types; use `IllegalArgumentException` for the module-access failure.
- Follow existing code style: Javadoc on every public method, `IllegalArgumentException` for bad arguments, ASL-2.0 header on new files.
- Primitive/array fallback in `getLookup` is preserved (those are `IllegalArgumentException`, not module denial).

---

### Task 1: `LookupManager.getLookup` fail-fast

**Files:**
- Modify: `src/main/java/io/github/lamspace/internal/LookupManager.java`
- Test: `src/test/java/io/github/lamspace/internal/LookupManagerTest.java`

**Interfaces:**
- Produces: `MethodHandles.Lookup LookupManager.getLookup(Class<?> targetClass)` — now throws `IllegalArgumentException` (with `--add-opens` hint) when `privateLookupIn` is denied by strong encapsulation. Consumed by Task 2.

- [ ] **Step 1: Write the failing tests**

Two edits in `LookupManagerTest.java`.

(a) Change `shouldReturnNonNullLookupForStandardClass` from `String.class` (which is itself strongly encapsulated) to a classpath class:

```java
    @Test
    void shouldReturnNonNullLookupForStandardClass() {
        // A classpath class lives in the unnamed module, which is always open.
        MethodHandles.Lookup lookup = LookupManager.getLookup(LookupManager.class);
        assertNotNull(lookup);
    }
```

(b) Add a fail-fast test after `shouldReturnLookupForInnerClass`:

```java
    @Test
    void shouldThrowActionableErrorForStronglyEncapsulatedClass() {
        // java.util is exported but not open, so privateLookupIn is denied.
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LookupManager.getLookup(java.util.ArrayList.class));

        assertTrue(ex.getMessage().contains("--add-opens"),
                "message should contain a --add-opens hint: "
                        + ex.getMessage());
    }
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -s /home/lam/repo/settings.xml -q test -Dtest=LookupManagerTest`
Expected: FAIL — `shouldThrowActionableErrorForStronglyEncapsulatedClass` fails with "Expected IllegalArgumentException to be thrown, but nothing was thrown" (the current code still falls back silently).

- [ ] **Step 3: Implement the fail-fast branch**

In `LookupManager.java`, replace three things: the class Javadoc (currently
"…degrades gracefully to a regular lookup if the module system denies it."),
the method Javadoc (currently "…falls back to a regular … public Lookup."), and
the `IllegalAccessException` catch branch. The new class Javadoc is:

```java
/**
 * Obtains a {@link MethodHandles.Lookup} with private access to the given
 * target class.
 *
 * <p>Attempts {@link MethodHandles#privateLookupIn(Class, MethodHandles.Lookup)}
 * to obtain full private access. If the target class is in a strongly
 * encapsulated module whose package is not open, an
 * {@link IllegalArgumentException} is thrown with an actionable
 * {@code --add-opens} hint. Primitive and array types (rejected by
 * {@code privateLookupIn}) fall back to a public lookup.
 */
public final class LookupManager {
```

```java
    /**
     * Returns a Lookup with private access to {@code targetClass}.
     * <p>
     * Attempts {@link MethodHandles#privateLookupIn(Class, MethodHandles.Lookup)}
     * to obtain full private access. If the target class is in a strongly
     * encapsulated module whose package is not open, an
     * {@link IllegalArgumentException} is thrown with an actionable
     * {@code --add-opens} hint. Primitive and array types (rejected by
     * {@code privateLookupIn}) fall back to a public lookup.
     *
     * @param targetClass the class to obtain a Lookup for
     * @return a Lookup with private access to the target class
     * @throws IllegalArgumentException if the target module does not open the
     *                                  target class's package
     */
    public static MethodHandles.Lookup getLookup(Class<?> targetClass) {
        try {
            return MethodHandles.privateLookupIn(targetClass,
                    MethodHandles.lookup());
        } catch (IllegalAccessException e) {
            String moduleName = targetClass.getModule().getName();
            String packageName = targetClass.getPackageName();
            throw new IllegalArgumentException(
                    "Cannot access " + targetClass.getName() + " in module "
                            + moduleName + " (package " + packageName
                            + "): the package is not open to the unnamed "
                            + "module. Add --add-opens " + moduleName + "/"
                            + packageName + "=ALL-UNNAMED to the JVM "
                            + "arguments, or declare 'opens " + packageName
                            + ";' in the module's module-info.java.", e);
        } catch (IllegalArgumentException e) {
            // Primitive and array classes are rejected by privateLookupIn —
            // fall back to public lookup for these edge cases
            LOGGER.fine(() -> "privateLookupIn rejected "
                    + targetClass.getName() + ": " + e.getMessage());
            return MethodHandles.lookup();
        }
    }
```

The `LOGGER` field and its `java.util.logging.Logger` import stay (still used by the `IllegalArgumentException` branch). The removed `LOGGER.warning(...)` is the only deletion.

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml -q test -Dtest=LookupManagerTest`
Expected: PASS — all 5 tests green (including the two primitive/array fallback tests and `shouldReturnLookupForInnerClass`).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/internal/LookupManager.java \
        src/test/java/io/github/lamspace/internal/LookupManagerTest.java
git commit -m "feat: fail fast on strongly encapsulated module access"
```

---

### Task 2: Transparent error propagation through `generateProxyClass`

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java`
- Test: `src/test/java/io/github/lamspace/JpmsStrongEncapsulationTest.java` (new)

**Interfaces:**
- Consumes: `LookupManager.getLookup` throwing `IllegalArgumentException` (Task 1).
- Produces: `proxy(Class, ...)` surfacing that `IllegalArgumentException` as the direct cause of its `RuntimeException`.

- [ ] **Step 1: Write the failing test file**

Create `src/test/java/io/github/lamspace/JpmsStrongEncapsulationTest.java` with the Apache header, package `io.github.lamspace`:

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

import java.util.ArrayList;

import static org.junit.jupiter.api.Assertions.*;

class JpmsStrongEncapsulationTest {

    @Test
    void proxyStronglyEncapsulatedClassFailsWithActionableHint() {
        RuntimeException ex = assertThrows(RuntimeException.class, () ->
                AcceleratedProxy.proxy(ArrayList.class, (o, m, a) -> null));

        Throwable cause = ex.getCause();
        assertTrue(cause instanceof IllegalArgumentException,
                "expected IllegalArgumentException cause, got " + cause);
        assertTrue(cause.getMessage().contains("--add-opens"),
                "message should contain --add-opens: " + cause.getMessage());
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml -q test -Dtest=JpmsStrongEncapsulationTest`
Expected: FAIL — `ex.getCause()` is currently `RuntimeException("Failed to generate proxy class")`, not an `IllegalArgumentException` with `--add-opens`.

- [ ] **Step 3: Re-throw `IllegalArgumentException` in `generateProxyClass`**

In `AcceleratedProxy.java`, in `generateProxyClass` (the class-proxy branch), add a specific catch before the existing `catch (Exception e)`:

```java
        } catch (IllegalArgumentException e) {
            // Surface actionable errors (e.g. the --add-opens hint) as-is
            // instead of burying them under a generic wrapper.
            throw e;
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to generate proxy class", e);
        }
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -s /home/lam/repo/settings.xml -q test -Dtest=JpmsStrongEncapsulationTest,LookupManagerTest`
Expected: PASS.

- [ ] **Step 5: Run the full test suite (regression)**

Run: `mvn -s /home/lam/repo/settings.xml -q test`
Expected: PASS — all green; no existing test regressed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java \
        src/test/java/io/github/lamspace/JpmsStrongEncapsulationTest.java
git commit -m "feat: surface module-access error through proxy() unchanged"
```

---

### Task 3: Documentation

**Files:**
- Modify: `docs/aps-future-roadmap.md`
- Modify: `README.md`
- Modify: `README_CN.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Update the roadmap**

In `docs/aps-future-roadmap.md`, make four edits:

(a) Mark item 8 done — change `**JPMS 强封装模块**` (in the Phase 3 table row) to `**JPMS 强封装模块**（已完成）`.

(b) Add item 10 after the item 9 row. After:

```
| 9    | P3     | **Maven Central 发布**         | 让其他项目能通过 Maven/Gradle 依赖引入，GroupId: `io.github.lamspace` |
```

append:

```
| 10   | P3     | **非 public 接口代理**         | 接口代理对首个接口走 `privateLookupIn`，支持代理包级私有的非 public 接口 |
```

(c) Fix the item 6 cross-reference — change `跨 ClassLoader 热部署待 item 8` to `跨 ClassLoader 热部署（独立待办）` (item 8 is now done; cross-ClassLoader hot deployment is a separate open concern).

(d) Replace the `### JPMS 强封装模块` subsection (the three-bullet list) with:

```markdown
### JPMS 强封装模块（已完成）

- 类代理经 `LookupManager.getLookup(target)` 获取 lookup：优先 `MethodHandles.privateLookupIn`，成功即拿到目标类包内的私有访问
- 目标类位于强封装模块（包未 `open`，含 `java.base`/`java.util` 等 JDK 模块）时，`privateLookupIn` 抛 `IllegalAccessException`，改为**快速失败**：抛 `IllegalArgumentException`，消息给出可操作的 `--add-opens <module>/<package>=ALL-UNNAMED` 提示（或 `opens` 声明）
- 原始类型/数组仍降级到公共 lookup（非模块访问问题，且非合法代理目标）
- 接口代理仍用 `MethodHandles.lookup()`，仅支持 public 接口（与 `java.lang.reflect.Proxy` 一致）；非 public 接口支持见 item 10
- 附带：`generateProxyClass` 对 `IllegalArgumentException` 原样重抛，避免把可操作报错埋在 `Failed to generate proxy class` 下

### 非 public 接口代理

- 当前接口代理用 `MethodHandles.lookup()` 定义隐藏类，隐藏类落在 `io.github.lamspace` 包，只能实现 public 接口
- 计划：接口代理对首个接口也走 `LookupManager.getLookup`，把隐藏类定义在接口所在包，从而支持包级私有的非 public 接口
- 待完善：多接口跨包时的冲突规则、缓存键语义
```

- [ ] **Step 2: Update `README.md`**

Insert a JPMS section between the Requirements and Installation sections (after the `- ASM 9.7.1 (declared as compile dependency)` line):

```markdown
## 🧩 JPMS / Strong Encapsulation

APS class proxies are defined in the target class's package via
`MethodHandles.privateLookupIn`. When the target class lives in a
strongly encapsulated module — any package that is not `open`, including
`java.base` packages such as `java.util` — `privateLookupIn` is denied and
`proxy()` fails fast with an actionable error:

```text
Cannot access java.util.ArrayList in module java.base (package java.util):
the package is not open to the unnamed module. Add --add-opens
java.base/java.util=ALL-UNNAMED to the JVM arguments, ...
```

To proxy such a class, add the suggested `--add-opens` JVM argument, or
declare `opens <package>;` in the target module's `module-info.java`.

Interface proxies use a public lookup and support `public` interfaces only
(same as `java.lang.reflect.Proxy`).
```

- [ ] **Step 3: Update `README_CN.md`**

Insert the Chinese equivalent between 环境要求 and 安装 (after the `- ASM 9.7.1（编译依赖）` line):

```markdown
## 🧩 JPMS / 强封装模块

类代理通过 `MethodHandles.privateLookupIn` 将隐藏类定义在目标类所在包内。
当目标类位于强封装模块（任何未 `open` 的包，含 `java.util` 等 `java.base` 包）时，
`privateLookupIn` 会被拒绝，`proxy()` 会快速失败并给出可操作报错：

```text
Cannot access java.util.ArrayList in module java.base (package java.util):
the package is not open to the unnamed module. Add --add-opens
java.base/java.util=ALL-UNNAMED to the JVM arguments, ...
```

要代理此类，可加上提示的 `--add-opens` JVM 参数，或在目标模块的
`module-info.java` 中声明 `opens <package>;`。

接口代理使用公共 lookup，仅支持 public 接口（与 `java.lang.reflect.Proxy` 一致）。
```

- [ ] **Step 4: Commit**

```bash
git add docs/aps-future-roadmap.md README.md README_CN.md
git commit -m "docs: document JPMS strong encapsulation handling"
```

---

## Verification checklist (before merging)

- [ ] `mvn -s /home/lam/repo/settings.xml -q test` — all green.
- [ ] `LookupManagerTest` has the fail-fast test and the corrected `String.class` → `LookupManager.class` test.
- [ ] `JpmsStrongEncapsulationTest` passes (actionable `--add-opens` cause).
- [ ] `docs/aps-future-roadmap.md` item 8 marked 已完成 and item 10 added.
- [ ] README (EN + CN) carry the JPMS section.

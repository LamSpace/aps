# APS Unified Proxy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Unify class and interface proxy APIs under `APS.proxy()` with a single `Interceptor` callback, replace type-erased `MethodHandle` dispatch with direct `INVOKESPECIAL` super calls via hashCode switch, add class caching, and remove the old `Callback`/`InterfaceCallback`/`SuperDispatcher` types.

**Architecture:** The generated proxy class implements a new internal interface `DispatchTarget` whose `dispatch(Method, Object[])` method contains a hashCode-driven if-else chain. Each class-method branch calls `super.method(args)` directly (INVOKESPECIAL, JIT-inlinable). Interface-method branches throw `AbstractMethodError`. The hash values use `Method.hashCode()` (deterministic, pre-computable at bytecode generation time) allowing them to be embedded as `ldc` constants. A `WeakCache` stores generated proxy classes keyed by `{targetClass, filter}`.

**Tech Stack:** Java 25, ASM 9.7.1, JMH 1.37 (benchmarks), JUnit 5 (tests)

## Global Constraints

- Java 24+ required (uses `Lookup.defineHiddenClass`)
- Maven commands: `mvn -s /home/lam/repo/settings.xml`
- Apache 2.0 license header on all new files
- Match existing code style (4-space indent, Javadoc on public API, ASM bytecode generation patterns)
- No speculative features — only what's in the design spec

---

## File Structure Map

```
Create (5 files):
  src/main/java/io/github/lamspace/Interceptor.java        — unified callback
  src/main/java/io/github/lamspace/DispatchTarget.java     — internal dispatch interface
  src/main/java/io/github/lamspace/generator/DispatchGenerator.java — dispatch() bytecode
  src/main/java/io/github/lamspace/WeakCache.java          — proxy class cache
  src/test/java/io/github/lamspace/APSUnifiedTest.java     — unified tests

Modify (8 files):
  src/main/java/io/github/lamspace/APS.java                — proxy() entry, invokeSuper
  src/main/java/io/github/lamspace/generator/ClassGenerator.java  — remove MH[], add dispatch
  src/main/java/io/github/lamspace/generator/InterfaceGenerator.java — add DispatchTarget, dispatch
  src/main/java/io/github/lamspace/generator/MethodDispatcher.java  — change Callback→Interceptor, drop index
  src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java — change InterfaceCallback→Interceptor
  src/main/java/io/github/lamspace/generator/BytecodeUtils.java    — add boxArgs/unboxReturn helpers
  src/main/java/io/github/lamspace/generator/ClinitRegistry.java   — no changes needed (hash values use ldc)
  src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java   — update to new API

Delete (4 files):
  src/main/java/io/github/lamspace/Callback.java
  src/main/java/io/github/lamspace/InterfaceCallback.java
  src/main/java/io/github/lamspace/SuperDispatcher.java
  src/main/java/io/github/lamspace/loader/HiddenClassLoader.java
```

---

### Task 1: Create `Interceptor` interface

**Files:**
- Create: `src/main/java/io/github/lamspace/Interceptor.java`

**Interfaces:**
- Produces: `Interceptor` — `@FunctionalInterface` with `Object intercept(Object proxy, Method method, Object[] args) throws Throwable`

- [ ] **Step 1: Write `Interceptor.java`**

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

import java.lang.reflect.Method;

/**
 * Intercepts method calls on a proxy instance.
 * A single Interceptor receives all method invocations for both class
 * and interface proxies — replacing the former {@code Callback} and
 * {@code InterfaceCallback}.
 *
 * <p>To invoke the original superclass method, use
 * {@link APS#invokeSuper(Object, Method, Object[])}.
 *
 * <pre>{@code
 *   Greeter proxy = APS.proxy(Greeter.class, (obj, method, args) -> {
 *       System.out.println("before " + method.getName());
 *       return APS.invokeSuper(obj, method, args);
 *   });
 * }</pre>
 */
@FunctionalInterface
public interface Interceptor {

    /**
     * Called for every method invocation on the proxy.
     *
     * @param proxy  the proxy instance
     * @param method the intercepted method (for metadata and dispatch)
     * @param args   the method arguments, boxed; empty array for no-arg methods
     * @return the method's return value (null for void, boxed for primitives)
     * @throws Throwable any throwable the interceptor wishes to propagate
     */
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/Interceptor.java
git commit -m "feat: add unified Interceptor interface"
```

---

### Task 2: Create `DispatchTarget` internal interface

**Files:**
- Create: `src/main/java/io/github/lamspace/DispatchTarget.java`

**Interfaces:**
- Produces: `DispatchTarget` — package-private interface with `Object dispatch(Method method, Object[] args) throws Throwable`

- [ ] **Step 1: Write `DispatchTarget.java`**

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

import java.lang.reflect.Method;

/**
 * Internal interface implemented by all generated proxy classes.
 * Provides hashCode-based method dispatch for super-method invocation.
 *
 * <p>Not part of the public API — users call
 * {@link APS#invokeSuper(Object, Method, Object[])} instead.
 */
interface DispatchTarget {

    /**
     * Dispatches a super-method call to the correct branch via
     * a hashCode-driven switch on the given method.
     *
     * @param method the method to dispatch (used for hashCode lookup)
     * @param args   boxed arguments
     * @return the method's return value (null for void, boxed for primitives)
     * @throws Throwable any throwable from the dispatched method
     */
    Object dispatch(Method method, Object[] args) throws Throwable;
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/DispatchTarget.java
git commit -m "feat: add DispatchTarget internal interface"
```

---

### Task 3: Create `DispatchGenerator`

**Files:**
- Create: `src/main/java/io/github/lamspace/generator/DispatchGenerator.java`
- Modify: `src/main/java/io/github/lamspace/generator/BytecodeUtils.java` (add helpers if needed)

**Interfaces:**
- Consumes: `DispatchTarget` (Task 2), `BytecodeUtils`, `ClinitRegistry`
- Produces: `DispatchGenerator.generateDispatch(ClassWriter, Class<?>, String, List<MethodInfo>, boolean isClassProxy)` — generates the `dispatch()` method. `MethodInfo` is a record/class holding `{Method method, String fieldName, int hash}`.

- [ ] **Step 1: Add `MethodInfo` record and compute hashes**

`DispatchGenerator` needs a data class to hold per-method info (method, static field name, pre-computed hash). Add it as a package-private record:

```java
package io.github.lamspace.generator;

import java.lang.reflect.Method;

/**
 * Per-method metadata for dispatch generation.
 */
record MethodInfo(Method method, String staticFieldName, int methodHash) {
    MethodInfo {
        if (method == null || staticFieldName == null) {
            throw new NullPointerException();
        }
    }
}
```

The hash uses `method.hashCode()` (deterministic, based on declaring class name + method name). This is computed at bytecode generation time so values can be embedded as `ldc` constants — no GETSTATIC loads in the hot path.

- [ ] **Step 2: Write `DispatchGenerator.generateDispatch()`**

```java
package io.github.lamspace.generator;

import io.github.lamspace.DispatchTarget;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.*;

/**
 * Generates the {@code dispatch(Method, Object[])} method for proxy classes.
 * Uses a hashCode-driven if-else chain with direct {@code INVOKESPECIAL}
 * super calls — no MethodHandle involvement.
 */
final class DispatchGenerator {

    private DispatchGenerator() {}

    /**
     * Pre-computes the Method.hashCode() for each method. This value is
     * deterministic (declaring class name XOR method name), so it can be
     * embedded as an {@code ldc} constant in bytecode.
     */
    static int computeHash(Method method) {
        return method.hashCode();
    }

    /**
     * Detects hash collisions among the given methods. If any two methods
     * produce the same hash, appends a secondary discriminator.
     *
     * @param methods the methods to check
     * @return a map from method to its final dispatch hash
     * @throws IllegalStateException if a collision cannot be resolved
     */
    static Map<Method, Integer> resolveHashes(List<Method> methods) {
        Map<Method, Integer> result = new LinkedHashMap<>();
        Set<Integer> seen = new HashSet<>();
        for (Method m : methods) {
            int hash = computeHash(m);
            if (!seen.add(hash)) {
                // Collision: use a secondary hash
                hash = hash * 31 + m.getName().hashCode();
                if (!seen.add(hash)) {
                    throw new IllegalStateException(
                        "Unresolvable hash collision for " + m);
                }
            }
            result.put(m, hash);
        }
        return result;
    }

    /**
     * Generates the dispatch method bytecode.
     *
     * @param cw                ClassWriter
     * @param generatedInternal ASM internal name of the generated class
     * @param superInternal     ASM internal name of the superclass (target class
     *                          for class proxies, "java/lang/Object" for interface)
     * @param infos             per-method metadata with pre-resolved hashes
     * @param isClassProxy      true = class proxy (direct super calls),
     *                          false = interface proxy (AbstractMethodError
     *                          for non-Object methods)
     */
    static void generateDispatch(ClassWriter cw,
                                 String generatedInternal,
                                 String superInternal,
                                 List<MethodInfo> infos,
                                 boolean isClassProxy) {
        String desc = "(Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;";
        MethodVisitor mv = cw.visitMethod(
                Opcodes.ACC_PUBLIC | Opcodes.ACC_FINAL,
                "dispatch", desc, null,
                new String[]{"java/lang/Throwable"});
        mv.visitCode();

        // int hash = method.hashCode();
        mv.visitVarInsn(Opcodes.ALOAD, 1);          // method param
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL,
                "java/lang/reflect/Method",
                "hashCode", "()I", false);
        int hashSlot = 3; // local var for hash
        mv.visitVarInsn(Opcodes.ISTORE, hashSlot);

        Label endLabel = new Label();
        Label nextLabel = null;

        for (int i = 0; i < infos.size(); i++) {
            MethodInfo info = infos.get(i);
            Method method = info.method();

            Label branchLabel = new Label();
            if (nextLabel == null) {
                // First comparison
                mv.visitVarInsn(Opcodes.ILOAD, hashSlot);
                mv.visitLdcInsn(info.methodHash());
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, branchLabel);
            } else {
                mv.visitLabel(nextLabel);
                mv.visitVarInsn(Opcodes.ILOAD, hashSlot);
                mv.visitLdcInsn(info.methodHash());
                mv.visitJumpInsn(Opcodes.IF_ICMPNE, branchLabel);
            }

            // Branch body: call super or throw
            Class<?> declaringClass = method.getDeclaringClass();
            boolean isObjectMethod = declaringClass == Object.class;

            if (isClassProxy || isObjectMethod) {
                // Direct super call
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this

                Class<?>[] paramTypes = method.getParameterTypes();
                int argSlot = 2; // args array
                for (int j = 0; j < paramTypes.length; j++) {
                    mv.visitVarInsn(Opcodes.ALOAD, argSlot);
                    BytecodeUtils.pushInt(mv, j);
                    mv.visitInsn(Opcodes.AALOAD);
                    // Unbox if primitive
                    Class<?> pt = paramTypes[j];
                    if (pt.isPrimitive()) {
                        BytecodeUtils.unboxPrimitive(mv, pt);
                    } else if (pt != Object.class) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST,
                                Type.getInternalName(pt));
                    }
                }

                String owner = isObjectMethod ? "java/lang/Object" : superInternal;
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        owner,
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        false);

                // Box return if needed
                Class<?> rt = method.getReturnType();
                if (rt == void.class) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                    mv.visitInsn(Opcodes.ARETURN);
                } else if (rt.isPrimitive()) {
                    BytecodeUtils.boxPrimitive(mv, rt);
                    mv.visitInsn(Opcodes.ARETURN);
                } else {
                    mv.visitInsn(Opcodes.ARETURN);
                }
            } else {
                // Interface proxy, non-Object method: throw AbstractMethodError
                mv.visitTypeInsn(Opcodes.NEW, "java/lang/AbstractMethodError");
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn("Cannot invoke super on interface method: "
                        + method.getName());
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "java/lang/AbstractMethodError",
                        "<init>", "(Ljava/lang/String;)V", false);
                mv.visitInsn(Opcodes.ATHROW);
            }

            nextLabel = branchLabel;
        }

        // Fallback: return null (unreachable in practice)
        if (nextLabel != null) {
            mv.visitLabel(nextLabel);
        }
        mv.visitInsn(Opcodes.ACONST_NULL);
        mv.visitInsn(Opcodes.ARETURN);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
```

- [ ] **Step 3: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml test-compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/DispatchGenerator.java
git commit -m "feat: add DispatchGenerator for hashCode-based super dispatch"
```

---

### Task 4: No ClinitRegistry changes needed

**Note:** Hash values use `Method.hashCode()` which is deterministic (based on declaring class name XOR method name). These values are computed at bytecode generation time and embedded directly as `ldc` constants in the `dispatch()` method bytecode. No static fields or `<clinit>` hash storage is needed. `ClinitRegistry` requires no changes — it continues to store `Method` reflection objects only.

- [ ] **Step 1: Verify `ClinitRegistry` keeps its current API**

Run: `mvn -s /home/lam/repo/settings.xml test-compile -q`
Expected: BUILD SUCCESS (this is a no-op verification step)

- [ ] **Step 2: No commit needed**

---

### Task 5: Update `MethodDispatcher` — Callback → Interceptor, drop index

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/MethodDispatcher.java`

**Interfaces:**
- Consumes: `Interceptor` (Task 1)
- Produces: Updated `dispatchMethods()` that generates Interceptor-based callbacks without index

- [ ] **Step 1: Change callback type and descriptor**

Replace all references:
- `io/github/lamspace/Callback` → `io/github/lamspace/Interceptor`
- Callback descriptor `(Ljava/lang/Object;Ljava/lang/reflect/Method;I[Ljava/lang/Object;)Ljava/lang/Object;` → `(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;`
- Remove the `BytecodeUtils.pushInt(mv, methodIndex)` instruction (lines 157)

- [ ] **Step 2: Remove index from callback call**

In `generateOverride()` (currently lines 128-225):
- Remove line ~157: `BytecodeUtils.pushInt(mv, methodIndex)`
- Update invokeinterface descriptor at line ~179-185 to the new 3-arg signature

- [ ] **Step 3: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml test-compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/MethodDispatcher.java
git commit -m "refactor: change MethodDispatcher to Interceptor, drop index"
```

---

### Task 6: Update `InterfaceDispatcher` — InterfaceCallback → Interceptor

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java`

**Interfaces:**
- Consumes: `Interceptor` (Task 1)
- Produces: Updated `dispatchMethods()` that generates Interceptor-based callbacks

- [ ] **Step 1: Change callback type and descriptor**

Replace all references:
- `io/github/lamspace/InterfaceCallback` → `io/github/lamspace/Interceptor`
- The `intercept` method descriptor already matches (3 args: Object, Method, Object[]) — same as `InterfaceCallback` — so the invokeinterface call at line ~176 needs only the class name changed

- [ ] **Step 2: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml test-compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java
git commit -m "refactor: change InterfaceDispatcher to Interceptor"
```

---

### Task 7: Update `ClassGenerator` — remove MethodHandle[], add dispatch()

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/ClassGenerator.java`

**Interfaces:**
- Consumes: `Interceptor` (Task 1), `DispatchTarget` (Task 2), `DispatchGenerator` (Task 3), updated `MethodDispatcher` (Task 5)
- Produces: Generates class proxy bytecode with `dispatch()` method and no `MethodHandle[]`

- [ ] **Step 1: Remove MethodHandle[] array field**

Delete line 124-125 (the `_handles` field declaration). Delete the `generateInvokeSuper()` method (lines 149-172). Remove `SuperDispatcher` from the interfaces array (line 114), add `DispatchTarget`:

```java
// Before (line 114):
String[] interfaces = {Type.getInternalName(SuperDispatcher.class)};
// After:
String[] interfaces = {Type.getInternalName(DispatchTarget.class)};
```

- [ ] **Step 2: Update callback field type**

Change `_callback` field descriptor from `Callback` to `Interceptor`:

```java
// Before:
String callbackDesc = Type.getDescriptor(Callback.class);
// After:
String callbackDesc = Type.getDescriptor(Interceptor.class);
```

- [ ] **Step 3: Add dispatch() generation call**

In `generate()`, after `MethodDispatcher.dispatchMethods()` returns, drain ClinitRegistry entries BEFORE calling `generateClinit()` (since both dispatch and clinit need them):

```java
List<ClinitRegistry.Entry> entries = ClinitRegistry.drain();
List<MethodInfo> infos = new ArrayList<>();
for (ClinitRegistry.Entry entry : entries) {
    infos.add(new MethodInfo(entry.method(), entry.methodFieldName(),
            DispatchGenerator.computeHash(entry.method())));
}
DispatchGenerator.generateDispatch(cw, generatedInternal, targetInternal, infos, true);
generateClinit(cw, generatedInternal, dispatched.size(), entries);
```

Note: `generateClinit()` signature changes to accept entries as a parameter instead of calling `ClinitRegistry.drain()` internally.

- [ ] **Step 4: Update `<clinit>` generation**

In `generateClinit()`:
- Change signature to `generateClinit(ClassWriter cw, String generatedInternal, int methodCount, List<ClinitRegistry.Entry> entries)`
- Remove MethodHandle array allocation (lines 195-198)
- Remove `Lookup.findSpecial()` + `asSpreader()` + `asType()` logic (lines 226-296)
- Keep Method object resolution (lines 207-224)
- Hash values are NOT stored in static fields — they're embedded directly as `ldc` constants in the `dispatch()` bytecode by DispatchGenerator

- [ ] **Step 5: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml test-compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/ClassGenerator.java
git commit -m "refactor: replace MethodHandle dispatch with hashCode switch in ClassGenerator"
```

---

### Task 8: Update `InterfaceGenerator` — add DispatchTarget, dispatch()

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java`

**Interfaces:**
- Consumes: `Interceptor` (Task 1), `DispatchTarget` (Task 2), `DispatchGenerator` (Task 3), updated `InterfaceDispatcher` (Task 6)
- Produces: Generates interface proxy bytecode with `dispatch()` method

- [ ] **Step 1: Add DispatchTarget to implemented interfaces**

In `generate()` (line 76):
```java
// Before:
new String[]{targetInternal}
// After:
new String[]{targetInternal, Type.getInternalName(DispatchTarget.class)}
```

- [ ] **Step 2: Change callback field type**

Change `InterfaceCallback` → `Interceptor` in field descriptor and constructor:
```java
// Before:
String callbackDesc = Type.getDescriptor(InterfaceCallback.class);
// After:
String callbackDesc = Type.getDescriptor(Interceptor.class);
```

- [ ] **Step 3: Add dispatch() generation**

After `InterfaceDispatcher.dispatchMethods()`, call:
```java
DispatchGenerator.generateDispatch(cw, generatedInternal,
        "java/lang/Object", infos, false);
```
This generates a dispatch method where class-type methods (Object.equals/hashCode/toString) call super directly, and interface methods throw AbstractMethodError.

- [ ] **Step 4: Update constructor to accept Interceptor**

The constructor parameter type changes from `InterfaceCallback` to `Interceptor` (already reflected in callbackDesc change).

- [ ] **Step 5: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml test-compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceGenerator.java
git commit -m "refactor: add DispatchTarget and hashCode dispatch to InterfaceGenerator"
```

---

### Task 9: Update `APS.java` — unified `proxy()` entry point

**Files:**
- Modify: `src/main/java/io/github/lamspace/APS.java`

**Interfaces:**
- Consumes: `Interceptor` (Task 1), `DispatchTarget` (Task 2), updated generators (Tasks 7-8)
- Produces: `proxy()` methods, updated `invokeSuper()`, unified class loading

- [ ] **Step 1: Replace `create()` and `createInterface()` with `proxy()`**

Three `proxy()` overloads, auto-detecting class vs interface:

```java
@SuppressWarnings("unchecked")
public static <T> T proxy(Class<T> target, Interceptor interceptor) {
    return proxy(target, interceptor, null, new Object[0]);
}

@SuppressWarnings("unchecked")
public static <T> T proxy(Class<T> target, Interceptor interceptor,
                           ClassFilter filter) {
    return proxy(target, interceptor, filter, new Object[0]);
}

@SuppressWarnings("unchecked")
public static <T> T proxy(Class<T> target, Interceptor interceptor,
                           ClassFilter filter, Object... constructorArgs) {
    Objects.requireNonNull(target, "target must not be null");
    Objects.requireNonNull(interceptor, "interceptor must not be null");

    try {
        // Check cache first (Task 12)
        // Class<?> proxyClass = proxyClassCache.get(target, filter);
        // if (proxyClass != null) { instantiate; return; }

        byte[] bytecode;
        Class<?>[] ctorArgTypes;

        if (target.isInterface()) {
            InterfaceGenerator generator = new InterfaceGenerator(target, filter);
            bytecode = generator.generate();
            ctorArgTypes = new Class<?>[]{Interceptor.class};
        } else {
            ClassGenerator generator = new ClassGenerator(target, filter, constructorArgs);
            bytecode = generator.generate();
            ctorArgTypes = generator.constructorArgs();
        }

        // Unified class loading via LookupManager
        Class<?> proxyClass = LookupManager.getLookup(target)
                .defineHiddenClass(bytecode, true).lookupClass();

        Constructor<?> ctor = proxyClass.getConstructor(ctorArgTypes);
        if (target.isInterface()) {
            return (T) ctor.newInstance(interceptor);
        } else {
            Object[] initArgs = new Object[1 + constructorArgs.length];
            initArgs[0] = interceptor;
            System.arraycopy(constructorArgs, 0, initArgs, 1,
                    constructorArgs.length);
            return (T) ctor.newInstance(initArgs);
        }
    } catch (Exception e) {
        throw new RuntimeException(
                "Failed to create proxy for " + target.getName(), e);
    }
}
```

- [ ] **Step 2: Update `invokeSuper()`**

```java
public static Object invokeSuper(Object proxy, Method method,
                                  Object[] args) throws Throwable {
    return ((DispatchTarget) proxy).dispatch(method, args);
}
```

- [ ] **Step 3: Remove old methods**

Delete `create()`, `createInterface()`, `invokeSuper(int, Object[])` overloads.

- [ ] **Step 4: Handle the `LookupManager` import**

`APS.java` already imports from `io.github.lamspace.internal.LookupManager` and `io.github.lamspace.loader.HiddenClassLoader`. The `LookupManager.getLookup(target).defineHiddenClass(bytecode, true)` call replaces both the old `HiddenClassLoader` path (class proxies) and the inline `MethodHandles.lookup()` path (interface proxies).

This means `HiddenClassLoader` is no longer used and can be deleted (in Task 11).

- [ ] **Step 5: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: compilation errors in test files (old API usage) — expected at this stage

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/APS.java
git commit -m "feat: add unified proxy() entry point and invokeSuper"
```

---

### Task 10: Add `WeakCache`

**Files:**
- Create: `src/main/java/io/github/lamspace/WeakCache.java`

**Interfaces:**
- Consumes: nothing
- Produces: `WeakCache<K, P, V>` with `get(K key, P parameter)`, `containsValue(V value)`

- [ ] **Step 1: Write `WeakCache.java`**

Copy the WeakCache implementation from newproxy (`/home/lam/workspace/newproxy/src/main/java/io/github/lamspace/newproxy/WeakCache.java`). It's a standalone class with no newproxy-specific dependencies. The source is ~335 lines.

Key adaptation: the `CacheKey.valueOf()` method needs to create keys from `{targetClass, filter}` tuples. Define the key factory to produce an appropriate weak-referenced key object.

- [ ] **Step 2: Wire cache into `APS.proxy()`**

Add cache field to `APS.java`:

```java
private static final WeakCache<ClassLoader, CacheKey, Class<?>> proxyClassCache =
        new WeakCache<>(new KeyFactory(), new ProxyClassFactory());
```

The `CacheKey` wraps `{targetClass, filter}`. The `ProxyClassFactory` performs bytecode generation and class definition. Update `proxy()` to check the cache before generating.

- [ ] **Step 3: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 4: Commit**

```bash
git add src/main/java/io/github/lamspace/WeakCache.java
git add src/main/java/io/github/lamspace/APS.java
git commit -m "feat: add WeakCache for proxy class caching"
```

---

### Task 11: Remove old types

**Files:**
- Delete: `src/main/java/io/github/lamspace/Callback.java`
- Delete: `src/main/java/io/github/lamspace/InterfaceCallback.java`
- Delete: `src/main/java/io/github/lamspace/SuperDispatcher.java`
- Delete: `src/main/java/io/github/lamspace/loader/HiddenClassLoader.java`

- [ ] **Step 1: Delete files and check for remaining references**

```bash
rm src/main/java/io/github/lamspace/Callback.java
rm src/main/java/io/github/lamspace/InterfaceCallback.java
rm src/main/java/io/github/lamspace/SuperDispatcher.java
rm src/main/java/io/github/lamspace/loader/HiddenClassLoader.java
```

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: BUILD SUCCESS (no remaining references in main sources; test sources may still reference old API — that's fine, they're handled in Task 12)

- [ ] **Step 2: Commit**

```bash
git rm src/main/java/io/github/lamspace/Callback.java
git rm src/main/java/io/github/lamspace/InterfaceCallback.java
git rm src/main/java/io/github/lamspace/SuperDispatcher.java
git rm src/main/java/io/github/lamspace/loader/HiddenClassLoader.java
git commit -m "refactor: remove old Callback, InterfaceCallback, SuperDispatcher, HiddenClassLoader"
```

---

### Task 12: Update functional tests

**Files:**
- Modify: `src/test/java/io/github/lamspace/APSFunctionalTest.java`
- Modify: `src/test/java/io/github/lamspace/APSInterfaceFunctionalTest.java`
- Create: `src/test/java/io/github/lamspace/APSUnifiedTest.java`

**Interfaces:**
- Consumes: new `APS.proxy()` API (Task 9)
- Produces: passing tests for unified API

- [ ] **Step 1: Read existing test files**

Read both test files to understand the current test structure and adapt accordingly.

- [ ] **Step 2: Update `APSFunctionalTest.java` (class proxy tests)**

Replace all `APS.create()` calls with `APS.proxy()`. Replace `Callback` lambdas with `Interceptor` lambdas (remove the `index` parameter). Replace `APS.invokeSuper(obj, index, args)` with `APS.invokeSuper(obj, method, args)`.

- [ ] **Step 3: Update `APSInterfaceFunctionalTest.java` (interface proxy tests)**

Replace `APS.createInterface()` with `APS.proxy()`. Replace `InterfaceCallback` with `Interceptor`.

- [ ] **Step 4: Write `APSUnifiedTest.java`**

New test covering the unified behavior:
- `APS.proxy(SomeClass.class, interceptor)` works as class proxy
- `APS.proxy(SomeInterface.class, interceptor)` works as interface proxy
- `invokeSuper` dispatches correctly for class methods
- `invokeSuper` throws AbstractMethodError for interface methods
- `ClassFilter` skips non-accepted methods
- Cache returns same class for duplicate proxy() calls

- [ ] **Step 5: Run tests**

Run: `mvn -s /home/lam/repo/settings.xml test -q`
Expected: all tests pass

- [ ] **Step 6: Commit**

```bash
git add src/test/java/io/github/lamspace/APSFunctionalTest.java
git add src/test/java/io/github/lamspace/APSInterfaceFunctionalTest.java
git add src/test/java/io/github/lamspace/APSUnifiedTest.java
git commit -m "test: update tests for unified proxy API"
```

---

### Task 13: Update benchmark

**Files:**
- Modify: `src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java`

**Interfaces:**
- Consumes: new `APS.proxy()` API (Task 9)
- Produces: updated benchmarks with new API, comparable results

- [ ] **Step 1: Update benchmark code**

Replace all `APS.create()` → `APS.proxy()`, `APS.createInterface()` → `APS.proxy()`. Replace `Callback` → `Interceptor`, `InterfaceCallback` → `Interceptor`. Replace `APS.invokeSuper(obj, index, args)` → `APS.invokeSuper(obj, method, args)`.

The `int index` parameter is gone — benchmarks that used it for `invokeSuper` now pass the `method` parameter directly. This means the passthrough/arg-modify/primitive/void/multi-param class proxy scenarios need to capture the `method` from the callback lambda:

```java
// Before:
apsProxy = APS.create(StringOpImpl.class,
    (obj, method, index, args) -> APS.invokeSuper(obj, index, args));
// After:
apsProxy = APS.proxy(StringOpImpl.class,
    (obj, method, args) -> APS.invokeSuper(obj, method, args));
```

- [ ] **Step 2: Run benchmark to verify**

Run: `java -cp target/classes:target/test-classes:<deps-cp> io.github.lamspace.benchmark.ProxyBenchmark`
Expected: all benchmarks complete, comparable/greater performance

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java
git commit -m "bench: update ProxyBenchmark for unified API"
```

---

### Task 14: Update documentation

**Files:**
- Modify: `README.md`
- Modify: `docs/benchmark-results.md`

- [ ] **Step 1: Update README.md**

- Update API examples from `APS.create/createInterface` to `APS.proxy`
- Update callback examples from `Callback/InterfaceCallback` to `Interceptor`
- Update `invokeSuper` examples
- Update the APS vs JavaProxy comparison table (simpler API column)
- Update benchmark results table with new numbers

- [ ] **Step 2: Update benchmark results doc**

Run the full benchmark suite and update `docs/benchmark-results.md` with new numbers.

- [ ] **Step 3: Commit**

```bash
git add README.md docs/benchmark-results.md
git commit -m "docs: update for unified proxy API and new benchmarks"
```

---

## Self-Review

**1. Spec coverage:**
- ✅ Unified entry `proxy()` → Task 9
- ✅ `Interceptor` callback → Task 1
- ✅ `invokeSuper(proxy, method, args)` → Task 9
- ✅ `DispatchTarget` internal interface → Task 2
- ✅ hashCode switch dispatch → Task 3, 7, 8
- ✅ Class proxy structure → Task 7
- ✅ Interface proxy structure → Task 8
- ✅ Class loading unification → Task 9
- ✅ Cache → Task 10
- ✅ Removal of old types → Task 11
- ✅ Test updates → Task 12
- ✅ Benchmark updates → Task 13
- ✅ Documentation → Task 14

**2. Placeholder scan:**
No TBD, TODO, or vague steps. Each step has concrete code or commands.

**3. Type consistency:**
- Interceptor: defined Task 1 (object, method, args → Object), used Tasks 5-9
- DispatchTarget: defined Task 2 (Method, Object[] → Object), used Tasks 7-9
- DispatchGenerator: defined Task 3, used Tasks 7-8
- MethodInfo: defined Task 3, used Tasks 7-8
- ClinitRegistry: unchanged (Task 4 verifies), used in Tasks 5-8 for Method object registration
- APS.proxy(): defined Task 9, consumed Tasks 12-13
- WeakCache: defined Task 10, wired in Task 9 step 2

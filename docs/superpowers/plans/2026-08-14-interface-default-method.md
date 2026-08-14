# Interface Default Method Invocation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AcceleratedProxy.invokeSuper(proxy, method, args)` invoke an interface `default` method's implementation, instead of throwing `AbstractMethodError`.

**Architecture:** The generated proxy class directly `implements` the target interface, so a `default` method declared directly on that interface can be called with a plain `INVOKESPECIAL` (fast path, zero `MethodHandle` overhead). Default methods *inherited* from a parent interface are not in a direct superinterface, so they fall back to `MethodHandles.Lookup.findSpecial` via a static helper. Non-default interface methods keep throwing `AbstractMethodError`.

**Tech Stack:** Java 25, ASM 9.7.1 (bytecode generation), JUnit 5.11.4 (tests), JMH 1.37 (benchmarks).

## Global Constraints

- Build with `mvn -s /home/lam/repo/settings.xml ...` (the repo's Maven settings; see `MEMORY.md`).
- Java source/target 25 (from `pom.xml` `maven-compiler-plugin`).
- ASM 9.7.1, JUnit 5.11.4, JMH 1.37 — versions already in `pom.xml`; do not change them.
- Public API unchanged: `AcceleratedProxy.invokeSuper(Object, Method, Object[])`, `AcceleratedProxy.proxy(...)`, `DispatchTarget.dispatch(...)` keep their exact signatures.
- Every new `.java` file starts with the Apache-2.0 license header already used throughout `src/` (copy from a neighboring file, replacing the year/name is not required — keep "Copyright 2026 Lam Tong").
- Non-default interface methods must still throw `AbstractMethodError` (existing test `AcceleratedProxyInterfaceProxyTest.invokeSuperShouldThrowForInterfaceMethod` must keep passing).
- Match existing code style: Javadoc on public types/methods, 4-space indent, `final` classes for utilities.

---

### Task 1: `DefaultMethodInvoker` helper + unit test

**Files:**
- Create: `src/main/java/io/github/lamspace/internal/DefaultMethodInvoker.java`
- Test: `src/test/java/io/github/lamspace/internal/DefaultMethodInvokerTest.java`

**Interfaces:**
- Produces: `public static Object io.github.lamspace.internal.DefaultMethodInvoker.invokeDefault(Object proxy, java.lang.reflect.Method method, Object[] args) throws Throwable` — invokes the given default method on `proxy`, returning its boxed result (null for void). Consumed by Task 3.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/lamspace/internal/DefaultMethodInvokerTest.java`:

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

package io.github.lamspace.internal;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class DefaultMethodInvokerTest {

    interface Parent {
        default String inheritedGreet() {
            return "Hello, inherited";
        }
    }

    interface Child extends Parent {
        String own();
    }

    static class ChildImpl implements Child {
        @Override
        public String own() {
            return "own";
        }
    }

    interface WithPrimitive {
        default int add(int a, int b) {
            return a + b;
        }

        default void run() {
        }
    }

    static class PrimitiveImpl implements WithPrimitive {
    }

    interface Throwing {
        default String boom() {
            throw new IllegalStateException("boom");
        }
    }

    static class ThrowingImpl implements Throwing {
    }

    @Test
    void invokesInheritedDefaultMethod() throws Throwable {
        ChildImpl impl = new ChildImpl();
        Method m = Child.class.getMethod("inheritedGreet");
        assertEquals("Hello, inherited",
                DefaultMethodInvoker.invokeDefault(impl, m, new Object[0]));
    }

    @Test
    void invokesPrimitiveReturningDefault() throws Throwable {
        PrimitiveImpl impl = new PrimitiveImpl();
        Method m = WithPrimitive.class.getMethod("add", int.class, int.class);
        assertEquals(7, DefaultMethodInvoker.invokeDefault(
                impl, m, new Object[]{3, 4}));
    }

    @Test
    void invokesVoidDefaultMethod() throws Throwable {
        PrimitiveImpl impl = new PrimitiveImpl();
        Method m = WithPrimitive.class.getMethod("run");
        assertNull(DefaultMethodInvoker.invokeDefault(impl, m, new Object[0]));
    }

    @Test
    void propagatesExceptionFromDefaultMethod() {
        ThrowingImpl impl = new ThrowingImpl();
        Method m;
        try {
            m = Throwing.class.getMethod("boom");
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
        assertThrows(IllegalStateException.class,
                () -> DefaultMethodInvoker.invokeDefault(impl, m, new Object[0]));
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=DefaultMethodInvokerTest test`
Expected: compilation fails — `DefaultMethodInvoker` does not exist ("cannot find symbol").

- [ ] **Step 3: Write the implementation**

Create `src/main/java/io/github/lamspace/internal/DefaultMethodInvoker.java`:

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

package io.github.lamspace.internal;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Invokes interface default methods from generated proxy dispatch code via
 * {@link MethodHandles.Lookup#findSpecial}. Used only for default methods
 * inherited from a parent interface, which cannot be invoked with a direct
 * {@code INVOKESPECIAL} because the declaring interface is not a direct
 * superinterface of the generated proxy class.
 */
public final class DefaultMethodInvoker {

    private static final Map<Method, MethodHandle> CACHE =
            new ConcurrentHashMap<>();

    private DefaultMethodInvoker() {
    }

    /**
     * Invokes the default method on the given proxy instance.
     *
     * @param proxy  the proxy instance (its class implements the interface)
     * @param method the default method to invoke
     * @param args   boxed arguments
     * @return the method's boxed return value (null for void)
     * @throws Throwable any throwable from the default method
     */
    public static Object invokeDefault(Object proxy, Method method,
                                       Object[] args) throws Throwable {
        MethodHandle handle = CACHE.computeIfAbsent(method,
                m -> resolve(m, proxy.getClass()));
        return handle.bindTo(proxy).invokeWithArguments(args);
    }

    private static MethodHandle resolve(Method method,
                                        Class<?> proxyClass) {
        try {
            MethodType type = MethodType.methodType(
                    method.getReturnType(), method.getParameterTypes());
            return MethodHandles.lookup().findSpecial(
                    method.getDeclaringClass(), method.getName(),
                    type, proxyClass);
        } catch (NoSuchMethodException | IllegalAccessException e) {
            throw new IllegalStateException(
                    "Cannot bind default method: " + method, e);
        }
    }
}
```

- [ ] **Step 4: Run test to verify it passes**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=DefaultMethodInvokerTest test`
Expected: PASS (4 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/internal/DefaultMethodInvoker.java \
        src/test/java/io/github/lamspace/internal/DefaultMethodInvokerTest.java
git commit -m "feat: add DefaultMethodInvoker for inherited interface default methods"
```

---

### Task 2: Directly-declared default fast path (`INVOKESPECIAL`)

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/DispatchGenerator.java:98-102` (signature) and `:137-185` (branch body)
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java:115-116`
- Modify: `src/main/java/io/github/lamspace/generator/ClassGenerator.java:161-162`
- Test: `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java`

**Interfaces:**
- Consumes: none from earlier tasks (the fast path does not use `DefaultMethodInvoker`).
- Produces: `DispatchGenerator.generateDispatch` gains a 5th parameter `String interfaceInternalName` (null for class proxies); both callers updated. Consumed by Task 3.

- [ ] **Step 1: Write the failing test**

Create `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java` with only the direct-default tests (the inherited test is added in Task 3):

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

import static org.junit.jupiter.api.Assertions.*;

class DefaultMethodInvocationTest {

    interface DirectDefault {
        String hello(String name);

        default String greet() {
            return "Hello, World";
        }

        default int add(int a, int b) {
            return a + b;
        }

        default void run() {
        }
    }

    @Test
    void invokeSuperOnDirectlyDeclaredDefaultReturnsDefaultImpl() {
        DirectDefault proxy = AcceleratedProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.isDefault()) {
                        return AcceleratedProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals("Hello, World", proxy.greet());
    }

    @Test
    void invokeSuperOnDirectDefaultWithArgsAndPrimitiveReturn() {
        DirectDefault proxy = AcceleratedProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.getName().equals("add")) {
                        return AcceleratedProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void invokeSuperOnDirectVoidDefault() {
        DirectDefault proxy = AcceleratedProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.getName().equals("run")) {
                        return AcceleratedProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertDoesNotThrow(proxy::run);
    }
}
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=DefaultMethodInvocationTest test`
Expected: the three tests FAIL with `AbstractMethodError` (current behavior: `invokeSuper` on an interface method throws).

- [ ] **Step 3: Add the `interfaceInternalName` parameter**

In `src/main/java/io/github/lamspace/generator/DispatchGenerator.java`, change the `generateDispatch` signature (lines 98-102) to add the new parameter, and update its Javadoc:

```java
    /**
     * Generates the dispatch method bytecode.
     *
     * @param cw                     ClassWriter
     * @param generatedInternal      ASM internal name of the generated class
     * @param superInternal          ASM internal name of the superclass (target class
     *                               for class proxies, "java/lang/Object" for interface)
     * @param interfaceInternalName  ASM internal name of the target interface, or
     *                               {@code null} for class proxies (used to detect
     *                               directly-declared default methods)
     * @param infos                  per-method metadata with pre-resolved hashes
     * @param isClassProxy           true = class proxy (direct super calls),
     *                               false = interface proxy (AbstractMethodError
     *                               for non-default methods, direct INVOKESPECIAL
     *                               for directly-declared default methods)
     */
    static void generateDispatch(ClassWriter cw,
                                 String generatedInternal,
                                 String superInternal,
                                 String interfaceInternalName,
                                 List<MethodInfo> infos,
                                 boolean isClassProxy) {
```

- [ ] **Step 4: Update both callers**

In `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java`, replace the call (lines 115-116):

```java
        DispatchGenerator.generateDispatch(cw, generatedInternal,
                "java/lang/Object", targetInternal, infos, false);
```

(`targetInternal` is already computed at the top of `generate()` as `Type.getInternalName(interfaceClass)`.)

In `src/main/java/io/github/lamspace/generator/ClassGenerator.java`, replace the call (lines 161-162):

```java
        DispatchGenerator.generateDispatch(cw, generatedInternal,
                targetInternal, null, infos, true);
```

- [ ] **Step 5: Add the directly-declared default branch**

In `src/main/java/io/github/lamspace/generator/DispatchGenerator.java`, replace the branch body (lines 137-185) — the `if (isClassProxy || isObjectMethod) { ... } else { throw ... }` block — with the following (the `isClassProxy || isObjectMethod` branch is unchanged; a new `else if` is inserted before the throw):

```java
            // Branch body
            Class<?> declaringClass = method.getDeclaringClass();
            boolean isObjectMethod = declaringClass == Object.class;
            boolean isDirectDefault = !isClassProxy
                    && method.isDefault()
                    && interfaceInternalName != null
                    && Type.getInternalName(declaringClass)
                            .equals(interfaceInternalName);

            if (isClassProxy || isObjectMethod) {
                // Direct super call: this.super.method(args...)
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this

                Class<?>[] paramTypes = method.getParameterTypes();
                int argSlot = 2; // args parameter
                for (int j = 0; j < paramTypes.length; j++) {
                    mv.visitVarInsn(Opcodes.ALOAD, argSlot);
                    BytecodeUtils.pushInt(mv, j);
                    mv.visitInsn(Opcodes.AALOAD);
                    Class<?> pt = paramTypes[j];
                    if (pt.isPrimitive()) {
                        BytecodeUtils.unboxPrimitive(mv, pt);
                    } else if (pt != Object.class) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST,
                                Type.getInternalName(pt));
                    }
                }

                String owner = isObjectMethod
                        ? "java/lang/Object" : superInternal;
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        owner,
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        false);

                // Box return if needed
                Class<?> rt = method.getReturnType();
                if (rt == void.class) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                } else if (rt.isPrimitive()) {
                    BytecodeUtils.boxPrimitive(mv, rt);
                }
                mv.visitInsn(Opcodes.ARETURN);
            } else if (isDirectDefault) {
                // Directly-declared default method: this.<interface>.method(...)
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this

                Class<?>[] paramTypes = method.getParameterTypes();
                int argSlot = 2; // args parameter
                for (int j = 0; j < paramTypes.length; j++) {
                    mv.visitVarInsn(Opcodes.ALOAD, argSlot);
                    BytecodeUtils.pushInt(mv, j);
                    mv.visitInsn(Opcodes.AALOAD);
                    Class<?> pt = paramTypes[j];
                    if (pt.isPrimitive()) {
                        BytecodeUtils.unboxPrimitive(mv, pt);
                    } else if (pt != Object.class) {
                        mv.visitTypeInsn(Opcodes.CHECKCAST,
                                Type.getInternalName(pt));
                    }
                }

                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        interfaceInternalName,
                        method.getName(),
                        Type.getMethodDescriptor(method),
                        true); // interface owner

                Class<?> rt = method.getReturnType();
                if (rt == void.class) {
                    mv.visitInsn(Opcodes.ACONST_NULL);
                } else if (rt.isPrimitive()) {
                    BytecodeUtils.boxPrimitive(mv, rt);
                }
                mv.visitInsn(Opcodes.ARETURN);
            } else {
                // Interface proxy, non-Object, non-direct-default method:
                // throw AbstractMethodError
                mv.visitTypeInsn(Opcodes.NEW,
                        "java/lang/AbstractMethodError");
                mv.visitInsn(Opcodes.DUP);
                mv.visitLdcInsn("Cannot invoke super on interface method: "
                        + method.getName());
                mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                        "java/lang/AbstractMethodError",
                        "<init>", "(Ljava/lang/String;)V", false);
                mv.visitInsn(Opcodes.ATHROW);
            }
```

- [ ] **Step 6: Run the tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml -q test`
Expected: all tests PASS, including the three new direct-default tests and the pre-existing `AcceleratedProxyInterfaceProxyTest` (which confirms class-proxy and non-default behavior are unchanged).

- [ ] **Step 7: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/DispatchGenerator.java \
        src/main/java/io/github/lamspace/generator/InterfaceGenerator.java \
        src/main/java/io/github/lamspace/generator/ClassGenerator.java \
        src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java
git commit -m "feat: invoke directly-declared interface default methods via INVOKESPECIAL"
```

---

### Task 3: Inherited default fallback (`findSpecial`)

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/DispatchGenerator.java` (add one `else if` branch)
- Test: `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java` (add one test)

**Interfaces:**
- Consumes: `DefaultMethodInvoker.invokeDefault(Object, Method, Object[])` (Task 1); `interfaceInternalName` parameter (Task 2).
- Produces: inherited default methods route to `DefaultMethodInvoker`.

- [ ] **Step 1: Write the failing test**

Add to `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java` (append these interface declarations and test inside the class):

```java
    interface Parent {
        default String inheritedGreet() {
            return "Hello, inherited";
        }
    }

    interface Child extends Parent {
        String own();
    }

    @Test
    void invokeSuperOnInheritedDefaultReturnsDefaultImpl() {
        Child proxy = AcceleratedProxy.proxy(Child.class,
                (obj, method, args) -> {
                    if (method.getName().equals("inheritedGreet")) {
                        return AcceleratedProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals("Hello, inherited", proxy.inheritedGreet());
    }
```

- [ ] **Step 2: Run test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=DefaultMethodInvocationTest test`
Expected: `invokeSuperOnInheritedDefaultReturnsDefaultImpl` FAILS with `AbstractMethodError` (the inherited method still falls into the throw branch).

- [ ] **Step 3: Add the inherited-default branch**

In `src/main/java/io/github/lamspace/generator/DispatchGenerator.java`, insert a new `else if` between the `isDirectDefault` branch and the final `else`. The final `else` (throw `AbstractMethodError`) stays. Insert:

```java
            } else if (method.isDefault()) {
                // Inherited default method: bind via findSpecial
                mv.visitVarInsn(Opcodes.ALOAD, 0); // this (proxy)
                mv.visitVarInsn(Opcodes.ALOAD, 1); // method
                mv.visitVarInsn(Opcodes.ALOAD, 2); // args
                mv.visitMethodInsn(Opcodes.INVOKESTATIC,
                        "io/github/lamspace/internal/DefaultMethodInvoker",
                        "invokeDefault",
                        "(Ljava/lang/Object;Ljava/lang/reflect/Method;"
                                + "[Ljava/lang/Object;)Ljava/lang/Object;",
                        false);
                mv.visitInsn(Opcodes.ARETURN);
            } else {
```

(The closing `} else {` here replaces the old `} else {` that began the throw branch — the throw body is unchanged.)

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml -q test`
Expected: all tests PASS, including the new inherited-default test.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/DispatchGenerator.java \
        src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java
git commit -m "feat: fall back to findSpecial for inherited interface default methods"
```

---

### Task 4: Edge-case and regression tests

**Files:**
- Test: `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java` (append tests)

**Interfaces:**
- Consumes: behavior from Tasks 1-3.

- [ ] **Step 1: Add edge-case tests**

Append to `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java` (reuse the existing `DirectDefault` interface):

```java
    @Test
    void invokeSuperOnNonDefaultStillThrows() {
        DirectDefault proxy = AcceleratedProxy.proxy(DirectDefault.class,
                (obj, method, args) ->
                        AcceleratedProxy.invokeSuper(obj, method, args));
        assertThrows(AbstractMethodError.class, () -> proxy.hello("x"));
    }

    @Test
    void interceptorCanReplaceDefault() {
        DirectDefault proxy = AcceleratedProxy.proxy(DirectDefault.class,
                (obj, method, args) -> "[overridden]");
        assertEquals("[overridden]", proxy.greet());
    }

    @Test
    void defaultMethodSeesModifiedArguments() {
        DirectDefault proxy = AcceleratedProxy.proxy(DirectDefault.class,
                (obj, method, args) -> {
                    if (method.getName().equals("add")) {
                        args[0] = (int) args[0] + 10;
                        return AcceleratedProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals(17, proxy.add(3, 4));
    }

    @Test
    void objectMethodsStillDispatchCorrectly() {
        DirectDefault proxy = AcceleratedProxy.proxy(DirectDefault.class,
                (obj, method, args) ->
                        AcceleratedProxy.invokeSuper(obj, method, args));
        assertNotNull(proxy.toString());
        assertTrue(proxy.hashCode() != 0);
    }
```

- [ ] **Step 2: Run the full test suite**

Run: `mvn -s /home/lam/repo/settings.xml -q test`
Expected: all tests PASS. In particular confirm `AcceleratedProxyInterfaceProxyTest.invokeSuperShouldThrowForInterfaceMethod` and `AcceleratedProxyInterfaceProxyTest.shouldInterceptDefaultMethod` still pass (non-default still throws; default methods still intercepted), and `AcceleratedProxyClassProxyTest` passes (class proxies unaffected).

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java
git commit -m "test: add default-method edge-case and regression tests"
```

---

### Task 5: JMH benchmark + report

**Files:**
- Modify: `src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java`
- Modify: `docs/benchmark-results.md`
- Modify: `docs/benchmark-results_cn.md`

**Interfaces:**
- Consumes: behavior from Tasks 1-4.

- [ ] **Step 1: Add the `InvocationHandler` import**

In `src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java`, add next to the existing `import java.lang.reflect.Proxy;`:

```java
import java.lang.reflect.InvocationHandler;
```

- [ ] **Step 2: Add the benchmark interface, state, and methods**

In `src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java`, insert before the final closing brace of the class (the class currently ends with the `mg_iface_single_format` method):

```java
    // ================================================================
    // Target: Interface default method invocation (Phase 3)
    // ================================================================

    interface DefaultParent {
        default String inheritedGreet() {
            return "Hello, inherited";
        }
    }

    interface DefaultGreeter extends DefaultParent {
        String hello(String name);

        default String greet() {
            return "Hello, World";
        }
    }

    @State(Scope.Thread)
    public static class DefaultMethodState {
        DefaultGreeter aps;
        DefaultGreeter javaProxy;

        @Setup
        public void setup() {
            aps = AcceleratedProxy.proxy(DefaultGreeter.class,
                    (obj, method, args) -> {
                        if (method.isDefault()) {
                            return AcceleratedProxy.invokeSuper(
                                    obj, method, args);
                        }
                        return null;
                    });

            javaProxy = (DefaultGreeter) Proxy.newProxyInstance(
                    DefaultGreeter.class.getClassLoader(),
                    new Class<?>[]{DefaultGreeter.class},
                    (proxy, method, args) -> {
                        if (method.isDefault()) {
                            return InvocationHandler.invokeDefault(
                                    proxy, method, args);
                        }
                        return null;
                    });
        }
    }

    @Benchmark
    public String i_default_greet(DefaultMethodState s) {
        return s.aps.greet();
    }

    @Benchmark
    public String i_default_inherited(DefaultMethodState s) {
        return s.aps.inheritedGreet();
    }

    @Benchmark
    public String i_jp_default_greet(DefaultMethodState s) {
        return s.javaProxy.greet();
    }

    @Benchmark
    public String i_jp_default_inherited(DefaultMethodState s) {
        return s.javaProxy.inheritedGreet();
    }
```

- [ ] **Step 3: Compile main + test and build the dependency classpath**

Run:

```bash
mvn -s /home/lam/repo/settings.xml -q -DskipTests test-compile
mvn -s /home/lam/repo/settings.xml -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
```

- [ ] **Step 4: Run the benchmark**

Run:

```bash
java --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED \
     -cp "target/test-classes:target/classes:$(cat target/cp.txt)" \
     io.github.lamspace.benchmark.ProxyBenchmark
```

Expected: JMH prints all benchmark methods including the four new `i_default_*` / `i_jp_default_*` rows, with `Score` in ns/op.

- [ ] **Step 5: Record the results in both reports**

In `docs/benchmark-results.md` and `docs/benchmark-results_cn.md`, add a new section after the existing "Interface Proxy" section (mirroring the existing table style, ns/op lower is better):

```markdown
## Interface Default Method Invocation

Compares APS default-method passthrough (`invokeSuper`) against the JDK
`InvocationHandler.invokeDefault` reference. Target: `DefaultGreeter`
(directly-declared `greet()` and inherited `inheritedGreet()`).

| Scenario              | APS (direct) | APS (inherited) | JDK Proxy | Best |
|-----------------------|--------------|-----------------|-----------|------|
| `greet()` default     | `<ns/op>`    | —               | `<ns/op>` |      |
| `inheritedGreet()`    | —            | `<ns/op>`       | `<ns/op>` |      |

**Key takeaway:** fill in from the measured numbers — directly-declared default
passthrough is expected to land near class-proxy passthrough (~4-5 ns/op) and
well below JDK `invokeDefault`; the inherited fallback carries `findSpecial`
overhead and is reported as measured.
```

Fill each `<ns/op>` cell with the actual measured `Score` for the corresponding benchmark method (`i_default_greet` → APS direct, `i_default_inherited` → APS inherited, `i_jp_default_greet`/`i_jp_default_inherited` → JDK Proxy). Also update the header date line in both files to `2026-08-14`.

- [ ] **Step 6: Commit**

```bash
git add src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java \
        docs/benchmark-results.md docs/benchmark-results_cn.md
git commit -m "bench: add interface default method benchmarks and update report"
```

---

## Self-Review

**Spec coverage** — every requirement in the design spec maps to a task:
- Directly-declared default via `INVOKESPECIAL` → Task 2.
- Inherited default via `findSpecial` → Tasks 1 + 3.
- Non-default still throws `AbstractMethodError` → Task 4 (plus unchanged code in Task 2/3).
- Class-proxy / `Object`-method / interception unchanged → verified in Task 2 Step 6 and Task 4 Step 2.
- Unit tests → Tasks 1-4; JMH benchmarks → Task 5; benchmark report → Task 5.

**Placeholder scan** — the only `<ns/op>` placeholders are in the benchmark *report* step, which are filled from actual JMH output at run time (Task 5 Step 5), not left for later design decisions.

**Type consistency** — `DefaultMethodInvoker.invokeDefault(Object, Method, Object[])` (Task 1) matches the `INVOKESTATIC` descriptor `(Ljava/lang/Object;Ljava/lang/reflect/Method;[Ljava/lang/Object;)Ljava/lang/Object;` in Task 3. The `generateDispatch` 5th parameter `String interfaceInternalName` (Task 2) matches both call sites and the `isDirectDefault` check.

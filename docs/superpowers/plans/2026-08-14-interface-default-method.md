# Interface Default Method Invocation Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Make `AcceleratedProxy.invokeSuper(proxy, method, args)` invoke an interface `default` method's implementation, instead of throwing `AbstractMethodError`.

**Architecture:** The generated proxy class directly `implements` the target interface, so a `default` method — whether declared directly on that interface or inherited from a parent — can be invoked with a single `INVOKESPECIAL <target-interface>.<method>` (fast path, zero `MethodHandle` overhead). Method resolution walks the superinterface chain to find the inherited default. Non-default interface methods keep throwing `AbstractMethodError`.

**Tech Stack:** Java 25, ASM 9.7.1 (bytecode generation), JUnit 5.11.4 (tests), JMH 1.37 (benchmarks).

## Global Constraints

- Build with `mvn -s /home/lam/repo/settings.xml ...`.
- Java source/target 25 (from `pom.xml`).
- ASM 9.7.1, JUnit 5.11.4, JMH 1.37 — versions already in `pom.xml`; do not change.
- Public API unchanged: `AcceleratedProxy.invokeSuper`, `AcceleratedProxy.proxy`, `DispatchTarget.dispatch`.
- New `.java` files use the Apache-2.0 license header used throughout `src/`.
- Non-default interface methods must still throw `AbstractMethodError` (existing test `AcceleratedProxyInterfaceProxyTest.invokeSuperShouldThrowForInterfaceMethod` keeps passing).

---

### Task 1: Default method fast path

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/DispatchGenerator.java`
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java`
- Modify: `src/main/java/io/github/lamspace/generator/ClassGenerator.java`
- Test: `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java`

- [ ] **Step 1: Write the failing test** — `DefaultMethodInvocationTest` covering directly-declared defaults (`greet()`, `add(int,int)`, `run()`) and an inherited default (`Child extends Parent`, `inheritedGreet()`); each interceptor calls `invokeSuper` on the default method.
- [ ] **Step 2: Run it to confirm failure** — `mvn -s /home/lam/repo/settings.xml -q -Dtest=DefaultMethodInvocationTest test` fails with `AbstractMethodError`.
- [ ] **Step 3: Add the `interfaceInternalName` parameter** to `DispatchGenerator.generateDispatch` (class proxies pass `null`); update the two callers.
- [ ] **Step 4: Emit `INVOKESPECIAL` (`itf = true`) against the target interface** for every `method.isDefault()` case (replacing the throw for default methods).
- [ ] **Step 5: Fix `<clinit>` inherited-method resolution** — in `InterfaceGenerator.generateClinit`, change `getDeclaredMethod` → `getMethod` so inherited default methods resolve.
- [ ] **Step 6: Run the full suite** — `mvn -s /home/lam/repo/settings.xml -q test` passes.
- [ ] **Step 7: Commit.**

### Task 2: Edge cases and regression

**Files:**
- Test: `src/test/java/io/github/lamspace/DefaultMethodInvocationTest.java`

- [ ] **Step 1: Add tests** — non-default still throws, interceptor replacement, argument modification before `invokeSuper`, `Object` methods still dispatch, exception propagation from a default method.
- [ ] **Step 2: Run the full suite** — passes, including `AcceleratedProxyInterfaceProxyTest` and `AcceleratedProxyClassProxyTest`.
- [ ] **Step 3: Commit.**

### Task 3: Benchmark and report

**Files:**
- Modify: `src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java`
- Modify: `docs/benchmark-results.md`, `docs/benchmark-results_cn.md`

- [ ] **Step 1: Add benchmarks** — `DefaultGreeter`/`DefaultParent` interfaces (made `public` so the hidden class in `io.github.lamspace` can access them), `DefaultMethodState`, and `i_default_greet` / `i_default_inherited` / `i_jp_default_greet` / `i_jp_default_inherited`.
- [ ] **Step 2: Build the classpath** — `mvn -s /home/lam/repo/settings.xml -q -DskipTests test-compile dependency:build-classpath -Dmdep.outputFile=target/cp.txt`.
- [ ] **Step 3: Run JMH** and capture ns/op scores.
- [ ] **Step 4: Update the reports** with a new "Interface Default Method Invocation" section and the updated date.
- [ ] **Step 5: Commit.**

> **Note (discovered during implementation):** the pre-existing interface-proxy benchmarks used package-private interfaces (`RetOps`, `ParamCount`, `Echo`), which made them throw `IllegalAccessError` (hidden class in `io.github.lamspace` can't implement a package-private interface from `io.github.lamspace.benchmark`). Those were made `public` and re-run; the report's "Interface Proxy" numbers were corrected to the freshly measured values.

# Multi-Interface Proxy Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Let one OpenProxy-generated proxy class implement multiple interfaces, with cross-interface method merging and deterministic conflict rejection.

**Architecture:** The interface path is unified internally to `Class<?>[]` (single-interface is the `N == 1` case). A new `InterfaceMethodResolver` merges each interface's `getMethods()` by signature, dedups, and rejects ambiguity. `InterfaceGenerator`/`InterfaceDispatcher` generate from the resolved (sorted, deduped) method list; `DispatchGenerator` routes each distinct `Method` object's hash to the same handler, using the resolved default owner for `INVOKESPECIAL`. The class path (`ClassGenerator`) is untouched.

**Tech Stack:** Java 25, ASM (`org.objectweb.asm`), JUnit 5, Maven. Spec: `docs/superpowers/specs/2026-08-14-multi-interface-proxy-design.md`.

## Global Constraints

- **Maven settings:** every `mvn` command must pass `-s /home/lam/repo/settings.xml`.
- **`N == 1` byte-identity:** for a single interface, the merged method set, sort order, and emitted class must be identical to today's output. Existing tests (including `DefaultMethodInvocationTest`) must stay green.
- **Class path untouched:** `ClassGenerator`, `MethodDispatcher`, and the class-proxy branch of `proxy()` must not change behavior.
- **Conflict policy (from spec §2.3):** same signature + same return type → merge; same signature + different return type → throw `IllegalArgumentException`; two `default`s from distinct interfaces → throw `IllegalArgumentException`; one `default` + one abstract → merge, `invokeSuper` calls the default.
- **Test command:** `mvn -s /home/lam/repo/settings.xml test` (optionally `-Dtest=ClassName`).

---

## File Structure

**New files**
- `src/main/java/io/github/lamspace/generator/InterfaceMethodResolver.java` — merges/dedups/conflict-checks interface methods; exposes `ResolvedMethod` (canonical, owner, variants, defaultOwner).
- `src/test/java/io/github/lamspace/generator/InterfaceMethodResolverTest.java` — unit tests for the resolver.
- `src/test/java/io/github/lamspace/MultiInterfaceProxyTest.java` — end-to-end multi-interface tests (spec §4 matrix).

**Modified files**
- `src/main/java/io/github/lamspace/AcceleratedProxy.java` — public `Class<?>[]` overloads; `CacheParams` gains `interfaces`; new `matchMethods(Class<?>[], Group[])`; `generateProxyClass` routes interfaces through the array path.
- `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java` — `Class<?>[]` constructor; resolves methods and emits multi-interface `implements`.
- `src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java` — accepts the resolved method list.
- `src/main/java/io/github/lamspace/generator/MethodInfo.java` — add `defaultOwner` component.
- `src/main/java/io/github/lamspace/generator/DispatchGenerator.java` — per-method default owner; drop `interfaceInternalName` param.
- `src/main/java/io/github/lamspace/generator/ClassGenerator.java` — update the `generateDispatch` call site (drop the `null` arg).
- Docs: `docs/openproxy-future-roadmap.md`, `README.md`, `README_CN.md`, `docs/migration-guide.md`.

**Boundaries:** `InterfaceMethodResolver` is the single source of truth for "which methods does an interface array expose, in what order, with which default." Both `AcceleratedProxy.matchMethods` and `InterfaceGenerator.generate` call it, which is what guarantees `mapping.indices()` stays aligned with the emitted methods.

---

## Task 1: InterfaceMethodResolver

**Files:**
- Create: `src/main/java/io/github/lamspace/generator/InterfaceMethodResolver.java`
- Test: `src/test/java/io/github/lamspace/generator/InterfaceMethodResolverTest.java`

**Interfaces:**
- Produces: `public static List<ResolvedMethod> resolve(Class<?>[] interfaces)` and
  `public record ResolvedMethod(Method canonical, Class<?> owner, List<Method> variants, Class<?> defaultOwner)`.
  - `canonical` — first `Method` object found for a signature (used for the generated method impl and the `intercept` arg).
  - `owner` — the array interface that yielded `canonical` (used as the `<clinit>` `getMethod` target).
  - `variants` — all distinct `Method` objects sharing the signature (used for dispatch hash routing).
  - `defaultOwner` — the array interface holding the `default` impl, or `null` if abstract.
  - Result is sorted by `(canonical.getName(), Arrays.toString(canonical.getParameterTypes()))`.

- [ ] **Step 1: Write the failing test**

```java
package io.github.lamspace.generator;

import io.github.lamspace.generator.InterfaceMethodResolver.ResolvedMethod;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceMethodResolverTest {

    interface A {
        String hello(String name);
        default String greet() { return "hi"; }
    }

    interface B {
        String hello(String name);          // same signature + same return -> merge
        int count();                        // distinct
    }

    interface C {
        Integer hello(String name);         // same signature, DIFFERENT return -> conflict
    }

    interface D {
        default String greet() { return "yo"; }  // both-default -> conflict
    }

    interface Parent {
        String inherited();
    }

    interface Child extends Parent {
        String own();
    }

    private static Method find(List<ResolvedMethod> rs, String name) {
        return rs.stream().filter(r -> r.canonical().getName().equals(name))
                .findFirst().orElseThrow().canonical();
    }

    @Test
    void mergesSameSignatureSameReturn() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(new Class<?>[]{A.class, B.class});
        // merged "hello" has 2 variants, "greet"/"count" have 1
        ResolvedMethod hello = rs.stream().filter(r -> r.canonical().getName().equals("hello")).findFirst().orElseThrow();
        assertEquals(2, hello.variants().size());
        assertNull(hello.defaultOwner());                 // A.hello and B.hello are abstract
        assertEquals(A.class, hello.owner());             // first array interface
    }

    @Test
    void dedupsObjectMethodsAcrossInterfaces() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(new Class<?>[]{A.class, B.class});
        long toStringCount = rs.stream().filter(r -> r.canonical().getName().equals("toString")).count();
        assertEquals(1, toStringCount);
    }

    @Test
    void oneDefaultPlusAbstractResolvesDefaultOwner() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(new Class<?>[]{A.class, B.class});
        ResolvedMethod greet = rs.stream().filter(r -> r.canonical().getName().equals("greet")).findFirst().orElseThrow();
        assertEquals(A.class, greet.defaultOwner());
    }

    @Test
    void differentReturnTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> InterfaceMethodResolver.resolve(new Class<?>[]{A.class, C.class}));
    }

    @Test
    void twoDefaultsThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> InterfaceMethodResolver.resolve(new Class<?>[]{A.class, D.class}));
    }

    @Test
    void parentChildDedupsInheritedMethod() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(new Class<?>[]{Child.class, Parent.class});
        long inherited = rs.stream().filter(r -> r.canonical().getName().equals("inherited")).count();
        assertEquals(1, inherited);
        // "inherited" is declared on Parent, discovered via Child (owner == Child)
        ResolvedMethod m = rs.stream().filter(r -> r.canonical().getName().equals("inherited")).findFirst().orElseThrow();
        assertEquals(Child.class, m.owner());
    }

    @Test
    void singleInterfaceReturnsAllPublicMethodsSorted() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(new Class<?>[]{A.class});
        assertFalse(rs.isEmpty());
        for (int i = 1; i < rs.size(); i++) {
            String prev = rs.get(i - 1).canonical().getName();
            String cur = rs.get(i).canonical().getName();
            assertTrue(prev.compareTo(cur) <= 0 || prev.equals(cur));
        }
    }

    @Test
    void rejectsNonInterface() {
        assertThrows(IllegalArgumentException.class,
                () -> InterfaceMethodResolver.resolve(new Class<?>[]{String.class}));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=InterfaceMethodResolverTest`
Expected: FAIL (compile error — `InterfaceMethodResolver` does not exist).

- [ ] **Step 3: Write the implementation**

```java
/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ... (full Apache-2.0 header, matching sibling files) ...
 */

package io.github.lamspace.generator;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the merged, deduplicated, conflict-checked method set for a list of
 * interfaces. This is the single source of truth for the method order used by
 * both {@code AcceleratedProxy.matchMethods} and {@code InterfaceGenerator},
 * which is what keeps the {@code MethodMapping} indices aligned with the
 * emitted method implementations.
 *
 * <p>Conflict rules (see the multi-interface design spec §2.3): a signature
 * (name + parameter types) may appear in several interfaces. Identical return
 * types merge; differing return types throw {@link IllegalArgumentException};
 * two {@code default} implementations from distinct interfaces throw; a single
 * {@code default} plus abstract declarations merge with the default as the
 * {@code invokeSuper} target.
 */
public final class InterfaceMethodResolver {

    /**
     * A method in the merged interface method set.
     *
     * @param canonical    the first {@code Method} object found for the
     *                     signature, used for the generated implementation and
     *                     the {@code intercept} argument
     * @param owner        the array interface that yielded {@code canonical},
     *                     used as the {@code <clinit>} {@code getMethod} target
     * @param variants     all distinct {@code Method} objects sharing the
     *                     signature, used for dispatch hash routing
     * @param defaultOwner the array interface holding the {@code default}
     *                     implementation, or {@code null} if abstract
     */
    public record ResolvedMethod(Method canonical, Class<?> owner,
                                 List<Method> variants, Class<?> defaultOwner) {
        public ResolvedMethod {
            canonical = Objects.requireNonNull(canonical, "canonical");
            owner = Objects.requireNonNull(owner, "owner");
            variants = List.copyOf(variants);
            if (variants.isEmpty()) {
                throw new IllegalArgumentException("variants must not be empty");
            }
        }
    }

    private InterfaceMethodResolver() {
    }

    /**
     * Resolves the merged method set for the given interfaces.
     *
     * @param interfaces the interfaces to implement; each must be an interface
     * @return the resolved methods, sorted by (name, parameter types)
     * @throws IllegalArgumentException if an element is not an interface, or a
     *                                  cross-interface method conflict is found
     */
    public static List<ResolvedMethod> resolve(Class<?>[] interfaces) {
        Map<String, Method> canonical = new LinkedHashMap<>();
        Map<String, Class<?>> owner = new LinkedHashMap<>();
        Map<String, List<Method>> variants = new LinkedHashMap<>();
        Map<String, Class<?>> defaultOwner = new LinkedHashMap<>();

        for (Class<?> itf : interfaces) {
            if (!itf.isInterface()) {
                throw new IllegalArgumentException(
                        "not an interface: " + itf.getName());
            }
            for (Method m : itf.getMethods()) {
                int mods = m.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                String key = signatureKey(m);
                Method existing = canonical.get(key);
                if (existing == null) {
                    canonical.put(key, m);
                    owner.put(key, itf);
                    List<Method> vs = new ArrayList<>();
                    vs.add(m);
                    variants.put(key, vs);
                    defaultOwner.put(key, m.isDefault() ? itf : null);
                } else if (existing != m) {
                    if (existing.getReturnType() != m.getReturnType()) {
                        throw new IllegalArgumentException(
                                "Conflicting return types for method '" + key
                                        + "': "
                                        + existing.getReturnType().getName()
                                        + " vs " + m.getReturnType().getName());
                    }
                    if (existing.isDefault() && m.isDefault()) {
                        throw new IllegalArgumentException(
                                "Ambiguous default method '" + key + "' in "
                                        + existing.getDeclaringClass().getName()
                                        + " and " + m.getDeclaringClass().getName());
                    }
                    List<Method> vs = variants.get(key);
                    if (!vs.contains(m)) {
                        vs.add(m);
                    }
                    if (m.isDefault() && !existing.isDefault()) {
                        defaultOwner.put(key, itf);
                    }
                }
            }
        }

        List<ResolvedMethod> result = new ArrayList<>();
        for (Map.Entry<String, Method> e : canonical.entrySet()) {
            String key = e.getKey();
            result.add(new ResolvedMethod(e.getValue(), owner.get(key),
                    variants.get(key), defaultOwner.get(key)));
        }
        result.sort(Comparator.comparing(
                        (ResolvedMethod r) -> r.canonical().getName())
                .thenComparing(r -> Arrays.toString(
                        r.canonical().getParameterTypes())));
        return result;
    }

    private static String signatureKey(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(p.getName()).append(',');
        }
        return sb.append(')').toString();
    }
}
```

- [ ] **Step 4: Run the test to verify it passes**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=InterfaceMethodResolverTest`
Expected: PASS (8 tests).

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceMethodResolver.java \
        src/test/java/io/github/lamspace/generator/InterfaceMethodResolverTest.java
git commit -m "feat: add InterfaceMethodResolver for multi-interface method merging"
```

---

## Task 2: Wire the resolver into the interface path (N == 1 refactor)

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (CacheParams, matchMethods, generateProxyClass, proxy)
- Modify: `src/main/java/io/github/lamspace/generator/MethodInfo.java`
- Modify: `src/main/java/io/github/lamspace/generator/DispatchGenerator.java`
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java`
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java`
- Modify: `src/main/java/io/github/lamspace/generator/ClassGenerator.java`

**Interfaces:**
- Consumes: `InterfaceMethodResolver.resolve` (Task 1).
- Produces: an interface path that accepts `Class<?>[]`; no public API change yet. The generated `dispatch` now reads the default owner from `MethodInfo.defaultOwner()`.

This is the load-bearing refactor. **No behavior change for a single interface** — the entire existing suite (`AcceleratedProxyInterfaceProxyTest`, `DefaultMethodInvocationTest`, etc.) is the regression gate.

- [ ] **Step 1: Add `defaultOwner` to `MethodInfo`**

In `MethodInfo.java`, change the record to four components with a convenience 3-arg constructor (class path keeps using the 3-arg form):

```java
record MethodInfo(Method method, String staticFieldName, int methodHash,
                  Class<?> defaultOwner) {
    MethodInfo(Method method, String staticFieldName, int methodHash) {
        this(method, staticFieldName, methodHash, null);
    }

    MethodInfo {
        if (method == null || staticFieldName == null) {
            throw new NullPointerException();
        }
    }
}
```

- [ ] **Step 2: Update `DispatchGenerator.generateDispatch` to use `defaultOwner`**

Remove the `interfaceInternalName` parameter and change the default branch:

```java
static void generateDispatch(ClassWriter cw,
                             String generatedInternal,
                             String superInternal,
                             List<MethodInfo> infos,
                             boolean isClassProxy) {
    // ... existing hash computation and branch chain, unchanged ...

    for (int i = 0; i < infos.size(); i++) {
        MethodInfo info = infos.get(i);
        Method method = info.method();
        // ... existing hash compare + branch body ...

        Class<?> declaringClass = method.getDeclaringClass();
        boolean isObjectMethod = declaringClass == Object.class;

        if (isClassProxy || isObjectMethod) {
            // ... unchanged direct-super-call body ...
        } else if (info.defaultOwner() != null) {
            // Default interface method: INVOKESPECIAL against the default
            // owner interface (a direct superinterface of the generated class).
            mv.visitVarInsn(Opcodes.ALOAD, 0);

            Class<?>[] paramTypes = method.getParameterTypes();
            int argSlot = 2;
            for (int j = 0; j < paramTypes.length; j++) {
                mv.visitVarInsn(Opcodes.ALOAD, argSlot);
                BytecodeUtils.pushInt(mv, j);
                mv.visitInsn(Opcodes.AALOAD);
                Class<?> pt = paramTypes[j];
                if (pt.isPrimitive()) {
                    BytecodeUtils.unboxPrimitive(mv, pt);
                } else if (pt != Object.class) {
                    mv.visitTypeInsn(Opcodes.CHECKCAST, Type.getInternalName(pt));
                }
            }

            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    Type.getInternalName(info.defaultOwner()),
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
            // ... unchanged AbstractMethodError body ...
        }
    }
    // ... unchanged fallback ...
}
```

Note: the previous default branch used `boolean isDefault = method.isDefault();` and the now-removed `interfaceInternalName` as owner. Replace with `info.defaultOwner() != null`.

- [ ] **Step 3: Update `ClassGenerator` call site**

In `ClassGenerator.generate()` (currently line 161-162), drop the `null` argument:

```java
DispatchGenerator.generateDispatch(cw, generatedInternal,
        targetInternal, infos, true);
```

- [ ] **Step 4: Update `InterfaceDispatcher.dispatchMethods` to take the resolved list**

Change the signature and the loop head. `generateImplementation`, `addStaticField`, and the registry/field-name logic are unchanged; only the source of `method` changes (now `rm.canonical()`) and the clinit target becomes `rm.owner()`.

```java
static List<String> dispatchMethods(ClassWriter cw,
                                    List<InterfaceMethodResolver.ResolvedMethod> resolved,
                                    String generatedInternal,
                                    MethodMapping mapping,
                                    int interceptorCount,
                                    ClinitRegistry registry) {
    List<String> dispatchedMethods = new ArrayList<>();

    for (int i = 0; i < resolved.size(); i++) {
        InterfaceMethodResolver.ResolvedMethod rm = resolved.get(i);
        Method method = rm.canonical();

        int interceptorIndex = mapping.indices()[i];
        boolean shouldIntercept = interceptorIndex >= 0;

        String suffix = "$" + i;
        String methodFieldName = "_method$" + method.getName() + suffix;

        addStaticField(cw, methodFieldName, "Ljava/lang/reflect/Method;");

        registry.register(rm.owner(), method, generatedInternal,
                methodFieldName, i);

        generateImplementation(cw, method, generatedInternal,
                shouldIntercept, interceptorIndex, methodFieldName);

        dispatchedMethods.add(method.getName());
    }
    return dispatchedMethods;
}
```

(Delete the old `interfaceClass.getMethods()` + `Arrays.sort` + static/final `continue` block — the resolver already filters and sorts.)

- [ ] **Step 5: Update `InterfaceGenerator` to resolve and emit multi-interface `implements`**

Replace the field `private final Class<?> interfaceClass;` with `private final Class<?>[] interfaces;`, and change the constructor (body assigns a defensive copy, mirroring `interceptors.clone()`):

```java
public InterfaceGenerator(Class<?>[] interfaces,
                          Interceptor[] interceptors,
                          MethodMapping mapping) {
    this.interfaces = interfaces.clone();
    this.interceptors = interceptors.clone();
    this.mapping = mapping;
}
```

Then rewrite `generate()`:

```java
public byte[] generate() {
    List<InterfaceMethodResolver.ResolvedMethod> resolved =
            InterfaceMethodResolver.resolve(interfaces);

    String baseName;
    if (interfaces.length == 1) {
        String targetInternal = Type.getInternalName(interfaces[0]);
        baseName = targetInternal.substring(targetInternal.lastIndexOf('/') + 1);
    } else {
        baseName = "MultiInterface";
    }
    String generatedInternal = "io/github/lamspace/" + baseName
            + "$$AcceleratedProxy$$" + COUNTER.getAndIncrement();

    ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

    String[] implemented = new String[interfaces.length + 1];
    for (int i = 0; i < interfaces.length; i++) {
        implemented[i] = Type.getInternalName(interfaces[i]);
    }
    implemented[interfaces.length] = "io/github/lamspace/DispatchTarget";

    cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
            generatedInternal, null, "java/lang/Object", implemented);

    String interceptorDesc = Type.getDescriptor(Interceptor.class);
    for (int i = 0; i < interceptors.length; i++) {
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "_interceptor$" + i, interceptorDesc, null, null);
    }

    generateConstructor(cw, generatedInternal, interceptorDesc);

    ClinitRegistry registry = new ClinitRegistry();
    InterfaceDispatcher.dispatchMethods(cw, resolved, generatedInternal,
            mapping, interceptors.length, registry);

    List<ClinitRegistry.Entry> entries = registry.drain();

    // Dispatch infos: one entry per distinct Method variant, all carrying the
    // merged method's defaultOwner so every hash routes to the same handler.
    List<Method> allVariants = new ArrayList<>();
    for (InterfaceMethodResolver.ResolvedMethod rm : resolved) {
        allVariants.addAll(rm.variants());
    }
    Map<Method, Integer> hashMap = DispatchGenerator.resolveHashes(allVariants);

    List<MethodInfo> infos = new ArrayList<>();
    for (int i = 0; i < resolved.size(); i++) {
        InterfaceMethodResolver.ResolvedMethod rm = resolved.get(i);
        String fieldName = entries.get(i).methodFieldName();
        for (Method variant : rm.variants()) {
            infos.add(new MethodInfo(variant, fieldName,
                    hashMap.get(variant), rm.defaultOwner()));
        }
    }
    DispatchGenerator.generateDispatch(cw, generatedInternal,
            "java/lang/Object", infos, false);

    generateClinit(cw, generatedInternal, entries);

    cw.visitEnd();
    return cw.toByteArray();
}
```

Keep `generateConstructor` and `generateClinit` unchanged (they already use `interceptors.length` and `entry.targetClass()`).

- [ ] **Step 6: Update `AcceleratedProxy` — `CacheParams`, `matchMethods`, `generateProxyClass`, `proxy`**

Add an `interfaces` field to `CacheParams` (nullable) and update `equals`/`hashCode`:

```java
private record CacheParams(Class<?> targetClass,
                           Class<?>[] interfaces,
                           MethodMapping mapping,
                           Object[] constructorArgs) {
    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof CacheParams other)) return false;
        return targetClass == other.targetClass
                && Arrays.equals(interfaces, other.interfaces)
                && mapping.equals(other.mapping)
                && Arrays.equals(constructorArgs, other.constructorArgs);
    }

    @Override
    public int hashCode() {
        int result = targetClass != null
                ? System.identityHashCode(targetClass) : 0;
        result = 31 * result + (interfaces != null
                ? Arrays.hashCode(interfaces) : 0);
        result = 31 * result + mapping.hashCode();
        result = 31 * result + Arrays.hashCode(constructorArgs);
        return result;
    }
}
```

Add an array-based `matchMethods` overload and a private `proxyInterfaces` helper; rewrite `generateProxyClass` to branch on `params.interfaces()`:

```java
private static MatchResult matchMethods(Class<?>[] interfaces,
                                        Group[] groups) {
    List<InterfaceMethodResolver.ResolvedMethod> resolved =
            InterfaceMethodResolver.resolve(interfaces);
    Method[] methods = new Method[resolved.size()];
    for (int i = 0; i < resolved.size(); i++) {
        methods[i] = resolved.get(i).canonical();
    }
    return matchMethods(methods, groups);
}

// shared: sort + Group chain (extract from the existing matchMethods steps 2-3)
private static MatchResult matchMethods(Method[] methods, Group[] groups) {
    Arrays.sort(methods, Comparator.comparing(Method::getName)
            .thenComparing(m -> Arrays.toString(m.getParameterTypes())));
    // ... existing Group-chain matching loop, unchanged ...
    return new MatchResult(interceptorList.toArray(new Interceptor[0]),
            new MethodMapping(indices));
}
```

Simplify the class-path `matchMethods(Class<?> target, Group[] groups)` to gather `target.getDeclaredMethods()`, filter static/final/private, and delegate to `matchMethods(methods, groups)` (remove the now-dead interface branch).

Rewrite `generateProxyClass`:

```java
private static Class<?> generateProxyClass(Class<?> key, CacheParams params) {
    try {
        byte[] bytecode;
        MethodMapping mapping = params.mapping();
        int interceptorCount = mapping.interceptorCount();
        Interceptor[] dummy = new Interceptor[interceptorCount];

        if (params.interfaces() != null) {
            InterfaceGenerator generator = new InterfaceGenerator(
                    params.interfaces(), dummy, mapping);
            bytecode = generator.generate();
            return java.lang.invoke.MethodHandles.lookup()
                    .defineHiddenClass(bytecode, true).lookupClass();
        } else {
            Class<?> target = params.targetClass();
            ClassGenerator generator = new ClassGenerator(target, dummy,
                    mapping, params.constructorArgs());
            bytecode = generator.generate();
            return LookupManager.getLookup(target)
                    .defineHiddenClass(bytecode, true).lookupClass();
        }
    } catch (Exception e) {
        throw new RuntimeException("Failed to generate proxy class", e);
    }
}
```

Add `proxyInterfaces` and route single-interface targets through it:

```java
private static Object proxyInterfaces(Class<?>[] interfaces, Group... groups) {
    if (interfaces == null || interfaces.length == 0) {
        throw new IllegalArgumentException(
                "interfaces must not be null or empty");
    }
    if (groups == null || groups.length == 0) {
        throw new IllegalArgumentException(
                "groups must not be null or empty");
    }
    for (Class<?> itf : interfaces) {
        if (itf == null || !itf.isInterface()) {
            throw new IllegalArgumentException(
                    "interfaces must contain only interfaces");
        }
    }
    Class<?>[] copy = interfaces.clone();
    MatchResult matchResult = matchMethods(copy, groups);
    CacheParams params = new CacheParams(null, copy,
            matchResult.mapping(), new Object[0]);
    try {
        Class<?> proxyClass = PROXY_CLASS_CACHE.get(copy[0], params);
        int interceptorCount = matchResult.interceptors().length;
        Object[] initArgs = new Object[interceptorCount];
        System.arraycopy(matchResult.interceptors(), 0, initArgs, 0,
                interceptorCount);
        Class<?>[] ctorArgTypes = new Class<?>[interceptorCount];
        Arrays.fill(ctorArgTypes, Interceptor.class);
        Constructor<?> ctor = proxyClass.getConstructor(ctorArgTypes);
        return ctor.newInstance((Object[]) initArgs);
    } catch (Exception e) {
        throw new RuntimeException(
                "Failed to create proxy for interfaces", e);
    }
}
```

In the existing `proxy(Class<T> target, Object[] constructorArgs, Group... groups)`, after the existing null/empty validations, insert the interface short-circuit and change the class-path `CacheParams` construction to pass `null` for `interfaces`:

```java
    if (target.isInterface()) {
        return (T) proxyInterfaces(new Class<?>[]{target}, groups);
    }

    MatchResult matchResult = matchMethods(target, groups);
    CacheParams params = new CacheParams(target, null,
            matchResult.mapping(), constructorArgs);
    // ... rest of the class path unchanged ...
```

Add `import io.github.lamspace.generator.InterfaceMethodResolver;`.

- [ ] **Step 7: Run the full existing suite to verify the refactor is regression-free**

Run: `mvn -s /home/lam/repo/settings.xml test`
Expected: PASS — especially `AcceleratedProxyInterfaceProxyTest`, `DefaultMethodInvocationTest`, `MultiInterceptorInterfaceProxyTest`, `GroupMatchingTest`, `AcceleratedProxyClassProxyTest`. No test may fail.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java \
        src/main/java/io/github/lamspace/generator/
git commit -m "refactor: unify interface proxy path onto Class<?>[]"
```

---

## Task 3: Public multi-interface API + end-to-end tests

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (two public overloads)
- Test: `src/test/java/io/github/lamspace/MultiInterfaceProxyTest.java`

**Interfaces:**
- Consumes: `proxyInterfaces` (Task 2).
- Produces: `Object proxy(Class<?>[] interfaces, Interceptor interceptor)` and `Object proxy(Class<?>[] interfaces, Group... groups)`.

- [ ] **Step 1: Add the public overloads**

```java
/**
 * Creates a proxy implementing all given interfaces. The returned object can
 * be cast to each interface. Methods with the same signature and return type
 * across interfaces are merged into a single implementation.
 *
 * @param interfaces  the interfaces to implement; must be non-null, non-empty,
 *                    and contain only interfaces
 * @param interceptor invoked for every method call on the proxy
 * @return a proxy instance implementing every interface
 * @throws IllegalArgumentException if interfaces is invalid or a
 *                                  cross-interface method conflict is found
 */
public static Object proxy(Class<?>[] interfaces, Interceptor interceptor) {
    if (interceptor == null) {
        throw new IllegalArgumentException("interceptor must not be null");
    }
    return proxyInterfaces(interfaces, Group.otherwise(interceptor));
}

/**
 * Creates a proxy implementing all given interfaces with method-group-based
 * interceptor assignment.
 *
 * @param interfaces the interfaces to implement
 * @param groups     one or more Group bindings
 * @return a proxy instance implementing every interface
 */
public static Object proxy(Class<?>[] interfaces, Group... groups) {
    return proxyInterfaces(interfaces, groups);
}
```

- [ ] **Step 2: Write the failing end-to-end tests**

`src/test/java/io/github/lamspace/MultiInterfaceProxyTest.java` — covering spec §4 cases 1-12 plus single-interface regression:

```java
package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.*;

class MultiInterfaceProxyTest {

    interface Greeter {
        String hello(String name);
    }

    interface Auditable {
        String audit();
    }

    interface Named {
        String hello(String name);          // same signature as Greeter.hello
    }

    interface DefaultGreeter {
        String hello(String name);
        default String greet() { return "Hello, World"; }
    }

    interface AbstractGreeter {
        String greet();                     // abstract same-signature as default
    }

    interface DefaultGreeter2 {
        default String greet() { return "Hello again"; }  // both-default conflict
    }

    interface NumberGreeter {
        int hello(String name);             // different return type conflict
    }

    interface Parent {
        String inherited();
    }

    interface Child extends Parent {
        String own();
    }

    @Test
    void proxiesMultipleInterfaces() {
        Object p = AcceleratedProxy.proxy(new Class<?>[]{Greeter.class, Auditable.class},
                (obj, method, args) -> "[" + method.getName() + "]");
        Greeter g = (Greeter) p;
        Auditable a = (Auditable) p;
        assertSame(g, a);
        assertEquals("[hello]", g.hello("x"));
        assertEquals("[audit]", a.audit());
    }

    @Test
    void mergesSharedMethod() {
        Object p = AcceleratedProxy.proxy(new Class<?>[]{Greeter.class, Named.class},
                (obj, method, args) -> "[" + args[0] + "]");
        assertEquals("[World]", ((Greeter) p).hello("World"));
        assertEquals("[World]", ((Named) p).hello("World"));
    }

    @Test
    void oneDefaultPlusAbstractInvokesDefault() {
        Object p = AcceleratedProxy.proxy(
                new Class<?>[]{DefaultGreeter.class, AbstractGreeter.class},
                (obj, method, args) -> {
                    if (method.isDefault()) {
                        return AcceleratedProxy.invokeSuper(obj, method, args);
                    }
                    return null;
                });
        assertEquals("Hello, World", ((DefaultGreeter) p).greet());
    }

    @Test
    void twoDefaultsThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(new Class<?>[]{
                        DefaultGreeter.class, DefaultGreeter2.class},
                        (obj, method, args) -> null));
    }

    @Test
    void differentReturnTypeThrows() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(new Class<?>[]{
                        Greeter.class, NumberGreeter.class},
                        (obj, method, args) -> null));
    }

    @Test
    void threeInterfaces() {
        Object p = AcceleratedProxy.proxy(new Class<?>[]{
                        Greeter.class, Auditable.class, Named.class},
                (obj, method, args) -> method.getName());
        assertEquals("hello", ((Greeter) p).hello("x"));
        assertEquals("audit", ((Auditable) p).audit());
    }

    @Test
    void parentChildDedup() {
        Object p = AcceleratedProxy.proxy(new Class<?>[]{Child.class, Parent.class},
                (obj, method, args) -> "[" + method.getName() + "]");
        assertEquals("[inherited]", ((Child) p).inherited());
        assertEquals("[own]", ((Child) p).own());
    }

    @Test
    void objectMethodsBehave() {
        Object p = AcceleratedProxy.proxy(new Class<?>[]{Greeter.class, Auditable.class},
                (obj, method, args) -> null);
        assertTrue(p.equals(p));
        assertNotNull(p.toString());
        assertNotEquals(0, p.hashCode());
    }

    @Test
    void groupChainAcrossInterfaces() {
        Object p = AcceleratedProxy.proxy(
                new Class<?>[]{Greeter.class, Auditable.class},
                Group.of(m -> m.getName().equals("hello"),
                        (obj, method, args) -> "[hello]"),
                Group.otherwise((obj, method, args) -> "[other]"));
        assertEquals("[hello]", ((Greeter) p).hello("x"));
        assertEquals("[other]", ((Auditable) p).audit());
    }

    @Test
    void cacheReusesGeneratedClass() {
        Interceptor i = (obj, method, args) -> null;
        Object p1 = AcceleratedProxy.proxy(new Class<?>[]{Greeter.class, Auditable.class}, i);
        Object p2 = AcceleratedProxy.proxy(new Class<?>[]{Greeter.class, Auditable.class}, i);
        assertEquals(p1.getClass(), p2.getClass());
    }

    @Test
    void invokeSuperRoutesBothHashesToSameDefault() throws Exception {
        Object p = AcceleratedProxy.proxy(
                new Class<?>[]{DefaultGreeter.class, AbstractGreeter.class},
                (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));
        Method viaDefault = DefaultGreeter.class.getMethod("greet");
        Method viaAbstract = AbstractGreeter.class.getMethod("greet");
        assertEquals("Hello, World",
                AcceleratedProxy.invokeSuper(p, viaDefault, new Object[0]));
        assertEquals("Hello, World",
                AcceleratedProxy.invokeSuper(p, viaAbstract, new Object[0]));
    }

    @Test
    void invalidInputsThrow() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.proxy((Class<?>[]) null, (obj, m, a) -> null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.proxy(new Class<?>[0], (obj, m, a) -> null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.proxy(new Class<?>[]{String.class}, (obj, m, a) -> null));
    }

    @Test
    void singleInterfaceStillWorks() {
        Greeter g = AcceleratedProxy.proxy(Greeter.class,
                (obj, method, args) -> "[solo]");
        assertEquals("[solo]", g.hello("x"));
    }
}
```

- [ ] **Step 3: Run the new tests to verify they fail (before the public overloads exist)**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=MultiInterfaceProxyTest`
Expected: FAIL (compile error — `proxy(Class<?>[], ...)` does not exist).

- [ ] **Step 4: Run the tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=MultiInterfaceProxyTest`
Expected: PASS (13 tests).

- [ ] **Step 5: Run the full suite**

Run: `mvn -s /home/lam/repo/settings.xml test`
Expected: PASS (all tests, old and new).

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java \
        src/test/java/io/github/lamspace/MultiInterfaceProxyTest.java
git commit -m "feat: add multi-interface proxy API"
```

---

## Task 4: Documentation and benchmark verification

**Files:**
- Modify: `docs/openproxy-future-roadmap.md`
- Modify: `README.md`
- Modify: `README_CN.md`
- Modify: `docs/migration-guide.md`

- [ ] **Step 1: Mark roadmap item complete and add a subsection**

In `docs/openproxy-future-roadmap.md`:
1. Change the Phase 3 table row 2 from `| 2 | P3 | **多接口代理** | 一个代理类实现多个接口 |` to `| 2 | P3 | **多接口代理**（已完成） | 一个代理类实现多个接口 |`.
2. Replace the existing `### 多接口代理` bullet list with a `### 多接口代理（已完成）` section that states the API and conflict rules, mirroring the existing `### 接口默认方法调用（已完成）` section:

```markdown
### 多接口代理（已完成）

- `AcceleratedProxy.proxy(Class<?>[] interfaces, Interceptor)` / `proxy(Class<?>[], Group...)` 生成一个实现全部接口的代理类，返回 `Object`，调用方按需 cast
- 内部接口路径统一为 `Class<?>[]`，单接口即长度 1 的特例（字节级一致，不影响基准）
- 冲突规则：相同签名 + 相同返回类型合并；不同返回类型抛 `IllegalArgumentException`；两个 `default` 抛 `IllegalArgumentException`；一个 `default` + 一个抽象合并并调用该 default
```

- [ ] **Step 2: Update README (EN + CN)**

In `README.md` `## ✨ Features`, add a bullet after the Multi-Interceptor line:

```markdown
- **Multi-interface proxy** — `AcceleratedProxy.proxy(new Class<?>[]{...}, interceptor)` implements several interfaces in one proxy object
```

In `## ⚡ Quick Start`, after the "Multi-Interceptor (Method Grouping)" example, add:

```java
// Multi-Interface Proxy
Object p = AcceleratedProxy.proxy(new Class<?>[]{Greeter.class, Auditable.class},
        (obj, method, args) -> {
            System.out.println("calling " + method.getName());
            return null;
        });
Greeter g = (Greeter) p;
Auditable a = (Auditable) p;
```

Make the matching additions to `README_CN.md` in Chinese.

- [ ] **Step 3: Update migration guide**

In `docs/migration-guide.md`, in the `java.lang.reflect.Proxy` → OpenProxy section, add a short multi-interface mapping note:

```markdown
`java.lang.reflect.Proxy.newProxyInstance(loader, new Class<?>[]{A.class, B.class}, handler)`
maps to `AcceleratedProxy.proxy(new Class<?>[]{A.class, B.class}, interceptor)`.
```

- [ ] **Step 4: Benchmark verification gate**

Run the existing JMH suite and confirm the interface- and class-proxy numbers are unchanged vs `docs/benchmark-results.md` (the `N == 1` invariant makes this expected; a regression means Task 2 changed `N == 1` bytecode and must be fixed). Benchmark invocation is documented in `docs/benchmark-results.md` (raw JMH main class `io.github.lamspace.benchmark.ProxyBenchmark`); build first with `mvn -s /home/lam/repo/settings.xml package -DskipTests`, then run the documented `java ... ProxyBenchmark` command.

- [ ] **Step 5: Commit**

```bash
git add docs/openproxy-future-roadmap.md README.md README_CN.md docs/migration-guide.md
git commit -m "docs: document multi-interface proxy"
```

---

## Known limitations (out of scope, from spec §7)

- **No covariant return-type merging** — differing return types always throw.
- **No most-specific-interface-wins for default conflicts** — two `default`s always throw; callers pass only the more specific interface.
- **Interface that redeclares an `Object` method** (`toString`/`equals`/`hashCode`) combined with another interface is not specially handled; the merged method's behavior follows the first interface in the array (same edge case as the existing single-interface path).
- **Class + interface proxying** (`extends` a class while also `implements` interfaces) is not part of this feature.

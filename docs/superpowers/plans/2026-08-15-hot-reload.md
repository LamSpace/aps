# Hot Reload / Hot Swap Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add two runtime-lifecycle capabilities to OpenProxy: (1) deterministic cache eviction so a hot-deployed target class gets a fresh proxy while old instances keep working, and (2) in-place interceptor rebinding on a live proxy instance.

**Architecture:** Class hot-reload already works structurally (identity-keyed `WeakCache` + per-generation unique class names), so that half adds a `removeIf` on `WeakCache` plus two `AcceleratedProxy` entry points. Interceptor rebind drops `final` on the per-interceptor instance fields and emits a `rebind(Interceptor[])` method on every generated proxy (via a shared ASM helper), exposed through a new internal `Rebindable` interface and `AcceleratedProxy.rebind(...)` overloads. The dispatch/override emitters are untouched, so the hot path stays byte-identical.

**Tech Stack:** Java 25, ASM 9.7.1, JUnit 5.11.4, JMH 1.37.

**Spec:** `docs/superpowers/specs/2026-08-15-hot-reload-design.md`

## Global Constraints

- All `mvn` commands MUST use `-s /home/lam/repo/settings.xml`.
- Java source/target 25; generated classes use `Opcodes.V24`.
- Do **not** modify `MethodDispatcher`, `InterfaceDispatcher`, `DispatchGenerator` — the hot-path emitters must stay untouched.
- Interceptor fields stay **plain** (non-`volatile`, non-`final` after this change). Rebind uses `VarHandle.fullFence()`, not `volatile`.
- Generated field naming: `_interceptor$i`; the generated `rebind` method descriptor is `([Lio/github/lamspace/Interceptor;)V`.
- Follow existing code style: Javadoc on every public method, `IllegalArgumentException` for bad arguments, ASL-2.0 header on new files.
- `docs/openproxy-future-roadmap.md` already has uncommitted changes in the working tree — edit it in place, do not revert or `git checkout` it.

---

### Task 1: `WeakCache.removeIf`

**Files:**
- Modify: `src/main/java/io/github/lamspace/WeakCache.java`
- Test: `src/test/java/io/github/lamspace/WeakCacheTest.java`

**Interfaces:**
- Produces: `void WeakCache.removeIf(Predicate<? super K> predicate)` — removes cache entries whose unwrapped key satisfies `predicate` (skipping the null-key sentinel). Consumed by Task 2.

- [ ] **Step 1: Write the failing tests**

Add to `WeakCacheTest.java`. Add imports `java.util.concurrent.atomic.AtomicInteger`, `java.util.concurrent.atomic.AtomicBoolean`.

```java
@Test
void removeIfRemovesMatchingKeysOnly() {
    WeakCache<String, Integer, String> cache = new WeakCache<>(
            (key, param) -> param,
            (key, param) -> key + ":" + param
    );
    String va = cache.get("a", 1);
    String vb = cache.get("b", 1);

    cache.removeIf(k -> "a".equals(k));

    assertFalse(cache.containsValue(va));
    assertTrue(cache.containsValue(vb));
    assertEquals(1, cache.size());
}

@Test
void removeIfCausesReevaluationOnNextGet() {
    AtomicInteger calls = new AtomicInteger();
    WeakCache<String, Integer, String> cache = new WeakCache<>(
            (key, param) -> param,
            (key, param) -> { calls.incrementAndGet(); return key + ":" + param; }
    );
    cache.get("a", 1);
    cache.get("a", 1);
    assertEquals(1, calls.get());

    cache.removeIf(k -> "a".equals(k));
    cache.get("a", 1);
    assertEquals(2, calls.get());
}

@Test
void removeIfOnEmptyCacheIsNoOp() {
    WeakCache<String, Integer, String> cache = new WeakCache<>(
            (key, param) -> param,
            (key, param) -> key + ":" + param
    );
    assertDoesNotThrow(() -> cache.removeIf(k -> true));
    assertEquals(0, cache.size());
}

@Test
void removeIfSkipsNullSentinel() {
    WeakCache<String, Integer, String> cache = new WeakCache<>(
            (key, param) -> param,
            (key, param) -> "null:" + param
    );
    cache.get(null, 1);
    AtomicBoolean sawNull = new AtomicBoolean(false);
    cache.removeIf(k -> { if (k == null) sawNull.set(true); return false; });
    assertFalse(sawNull.get());
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=WeakCacheTest test`
Expected: FAIL — `removeIf` does not exist (compile error).

- [ ] **Step 3: Implement `removeIf`**

In `WeakCache.java`, add `import java.util.function.Predicate;` to the imports (alongside the existing `BiFunction`/`Supplier`). Add the method right after `size()` (before `expungeStaleEntries`):

```java
/**
 * Removes every entry whose key satisfies the given predicate. The null-key
 * sentinel is never passed to the predicate. This is a best-effort, weakly
 * consistent sweep — safe to call concurrently with {@link #get}.
 *
 * @param predicate tests each (non-null) key for removal
 */
void removeIf(Predicate<? super K> predicate) {
    Objects.requireNonNull(predicate);
    expungeStaleEntries();
    for (Object cacheKey : map.keySet()) {
        if (cacheKey == CacheKey.NULL_KEY) {
            continue;
        }
        @SuppressWarnings("unchecked")
        K key = (K) ((CacheKey<?>) cacheKey).get();
        if (predicate.test(key)) {
            ((CacheKey<?>) cacheKey).expungeFrom(map, reverseMap);
        }
    }
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=WeakCacheTest test`
Expected: PASS.

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/WeakCache.java src/test/java/io/github/lamspace/WeakCacheTest.java
git commit -m "feat: add WeakCache.removeIf for deterministic eviction"
```

---

### Task 2: `evict` / `evictClassLoader` (class hot-reload)

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java`
- Test: `src/test/java/io/github/lamspace/HotReloadTest.java` (new)

**Interfaces:**
- Consumes: `WeakCache.removeIf` (Task 1).
- Produces: `static void AcceleratedProxy.evict(Class<?> target)`, `static void AcceleratedProxy.evictClassLoader(ClassLoader cl)`.

- [ ] **Step 1: Write the failing test file**

Create `src/test/java/io/github/lamspace/HotReloadTest.java` with the Apache header, package `io.github.lamspace`. Imports: `org.junit.jupiter.api.Test`, `java.io.IOException`, `java.io.InputStream`, `static org.junit.jupiter.api.Assertions.*`.

```java
class HotReloadTest {

    public static class Greeter {
        public String hello(String name) { return "Hello, " + name; }
    }

    /** Child-first loader that gives the named class a fresh definition. */
    static final class ReloadingClassLoader extends ClassLoader {
        private final String targetName;

        ReloadingClassLoader(String targetName) {
            super(HotReloadTest.class.getClassLoader());
            this.targetName = targetName;
        }

        @Override
        protected Class<?> loadClass(String name, boolean resolve)
                throws ClassNotFoundException {
            synchronized (getClassLoadingLock(name)) {
                Class<?> c = findLoadedClass(name);
                if (c == null) {
                    if (name.equals(targetName)) {
                        c = defineFresh(name);
                    } else {
                        c = super.loadClass(name, resolve);
                    }
                }
                if (resolve) {
                    resolveClass(c);
                }
                return c;
            }
        }

        private Class<?> defineFresh(String name) throws ClassNotFoundException {
            String resource = name.replace('.', '/') + ".class";
            try (InputStream in = getParent().getResourceAsStream(resource)) {
                if (in == null) {
                    throw new ClassNotFoundException(name);
                }
                byte[] bytes = in.readAllBytes();
                return defineClass(name, bytes, 0, bytes.length);
            } catch (IOException e) {
                throw new ClassNotFoundException(name, e);
            }
        }
    }

    @Test
    void evictForcesRegeneration() {
        Greeter p1 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        Class<?> c1 = p1.getClass();
        Greeter p2 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        assertSame(c1, p2.getClass());   // cached: same class

        AcceleratedProxy.evict(Greeter.class);
        Greeter p3 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> null);
        assertNotSame(c1, p3.getClass()); // evicted: fresh class
    }

    @Test
    void oldInstanceSurvivesEviction() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> "intercepted");
        AcceleratedProxy.evict(Greeter.class);
        assertEquals("intercepted", p.hello("x"));
    }

    @Test
    void evictClassLoaderScopesByLoader() throws Exception {
        Greeter app = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> "app");
        Class<?> appClass = app.getClass();

        ReloadingClassLoader loader =
                new ReloadingClassLoader("io.github.lamspace.HotReloadTest$Greeter");
        Class<?> reloaded =
                loader.loadClass("io.github.lamspace.HotReloadTest$Greeter");
        Object other = AcceleratedProxy.proxy(reloaded, (o, m, a) -> "other");
        Class<?> otherClass = other.getClass();

        AcceleratedProxy.evictClassLoader(loader);
        Object other2 = AcceleratedProxy.proxy(reloaded, (o, m, a) -> "other2");
        assertNotSame(otherClass, other2.getClass());   // regenerated

        Greeter app2 = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> "app2");
        assertSame(appClass, app2.getClass());          // app-loader entry untouched
    }

    @Test
    void reloadedClassYieldsDistinctProxy() throws Exception {
        Greeter original = AcceleratedProxy.proxy(Greeter.class, (o, m, a) -> "orig");
        Class<?> originalClass = original.getClass();

        ReloadingClassLoader loader =
                new ReloadingClassLoader("io.github.lamspace.HotReloadTest$Greeter");
        Class<?> reloaded =
                loader.loadClass("io.github.lamspace.HotReloadTest$Greeter");
        assertNotSame(Greeter.class, reloaded);

        Object reloadedProxy = AcceleratedProxy.proxy(reloaded, (o, m, a) -> "reloaded");
        assertNotSame(originalClass, reloadedProxy.getClass());

        assertEquals("orig", original.hello("x"));
        assertEquals("reloaded",
                reloaded.getMethod("hello", String.class).invoke(reloadedProxy, "x"));
    }

    @Test
    void evictIsIdempotentAndRejectsNull() {
        assertDoesNotThrow(() -> AcceleratedProxy.evict(Greeter.class));
        assertDoesNotThrow(() -> AcceleratedProxy.evictClassLoader(
                HotReloadTest.class.getClassLoader()));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.evict(null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.evictClassLoader(null));
    }
}
```

- [ ] **Step 2: Run tests to verify they fail**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=HotReloadTest test`
Expected: FAIL — `evict`/`evictClassLoader` do not exist (compile error).

- [ ] **Step 3: Implement `evict` and `evictClassLoader`**

In `AcceleratedProxy.java`, add the two methods immediately after the `proxyStatic(Class<?> target, Group... groups)` method (before `collectStaticMethods`):

```java
/**
 * Evicts all cached proxy classes whose cache-key class is {@code target},
 * forcing the next {@code proxy(...)} call for it to regenerate a fresh
 * class. Existing proxy instances are unaffected — they hold direct
 * references to their own hidden class and target class.
 *
 * <p>Cache-key asymmetry: class proxies key on the target; interface proxies
 * key on the first interface. So for an interface proxy, pass the first
 * interface.
 *
 * @param target the cache-key class to evict; must not be null
 */
public static void evict(Class<?> target) {
    if (target == null) {
        throw new IllegalArgumentException("target must not be null");
    }
    PROXY_CLASS_CACHE.removeIf(k -> k == target);
}

/**
 * Evicts all cached proxy classes whose cache-key class was loaded by the
 * given {@link ClassLoader}. Intended for long-running frameworks that
 * hot-deploy classes under a dedicated loader and want deterministic
 * cleanup when that loader is retired.
 *
 * @param cl the class loader whose proxy classes to evict; must not be null
 */
public static void evictClassLoader(ClassLoader cl) {
    if (cl == null) {
        throw new IllegalArgumentException("classLoader must not be null");
    }
    PROXY_CLASS_CACHE.removeIf(k -> k != null && k.getClassLoader() == cl);
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=HotReloadTest test`
Expected: PASS.

- [ ] **Step 5: Run full test suite (regression)**

Run: `mvn -s /home/lam/repo/settings.xml -q test`
Expected: PASS — no existing test regressed.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java src/test/java/io/github/lamspace/HotReloadTest.java
git commit -m "feat: add evict/evictClassLoader for class hot-reload"
```

---

### Task 3: Interceptor rebind — class proxies

**Files:**
- Create: `src/main/java/io/github/lamspace/internal/Rebindable.java`
- Modify: `src/main/java/io/github/lamspace/generator/BytecodeUtils.java`
- Modify: `src/main/java/io/github/lamspace/generator/ClassGenerator.java`
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java`
- Test: `src/test/java/io/github/lamspace/RebindClassProxyTest.java` (new)

**Interfaces:**
- Produces: `public interface Rebindable { void rebind(Interceptor[] interceptors); }`; `static void AcceleratedProxy.rebind(Object proxy, Interceptor interceptor)` and `rebind(Object proxy, Interceptor[])`. Consumed by Task 4 (which makes interface proxies implement `Rebindable`).

- [ ] **Step 1: Create the `Rebindable` interface**

Create `src/main/java/io/github/lamspace/internal/Rebindable.java` (Apache header, package `io.github.lamspace.internal`, import `io.github.lamspace.Interceptor`):

```java
/**
 * Internal interface implemented by all generated proxy classes. Lets a live
 * proxy replace its bound interceptors without recreating the instance.
 *
 * <p>Not part of the public API — users call
 * {@link io.github.lamspace.AcceleratedProxy#rebind(Object, Interceptor)}
 * instead.
 */
public interface Rebindable {

    /**
     * Replaces the interceptors bound to this proxy. The array length must
     * equal the proxy's distinct interceptor count.
     *
     * @param interceptors the new interceptors, index-aligned with the
     *                     generated class's interceptor fields
     * @throws IllegalArgumentException if the array is null or its length
     *                                  differs from the expected count
     */
    void rebind(Interceptor[] interceptors);
}
```

- [ ] **Step 2: Add the `generateRebind` bytecode helper**

In `BytecodeUtils.java`, add `import org.objectweb.asm.Label;`. Add the method after `pushInt`:

```java
/**
 * Emits the body of a {@code public void rebind(Interceptor[])} method:
 * validates the array (non-null, length == {@code interceptorCount}), assigns
 * each {@code _interceptor$i} field, then a {@code VarHandle.fullFence()} so
 * the stores are ordered and flushed. Slot 0 is {@code this}, slot 1 the
 * array.
 *
 * @param mv               the method visitor (method already opened, in code)
 * @param generatedInternal internal name of the generated class
 * @param interceptorCount number of distinct interceptors (field count)
 * @param interceptorDesc  the {@code Interceptor} type descriptor
 */
static void generateRebind(MethodVisitor mv, String generatedInternal,
                           int interceptorCount, String interceptorDesc) {
    Label notNull = new Label();
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitJumpInsn(Opcodes.IFNONNULL, notNull);
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
    mv.visitInsn(Opcodes.DUP);
    mv.visitLdcInsn("interceptors must not be null");
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException",
            "<init>", "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.ATHROW);
    mv.visitLabel(notNull);

    Label lengthOk = new Label();
    mv.visitVarInsn(Opcodes.ALOAD, 1);
    mv.visitInsn(Opcodes.ARRAYLENGTH);
    pushInt(mv, interceptorCount);
    mv.visitJumpInsn(Opcodes.IF_ICMPEQ, lengthOk);
    mv.visitTypeInsn(Opcodes.NEW, "java/lang/IllegalArgumentException");
    mv.visitInsn(Opcodes.DUP);
    mv.visitLdcInsn("interceptor count mismatch: expected " + interceptorCount);
    mv.visitMethodInsn(Opcodes.INVOKESPECIAL, "java/lang/IllegalArgumentException",
            "<init>", "(Ljava/lang/String;)V", false);
    mv.visitInsn(Opcodes.ATHROW);
    mv.visitLabel(lengthOk);

    for (int i = 0; i < interceptorCount; i++) {
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        pushInt(mv, i);
        mv.visitInsn(Opcodes.AALOAD);
        mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal,
                "_interceptor$" + i, interceptorDesc);
    }
    mv.visitMethodInsn(Opcodes.INVOKESTATIC, "java/lang/invoke/VarHandle",
            "fullFence", "()V", false);
    mv.visitInsn(Opcodes.RETURN);
}
```

- [ ] **Step 3: Modify `ClassGenerator` to emit `rebind` and drop `final`**

Three edits in `ClassGenerator.java`:

(a) In `generate()`, change the interface array from:

```java
        String[] interfaces = {"io/github/lamspace/DispatchTarget"};
```

to:

```java
        String[] interfaces = {"io/github/lamspace/DispatchTarget",
                "io/github/lamspace/internal/Rebindable"};
```

(b) In `generate()`, change the interceptor field declaration from:

```java
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    "_interceptor$" + i, interceptorDesc, null, null);
```

to (drop `ACC_FINAL`):

```java
            cw.visitField(Opcodes.ACC_PRIVATE,
                    "_interceptor$" + i, interceptorDesc, null, null);
```

(c) In `generate()`, right after the `generateConstructor(...)` call and before the `// -- Method overrides` comment, insert:

```java
        // -- rebind(Interceptor[]): swap interceptors on a live instance --
        generateRebindMethod(cw, generatedInternal, interceptorDesc);
```

Then add the private helper (place it after `storeInterceptorFields`):

```java
private void generateRebindMethod(ClassWriter cw, String generatedInternal,
                                  String interceptorDesc) {
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "rebind",
            "([Lio/github/lamspace/Interceptor;)V", null, null);
    mv.visitCode();
    BytecodeUtils.generateRebind(mv, generatedInternal,
            interceptors.length, interceptorDesc);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
}
```

- [ ] **Step 4: Add `rebind` overloads to `AcceleratedProxy`**

In `AcceleratedProxy.java`, add `import io.github.lamspace.internal.Rebindable;` (next to the existing `import io.github.lamspace.internal.LookupManager;`). Add the two overloads immediately after the `invokeSuper` method:

```java
/**
 * Replaces the interceptor on an existing proxy instance with {@code
 * interceptor}, without recreating the instance. The proxy must be an
 * OpenProxy-generated single-interceptor proxy.
 *
 * @param proxy       the OpenProxy proxy instance
 * @param interceptor the new interceptor; must not be null
 * @throws IllegalArgumentException if {@code interceptor} is null or
 *                                  {@code proxy} is not an OpenProxy proxy
 */
public static void rebind(Object proxy, Interceptor interceptor) {
    if (interceptor == null) {
        throw new IllegalArgumentException("interceptor must not be null");
    }
    rebind(proxy, new Interceptor[]{interceptor});
}

/**
 * Replaces the interceptors on an existing proxy instance with {@code
 * interceptors}, without recreating the instance. The array length must equal
 * the proxy's distinct interceptor count (the number of deduped interceptors
 * it was created with).
 *
 * <p>This is a single-writer management operation. The stores are followed by
 * a full fence, but a caller that rebinds on one thread and invokes methods
 * on another must establish its own happens-before edge (a lock, thread
 * start, latch, or volatile flag).
 *
 * @param proxy        the OpenProxy proxy instance
 * @param interceptors the new interceptors, index-aligned with the generated
 *                     class's interceptor fields
 * @throws IllegalArgumentException if {@code proxy} is not an OpenProxy proxy or
 *                                  {@code interceptors} is null/ill-sized
 */
public static void rebind(Object proxy, Interceptor[] interceptors) {
    if (!(proxy instanceof Rebindable rebindable)) {
        throw new IllegalArgumentException("not an OpenProxy-generated proxy");
    }
    rebindable.rebind(interceptors);
}
```

- [ ] **Step 5: Write the failing class-proxy rebind tests**

Create `src/test/java/io/github/lamspace/RebindClassProxyTest.java` (Apache header, package `io.github.lamspace`, import `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`):

```java
class RebindClassProxyTest {

    public static class Greeter {
        public String hello(String name) { return "Hello, " + name; }
    }

    public static class Pair {
        public String a() { return "a"; }
        public String b() { return "b"; }
    }

    private static Interceptor constant(String value) {
        return (o, m, a) -> value;
    }

    @Test
    void rebindSwapsInterceptor() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("old"));
        assertEquals("old", p.hello("x"));
        AcceleratedProxy.rebind(p, constant("new"));
        assertEquals("new", p.hello("x"));
    }

    @Test
    void rebindMultiInterceptorPreservesIndices() {
        Pair p = AcceleratedProxy.proxy(Pair.class,
                Group.of(m -> m.getName().equals("a"), constant("A1")),
                Group.otherwise(constant("B1")));
        assertEquals("A1", p.a());
        assertEquals("B1", p.b());

        AcceleratedProxy.rebind(p,
                new Interceptor[]{constant("A2"), constant("B2")});
        assertEquals("A2", p.a());
        assertEquals("B2", p.b());
    }

    @Test
    void rebindLengthMismatchThrows() {
        Pair p = AcceleratedProxy.proxy(Pair.class,
                Group.of(m -> m.getName().equals("a"), constant("A1")),
                Group.otherwise(constant("B1")));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, new Interceptor[]{constant("x")}));
    }

    @Test
    void passthroughMethodUnaffectedByRebind() {
        Pair p = AcceleratedProxy.proxy(Pair.class,
                Group.of(m -> m.getName().equals("a"), constant("A1")));
        assertEquals("A1", p.a());
        assertEquals("b", p.b());   // matched by no Group -> passthrough

        AcceleratedProxy.rebind(p, constant("A2"));
        assertEquals("A2", p.a());
        assertEquals("b", p.b());   // still passthrough, no interceptor touched
    }

    @Test
    void rebindRejectsNullAndNonProxy() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind("not a proxy", constant("x")));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(null, constant("x")));
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("old"));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor) null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor[]) null));
    }

    @Test
    void invokeSuperStillWorksAfterRebind() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, (o, m, a) ->
                AcceleratedProxy.invokeSuper(o, m, a) + " (intercepted)");
        assertEquals("Hello, x (intercepted)", p.hello("x"));
        AcceleratedProxy.rebind(p, (o, m, a) ->
                "REBOUND:" + AcceleratedProxy.invokeSuper(o, m, a));
        assertEquals("REBOUND:Hello, x", p.hello("x"));
    }

    @Test
    void rebindIsPerInstance() {
        Greeter p1 = AcceleratedProxy.proxy(Greeter.class, constant("one"));
        Greeter p2 = AcceleratedProxy.proxy(Greeter.class, constant("two"));
        assertSame(p1.getClass(), p2.getClass());  // same cached class
        AcceleratedProxy.rebind(p1, constant("one-R"));
        assertEquals("one-R", p1.hello("x"));
        assertEquals("two", p2.hello("x"));
    }

    @Test
    void singleOverloadMatchesArray() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("old"));
        AcceleratedProxy.rebind(p, constant("new"));
        assertEquals("new", p.hello("x"));
        AcceleratedProxy.rebind(p, new Interceptor[]{constant("arr")});
        assertEquals("arr", p.hello("x"));
    }

    @Test
    void repeatedRebindReplacesCleanly() {
        Greeter p = AcceleratedProxy.proxy(Greeter.class, constant("0"));
        AcceleratedProxy.rebind(p, constant("1"));
        AcceleratedProxy.rebind(p, constant("2"));
        assertEquals("2", p.hello("x"));
    }
}
```

- [ ] **Step 6: Run tests to verify they fail first, then pass**

Run (expect FAIL — `rebind`/`Rebindable` missing): `mvn -s /home/lam/repo/settings.xml -q -Dtest=RebindClassProxyTest test`

After Steps 1–4 are implemented, re-run (expect PASS): `mvn -s /home/lam/repo/settings.xml -q -Dtest=RebindClassProxyTest test`

- [ ] **Step 7: Run full test suite (regression)**

Run: `mvn -s /home/lam/repo/settings.xml -q test`
Expected: PASS.

- [ ] **Step 8: Commit**

```bash
git add src/main/java/io/github/lamspace/internal/Rebindable.java \
        src/main/java/io/github/lamspace/generator/BytecodeUtils.java \
        src/main/java/io/github/lamspace/generator/ClassGenerator.java \
        src/main/java/io/github/lamspace/AcceleratedProxy.java \
        src/test/java/io/github/lamspace/RebindClassProxyTest.java
git commit -m "feat: add interceptor rebind for class proxies"
```

---

### Task 4: Interceptor rebind — interface proxies

**Files:**
- Modify: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java`
- Test: `src/test/java/io/github/lamspace/RebindInterfaceProxyTest.java` (new)

**Interfaces:**
- Consumes: `BytecodeUtils.generateRebind` (Task 3), `Rebindable` (Task 3), `AcceleratedProxy.rebind` (Task 3).

- [ ] **Step 1: Write the failing interface-proxy rebind test**

Create `src/test/java/io/github/lamspace/RebindInterfaceProxyTest.java` (Apache header, package `io.github.lamspace`, import `org.junit.jupiter.api.Test`, `static org.junit.jupiter.api.Assertions.*`):

```java
class RebindInterfaceProxyTest {

    public interface Echo {
        String echo(String s);
    }

    private static Interceptor constant(String value) {
        return (o, m, a) -> value;
    }

    @Test
    void rebindInterfaceProxySwapsInterceptor() {
        Echo p = AcceleratedProxy.proxy(Echo.class, constant("old"));
        assertEquals("old", p.echo("x"));
        AcceleratedProxy.rebind(p, constant("new"));
        assertEquals("new", p.echo("x"));
    }

    @Test
    void rebindIsPerInstance() {
        Echo p1 = AcceleratedProxy.proxy(Echo.class, constant("one"));
        Echo p2 = AcceleratedProxy.proxy(Echo.class, constant("two"));
        assertSame(p1.getClass(), p2.getClass());
        AcceleratedProxy.rebind(p1, constant("one-R"));
        assertEquals("one-R", p1.echo("x"));
        assertEquals("two", p2.echo("x"));
    }

    @Test
    void rebindRejectsNullAndNonProxy() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(new Object(), constant("x")));
        Echo p = AcceleratedProxy.proxy(Echo.class, constant("old"));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor) null));
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.rebind(p, (Interceptor[]) null));
    }
}
```

- [ ] **Step 2: Run the test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=RebindInterfaceProxyTest test`
Expected: FAIL — interface proxies do not yet implement `Rebindable` (`ClassCastException`/`IllegalArgumentException`).

- [ ] **Step 3: Modify `InterfaceGenerator`**

Three edits in `InterfaceGenerator.java`:

(a) In `generate()`, change the implemented-array block from:

```java
        String[] implemented = new String[interfaces.length + 1];
        for (int i = 0; i < interfaces.length; i++) {
            implemented[i] = Type.getInternalName(interfaces[i]);
        }
        implemented[interfaces.length] = "io/github/lamspace/DispatchTarget";
```

to:

```java
        String[] implemented = new String[interfaces.length + 2];
        for (int i = 0; i < interfaces.length; i++) {
            implemented[i] = Type.getInternalName(interfaces[i]);
        }
        implemented[interfaces.length] = "io/github/lamspace/DispatchTarget";
        implemented[interfaces.length + 1] = "io/github/lamspace/internal/Rebindable";
```

(b) In `generate()`, change the interceptor field declaration from:

```java
            cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                    "_interceptor$" + i, interceptorDesc, null, null);
```

to (drop `ACC_FINAL`):

```java
            cw.visitField(Opcodes.ACC_PRIVATE,
                    "_interceptor$" + i, interceptorDesc, null, null);
```

(c) In `generate()`, right after the `generateConstructor(...)` call and before the `// -- Method implementations` comment, insert:

```java
        // -- rebind(Interceptor[]): swap interceptors on a live instance --
        generateRebindMethod(cw, generatedInternal, interceptorDesc);
```

Then add the private helper (place it after `generateConstructor`):

```java
private void generateRebindMethod(ClassWriter cw, String generatedInternal,
                                  String interceptorDesc) {
    MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "rebind",
            "([Lio/github/lamspace/Interceptor;)V", null, null);
    mv.visitCode();
    BytecodeUtils.generateRebind(mv, generatedInternal,
            interceptors.length, interceptorDesc);
    mv.visitMaxs(0, 0);
    mv.visitEnd();
}
```

- [ ] **Step 4: Run tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml -q -Dtest=RebindInterfaceProxyTest,RebindClassProxyTest test`
Expected: PASS.

- [ ] **Step 5: Run full test suite (regression)**

Run: `mvn -s /home/lam/repo/settings.xml -q test`
Expected: PASS.

- [ ] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceGenerator.java \
        src/test/java/io/github/lamspace/RebindInterfaceProxyTest.java
git commit -m "feat: add interceptor rebind for interface proxies"
```

---

### Task 5: Rebind benchmark + JMH parity verification

**Files:**
- Create: `src/test/java/io/github/lamspace/benchmark/RebindBenchmark.java`

**Interfaces:**
- Consumes: `AcceleratedProxy.rebind` (Task 3).

- [ ] **Step 1: Create `RebindBenchmark`**

Create `src/test/java/io/github/lamspace/benchmark/RebindBenchmark.java` (Apache header, package `io.github.lamspace.benchmark`, imports `io.github.lamspace.AcceleratedProxy`, `io.github.lamspace.Interceptor`, `org.openjdk.jmh.annotations.*`, `java.util.concurrent.TimeUnit`):

```java
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class RebindBenchmark {

    public static class Target {
        public String hello(String name) { return "Hello, " + name; }
    }

    private static final Interceptor NOOP_A = (o, m, a) -> null;
    private static final Interceptor NOOP_B = (o, m, a) -> null;

    private Target proxy;

    @Setup
    public void setup() {
        proxy = AcceleratedProxy.proxy(Target.class, NOOP_A);
    }

    @Benchmark
    public void rebind() {
        AcceleratedProxy.rebind(proxy, NOOP_B);
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}
```

Note: this number is informational (rebind is a rare management op, not the hot path); it is not a gate.

- [ ] **Step 2: Compile**

Run: `mvn -s /home/lam/repo/settings.xml -q test-compile`
Expected: BUILD SUCCESS.

- [ ] **Step 3: Run the parity benchmarks**

Build the test classpath once, then run each existing benchmark:

```bash
mvn -s /home/lam/repo/settings.xml -q dependency:build-classpath \
    -DincludeScope=test -Dmdep.outputFile=target/test-cp.txt
java -cp "target/classes:target/test-classes:$(cat target/test-cp.txt)" \
    io.github.lamspace.benchmark.ProxyBenchmark
java -cp "target/classes:target/test-classes:$(cat target/test-cp.txt)" \
    io.github.lamspace.benchmark.ConstructorInterceptionBenchmark
java -cp "target/classes:target/test-classes:$(cat target/test-cp.txt)" \
    io.github.lamspace.benchmark.StaticMethodProxyBenchmark
```

Expected: results unchanged within noise vs. the numbers in `docs/benchmark-results.md`. If any number regresses materially (>~5% on the proxy/constructor rows), stop and investigate — the hot path should be byte-identical.

- [ ] **Step 4: Run the new benchmark (informational)**

```bash
java -cp "target/classes:target/test-classes:$(cat target/test-cp.txt)" \
    io.github.lamspace.benchmark.RebindBenchmark
```

Record the `rebind` ns/op result for Task 6.

- [ ] **Step 5: Commit**

```bash
git add src/test/java/io/github/lamspace/benchmark/RebindBenchmark.java
git commit -m "bench: add rebind benchmark"
```

---

### Task 6: Documentation

**Files:**
- Modify: `docs/openproxy-future-roadmap.md`
- Modify: `README.md`
- Modify: `README_CN.md`
- Modify: `docs/migration-guide.md`
- Modify: `docs/benchmark-results.md`, `docs/benchmark-results_cn.md`

**Interfaces:** none (docs only).

- [ ] **Step 1: Update the roadmap**

In `docs/openproxy-future-roadmap.md` (edit the current working-tree version; preserve its pre-existing uncommitted changes):

(a) Change the Phase 3 item 6 row from:

```
| 6    | P3     | **热加载/热替换**              | 运行时重新生成代理类，适合长期运行的框架场景                          |
```

to:

```
| 6    | P3     | **热加载/热替换**（已完成）    | 类热重载（`evict`/`evictClassLoader`）+ 拦截器热替换（`rebind`）         |
```

(b) Replace the `### 热加载挑战` section (the three-bullet list) with:

```markdown
### 类热重载（已完成）

- 框架用新 `ClassLoader` 重载目标类时，`proxy()` 对新 `Class` 对象透明生成新代理类；旧实例继续用旧类、新实例用新类（缓存键按 `Class` 身份，类名按 `COUNTER` 唯一）
- 显式生命周期控制：`AcceleratedProxy.evict(Class)` / `evictClassLoader(ClassLoader)` 确定性驱逐缓存项，下次 `proxy()` 重新生成
- 缓存键不对称：接口代理以「第一个接口」为键，`evict` 针对该键

### 拦截器热替换（已完成）

- `AcceleratedProxy.rebind(proxy, interceptor)` / `rebind(proxy, Interceptor[])` 原地替换已创建代理实例上的拦截器，不重建实例、不换类
- 拦截器字段由 `final` 改为 plain，`rebind` 末尾 `VarHandle.fullFence()`；单写者 + 调用方负责 happens-before
- `ConstructorInterceptor` 不可热替换（仅构造期使用，不落实例字段）
```

- [ ] **Step 2: Update `README.md` and `README_CN.md`**

In each README's Features list, add a bullet after the static-method-proxy bullet:

```markdown
- **Hot reload / hot swap**: `evict(Class)` / `evictClassLoader(ClassLoader)` to
  deterministically drop cached proxy classes for a hot-deployed target, and
  `rebind(proxy, interceptor)` to swap an interceptor on a live instance.
```

(`README_CN.md` uses the Chinese equivalent; translate the bullet to match that file's language.)

Add a Quick Start snippet after the existing quick-start examples:

```java
// Swap an interceptor on a live proxy without recreating it
Greeter proxy = AcceleratedProxy.proxy(Greeter.class, oldInterceptor);
AcceleratedProxy.rebind(proxy, newInterceptor);

// Drop cached proxy classes for a hot-deployed classloader
AcceleratedProxy.evictClassLoader(pluginClassLoader);
```

- [ ] **Step 3: Update `docs/migration-guide.md`**

Add a short note that `rebind`/`evict`/`evictClassLoader` are purely additive, and that CGLib has no direct post-construction callback swap (analogous behavior is a new proxy per reloaded class).

- [ ] **Step 4: Update benchmark results docs**

Add the `RebindBenchmark` `rebind` ns/op number to `docs/benchmark-results.md` and `docs/benchmark-results_cn.md`, labeled as informational (not a hot-path comparison).

- [ ] **Step 5: Commit**

```bash
git add docs/openproxy-future-roadmap.md README.md README_CN.md \
        docs/migration-guide.md docs/benchmark-results.md docs/benchmark-results_cn.md
git commit -m "docs: document hot reload / hot swap"
```

---

## Verification checklist (before merging)

- [ ] `mvn -s /home/lam/repo/settings.xml -q test` — all green.
- [ ] JMH `ProxyBenchmark`, `ConstructorInterceptionBenchmark`, `StaticMethodProxyBenchmark` unchanged within noise (hot path byte-identical).
- [ ] `evict`/`evictClassLoader`/`rebind` all exercised by passing tests.
- [ ] `docs/openproxy-future-roadmap.md` item 6 marked 已完成.

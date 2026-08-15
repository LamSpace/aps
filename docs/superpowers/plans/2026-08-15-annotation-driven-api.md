# Annotation-Driven API Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [x]`) syntax for tracking.

**Goal:** Add a declarative, annotation-driven API (`@Intercept` + `@Around` + `AcceleratedProxy.intercept`) that compiles down to the existing `Group`/`Interceptor` pipeline.

**Architecture:** A thin sugar layer on top of `AcceleratedProxy.proxy(Class, Group...)`. `intercept()` reflects over an `@Intercept`-annotated object, builds a `Group[]` from its `@Around` methods (predicate from glob/regex/annotation dimensions; interceptor from a `LambdaMetafactory` call site), and delegates to `proxy()`. No change to generators, cache, or hot path.

**Tech Stack:** Java 25, ASM 9.7.1, JUnit 5 (Jupiter), JMH 1.37.

**Spec:** `docs/superpowers/specs/2026-08-15-annotation-driven-api-design.md`

## Global Constraints

- All new public types go in package `io.github.lamspace`.
- Every new `.java` file begins with the standard Apache-2.0 license header — copy the 14-line header verbatim from any existing source file (e.g. `src/main/java/io/github/lamspace/Interceptor.java`).
- All `mvn` commands use `-s /home/lam/repo/settings.xml`.
- Tests are JUnit 5, package-private classes, static `org.junit.jupiter.api.Assertions.*` imports (match `MultiInterceptorClassProxyTest`).
- Do **not** modify `ClassGenerator`, `InterfaceGenerator`, any `Dispatcher`, `MethodMapping`, `WeakCache`, `Interceptor`, `Group`, `MethodPredicate`, `DispatchTarget`, or `invokeSuper`.
- `@Around` method contract: instance method, params exactly `(Object, Method, Object[])`, reference return type (non-`void`, non-primitive).

---

### Task 1: `@Intercept` and `@Around` annotations

**Files:**
- Create: `src/main/java/io/github/lamspace/Intercept.java`
- Create: `src/main/java/io/github/lamspace/Around.java`
- Test: `src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java`

**Interfaces:**
- Consumes: nothing.
- Produces: `@io.github.lamspace.Intercept` (type-level, RUNTIME), `@io.github.lamspace.Around` (method-level, RUNTIME) with elements `String value() default ""`, `String[] glob() default {}`, `String[] regex() default {}`, `Class<? extends Annotation>[] annotatedWith() default {}`.

- [x] **Step 1: Write the failing test**

Create `src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java` (with the license header):

```java
package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import static org.junit.jupiter.api.Assertions.*;

class AnnotationDrivenApiTest {

    @Test
    void aroundAnnotationHasRuntimeRetentionAndDefaults() throws Exception {
        assertTrue(Around.class.isAnnotationPresent(Retention.class));
        assertEquals(RetentionPolicy.RUNTIME,
                Around.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.METHOD,
                Around.class.getAnnotation(Target.class).value()[0]);

        assertEquals("", Around.class.getMethod("value").getDefaultValue());
        assertArrayEquals(new String[0],
                (String[]) Around.class.getMethod("glob").getDefaultValue());
        assertArrayEquals(new String[0],
                (String[]) Around.class.getMethod("regex").getDefaultValue());
        assertArrayEquals(new Class[0],
                (Class[]) Around.class.getMethod("annotatedWith").getDefaultValue());
    }

    @Test
    void interceptAnnotationHasRuntimeRetentionAndTypeTarget() {
        assertEquals(RetentionPolicy.RUNTIME,
                Intercept.class.getAnnotation(Retention.class).value());
        assertEquals(ElementType.TYPE,
                Intercept.class.getAnnotation(Target.class).value()[0]);
    }
}
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: compilation fails — `Around` and `Intercept` do not exist.

- [x] **Step 3: Write the annotations**

Create `src/main/java/io/github/lamspace/Intercept.java` (license header first):

```java
package io.github.lamspace;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a class as an annotation-driven interceptor container.
 *
 * <p>Methods of a {@code @Intercept} class annotated with {@link Around} are
 * bound as interceptors by {@link AcceleratedProxy#intercept(Class, Object)}.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.TYPE)
public @interface Intercept {
}
```

Create `src/main/java/io/github/lamspace/Around.java` (license header first):

```java
package io.github.lamspace;

import java.lang.annotation.Annotation;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declarative method-matching for an {@link Intercept} interceptor method.
 *
 * <p>Three match dimensions are AND-combined; within each dimension, multiple
 * values are OR-combined. A method matches when every non-empty dimension
 * matches. An empty dimension imposes no constraint.
 *
 * <ul>
 *   <li>{@code value}/{@code glob} — method-name glob ({@code *} matches any
 *       sequence, {@code ?} matches one character);</li>
 *   <li>{@code regex} — method-name regular expression (whole-name match);</li>
 *   <li>{@code annotatedWith} — annotation types the target method must carry
 *       (direct presence only).</li>
 * </ul>
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Around {

    /** Single name-glob shorthand: {@code @Around("get*")}. */
    String value() default "";

    /** Method-name glob patterns. Empty = no glob constraint. */
    String[] glob() default {};

    /** Method-name regex patterns. Empty = no regex constraint. */
    String[] regex() default {};

    /** Annotation types the target method must carry. Empty = no constraint. */
    Class<? extends Annotation>[] annotatedWith() default {};
}
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: PASS (2 tests).

- [x] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/Intercept.java \
        src/main/java/io/github/lamspace/Around.java \
        src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java
git commit -m "feat: add @Intercept and @Around annotations"
```

---

### Task 2: `intercept()` end-to-end with name-glob matching

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java`
- Test: `src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java` (add tests)

**Interfaces:**
- Consumes: `@Intercept`, `@Around` (Task 1); existing `Group`, `MethodPredicate`, `Interceptor`, `proxy(Class<T>, Group...)`, `LookupManager.getLookup`.
- Produces: `public static <T> T intercept(Class<T> target, Object interceptor)`; private helpers `resolveAnnotationGroups`, `validateAroundMethod`, `toPredicate`, `buildGlobs`, `matchesAnyGlob`, `globMatches`, `toInterceptor`.

- [x] **Step 1: Write the failing test**

Append to `AnnotationDrivenApiTest.java` (add `import java.lang.reflect.Method;` and `import java.util.concurrent.atomic.AtomicReference;` to the import block):

```java
    public static class Greeter {
        public String getGreeting() { return "hello"; }
        public void setGreeting(String g) { }
        public String format(String prefix) { return prefix + ":ok"; }
    }

    @Intercept
    public static class GetterInterceptor {
        final AtomicReference<String> lastMethod = new AtomicReference<>();

        @Around("get*")
        public Object measure(Object proxy, Method method, Object[] args)
                throws Throwable {
            lastMethod.set(method.getName());
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void singleGlobRoutesMatchedMethodsAndPassthroughsOthers() {
        GetterInterceptor interceptor = new GetterInterceptor();
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class, interceptor);

        assertEquals("hello", proxy.getGreeting());
        assertEquals("getGreeting", interceptor.lastMethod.get());

        proxy.setGreeting("x");
        assertEquals("p:ok", proxy.format("p"));
        assertEquals("getGreeting", interceptor.lastMethod.get());
    }
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: compilation fails — `AcceleratedProxy.intercept` does not exist.

- [x] **Step 3: Implement `intercept()` and helpers**

In `AcceleratedProxy.java`, add these imports to the import block (keep existing imports intact; `LookupManager` is already imported):

```java
import java.lang.annotation.Annotation;
import java.lang.invoke.LambdaMetafactory;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
```

Add the following methods immediately before the final closing brace of the class (after `proxyInterfaces`):

```java
    /**
     * Creates a proxy whose methods are matched to interceptors declaratively,
     * from the {@code @Around}-annotated methods of the given {@code @Intercept}
     * object.
     *
     * <p>Each {@code @Around} method must have signature
     * {@code (Object, Method, Object[]) -> reference}. Methods not matching any
     * {@code @Around} method passthrough (direct super call), consistent with the
     * programmatic {@code Group} API.
     *
     * @param target      the class or interface to proxy
     * @param interceptor an instance of a {@code @Intercept}-annotated class
     * @param <T>         the proxy type
     * @return a proxy instance of type {@code T}
     * @throws IllegalArgumentException if {@code interceptor} is invalid
     */
    public static <T> T intercept(Class<T> target, Object interceptor) {
        if (target == null) {
            throw new IllegalArgumentException("target must not be null");
        }
        return proxy(target, resolveAnnotationGroups(interceptor));
    }

    /**
     * Reflects over the {@code @Intercept} object and builds a {@code Group[]}
     * from its {@code @Around} methods, in deterministic (name-sorted) order.
     */
    private static Group[] resolveAnnotationGroups(Object interceptor) {
        if (interceptor == null) {
            throw new IllegalArgumentException("interceptor must not be null");
        }
        Class<?> interceptorClass = interceptor.getClass();
        if (!interceptorClass.isAnnotationPresent(Intercept.class)) {
            throw new IllegalArgumentException(
                    interceptorClass.getName() + " must be annotated with @Intercept");
        }
        Method[] methods = interceptorClass.getDeclaredMethods();
        Arrays.sort(methods,
                Comparator.comparing(Method::getName)
                        .thenComparing(m -> Arrays.toString(m.getParameterTypes())));
        List<Group> groups = new ArrayList<>();
        for (Method m : methods) {
            Around around = m.getAnnotation(Around.class);
            if (around == null) {
                continue;
            }
            validateAroundMethod(m);
            groups.add(Group.of(toPredicate(around), toInterceptor(interceptor, m)));
        }
        if (groups.isEmpty()) {
            throw new IllegalArgumentException(interceptorClass.getName()
                    + " must declare at least one @Around method");
        }
        return groups.toArray(new Group[0]);
    }

    /**
     * Validates the fixed {@code @Around} method contract.
     */
    private static void validateAroundMethod(Method m) {
        if (Modifier.isStatic(m.getModifiers())) {
            throw new IllegalArgumentException(
                    "@Around method must not be static: " + m.getName());
        }
        Class<?>[] params = m.getParameterTypes();
        if (params.length != 3
                || params[0] != Object.class
                || params[1] != Method.class
                || params[2] != Object[].class) {
            throw new IllegalArgumentException("@Around method must have signature "
                    + "(Object, Method, Object[]): " + m.getName());
        }
        Class<?> ret = m.getReturnType();
        if (ret == void.class || ret.isPrimitive()) {
            throw new IllegalArgumentException("@Around method must return a "
                    + "reference type (not void or primitive): " + m.getName());
        }
    }

    /**
     * Builds a {@link MethodPredicate} from the glob dimension of {@code around}.
     */
    private static MethodPredicate toPredicate(Around around) {
        String[] globs = buildGlobs(around);
        return m -> globs.length == 0 || matchesAnyGlob(globs, m.getName());
    }

    private static String[] buildGlobs(Around around) {
        List<String> globs = new ArrayList<>();
        if (!around.value().isEmpty()) {
            globs.add(around.value());
        }
        globs.addAll(Arrays.asList(around.glob()));
        return globs.toArray(new String[0]);
    }

    private static boolean matchesAnyGlob(String[] globs, String name) {
        for (String glob : globs) {
            if (globMatches(glob, name)) {
                return true;
            }
        }
        return false;
    }

    private static boolean globMatches(String glob, String name) {
        StringBuilder regex = new StringBuilder();
        for (int i = 0; i < glob.length(); i++) {
            char c = glob.charAt(i);
            if (c == '*') {
                regex.append(".*");
            } else if (c == '?') {
                regex.append('.');
            } else {
                regex.append(Pattern.quote(String.valueOf(c)));
            }
        }
        return name.matches(regex.toString());
    }

    /**
     * Binds the {@code @Around} instance method to an {@link Interceptor} via a
     * {@code LambdaMetafactory} call site (no per-call reflection). The instance
     * is captured through the factory type; {@code implMethod} stays a direct
     * method handle so the metafactory can crack it.
     */
    private static Interceptor toInterceptor(Object instance, Method m) {
        try {
            MethodHandles.Lookup lookup = LookupManager.getLookup(instance.getClass());
            MethodHandle implMethod = lookup.unreflect(m);
            MethodType samType = MethodType.methodType(Object.class,
                    Object.class, Method.class, Object[].class);
            MethodType factoryType = MethodType.methodType(Interceptor.class,
                    instance.getClass());
            return (Interceptor) LambdaMetafactory.metafactory(
                    lookup, "intercept",
                    factoryType,
                    samType,
                    implMethod,
                    samType)
                    .getTarget().invokeWithArguments(instance);
        } catch (Throwable t) {
            throw new IllegalArgumentException(
                    "Failed to bind @Around method: " + m.getName(), t);
        }
    }
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: PASS (3 tests).

- [x] **Step 5: Run the full suite to check for regressions**

Run: `mvn -s /home/lam/repo/settings.xml test`
Expected: BUILD SUCCESS; all existing tests still green.

- [x] **Step 6: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java \
        src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java
git commit -m "feat: add annotation-driven intercept() with glob matching"
```

---

### Task 3: regex and `annotatedWith` match dimensions

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (extend `toPredicate`, add helpers)
- Test: `src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java` (add tests)

**Interfaces:**
- Consumes: `toPredicate`, `buildGlobs` (Task 2).
- Produces: `matchesAnyRegex`, `hasAnyAnnotation`, invalid-regex validation.

- [x] **Step 1: Write the failing test**

Append to `AnnotationDrivenApiTest.java` (add `import java.lang.annotation.*;` and `import java.util.concurrent.atomic.AtomicInteger;`):

```java
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.METHOD)
    public @interface Tx {}

    public static class Service {
        @Tx public String save(String x) { return "saved:" + x; }
        @Tx public int load() { return 1; }
        public String ping() { return "pong"; }
    }

    @Intercept
    public static class TxInterceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Around(annotatedWith = Tx.class)
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            calls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void annotatedWithMatchesOnlyAnnotatedMethods() {
        TxInterceptor interceptor = new TxInterceptor();
        Service proxy = AcceleratedProxy.intercept(Service.class, interceptor);

        assertEquals("saved:a", proxy.save("a"));
        assertEquals(1, proxy.load());
        assertEquals("pong", proxy.ping());
        assertEquals(2, interceptor.calls.get());
    }

    @Intercept
    public static class RegexInterceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Around(regex = "get[A-Z].*")
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            calls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void regexMatchesMethodName() {
        RegexInterceptor interceptor = new RegexInterceptor();
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class, interceptor);

        assertEquals("hello", proxy.getGreeting());
        assertEquals(1, interceptor.calls.get());
    }

    @Intercept
    public static class AndInterceptor {
        final AtomicInteger calls = new AtomicInteger();

        @Around(value = "get*", annotatedWith = Tx.class)
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            calls.incrementAndGet();
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    public static class MixedService {
        @Tx public String getTagged() { return "tagged"; }
        public String getPlain() { return "plain"; }
    }

    @Test
    void globAndAnnotatedWithCombineWithAnd() {
        AndInterceptor interceptor = new AndInterceptor();
        MixedService proxy = AcceleratedProxy.intercept(MixedService.class, interceptor);

        assertEquals("tagged", proxy.getTagged());
        assertEquals("plain", proxy.getPlain());
        assertEquals(1, interceptor.calls.get());
    }

    @Intercept
    public static class MultiGlobInterceptor {
        final AtomicReference<String> last = new AtomicReference<>();

        @Around(glob = {"get*", "is*"})
        public Object handle(Object proxy, Method method, Object[] args)
                throws Throwable {
            last.set(method.getName());
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    public static class HasGetterAndIsser {
        public String getName() { return "n"; }
        public boolean isReady() { return true; }
        public String ping() { return "p"; }
    }

    @Test
    void multipleGlobsOrWithinDimension() {
        MultiGlobInterceptor interceptor = new MultiGlobInterceptor();
        HasGetterAndIsser proxy = AcceleratedProxy.intercept(HasGetterAndIsser.class, interceptor);

        assertEquals("n", proxy.getName());
        assertTrue(proxy.isReady());
        assertEquals("p", proxy.ping());
        assertEquals("isReady", interceptor.last.get());
    }

    @Intercept
    public static class BadRegexInterceptor {
        @Around(regex = "[")
        public Object handle(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    @Test
    void invalidRegexFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class,
                        new BadRegexInterceptor()));
    }
```

- [x] **Step 2: Run test to verify it fails**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: `annotatedWith`/`regex` tests FAIL — the predicate ignores those dimensions (and the invalid-regex test does not throw).

- [x] **Step 3: Extend `toPredicate` and add helpers**

Replace the `toPredicate` method added in Task 2 with:

```java
    /**
     * Builds a {@link MethodPredicate} from all three dimensions of
     * {@code around}, AND-combined across dimensions and OR within each.
     */
    private static MethodPredicate toPredicate(Around around) {
        String[] globs = buildGlobs(around);
        String[] regexes = around.regex();
        Class<? extends Annotation>[] annotations = around.annotatedWith();
        for (String regex : regexes) {
            if (regex.isEmpty()) {
                throw new IllegalArgumentException("@Around regex must not be empty");
            }
            try {
                Pattern.compile(regex);
            } catch (PatternSyntaxException e) {
                throw new IllegalArgumentException(
                        "Invalid @Around regex: " + regex, e);
            }
        }
        return m -> {
            if (globs.length > 0 && !matchesAnyGlob(globs, m.getName())) {
                return false;
            }
            if (regexes.length > 0 && !matchesAnyRegex(regexes, m.getName())) {
                return false;
            }
            if (annotations.length > 0 && !hasAnyAnnotation(m, annotations)) {
                return false;
            }
            return true;
        };
    }
```

Add these two helpers next to `matchesAnyGlob`/`globMatches`:

```java
    private static boolean matchesAnyRegex(String[] regexes, String name) {
        for (String regex : regexes) {
            if (name.matches(regex)) {
                return true;
            }
        }
        return false;
    }

    private static boolean hasAnyAnnotation(Method m,
            Class<? extends Annotation>[] annotations) {
        for (Class<? extends Annotation> a : annotations) {
            if (m.isAnnotationPresent(a)) {
                return true;
            }
        }
        return false;
    }
```

- [x] **Step 4: Run test to verify it passes**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: PASS (9 tests).

- [x] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/AcceleratedProxy.java \
        src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java
git commit -m "feat: add regex and annotatedWith dimensions to @Around matching"
```

---

### Task 4: adapter fidelity and fail-fast validation

**Files:**
- Modify: `src/main/java/io/github/lamspace/AcceleratedProxy.java` (only if a validation is missing)
- Test: `src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java` (add tests)

**Interfaces:**
- Consumes: `intercept`, `validateAroundMethod`, `toInterceptor`, `resolveAnnotationGroups` (Tasks 2–3).
- Produces: nothing new (tests only; fix code only if a test fails).

- [x] **Step 1: Write the failing test**

Append to `AnnotationDrivenApiTest.java` (add `import java.util.concurrent.atomic.AtomicBoolean;`):

```java
    @Intercept
    public static class ArgsCapturingInterceptor {
        final Object[] captured = new Object[3];
        final AtomicBoolean sawDispatchTarget = new AtomicBoolean();

        @Around("get*")
        public Object capture(Object proxy, Method method, Object[] args)
                throws Throwable {
            captured[0] = proxy;
            captured[1] = method;
            captured[2] = args;
            sawDispatchTarget.set(proxy instanceof DispatchTarget);
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void adapterPassesProxyMethodArgs() {
        ArgsCapturingInterceptor interceptor = new ArgsCapturingInterceptor();
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class, interceptor);

        assertEquals("hello", proxy.getGreeting());

        assertSame(proxy, interceptor.captured[0]);
        assertEquals("getGreeting", ((Method) interceptor.captured[1]).getName());
        assertEquals(0, ((Object[]) interceptor.captured[2]).length);
        assertTrue(interceptor.sawDispatchTarget.get());
    }

    @Intercept
    public static class StringReturnInterceptor {
        @Around("get*")
        public String shorten(Object proxy, Method method, Object[] args)
                throws Throwable {
            return "[" + AcceleratedProxy.invokeSuper(proxy, method, args) + "]";
        }
    }

    @Test
    void subtypeReturnIsWidenedToObject() {
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class,
                new StringReturnInterceptor());
        assertEquals("[hello]", proxy.getGreeting());
    }

    @Test
    void nullTargetFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(null, new GetterInterceptor()));
    }

    @Test
    void nullInterceptorFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, null));
    }

    public static class NotAnnotated {
        @Around("get*")
        public Object handle(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    @Test
    void nonInterceptClassFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new NotAnnotated()));
    }

    @Intercept
    public static class NoAroundMethod { }

    @Test
    void noAroundMethodFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new NoAroundMethod()));
    }

    @Intercept
    public static class WrongParamCount {
        @Around("get*")
        public Object handle(Object proxy, Method method) {
            return null;
        }
    }

    @Test
    void wrongParameterCountFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new WrongParamCount()));
    }

    @Intercept
    public static class VoidReturn {
        @Around("get*")
        public void handle(Object proxy, Method method, Object[] args) { }
    }

    @Test
    void voidReturnFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new VoidReturn()));
    }

    @Intercept
    public static class StaticAround {
        @Around("get*")
        public static Object handle(Object proxy, Method method, Object[] args) {
            return null;
        }
    }

    @Test
    void staticAroundFailsFast() {
        assertThrows(IllegalArgumentException.class,
                () -> AcceleratedProxy.intercept(Greeter.class, new StaticAround()));
    }
```

- [x] **Step 2: Run test to verify it fails (or passes — see note)**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: all these should PASS already (validation and adapter were implemented in Tasks 2–3). If any fails, that is a real gap — fix the corresponding private helper in `AcceleratedProxy.java` before proceeding.

- [x] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java
git commit -m "test: cover adapter fidelity and @Around validation"
```

---

### Task 5: equivalence with programmatic `Group` and determinism

**Files:**
- Test: `src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java` (add tests)

**Interfaces:**
- Consumes: `intercept`, `proxy`, `Group`, `invokeSuper` (public API).
- Produces: nothing new.

- [x] **Step 1: Write the failing test**

Append to `AnnotationDrivenApiTest.java`:

```java
    @Test
    void annotationDrivenSharesClassWithEquivalentProgrammatic() {
        GetterInterceptor interceptor = new GetterInterceptor();

        Greeter annotationDriven = AcceleratedProxy.intercept(Greeter.class, interceptor);
        Greeter programmatic = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"),
                        (obj, method, args) ->
                                AcceleratedProxy.invokeSuper(obj, method, args)));

        assertSame(annotationDriven.getClass(), programmatic.getClass());
        assertEquals("hello", annotationDriven.getGreeting());
        assertEquals("hello", programmatic.getGreeting());
    }

    @Intercept
    public static class OverlappingInterceptor {
        final AtomicReference<String> winner = new AtomicReference<>();

        @Around("getGreeting")   // declared first, but sorts after "getters"
        public Object specific(Object proxy, Method method, Object[] args)
                throws Throwable {
            winner.set("specific");
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }

        @Around("get*")          // declared second, but sorts first
        public Object getters(Object proxy, Method method, Object[] args)
                throws Throwable {
            winner.set("getters");
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @Test
    void overlappingPatternsUseDeterministicNameSortedFirstMatch() {
        OverlappingInterceptor interceptor = new OverlappingInterceptor();
        Greeter proxy = AcceleratedProxy.intercept(Greeter.class, interceptor);

        assertEquals("hello", proxy.getGreeting());
        assertEquals("getters", interceptor.winner.get());
    }
```

- [x] **Step 2: Run test to verify it fails (or passes — see note)**

Run: `mvn -s /home/lam/repo/settings.xml test -Dtest=AnnotationDrivenApiTest`
Expected: both should PASS already (equivalence follows from the shared cache key; determinism from the name-sort in `resolveAnnotationGroups`). If either fails, that indicates a real defect in `resolveAnnotationGroups`/`toPredicate` — investigate before proceeding.

- [x] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/AnnotationDrivenApiTest.java
git commit -m "test: verify annotation-driven equivalence and determinism"
```

---

### Task 6: benchmark — annotation-driven vs programmatic

**Files:**
- Modify: `src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java`

**Interfaces:**
- Consumes: `intercept`, `Group`, `AcceleratedProxy.invokeSuper`, `MultiGroupTarget` (already in `ProxyBenchmark`).
- Produces: benchmark methods `ann_getGreeting`, `ann_prog_getGreeting`.

- [x] **Step 1: Add the benchmark state and methods**

In `ProxyBenchmark.java`, add `import java.lang.reflect.Method;`, `import io.github.lamspace.Around;`, and `import io.github.lamspace.Intercept;` to the import block. Then append this block just before the final closing brace of the class (after the multi-interface state):

```java
    // ================================================================
    // Target: Annotation-driven vs programmatic Group (Phase 3)
    // ================================================================

    @Intercept
    static class MetricsInterceptor {
        @Around("get*")
        public Object measure(Object proxy, Method method, Object[] args)
                throws Throwable {
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        }
    }

    @State(Scope.Thread)
    public static class AnnotationDrivenState {
        MultiGroupTarget annotationDriven;
        MultiGroupTarget programmatic;

        @Setup
        public void setup() {
            annotationDriven = AcceleratedProxy.intercept(
                    MultiGroupTarget.class, new MetricsInterceptor());
            programmatic = AcceleratedProxy.proxy(MultiGroupTarget.class,
                    Group.of(m -> m.getName().startsWith("get"),
                            (obj, method, args) ->
                                    AcceleratedProxy.invokeSuper(obj, method, args)));
        }
    }

    @Benchmark
    public String ann_getGreeting(AnnotationDrivenState s) {
        return s.annotationDriven.getGreeting();
    }

    @Benchmark
    public String ann_prog_getGreeting(AnnotationDrivenState s) {
        return s.programmatic.getGreeting();
    }
```

- [x] **Step 2: Compile**

Run: `mvn -s /home/lam/repo/settings.xml -q test-compile`
Expected: BUILD SUCCESS (JMH annotation processor generates benchmark metadata).

- [x] **Step 3: Run the benchmark (smoke)**

```bash
mvn -s /home/lam/repo/settings.xml -q dependency:build-classpath -Dmdep.outputFile=target/cp.txt
java --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED \
  -cp target/classes:target/test-classes:$(cat target/cp.txt) \
  io.github.lamspace.benchmark.ProxyBenchmark -f 1 -wi 1 -i 2 'ann_.*'
```

Expected: two benchmark lines, `ann_getGreeting` and `ann_prog_getGreeting`, both ≈3 ns/op and within noise of each other (the annotation-driven adapter is a `LambdaMetafactory` call site equivalent to the hand-written lambda).

- [x] **Step 4: Commit**

```bash
git add src/test/java/io/github/lamspace/benchmark/ProxyBenchmark.java
git commit -m "bench: add annotation-driven vs programmatic benchmark"
```

---

### Task 7: documentation

**Files:**
- Modify: `docs/aps-future-roadmap.md`
- Modify: `README.md`
- Modify: `README_CN.md`

**Interfaces:**
- Consumes: the final public API (`@Intercept`, `@Around`, `intercept`).
- Produces: user-facing docs.

- [x] **Step 1: Update the roadmap**

In `docs/aps-future-roadmap.md`, change the Phase 3 table row 3 from `**注解驱动 API**` to `**注解驱动 API**（已完成）`, and replace the existing `### 注解驱动 API` section (the one with the `@Around("get*")` example) with:

```markdown
### 注解驱动 API（已完成）

- `@Intercept`（类级）+ `@Around`（方法级）注解声明式匹配方法，替代编程式 `Group.of(m -> ...)`
- 三个匹配维度 AND 组合、维度内 OR：`value`/`glob`（方法名 glob）、`regex`（方法名正则）、`annotatedWith`（按方法注解）
- `@Around` 方法契约：实例方法，签名固定 `(Object, Method, Object[])`，返回引用类型
- 入口 `AcceleratedProxy.intercept(target, interceptor)`，未匹配方法透传，与程序化行为一致
- 注解驱动与等价手写 `Group` 生成相同代理类（同一缓存项），稳态开销 ≈ 手写 lambda（`LambdaMetafactory` 调用点）

```java
@Intercept
class MetricsInterceptor {
    @Around(value = "get*", annotatedWith = Tx.class)
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
```
```

- [x] **Step 2: Update README.md**

In `README.md`:

1. Add a feature bullet to the `## ✨ Features` list, immediately after the "Multi-interface proxy" bullet (line 19):

```markdown
- **Annotation-driven API** — declarative `@Intercept`/`@Around` method matching that compiles down to the same `Group` pipeline
```

2. Add a Quick Start subsection after the "Multi-Interface Proxy" code block (after line 90), before `## 📊 Performance`:

```markdown
### Annotation-Driven API

```java
@Intercept
class MetricsInterceptor {
    @Around("get*")
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
String s = proxy.getGreeting(); // routed through measure()
```
```

3. Add a Documentation link after the "Multi-Interceptor Design Spec" bullet (line 294):

```markdown
- [Annotation-Driven API Design Spec](docs/superpowers/specs/2026-08-15-annotation-driven-api-design.md)
```

- [x] **Step 3: Update README_CN.md**

In `README_CN.md`, mirror Step 2 with Chinese copy:

1. Feature bullet (after the "多接口代理" bullet, line 19):

```markdown
- **注解驱动 API** — 声明式 `@Intercept`/`@Around` 方法匹配，编译期落到同一条 `Group` 管线
```

2. Quick Start subsection (after the "多接口代理" code block, before `## 📊 性能`):

```markdown
### 注解驱动 API

```java
@Intercept
class MetricsInterceptor {
    @Around("get*")
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
String s = proxy.getGreeting(); // 经由 measure() 路由
```
```

3. Documentation link (after the "多拦截器设计文档" bullet, line 294):

```markdown
- [注解驱动 API 设计文档](docs/superpowers/specs/2026-08-15-annotation-driven-api-design.md)
```

- [x] **Step 4: Commit**

```bash
git add docs/aps-future-roadmap.md README.md README_CN.md
git commit -m "docs: document annotation-driven API in roadmap and READMEs"
```

---

## Self-Review Notes

- **Spec coverage:** spec §2.1 (annotations + entry point) → Tasks 1–2; §2.2 (resolution pipeline) → Task 2; §2.3 (contract) → Tasks 2 & 4; §2.4 (predicate) → Tasks 2–3; §2.5 (adapter) → Task 2; §3 (error handling) → Tasks 3–4; §4 (testing table) → Tasks 1–5; §5 (benchmark) → Task 6; §6 (docs) → Task 7.
- **Type consistency:** `intercept(Class<T>, Object)`, `resolveAnnotationGroups`, `validateAroundMethod`, `toPredicate`, `buildGlobs`, `matchesAnyGlob`, `globMatches`, `toInterceptor`, `matchesAnyRegex`, `hasAnyAnnotation` — names and signatures are identical across all tasks.

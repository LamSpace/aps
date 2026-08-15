# APS Phase 3: Static Method Proxy — Design Spec

**Date:** 2026-08-15 **Status:** Awaiting review **Phase:** 3 — Advanced Features

## 1. Motivation

The proxy API intercepts every non-final *instance* method, but static
methods are invisible to it: `MethodDispatcher` explicitly skips
`static`/`final`/`private` members, and the generated subclass's dispatch
machinery is instance-bound (`this`-based fields and `dispatch`). A small but
real set of use cases — reflective test harnesses and plugin/DI frameworks
that look up a class and invoke its static methods, or log-injection wrappers
obtained via `MethodHandle` — need a way to route static calls through an
`Interceptor`.

This feature generates a proxy class whose *static* methods shadow the
target's static methods and route them through the same `Interceptor`
contract, with `proxy = null`.

**Hard constraint that shapes everything:** `INVOKESTATIC` resolves to the
declaring class at compile time. Writing `Target.staticMethod()` always hits
the original; a generated same-signature static method is only *hiding*, never
an override, and is reachable only by reflecting on (or binding a
`MethodHandle` to) the generated class. This feature therefore **does not**
make `Target.staticMethod()` transparently interceptable — it returns a
decorated `Class` the caller invokes reflectively or via `MethodHandle`. The
roadmap already flags the resulting narrow use case ("测试 mock、日志注入").

**Success criteria**

1. `AcceleratedProxy.proxyStatic(Class<?> target, Group... groups)` returns a
   generated proxy `Class<?>`; a single-interceptor convenience overload
   delegates with `Group.otherwise(interceptor)`.
2. Every `public static`, non-`final`, non-`private` method declared by
   `target` **or inherited from its superclasses** is shadowed; a matching
   method routes through its `Interceptor` with `proxy == null`, a
   non-matching method passes through to the original via a direct
   `INVOKESTATIC`.
3. The `Interceptor` receives the correct `Method` (the target's declaring
   method) and boxed arguments; calling the original static method from the
   interceptor is `method.invoke(null, args)`.
4. The existing `proxy()` path — instance and interface — is **byte-for-byte
   unchanged** and the existing `WeakCache` is untouched: this feature is
   purely additive and cannot regress current proxies.
5. Unit/integration tests, a JMH benchmark, an updated roadmap, and updated
   README docs.

## 2. Design

### 2.1 Public API

New entry points on `AcceleratedProxy`:

```java
public static Class<?> proxyStatic(Class<?> target, Group... groups)

public static Class<?> proxyStatic(Class<?> target, Interceptor interceptor)
```

- `target` must be non-null and **not** an interface (interface static
  methods are out of scope, §8); `groups` must be non-null and non-empty, and
  the `Interceptor` must be non-null — the same `IllegalArgumentException`
  discipline as `proxy()`.
- The returned `Class` declares one `public static` shadow per collected
  method. Callers invoke it reflectively (`cls.getMethod("foo", …).invoke(null,
  …)`) or via `MethodHandles.lookup().findStatic(cls, "foo", …)`.
- Reuses `Interceptor` as-is; for static methods the `proxy` argument is
  `null` (there is no instance). To run the original, the interceptor calls
  `method.invoke(null, args)` — the `Method` handed to it is the target's own
  reflected static method, so this invokes the original, not the shadow.

### 2.2 Generated class structure

A new generator emits a hidden class (no inheritance from the target):

```
class io.github.lamspace.StaticProxy$$AcceleratedProxy$$<n> extends Object {

    private static Interceptor _staticInterceptor$0, $1, …;   // per distinct interceptor
    private static Method     _method$<name>$<index>;          // per shadowed method

    static void __bindStatics(Interceptor[] interceptors) {    // assign _staticInterceptor$N = interceptors[N] }
    static { … }                                               // <clinit>: resolve each Method

    public static <R> foo(<params>) { … }   // one per shadowed method
}
```

- **No `DispatchTarget`, no `dispatch` method.** Static dispatch has no
  instance to dispatch on, and the interceptor calls the original itself via
  `method.invoke`, so the hashCode super-dispatch machinery is unused.
- The class is defined in `io.github.lamspace` (APS's own package) with
  `MethodHandles.lookup().defineHiddenClass(bytecode, true)`. It targets only
  `public` static methods, so no `LookupManager` / in-target-package access is
  needed.
- `<clinit>` resolves each `Method` once via
  `declaringClass.getDeclaredMethod(name, paramTypes)` on the method's *declaring*
  class (so inherited static methods resolve to the declaring superclass),
  reusing the existing LDC/`pushClassConstant` emission pattern.

#### Intercepted shadow (matched by a Group)

```
public static <R> foo(<params>) {
    try {
        Object[] args = new Object[<n>];            // box each param (slot 0 for the first — no `this`)
        Object result = _staticInterceptor$N.intercept(null, _method$foo$i, args);
        <unbox/CHECKCAST result; RETURN>            // void ⇒ POP result, RETURN
    } catch (RuntimeException e) { throw e; }
      catch (Error e)          { throw e; }
      catch (Exception e)      { throw new UndeclaredThrowableException(e); }
}
```

This mirrors `MethodDispatcher.generateOverride` with three differences:
`ACONST_NULL` instead of `ALOAD 0` (proxy is null), `GETSTATIC` instead of
`GETFIELD` (interceptor is a static field), and local-variable slots start at
`0` instead of `1` (static methods have no `this`).

#### Passthrough shadow (matched by no Group)

```
public static <R> foo(<params>) {
    return DeclaringClass.foo(<load params from slots>);   // INVOKESTATIC to the original
}
```

Direct `INVOKESTATIC` to `method.getDeclaringClass()` — zero interception
overhead, and the generated class still presents the target's full static
surface so `cls.getMethod("foo")` never fails for a collected method.

### 2.3 Method collection (declared + inherited)

A private `collectStaticMethods(Class<?> target)` walks the hierarchy
`target → … → Object` (exclusive) and gathers methods where
`isStatic() && isPublic() && !isFinal() && !isPrivate()`, deduplicating by
`name + parameterTypes` **subclass-first** (a subclass redeclaration shadows
the parent's, matching JVM hiding). The result is stable-sorted by
`name`, then `parameterTypes` — the same sort `matchMethods` already applies —
so mapping indices and generated shadows stay aligned.

### 2.4 Matching reuse

`proxyStatic` collects the sorted `Method[]`, then calls the existing private
`matchMethods(Method[] methods, Group[] groups)` to obtain
`MatchResult(interceptors, mapping)` with first-match-wins semantics and
`-1` for passthrough. This is exactly the matching used by `proxy()`; the
generator is handed the same sorted array so `mapping.indices()[i]` lines up
with the `i`-th shadow.

### 2.5 Binding and caching

The generated class is **not** cached: `proxyStatic` defines a fresh hidden
class per call, binds its interceptors once by reflecting
`__bindStatics(Interceptor[]`), and returns it. Static fields are class-global
state, so sharing a class across different interceptor instances would race;
the existing `WeakCache` is keyed on method-mapping *shape* (instance-agnostic)
and is the wrong model here. Static proxying is a startup-time, niche cost, so
per-call generation is acceptable, and hidden classes remain GC-eligible once
the caller drops the `Class` reference.

### 2.6 Files touched

| File                              | Change                                                                                                |
|-----------------------------------|-------------------------------------------------------------------------------------------------------|
| `generator/StaticMethodGenerator.java` | **New** — emits the static proxy class (§2.2); reuses `BytecodeUtils`, `ClinitRegistry`.              |
| `AcceleratedProxy.java`           | **New** two `proxyStatic()` overloads; `collectStaticMethods`; generation + `__bindStatics` invocation. |

No change to `ClassGenerator`, `InterfaceGenerator`, `MethodDispatcher`,
`InterfaceDispatcher`, `DispatchGenerator`, `MethodMapping`, `WeakCache`,
`Interceptor`, `Group`, `MethodPredicate`, `DispatchTarget`, `invokeSuper`, or
`LookupManager`.

## 3. Error handling

1. `target == null`, `groups` null/empty, `interceptor == null` →
   `IllegalArgumentException` (same messages as the existing `proxy()`).
2. `target.isInterface()` → `IllegalArgumentException` ("static proxy requires
   a class, not an interface").
3. `Interceptor.intercept` throws `RuntimeException`/`Error` → propagates as-is
   (mirrors the instance path). Checked exception → wrapped in
   `UndeclaredThrowableException` (same as the instance path).
4. `method.invoke(null, args)` inside the interceptor wraps the target's
   exceptions in `InvocationTargetException` — standard reflection behavior,
   documented, not special-cased.
5. A shadow whose `invoke`/`findStatic` is attempted but whose method was not
   collected (e.g. `final`/`private` static) → `NoSuchMethodException` from the
   JDK; documented, not guarded.
6. Bind failure (unexpected reflection error on `__bindStatics`) → wrapped in
   `RuntimeException("Failed to bind static proxy …")`.

## 4. Testing

New `src/test/java/io/github/lamspace/StaticMethodProxyTest.java`
(integration) plus, if warranted, a light
`src/test/java/io/github/lamspace/generator/StaticMethodGeneratorTest.java` for
the `<clinit>`/`__bindStatics` emission.

| #  | Scenario                             | Coverage                                                                                     |
|----|--------------------------------------|----------------------------------------------------------------------------------------------|
| 1  | Basic interception                   | `proxyClass.getMethod("foo").invoke(null, …)` routes through the interceptor with `proxy == null` and the correct `Method` + boxed args |
| 2  | Call original                        | interceptor does `method.invoke(null, args)`; asserts the original static result              |
| 3  | Passthrough                          | non-matching static method returns the original result, interceptor never called              |
| 4  | Return types                         | `void`, `int`, `long`, `double`, `boolean`, reference round-trip through box/unbox            |
| 5  | Overloaded statics                   | same name, different params → distinct shadows and correct dispatch                           |
| 6  | Inherited static method              | parent's public static is shadowed; `method.invoke` hits the parent's original                |
| 7  | Redeclared (hiding) static           | target redeclares parent's static → target's version shadowed, parent's not                   |
| 8  | `final`/`private` static skipped     | absent from the generated class (`getMethod` throws `NoSuchMethodException`)                  |
| 9  | Group matching                       | different statics bound to different interceptors; first-match-wins                           |
| 10 | Multiple interceptors dedup          | shared interceptor across groups stored once                                                    |
| 11 | Exceptions                           | interceptor `RuntimeException` propagates; checked → `UndeclaredThrowableException`           |
| 12 | MethodHandle invocation              | `MethodHandles.lookup().findStatic(cls, "foo", …)` works (fast-path smoke)                    |
| 13 | Class independence                   | two `proxyStatic` calls return distinct classes with independently bound interceptors          |
| 14 | Convenience overload                 | `proxyStatic(target, interceptor)` ≡ `proxyStatic(target, Group.otherwise(interceptor))`      |
| 15 | Negative: null target / empty groups / interface | `IllegalArgumentException`                                                              |
| 16 | Instance path unchanged (regression) | `proxy(target, interceptor)` and interface proxy still behave exactly as before               |

## 5. Benchmark

**Conclusion: zero impact on the instance path.** No instance-path file is
touched (§2.6), so `proxy()` generated bytecode is unchanged; the regression
guard is procedural — run the existing JMH suite before/after and require
identical class/interface/constructor-interception numbers.

Add `src/test/java/io/github/lamspace/benchmark/StaticMethodProxyBenchmark.java`.
Because static entry is inherently reflective, the benchmark measures against
the *reflection floor*, not a direct call:

1. **Direct** `Target.staticMethod()` — lower bound, for reference only.
2. **Reflection floor** `Method.invoke(null, …)` on the target's own `Method`
   (no proxy) — the minimum cost of reflective static invocation.
3. **Proxy + reflection** `proxyClass.getMethod("foo").invoke(null, …)` with a
   passthrough (no-op interceptor) — isolates APS's marginal overhead over (2).
4. **Proxy + MethodHandle** `findStatic(proxyClass, "foo", …).invoke(…)` — the
   JIT-friendly path; APS overhead should be near the box/unbox/intercept cost.

Report ns/op and ratios in `docs/benchmark-results.md` (and `_cn`). Interpret
the result as "APS adds one box, one interface call, one unbox on top of the
invocation mechanism the caller already chose" — the caller's entry mechanism
(2/3/4), not APS, dominates.

## 6. Documentation changes

- `docs/aps-future-roadmap.md`: mark Phase 3 item 5 **静态方法代理** as
  已完成; add a `### 静态方法代理（已完成）` subsection with the API example
  and the compile-time-binding limitation (why `Target.staticMethod()` is not
  interceptable).
- `README.md` / `README_CN.md`: add a "Static method proxy" feature bullet and
  a Quick Start example (reflective + `MethodHandle` invocation).
- Javadoc on `proxyStatic` (matching the existing detailed style) and on
  `StaticMethodGenerator`.
- `docs/benchmark-results.md` / `docs/benchmark-results_cn.md`: add the static
  proxy numbers.
- `docs/migration-guide.md`: no entry — the feature is purely additive and
  CGLib cannot proxy static methods either.

## 7. Deliberate decisions

1. **`proxyStatic` returns `Class<?>`, not a wrapper or `MethodHandle`.**
   Minimal API; the caller already chooses reflection or `MethodHandle`, and
   APS should not add an indirection layer whose performance ceiling is set by
   the entry mechanism anyway.
2. **Static proxy is uncached.** Static interceptor state is class-global, so
   sharing a generated class across interceptor instances would race; the
   existing instance-agnostic `WeakCache` is the wrong tool. Per-call
   generation is a startup cost, and hidden classes stay GC-eligible.
3. **`extends Object`, defined in APS's package, no `LookupManager`.** The
   feature targets `public` static methods only, so no in-target-package
   access or JPMS `--add-opens` is needed; that complexity belongs to the
   separate JPMS roadmap item (item 8).
4. **Interceptor contract reused with `proxy == null`; original invoked via
   `method.invoke(null, args)`.** No new interface, no static `dispatch`
   method; the reflection call is the explicit opt-in and is JIT-backed.
5. **Passthrough shadows emitted for non-matching statics** (direct
   `INVOKESTATIC`), so the generated class always exposes the target's full
   static surface and never surprises callers with a missing method.
6. **`public` static methods only.** Non-public statics need `setAccessible`
   / opens for the interceptor's `method.invoke`, which overlaps the JPMS item;
   deferred rather than half-implemented here.
7. **No caching of `MethodHandle`s.** The caller owns invocation handles;
   APS adds no global handle cache.

## 8. Out of scope

- Transparently intercepting `Target.staticMethod()` call sites (requires
  target-class instrumentation, not subclass generation).
- Interface static methods.
- Non-public (package-private/protected) static methods (JPMS/opens dependent).
- `final` or `private` static methods (cannot be hidden / not inherited).
- Combining static and instance proxying in a single generated class.
- Static method proxy for the annotation-driven `intercept()` API.
- Hot-load/hot-replace of static proxies (separate roadmap item 6).
- Faithful shadowing when a subclass declares a `public static final` method
  hiding an inherited non-`final` `public static` method: the `final` method is
  skipped and the shadow routes to the inherited (non-`final`) method, which
  does not mirror JVM hiding semantics for that one corner case.

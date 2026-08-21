# OpenProxy Unified Proxy Design

**Date**: 2026-08-02 | **Status**: approved

## Motivation

OpenProxy currently has two parallel APIs for class and interface proxies:

|                  | Class Proxy                                        | Interface Proxy                                              |
|------------------|----------------------------------------------------|--------------------------------------------------------------|
| Entry            | `AcceleratedProxy.create(Class, Callback)`         | `AcceleratedProxy.createInterface(Class, InterfaceCallback)` |
| Callback         | `intercept(proxy, method, index, args)`            | `intercept(proxy, method, args)`                             |
| Super invocation | `AcceleratedProxy.invokeSuper(proxy, index, args)` | N/A                                                          |
| Structure        | `extends TargetClass + SuperDispatcher`            | `extends Object + Interface`                                 |

The `invokeSuper` path for class proxies goes through a type-erased `MethodHandle.invoke()` (via `_handles[index].invoke(this, args)`), which adds ~10ns of overhead compared to a direct `super.method(args)` call. The design unifies the two APIs and replaces the MethodHandle dispatch with direct `INVOKESPECIAL` super calls routed through a hashCode-based switch.

## Public API

```java
// Unified entry point — auto-detects class vs interface
<T> T proxy(Class<T> target, Interceptor interceptor);

<T> T proxy(Class<T> target, Interceptor interceptor, ClassFilter filter);

<T> T proxy(Class<T> target, Interceptor interceptor, ClassFilter filter,
            Object... constructorArgs);

// Unified callback — no index parameter
@FunctionalInterface
interface Interceptor {
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}

// Original implementation invocation — uses Method object instead of index
static Object invokeSuper(Object proxy, Method method, Object[] args) throws Throwable;

// ClassFilter — unchanged. null means intercept all methods
interface ClassFilter {
    boolean accept(Method method);
}
```

**Changes from current API**:

- `create` + `createInterface` → `proxy` (single method for both classes and interfaces)
- `Callback` + `InterfaceCallback` → `Interceptor` (unified signature, `index` removed)
- `invokeSuper(proxy, index, args)` → `invokeSuper(proxy, method, args)` (Method-based dispatch)

## Generated Class Structure

### Class Proxy

```
extends TargetClass implements DispatchTarget
```

### Interface Proxy

```
extends Object implements TargetInterface, DispatchTarget
```

### `DispatchTarget` (internal interface)

```java
// Not exposed to users — used internally by invokeSuper
interface DispatchTarget {
    Object dispatch(Method method, Object[] args) throws Throwable;
}
```

### Per-method generation

**Intercepted methods** (all methods by default, or filter-accepted):

- Delegate to `interceptor.intercept(proxy, method, boxedArgs)`
- Box primitives before call, unbox return value after
- Exception handling: `RuntimeException`/`Error` rethrown directly, checked `Exception` wrapped in `UndeclaredThrowableException`

**Non-intercepted methods** (filter-rejected):

- Class proxy: direct `super.method(args)` — zero interception overhead
- Interface proxy: throw `AbstractMethodError`

### The `dispatch()` method — hashCode-based switch

Replaces the current `_handles[index].invoke(this, args)` MethodHandle dispatch.

```
public final Object dispatch(Method method, Object[] args) throws Throwable {
    int hash = System.identityHashCode(method);

    // Object methods
    if (hash == H_equals) { return super.equals(args[0]); }
    else if (hash == H_hashCode) { return super.hashCode(); }
    else if (hash == H_toString) { return super.toString(); }
    // Class methods — direct INVOKESPECIAL
    else if (hash == H_call_String) { return super.call((String) args[0]); }
    else if (hash == H_add_int_int) { return super.add((int) args[0], (int) args[1]); }
    else if (hash == H_void_run) { super.run(); return null; }
    // Interface methods — no super implementation
    else if (hash == H_foo) { throw new AbstractMethodError("..."); }

    return null; // unreachable
}
```

Key properties:

- Hash values are pre-computed in `<clinit>` as `private static final int H_xxx` constants (bytecode uses `ldc` to load them)
- Class methods use direct `super.method(args)` — `INVOKESPECIAL` with specific types, inlinable by JIT
- Parameter unboxing is hardcoded per branch (`(String) args[0]`, `(int) args[0]`, etc.)
- Hash collision: if two methods produce the same `System.identityHashCode`, fall back to `method.equals()` check in `<clinit>` validation; if collision detected, append a secondary discriminator (e.g., `hash == H_xxx && method.getName().equals("call")`)
- Interface methods (for interface proxies) throw `AbstractMethodError` — consistent with the fact that interfaces have no super implementation to delegate to. Interface `default` method invocation is deferred to a future iteration.

### `<clinit>` static initializer

1. For each method: resolve via `Class.getDeclaredMethod(...)`, store as a `private static final Method _method$N` field
2. Compute `System.identityHashCode(method)`, store as `private static final int H_methodName$N`
3. Detect hash collisions: if two methods have the same hash, add a secondary discriminator and emit a warning
4. No `MethodHandle[]` array — eliminated entirely

### Class loading

Uses `Lookup.defineHiddenClass()` for both class and interface proxies. Unify the two current paths (class proxy through `HiddenClassLoader` + `LookupManager`, interface proxy through inline `MethodHandles.lookup()`) into a single `defineClass` method.

### Class cache

Introduce a `WeakCache<ClassLoader, CacheKey, Class<?>>` (following the JDK Proxy pattern, as used by newproxy) to avoid re-generating bytecode for the same `(targetClass, filter)` combination.

Cache key: `{targetClass, filter}` where `null` filter means "intercept all".

## What Is Removed

- `Callback` interface → replaced by `Interceptor`
- `InterfaceCallback` interface → merged into `Interceptor`
- `SuperDispatcher` interface → replaced by `DispatchTarget`
- `MethodHandle[] _handles` static array → replaced by per-method hash constants + `dispatch()` switch
- `MethodHandle` asSpreader/asType type-erasure logic → no longer needed
- `AcceleratedProxy.create()` / `AcceleratedProxy.createInterface()` methods → merged into `AcceleratedProxy.proxy()`
- `HiddenClassLoader` (if no longer needed after unification)

## Expected Performance Impact

| Scenario                     | Before (OpenProxy) | After (expected) | Improvement |
|------------------------------|--------------|------------------|-------------|
| Class proxy passthrough      | 17.15 ns     | ~6 ns            | ~3x         |
| Class proxy arg modify       | 22.85 ns     | ~12 ns           | ~2x         |
| Class proxy primitive return | 9.46 ns      | ~2 ns            | ~5x         |
| Class proxy void method      | 8.02 ns      | ~2 ns            | ~4x         |
| Class proxy multi-param      | 72.47 ns     | ~65 ns           | ~10%        |
| Class proxy no-op            | 1.35 ns      | ~1.0 ns          | ~25%        |
| Interface proxy (all)        | ~parity      | ~parity          | —           |

The interface proxy path is already at parity with `java.lang.reflect.Proxy` and requires no structural change beyond API unification.

## Open Questions / Deferred

- **Interface `default` method invocation**: Neither current OpenProxy nor this design supports calling an interface's default method from within an interceptor. This requires `MethodHandles.Lookup.findSpecial()` and is deferred to a future iteration.
- **Static/final method interception**: Out of scope. OpenProxy does not intercept static or final methods.

## Migration Path

Users migrate from:

```java
// Old class proxy
MyClass proxy = AcceleratedProxy.create(MyClass.class,
                (obj, method, index, args) -> {
                    return AcceleratedProxy.invokeSuper(obj, index, args);
                });

// Old interface proxy
MyInterface proxy = AcceleratedProxy.createInterface(MyInterface.class,
        (obj, method, args) -> { ...});
```

To:

```java
// Unified — same API for both
MyClass proxy = AcceleratedProxy.proxy(MyClass.class,
                (obj, method, args) -> {
                    return AcceleratedProxy.invokeSuper(obj, method, args);
                });

MyInterface proxy = AcceleratedProxy.proxy(MyInterface.class,
        (obj, method, args) -> { ...});
```

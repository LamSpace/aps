# APS Interface Proxy Support — Design Spec

Date: 2026-08-01 Status: draft

## Motivation

APS v1 supports proxying concrete classes only (`ClassGenerator` generates a subclass that `extends TargetClass`). It cannot proxy interfaces because:

1. JVM forbids a class from `extends` an interface
2. `findSpecial` has no super implementation to bind for abstract methods

Adding interface support makes APS a complete alternative to both JDK `java.lang.reflect.Proxy` (interfaces) and CGLib (classes) in a single API, with MethodHandle-based dispatch instead of reflection.

## Design

### 1. API Surface

Two callback interfaces, cleanly separated by proxy target type:

```java
// Existing — class proxies (4-arg callback with superHandle)
@FunctionalInterface
public interface Callback {
    Object intercept(Object proxy, Method method, MethodHandle superHandle,
                     Object[] args) throws Throwable;
}

// New — interface proxies (3-arg callback, no superHandle)
@FunctionalInterface
public interface InterfaceCallback {
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}
```

New entry point on `APS`:

```java
// Interface proxy (no constructorArgs — always extends Object)
public static <T> T createInterface(Class<T> interfaceClass,
                                    InterfaceCallback callback);

public static <T> T createInterface(Class<T> interfaceClass,
                                    InterfaceCallback callback,
                                    ClassFilter filter);
```

- `createInterface` validates that `interfaceClass.isInterface()` is true
- `ClassFilter` semantics carry over unmodified — null means intercept all methods

Usage comparison:

```java
// Class proxy (4 args, superHandle available)
APS.create(UserService .class, (obj, method, superHandle, args) ->{
        System.out.

println("before");
    return superHandle.

invoke(args);
});

// Interface proxy (3 args, no superHandle)
        APS.

createInterface(Runnable .class, (proxy, method, args) ->{
        System.out.

println("running");
    return null;
            });
```

### 2. Generated Class Structure

Interface proxies generate a class that `extends Object implements TargetInterface`:

```
class Target$$APS$$N extends java.lang.Object implements Target {
    private final InterfaceCallback _callback;
    private static Method _method$m$0, _method$m$1, ...

    public <init>(InterfaceCallback cb) {
        super();  // always no-arg
        this._callback = cb;
    }

    // Per interface method:
    public RetType someMethod(ArgTypes...) {
        return (_callback).intercept(this, _method$m$N, new Object[]{...});
    }
}
```

No MethodHandle fields or `<clinit>` MethodHandle setup — only `Method` objects are stored for method identification.

### 3. Class vs Interface Generation — Key Differences

|                   | Class (ClassGenerator)                                            | Interface (InterfaceGenerator)             |
|-------------------|-------------------------------------------------------------------|--------------------------------------------|
| Superclass        | `extends TargetClass`                                             | `extends java.lang.Object`                 |
| Interfaces        | none                                                              | `implements TargetInterface`               |
| Constructor       | may pass args to `super(...)`                                     | always `super()`                           |
| Static fields     | `Method` + `MethodHandle` per method                              | `Method` only per method                   |
| `<clinit>`        | `findSpecial` + `asSpreader` + store Method                       | store Method only (no MethodHandle)        |
| Method body       | `_callback.intercept(proxy, method, spreader.bindTo(this), args)` | `_callback.intercept(proxy, method, args)` |
| Exception handler | try/catch for Runtime/Error/checked                               | same pattern                               |

### 4. Implementation Plan — New Files & Changes

**New files:**

| File                                 | Role                                                                  |
|--------------------------------------|-----------------------------------------------------------------------|
| `InterfaceCallback.java`             | 3-arg callback functional interface                                   |
| `generator/InterfaceDispatcher.java` | Generates method bodies for interface impls (no MethodHandle binding) |
| `generator/InterfaceGenerator.java`  | Orchestrates bytecode generation for interface proxies                |

**Changed files:**

| File       | Change                                                       |
|------------|--------------------------------------------------------------|
| `APS.java` | Add `createInterface()` overloads with `isInterface()` guard |

**Untouched files:**

| File                     | Reason                                               |
|--------------------------|------------------------------------------------------|
| `Callback.java`          | Stays as-is for class proxies                        |
| `ClassGenerator.java`    | Stays as-is                                          |
| `MethodDispatcher.java`  | Stays as-is                                          |
| `ClinitRegistry.java`    | Reused by InterfaceGenerator for Method registration |
| `HiddenClassLoader.java` | Works for both classes and interfaces                |
| `LookupManager.java`     | Works for both                                       |

### 5. Method Body Generation (InterfaceDispatcher)

Similar to `MethodDispatcher` but simpler — no `bindTo`, no `asSpreader`, no superHandle construction:

```
1. Load _callback field
2. Load proxy = this
3. Load static _method$X
4. Build Object[]{args...} with primitive boxing
5. Call InterfaceCallback.intercept(proxy, method, args)
6. Unbox return / pop + return for void
7. try/catch for RuntimeException, Error, checked Exception
```

Non-intercepted methods (filter rejects): call `throw new AbstractMethodError()` — there is no super implementation to fall back to.

### 6. Error Handling

- `createInterface(null, ...)` → `IllegalArgumentException("targetClass must not be null")`
- `createInterface(SomeClass.class, ...)` where `SomeClass` is not an interface → `IllegalArgumentException("targetClass must be an interface")`
- `createInterface(..., null)` → `IllegalArgumentException("callback must not be null")`
- Filter rejects a method → generated method body throws `AbstractMethodError` (consistent with unimplemented interface method semantics)

### 7. Testing

Functional tests (mirroring existing `APSFunctionalTest`):

- Noop callback returning fixed value
- Arg modification in callback
- Primitive return types
- Void methods
- ClassFilter routing
- Default methods on interfaces (treated as regular methods by the callback)
- `createInterface` with non-interface class → exception
- Callback throws RuntimeException → propagates
- Callback throws checked Exception → wraps in `UndeclaredThrowableException`

### 8. Non-Goals (for this change)

- Multiple interface implementation (`implements A, B, C`)
- Default method `superHandle` invocation
- Static or private interface methods
- `create()` auto-detecting interface vs class (explicit `createInterface` keeps API clear)

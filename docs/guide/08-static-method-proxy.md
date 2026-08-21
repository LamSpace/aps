# 8. Static Method Proxy

Static methods are compile-time bound, so a subclass cannot override them. OpenProxy instead generates a **shadow class** whose `public static` methods mirror the target's, routing them through the same `Interceptor`.

## `AcceleratedProxy.proxyStatic`

```java
public class Utils {
    public static int add(int a, int b) { return a + b; }
}

Class<?> proxyClass = AcceleratedProxy.proxyStatic(Utils.class,
        (proxy, method, args) -> {
            System.out.println("calling " + method.getName());
            return method.invoke(null, args);   // call the original static method
        });
```

The returned `Class` has a static `add(int, int)` that invokes your interceptor with `proxy == null`. To invoke it (static methods don't participate in virtual dispatch, so you must call the *returned class*):

```java
int result = (Integer) proxyClass
        .getMethod("add", int.class, int.class)
        .invoke(null, 2, 3);          // -> 5, prints "calling add"
```

Or via a `MethodHandle`:

```java
MethodHandle mh = MethodHandles.lookup()
        .findStatic(proxyClass, "add", MethodType.methodType(int.class, int.class, int.class));
int result = (int) mh.invoke(2, 3);
```

## Group support

```java
Class<?> cls = AcceleratedProxy.proxyStatic(Utils.class,
        Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
        Group.otherwise(otherInterceptor));
```

Methods matched by no group pass through to the original via a direct
`INVOKESTATIC` (no interceptor). Methods are collected from the target and its superclasses, deduplicated subclass-first.

## Notes and limits

- **`Utils.add(...)` is not interceptable** — callers are compiled against the original class, so only calls on the returned `Class` route through the interceptor.
- The generated class is **not cached** (static fields are class-level state); each `proxyStatic` call returns a fresh class.
- Within an intercepted static method, call the original via
  `method.invoke(null, args)`.

Next: [Multi-Interface Proxy](09-multi-interface-proxy.md).

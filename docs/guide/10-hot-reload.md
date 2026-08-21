# 10. Hot Reload / Hot Swap

OpenProxy gives you two levers for long-running applications: evicting cached proxy classes (for hot-deployed classes) and swapping interceptors on a live instance.

## Evicting cached proxy classes

```java
AcceleratedProxy.evict(MyClass.class);            // drop proxies keyed on MyClass
AcceleratedProxy.evictClassLoader(pluginClassLoader); // drop proxies for a loader
```

The next `proxy(...)` call regenerates a fresh class. Existing instances are unaffected (they hold a direct reference to their hidden class). Use this in frameworks that hot-deploy classes under a dedicated `ClassLoader`.

Cache-key note: class proxies key on the target class; interface proxies key on the **first** interface, so pass that to `evict`.

## Swapping interceptors on a live instance

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class, oldInterceptor);
AcceleratedProxy.rebind(proxy, newInterceptor);   // no recreation
```

`rebind` replaces the bound interceptors in place. The array form lets you swap several at once, index-aligned with the generated class's interceptor fields:

```java
AcceleratedProxy.rebind(proxy, new Interceptor[]{a, b});
```

- `ConstructorInterceptor` is **not** rebindable (it runs only at construction).
- `rebind` is a single-writer management operation: a caller that rebinds on one thread and invokes methods on another must establish its own happens-before edge (lock, thread start, latch, or volatile flag).

Next: [JPMS / Strong Encapsulation](11-jpms.md).

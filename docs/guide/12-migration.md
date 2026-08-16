# 12. Migration

This chapter shows the shortest path from CGLib and `java.lang.reflect.Proxy` to APS. A more detailed guide lives at [docs/migration-guide.md](../migration-guide.md).

## From CGLib

**Before:**

```java
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(MyService.class);
enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
        proxy.invokeSuper(obj, args));
MyService proxy = (MyService) enhancer.create();
```

**After:**

```java
MyService proxy = AcceleratedProxy.proxy(MyService.class,
        (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));
```

Differences:

| CGLib                          | APS                                               |
|--------------------------------|---------------------------------------------------|
| `Enhancer` builder             | `AcceleratedProxy.proxy()` factory                |
| `MethodInterceptor` (4 args)   | `Interceptor` (3 args)                            |
| `proxy.invokeSuper(obj, args)` | `AcceleratedProxy.invokeSuper(obj, method, args)` |
| explicit cast                  | generic inference, no cast                        |
| custom ClassLoader             | hidden class, GC-safe                             |

## From `java.lang.reflect.Proxy`

**Before:**

```java
Service proxy = (Service) Proxy.newProxyInstance(
        Service.class.getClassLoader(),
        new Class<?>[]{Service.class},
        (p, m, a) -> m.invoke(new ServiceImpl(), a));
```

**After (proxy the concrete class directly):**

```java
ServiceImpl proxy = AcceleratedProxy.proxy(ServiceImpl.class,
        (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));
```

The main win: APS proxies the **implementation class**, so the interceptor calls the real method via `invokeSuper` instead of you hand-rolling a delegate.

## Feature comparison

| Feature                  | APS           | CGLib               | Java Proxy |
|--------------------------|---------------|---------------------|------------|
| Proxies concrete classes | ✅            | ✅                  | ❌         |
| Selective interception   | ✅ `Group.of` | ✅ `CallbackFilter` | ❌         |
| Constructor interception | ✅            | ✅                  | ❌         |
| Static method proxy      | ✅            | ❌                  | ❌         |
| Hot reload / rebind      | ✅            | ❌                  | ❌         |
| GC-safe class loading    | ✅            | ❌                  | ✅         |

Next: [Performance & Benchmarking](13-performance.md).

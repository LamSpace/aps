# OpenProxy Migration Guide

How to migrate from CGLib or `java.lang.reflect.Proxy` to OpenProxy.

## CGLib → OpenProxy

### Before (CGLib)

```java
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import net.sf.cglib.proxy.MethodProxy;

Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(MyService.class);
enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
    System.out.println("before " + method.getName());
    Object result = proxy.invokeSuper(obj, args);
    System.out.println("after " + method.getName());
    return result;
});
MyService proxy = (MyService) enhancer.create();
```

### After (OpenProxy)

```java
import io.github.lamspace.AcceleratedProxy;

MyService proxy = AcceleratedProxy.proxy(MyService.class, (obj, method, args) -> {
    System.out.println("before " + method.getName());
    Object result = AcceleratedProxy.invokeSuper(obj, method, args);
    System.out.println("after " + method.getName());
    return result;
});
// No cast needed — generic type inference returns MyService
```

### Key differences

| CGLib                          | OpenProxy                                               |
|--------------------------------|---------------------------------------------------|
| `Enhancer` builder             | `AcceleratedProxy.proxy()` static factory         |
| `MethodInterceptor` (4 args)   | `Interceptor` (3 args, `@FunctionalInterface`)    |
| `proxy.invokeSuper(obj, args)` | `AcceleratedProxy.invokeSuper(obj, method, args)` |
| Requires explicit cast         | Generic inference, no cast                        |
| Custom ClassLoader             | Hidden class, GC-safe                             |

### Method filtering

**CGLib (`CallbackFilter` + `NoOp`):**

```java
enhancer.setCallbacks(new Callback[] {
    interceptor, NoOp.INSTANCE
});
enhancer.setCallbackFilter(method ->
    method.getName().startsWith("get") ? 0 : 1);
```

**OpenProxy (`Group.of`):**

```java
MyService proxy = AcceleratedProxy.proxy(MyService.class,
        Group.of(m -> m.getName().startsWith("get"), interceptor));
// Methods not matching any Group skip interception entirely — zero overhead
```

### Constructor arguments

**CGLib:**

```java
enhancer.create(new Class[] { String.class }, new Object[] { "arg" });
```

**OpenProxy:**

```java
AcceleratedProxy.proxy(MyService.class, new Object[]{"arg"},
        Group.otherwise(interceptor));
```

---

## Java Proxy → OpenProxy

### Before (java.lang.reflect.Proxy)

```java
import java.lang.reflect.Proxy;
import java.lang.reflect.InvocationHandler;

Service proxy = (Service) Proxy.newProxyInstance(
        Service.class.getClassLoader(),
        new Class<?>[]{Service.class},
        (proxyObj, method, args) -> {
            System.out.println("before " + method.getName());
            return method.invoke(new ServiceImpl(), args);
        }
);
```

### After (OpenProxy)

```java
import io.github.lamspace.AcceleratedProxy;

ServiceImpl proxy = AcceleratedProxy.proxy(ServiceImpl.class,
        (obj, method, args) -> {
            System.out.println("before " + method.getName());
            return AcceleratedProxy.invokeSuper(obj, method, args);
        }
);
```

### Key differences

| Java Proxy                    | OpenProxy                                               |
|-------------------------------|---------------------------------------------------|
| Interface-based only          | Concrete class-based                              |
| `InvocationHandler` (3 args)  | `Interceptor` (3 args, `@FunctionalInterface`)    |
| `method.invoke(target, args)` | `AcceleratedProxy.invokeSuper(obj, method, args)` |
| Requires target instance      | Built-in super-call binding                       |
| `Proxy.newProxyInstance(...)` | `AcceleratedProxy.proxy(Class, Interceptor)`      |

### Multi-interface

`java.lang.reflect.Proxy` supports one handler across several interfaces:

```java
Object proxy = Proxy.newProxyInstance(
        loader,
        new Class<?>[]{A.class, B.class},
        handler);
```

OpenProxy mirrors this with `AcceleratedProxy.proxy(new Class<?>[]{A.class, B.class}, interceptor)`:

```java
Object p = AcceleratedProxy.proxy(new Class<?>[]{A.class, B.class},
        (obj, method, args) -> {
            System.out.println("before " + method.getName());
            return null;
        });
A a = (A) p;   // one object, multiple interface views
B b = (B) p;
```

Methods with the same signature and return type across interfaces are merged; ambiguous conflicts throw `IllegalArgumentException`.

---

## Feature Comparison

| Feature                        | OpenProxy                                  | CGLib                      | Java Proxy                    |
|--------------------------------|--------------------------------------|----------------------------|-------------------------------|
| Proxies concrete classes       | Yes                                  | Yes                        | No (interfaces only)          |
| Dispatch mechanism             | hashCode switch + INVOKESPECIAL      | Generated bytecode         | `Method.invoke`               |
| Class loading                  | Hidden class                         | Custom ClassLoader         | Native Proxy                  |
| GC-safe                        | Yes                                  | No (ClassLoader leak risk) | Yes                           |
| Lambda-friendly API            | Yes                                  | Yes                        | Yes                           |
| Method filtering               | Yes (Group.of)                       | Yes (CallbackFilter)       | No                            |
| No-default-constructor support | Yes                                  | Yes                        | N/A                           |
| Primitive boxing               | Automatic                            | Automatic                  | Automatic                     |
| Exception propagation          | Checked → UndeclaredThrowable        | Checked → InvocationTarget | Checked → UndeclaredThrowable |
| Final class/method proxy       | No (JVM limit)                       | No (JVM limit)             | N/A                           |
| Static method proxy            | Yes                                  | No                         | No                            |
| Constructor interception       | Yes                                  | Yes                        | No                            |
| Hot reload / rebind            | Yes (`evict`, `rebind`)              | No                         | No                            |
| Maven Central                  | Coming soon                          | Yes                        | Built-in (JDK)                |

---

## Hot reload / hot swap

`evict(Class)`, `evictClassLoader(ClassLoader)`, and `rebind(proxy, ...)` are
purely additive. CGLib has no post-construction callback swap — the equivalent
is a fresh proxy per reloaded class — and `java.lang.reflect.Proxy` instances
are immutable after creation, so neither has a direct counterpart for `rebind`.
Note that `evict`/`evictClassLoader` only manage the *cache*; a target loaded by
a child `ClassLoader` (with OpenProxy in a shared parent) needs the JPMS
`--add-opens` strategy before it can be proxied at all.

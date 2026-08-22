# 7. Constructor Interception

Constructor interception runs hooks around the *superclass constructor* — useful for injecting dependencies, validating construction, or tracking object creation. It is **class proxies only** (interfaces have no superclass constructor).

## The `ConstructorInterceptor` interface

```java
@FunctionalInterface
public interface ConstructorInterceptor {
    Object[] before(Constructor<?> ctor, Object[] args) throws Throwable;
    default void after(Object proxy, Constructor<?> ctor, Object[] args) {}
}
```

- `before` runs **before** the superclass constructor. It can rewrite the arguments (return a new array) or veto construction by throwing.
- `after` runs **after** the constructor and full initialization; it is observational (default no-op).

## Example

```java
ConstructorInterceptor ctorInterceptor = new ConstructorInterceptor() {
    public Object[] before(Constructor<?> ctor, Object[] args) {
        System.out.println("before " + ctor.getName());
        return args;                       // may rewrite
    }

    public void after(Object proxy, Constructor<?> ctor, Object[] args) {
        System.out.println("after " + ctor.getName());
    }
};

Greeter proxy = OpenProxy.proxy(Greeter.class, interceptor, ctorInterceptor);
```

Output during construction:

```
before io.github.lamspace.Greeter
after io.github.lamspace.Greeter
```

## Entry points

```java
proxy(Class<T>, Interceptor, ConstructorInterceptor)
proxy(Class<T>, ConstructorInterceptor, Group...)
proxy(Class<T>, Object[] constructorArgs, ConstructorInterceptor, Group...)
```

## Notes and limits

- `before` receives no proxy instance — `this` is not yet initialized before the superclass constructor runs.
- `after` receives the fully initialized `proxy`.
- A checked exception thrown from `before` is surfaced to the caller as
  `UndeclaredThrowableException`.
- The constructor hook adds a small, once-per-instance cost (~9 ns in the benchmark); the per-method hot path is byte-for-byte unchanged.

Next: [Static Method Proxy](08-static-method-proxy.md).

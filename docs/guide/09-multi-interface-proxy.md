# 9. Multi-Interface Proxy

One proxy object can implement several interfaces at once.

```java
Object p = OpenProxy.proxy(
        new Class<?>[]{Greeter.class, Auditable.class},
        (obj, method, args) -> {
            System.out.println("calling " + method.getName());
            return null;
        });

Greeter g = (Greeter) p;     // one object, several interface views
Auditable a = (Auditable) p;
```

## Method merging and conflicts

Interfaces often share methods (e.g. a common `getName()`). OpenProxy resolves the merged, deduplicated method set at creation time:

- Same signature + same return type → **merged** into one implementation.
- Same signature + **different** return types → `IllegalArgumentException`.
- Two `default` implementations from distinct interfaces → `IllegalArgumentException`.
- One `default` + abstract declarations → merged, calling the `default`.

## Non-public interfaces

Package-private interfaces are supported: the generated class is defined in the package shared by the non-public interfaces, so it can implement them.

```java
// package-private interface in your package
interface SecretService {
    String greet(String name);

    default String shout(String s) { return s.toUpperCase(); }
}

SecretService proxy = OpenProxy.proxy(SecretService.class,
        (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));

proxy.greet("world");   // routed through the interceptor
proxy.shout("hi");      // invokeSuper calls the default implementation
```

Constraints:

- All non-public interfaces must share a single package (otherwise
  `IllegalArgumentException`); public interfaces may be in any package.
- When all interfaces are public, the generated class lives in
  `io.github.lamspace` — public JDK interfaces (e.g. `java.util.function.Function`)
  work unchanged.

Next: [Hot Reload / Hot Swap](10-hot-reload.md).

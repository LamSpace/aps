# 5. Method Grouping

A single interceptor is often not enough: you may want one behaviour for getters, another for setters, and none for everything else. OpenProxy models this with `Group`.

## `Group.of` and `Group.otherwise`

```java
Greeter proxy = OpenProxy.proxy(Greeter.class,
        Group.of(m -> m.getName().startsWith("get"), (obj, method, args) -> {
            System.out.println("[GET] " + method.getName());
            return OpenProxy.invokeSuper(obj, method, args);
        }),
        Group.of(m -> m.getName().startsWith("set"), (obj, method, args) -> {
            System.out.println("[SET] " + method.getName());
            return OpenProxy.invokeSuper(obj, method, args);
        }),
        Group.otherwise((obj, method, args) ->
                OpenProxy.invokeSuper(obj, method, args)));
```

- `Group.of(predicate, interceptor)` binds `interceptor` to every method where
  `predicate.test(method)` returns `true`.
- `Group.otherwise(interceptor)` is a catch-all that matches any method not matched by an earlier group.
- Groups are evaluated in **declaration order**, `first-match-wins`.

```java
proxy.getGreeting();   // [GET] getGreeting
proxy.setGreeting("hi"); // [SET] setGreeting
```

## The predicate

`MethodPredicate` is `boolean test(Method method)` — a lambda over the
`java.lang.reflect.Method`. You can match on name, parameter types, annotations, return type, or anything reflection exposes:

```java
Group.of(m -> m.isAnnotationPresent(Tx.class), txInterceptor)
```

## Passthrough — zero overhead for unmatched methods

Any method that matches **no** group is not intercepted at all: its override is a direct `super.method(...)`, so it costs the same as a direct call. If you want an explicit catch-all instead, end the chain with `Group.otherwise(...)`.

## A note on ambiguous matches

If a method matches several groups, the first one wins and OpenProxy logs a `WARNING`
(helpful for debugging overlapping predicates).

Next: [Annotation-Driven API](06-annotation-api.md).

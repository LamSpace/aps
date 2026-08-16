# 6. Annotation-Driven API

For a more declarative style, APS can derive the method matching from annotations instead of hand-written `Group` predicates.

## `@Intercept` + `@Around`

```java
@Intercept
class MetricsInterceptor {
    @Around("get*")
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        long start = System.nanoTime();
        try {
            return AcceleratedProxy.invokeSuper(proxy, method, args);
        } finally {
            System.out.println(method.getName() + " took " +
                    (System.nanoTime() - start) + " ns");
        }
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
String s = proxy.getGreeting();   // routed through measure()
```

- `@Intercept` marks the container class.
- `@Around` marks a method to bind as an interceptor for matching target methods.
- `AcceleratedProxy.intercept(target, interceptorObject)` builds the proxy.

The `@Around` method must be an instance method with signature
`(Object, Method, Object[])` returning a reference type (not `void`/primitive).

## Matching dimensions

`@Around` combines three dimensions with **AND** (within a dimension, multiple values are **OR**-combined):

| Attribute        | Matches                                               |
|------------------|-------------------------------------------------------|
| `value` / `glob` | method-name glob (`*` = any sequence, `?` = one char) |
| `regex`          | method-name regular expression (whole-name match)     |
| `annotatedWith`  | target method carries the given annotation(s)         |

```java
@Around(value = "get*", annotatedWith = Tx.class)
Object measure(...) { ... }
// matches methods named get* that are also annotated @Tx
```

## Semantics

- Methods matched by no `@Around` method **passthrough** (direct super call), the same as the programmatic `Group` API.
- The `@Around` method is bound to the `Interceptor` SAM via a
  `LambdaMetafactory` call site — no per-call reflection, so it runs at hand-written-lambda speed.

Next: [Constructor Interception](07-constructor-interception.md).

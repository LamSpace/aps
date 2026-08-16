# 5. 方法分组

单个拦截器往往不够：你可能希望 getter 一套行为、setter 另一套、其余方法完全不拦截。 APS 用 `Group` 来建模。

## `Group.of` 与 `Group.otherwise`

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
        Group.of(m -> m.getName().startsWith("get"), (obj, method, args) -> {
            System.out.println("[GET] " + method.getName());
            return AcceleratedProxy.invokeSuper(obj, method, args);
        }),
        Group.of(m -> m.getName().startsWith("set"), (obj, method, args) -> {
            System.out.println("[SET] " + method.getName());
            return AcceleratedProxy.invokeSuper(obj, method, args);
        }),
        Group.otherwise((obj, method, args) ->
                AcceleratedProxy.invokeSuper(obj, method, args)));
```

- `Group.of(predicate, interceptor)` 把 `interceptor` 绑定到所有 `predicate.test(method)`
  为 `true` 的方法。
- `Group.otherwise(interceptor)` 是兜底，匹配前面所有组都未匹配的方法。
- 组按 **声明顺序**求值， **先匹配先胜出**。

```java
proxy.getGreeting();     // [GET] getGreeting
proxy.setGreeting("hi"); // [SET] setGreeting
```

## 谓词

`MethodPredicate` 是 `boolean test(Method method)` —— 一个作用于 `java.lang.reflect.Method`
的 lambda。你可以按方法名、参数类型、注解、返回类型等反射信息匹配：

```java
Group.of(m -> m.isAnnotationPresent(Tx.class), txInterceptor)
```

## 透传 —— 未匹配方法零开销

任何未匹配任何组的方法都不会被拦截：它的重写就是直接 `super.method(...)`，开销与直接调用 相同。若需要显式兜底，就在链尾加 `Group.otherwise(...)`。

## 关于歧义匹配

若某方法匹配了多个组，第一个组胜出，APS 会记录一条 `WARNING`（便于排查重叠谓词）。

下一章：[注解驱动 API](06-annotation-api_cn.md)。

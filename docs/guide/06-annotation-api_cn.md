# 6. 注解驱动 API

若想要更声明式的风格，APS 可以从注解推导方法匹配，而不是手写 `Group` 谓词。

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
            System.out.println(method.getName() + " 耗时 " +
                    (System.nanoTime() - start) + " ns");
        }
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
String s = proxy.getGreeting();   // 经 measure() 路由
```

- `@Intercept` 标记容器类。
- `@Around` 标记要绑定为拦截器的方法，作用于匹配的目标方法。
- `AcceleratedProxy.intercept(target, interceptorObject)` 构建代理。

`@Around` 方法必须是实例方法，签名为 `(Object, Method, Object[])`，返回引用类型 （不能是 `void`/基本类型）。

## 匹配维度

`@Around` 以 **AND** 组合三个维度（同一维度内多个值为 **OR**）：

| 属性             | 匹配                                          |
|------------------|-----------------------------------------------|
| `value` / `glob` | 方法名 glob（`*` = 任意序列，`?` = 单个字符） |
| `regex`          | 方法名正则（整名匹配）                        |
| `annotatedWith`  | 目标方法带有指定注解                          |

```java
@Around(value = "get*", annotatedWith = Tx.class)
Object measure(...) { ... }
// 匹配名为 get* 且带 @Tx 注解的方法
```

## 语义

- 未被任何 `@Around` 匹配的方法 **透传**（直接 super 调用），与程序化 `Group` API 一致。
- `@Around` 方法通过 `LambdaMetafactory` 调用点绑定到 `Interceptor` SAM —— 无逐次反射， 因此以手写 lambda 的速度运行。

下一章：[构造器拦截](07-constructor-interception_cn.md)。

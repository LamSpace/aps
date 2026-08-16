# 4. 拦截器 API

## `Interceptor` 接口

```java
@FunctionalInterface
public interface Interceptor {
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}
```

每个被拦截的调用都会进入此方法。三个参数：

| 参数     | 含义                                                   |
|----------|--------------------------------------------------------|
| `proxy`  | 代理实例（调用方看到的 `this`）                        |
| `method` | 被调用的 `java.lang.reflect.Method`（元数据 + 调度键） |
| `args`   | 装箱后的参数；无参方法为空数组                         |

返回值由生成的字节码拆箱/转型以匹配声明的返回类型（`void` 为 `null`，基本类型为装箱值）。

## 调用原方法

使用 `AcceleratedProxy.invokeSuper`：

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) ->
        AcceleratedProxy.invokeSuper(obj, method, args));
```

`invokeSuper` 让代理的 `dispatch()` 方法跳转到原始 `super.method(...)` —— 直接
`INVOKESPECIAL`，无反射。

## 参数与返回值处理

- **基本类型**在拦截器运行前自动装箱、返回后自动拆箱。`int` 以 `Integer` 传入，以此类推。
- **`void` 方法**：拦截器的返回值会被丢弃。
- **引用返回**：生成代码会插入 `CHECKCAST` 到声明类型，返回不兼容对象会抛 `ClassCastException`。

```java
// 在真正调用前改写参数
Echo proxy = AcceleratedProxy.proxy(Echo.class, (obj, method, args) -> {
    args[0] = "[" + args[0] + "]";
    return AcceleratedProxy.invokeSuper(obj, method, args);
});
```

## 异常

- 拦截器（或原方法）抛出的 **`RuntimeException`** 与 **`Error`** 原样传播。
- **受检异常**会被包装为 `java.lang.reflect.UndeclaredThrowableException`（生成的重写方法 不声明受检异常）。

如需拿到原始受检异常，请从 `UndeclaredThrowableException.getCause()` 解包。

下一章：[方法分组](05-method-grouping_cn.md)。

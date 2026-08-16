# 3. 快速开始

本章通过两个例子带你从零上手：一个具体类代理、一个接口代理。

## 类代理

```java
import io.github.lamspace.AcceleratedProxy;

public class Greeter {
    private String greeting = "Hello";

    public String hello(String name) {
        return greeting + ", " + name;
    }
}

Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
    System.out.println("调用前 " + method.getName());
    Object result = AcceleratedProxy.invokeSuper(obj, method, args);
    System.out.println("调用后 " + method.getName());
    return result;
});

String greeting = proxy.hello("World");
```

输出：

```
调用前 hello
调用后 hello
```

`greeting` 为 `"Hello, World"`。拦截器包住了真正的 `hello` 调用：`invokeSuper`
通过直接 `super` 调用重新分发到原方法。

## 接口代理

```java
public interface Calculator {
    int add(int a, int b);
}

Calculator calc = AcceleratedProxy.proxy(Calculator.class, (obj, method, args) -> {
    System.out.println("正在调用 " + method.getName());
    return (int) args[0] + (int) args[1];
});

int result = calc.add(10, 20);
```

输出：

```
正在调用 add
```

`result` 为 `30`。接口没有父类可调——拦截器本身就是实现（你也可以返回预设值、记日志或 委托到任何地方）。

## 关键点

- 同一个 `AcceleratedProxy.proxy(...)` 入口同时处理两种场景。
- 目标类型从参数自动推导—— **无需转型**。
- 类代理场景下，调用 `AcceleratedProxy.invokeSuper(obj, method, args)` 执行原方法；省略它 即可短路。

下一章：[拦截器 API](04-interceptor-api_cn.md)。

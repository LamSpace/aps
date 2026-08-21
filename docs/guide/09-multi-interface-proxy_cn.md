# 9. 多接口代理

一个代理对象可以同时实现多个接口。

```java
Object p = AcceleratedProxy.proxy(
        new Class<?>[]{Greeter.class, Auditable.class},
        (obj, method, args) -> {
            System.out.println("正在调用 " + method.getName());
            return null;
        });

Greeter g = (Greeter) p;     // 一个对象，多个接口视角
Auditable a = (Auditable) p;
```

## 方法合并与冲突

接口之间常共享方法（如共有的 `getName()`）。OpenProxy 在创建时解析合并、去重后的方法集：

- 相同签名 + 相同返回类型 → **合并**为一份实现。
- 相同签名 + **不同**返回类型 → `IllegalArgumentException`。
- 两个来自不同接口的 `default` 实现 → `IllegalArgumentException`。
- 一个 `default` + 多个抽象声明 → 合并，并调用该 `default`。

## 非 public 接口

支持包级私有接口：生成类被定义在非 public 接口共享的包内，从而能实现它们。

```java
// 你包内的包级私有接口
interface SecretService {
    String greet(String name);

    default String shout(String s) { return s.toUpperCase(); }
}

SecretService proxy = AcceleratedProxy.proxy(SecretService.class,
        (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));

proxy.greet("world");   // 经拦截器路由
proxy.shout("hi");      // invokeSuper 调用默认实现
```

约束：

- 所有非 public 接口必须位于同一包（否则抛 `IllegalArgumentException`）；public 接口可在 任意包。
- 当所有接口都是 public 时，生成类位于 `io.github.lamspace` 包——public JDK 接口（如
  `java.util.function.Function`）照常可用。

下一章：[热加载 / 热替换](10-hot-reload_cn.md)。

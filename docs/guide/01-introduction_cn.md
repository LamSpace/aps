# 1. 简介

OpenProxy是一个高性能 Java 动态代理库。它在运行时用 ASM 生成代理类，把每个被拦截的方法调用路由到一个 hashCode 驱动的 `dispatch()` 开关，开关里 直接生成 `INVOKESPECIAL super.method(args)` —— **热路径上零反射、零 MethodHandle**。

OpenProxy 类代理在有实际工作的场景中比 CGLib 快约 3–5×，接口代理与 `java.lang.reflect.Proxy` 持平，默认方法比 JDK 快约 6×。

## 核心亮点

| 亮点                | 对你的意义                                                   |
|---------------------|--------------------------------------------------------------|
| **零开销父类调度**  | `invokeSuper` 编译为直接 `super.method()` 调用，JIT 可内联   |
| **统一 API**        | `OpenProxy.proxy(...)` 同时支持类和接口               |
| **GC 安全的类加载** | 代理类使用 `Lookup.defineHiddenClass()`，无 ClassLoader 泄漏 |
| **函数式 API**      | `Interceptor` 是单方法接口，可直接用 lambda                  |
| **选择性拦截**      | `Group.of(...)` 只拦截你指定的方法，其余零开销透传           |
| **无需手动转型**    | `proxy(MyClass.class, i)` 通过泛型推导直接返回 `MyClass`     |
| **高级钩子**        | 构造器拦截、静态方法遮蔽、注解驱动匹配、热替换               |

## 一览

```java
Greeter proxy = OpenProxy.proxy(Greeter.class, (obj, method, args) -> {
    System.out.println("调用前 " + method.getName());
    Object result = OpenProxy.invokeSuper(obj, method, args);
    System.out.println("调用后 " + method.getName());
    return result;
});

String greeting = proxy.hello("World");
// 调用前 hello
// 调用后 hello
// greeting == "Hello, World"
```

## 工作原理（30 秒版）

1. `OpenProxy.proxy(...)` 构建 `Group` 链，把每个可代理方法匹配到拦截器（先匹配先胜出）。
2. 生成器（类代理用 `ClassGenerator`，接口代理用 `InterfaceGenerator`）生成字节码：每个去重后的 拦截器一个 `_interceptor$N` 字段、每个方法一个重写、一个 `dispatch(Method, Object[])` 方法。
3. 用 `defineHiddenClass` 定义生成类并实例化。
4. 每次调用时，重写方法装箱参数、调用 `Interceptor.intercept(proxy, method, args)`、拆箱返回值。 若拦截器调用 `invokeSuper`，则 `dispatch()` 按 `method.hashCode()` 分支，直接跳转
   `INVOKESPECIAL super.method(...)`。

## OpenProxy vs 其他方案

|                   | OpenProxy                  | CGLib                       | `java.lang.reflect.Proxy` |
|-------------------|----------------------|-----------------------------|---------------------------|
| 代理具体类        | ✅                   | ✅                          | ❌（仅接口）              |
| 父类调用机制      | 直接 `INVOKESPECIAL` | `MethodProxy` + `FastClass` | 不适用                    |
| GC 安全（隐藏类） | ✅                   | ❌                          | ✅                        |
| 选择性拦截        | ✅ `Group.of`        | ✅ `CallbackFilter`         | ❌ 全有或全无             |
| 构造器拦截        | ✅                   | ✅                          | ❌                        |
| 静态方法代理      | ✅                   | ❌                          | ❌                        |
| 函数式 API        | ✅ lambda            | ✅                          | ✅                        |
| Java 25+          | ✅                   | ✅（受限）                  | ✅                        |

下一章：[安装](02-installation_cn.md)。

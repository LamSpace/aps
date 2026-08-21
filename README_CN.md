# 🚀 OpenProxy

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-25%2B-orange)](https://jdk.java.net/)
[![JMH](https://img.shields.io/badge/benchmark-JMH%201.37-red)](https://github.com/openjdk/jmh)

[English](README.md) | [用户指导](docs/guide/README_CN.md) | [基准测试报告](docs/benchmark-results_cn.md)

一个高性能 Java 动态代理库。OpenProxy 在运行时用 ASM 生成代理类，通过 **hashCode 驱动的开关 + 直接 `INVOKESPECIAL` 父类调用** 分发每次拦截——无反射、无 `MethodHandle`、JIT 可内联。 接口代理与 `java.lang.reflect.Proxy` 持平，默认方法快约 6.5×。

## ✨ 为什么选择 OpenProxy

- **直接 `super` 调度** — `invokeSuper` 编译为直接 `super.method(args)`；无反射、无
  `MethodHandle`、JIT 可内联。
- **类代理比 CGLib 快约 3–5×**；接口代理与 `java.lang.reflect.Proxy` 持平，默认方法快约 6×。
- **类与接口统一 API** — `AcceleratedProxy.proxy(...)` 泛型自动推导，无需转型。
- **GC 安全** — 代理类使用 `Lookup.defineHiddenClass()`，无 `ClassLoader` 泄漏。

## 📋 特性

**核心**

- 统一入口 `AcceleratedProxy.proxy(target, interceptor)` 同时支持类和接口
- 函数式 `Interceptor` API —— 单方法接口，直接用 lambda
- `invokeSuper(proxy, method, args)` 实现零开销父类调度
- `WeakCache` 代理类缓存，以「方法 → 拦截器」映射为键

**选择性拦截**

- `Group.of(predicate, interceptor)` + `Group.otherwise(...)` —— 先匹配先胜出，热路径零开销
- 未匹配任何组的方法以 **零**拦截成本透传

**代理能力**

- **接口代理** — 运行时生成接口实现，无反射
- **多接口代理** — 一个对象实现多个接口，带冲突检测
- **非 public 接口代理** — 包级私有接口，定义在接口自身包内
- **构造参数** — 代理无默认构造方法的类
- **构造器拦截** — `ConstructorInterceptor` 钩子在父类构造器前后运行，支持参数改写与否决
- **静态方法代理** — `proxyStatic` 返回遮蔽 `public static` 方法的类
- **注解驱动 API** — `@Intercept` / `@Around` 声明式匹配，lambda 级速度
- **热加载/热替换** — `evict` / `evictClassLoader` 用于热部署类，`rebind` 原地替换拦截器

## ⚡ 快速开始

### 类代理

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
    System.out.println("调用前 " + method.getName());
    Object result = AcceleratedProxy.invokeSuper(obj, method, args);
    System.out.println("调用后 " + method.getName());
    return result;
});

String greeting = proxy.hello("World");
// 调用前 hello
// 调用后 hello
// greeting == "Hello, World"
```

### 接口代理

```java
Calculator calc = AcceleratedProxy.proxy(Calculator.class, (obj, method, args) -> {
    System.out.println("正在调用 " + method.getName());
    return (int) args[0] + (int) args[1];
});

int result = calc.add(10, 20);   // 30
```

### 方法分组

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
        Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
        Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
        Group.otherwise(fallbackInterceptor));
```

### 注解驱动

```java

@Intercept
class MetricsInterceptor {
    @Around("get*")
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
```

## 📊 性能

JMH 基准测试 | Java 25（单位 ns/op，越低越好）。完整表格、方法与运行说明见
[docs/benchmark-results_cn.md](docs/benchmark-results_cn.md)。

- **类代理比 CGLib 快约 3–5×**（有实际工作的场景），未匹配方法达到直接调用速度。
- **接口代理** 与 `java.lang.reflect.Proxy` 持平，默认方法快约 **6×**。
- **多拦截器（`Group`）** 热路径与单拦截器 API 字节级一致 —— **零**退化。
- **注解驱动** 拦截达到手写 lambda 平价。

## 🏗️ 工作原理

1. `AcceleratedProxy.proxy(...)` 通过 `Group` 链把每个可代理方法匹配到拦截器。
2. 生成器生成字节码：每个去重拦截器一个 `_interceptor$N` 字段、每个方法一个重写、一个
   `dispatch(Method, Object[])` 方法。
3. 每次调用时，重写方法装箱参数并调用 `Interceptor.intercept(...)`。若拦截器调用
   `invokeSuper`，`dispatch()` 按 `method.hashCode()` 分支，直接跳转
   `INVOKESPECIAL super.method(...)`。

核心洞察：调度使用确定性的 `Method.hashCode()` 构建 if-else 分支链，分支即 **直接 `super`
调用**——无反射、无 `MethodHandle`、完全 JIT 可内联。完整流程见
[用户指导](docs/guide/README_CN.md)。

## 📋 环境要求

- Java 25+
- ASM 9.7.1（编译依赖）

## 🧩 JPMS / 强封装模块

类代理通过 `MethodHandles.privateLookupIn` 定义在目标类所在包内。若目标位于强封装模块 （任何未 `open` 的包，含 `java.util` 等 `java.base` 包），`proxy()` 会快速失败并给出可操作的
`--add-opens` 提示。接口代理使用公共 lookup，仅支持 `public` 接口（与
`java.lang.reflect.Proxy` 一致）。详见 [JPMS](docs/guide/11-jpms_cn.md)。

## 📦 安装

```bash
git clone https://github.com/lamspace/openproxy.git
cd openproxy
mvn install -DskipTests
```

Maven Central 发布进行中；在此之前，请从本地仓库引用：

```xml

<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>openproxy</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 🆚 OpenProxy vs 其他方案

| 特性              | OpenProxy                  | CGLib                       | `java.lang.reflect.Proxy` |
|-------------------|----------------------|-----------------------------|---------------------------|
| 代理具体类        | ✅                   | ✅                          | ❌                        |
| 父类调用机制      | 直接 `INVOKESPECIAL` | `MethodProxy` + `FastClass` | 不适用                    |
| GC 安全（隐藏类） | ✅                   | ❌                          | ✅                        |
| 选择性拦截        | ✅ `Group.of`        | ✅ `CallbackFilter`         | ❌                        |
| 多接口代理        | ✅                   | ❌                          | ✅                        |
| 构造器拦截        | ✅                   | ✅                          | ❌                        |
| 静态方法代理      | ✅                   | ❌                          | ❌                        |
| 热加载 / rebind   | ✅                   | ❌                          | ❌                        |
| 注解驱动 API      | ✅                   | ❌                          | ❌                        |
| 函数式 API        | ✅ lambda            | ✅                          | ✅                        |
| Maven Central     | 即将上线             | ✅                          | 内置                      |

## 📖 文档

- [用户指导](docs/guide/README_CN.md) — 13 章，含可运行示例
- [基准测试报告（中文）](docs/benchmark-results_cn.md) / [English](docs/benchmark-results.md)
- [迁移指南](docs/migration-guide.md)

## 📄 许可证

Apache License 2.0

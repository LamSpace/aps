# 🚀 APS — 加速代理解决方案

高性能 Java 动态代理库，基于哈希码调度 + 直接 `INVOKESPECIAL` 父类调用，实现近零拦截开销，可作为 CGLib 的替代方案。

## ✨ 特性

- **零开销父类调度** — hashCode 驱动的 `dispatch()` 开关直接调用 `super.method(args)`；无 MethodHandle、无反射、JIT 可内联
- **统一 API** — 单一入口 `APS.proxy(target, interceptor)` 同时支持类和接口
- **接口代理支持** — 运行时生成接口实现，性能与 `java.lang.reflect.Proxy` 接近持平
- **无 ClassLoader 泄漏** — 使用 `Lookup.defineHiddenClass()`，代理类在无引用时可被 GC 回收
- **一行代码** — `APS.proxy(MyClass.class, interceptor)` 泛型自动推导，无需手动转型
- **零开销过滤** — `ClassFilter` 排除的方法直接调用父类，无任何拦截开销
- **构造参数支持** — 支持代理无默认构造方法的类

## ⚡ 快速开始

### 类代理

```java
Greeter proxy = APS.proxy(Greeter.class, (obj, method, args) -> {
    System.out.println("调用前 " + method.getName());
    Object result = APS.invokeSuper(obj, method, args);
    System.out.println("调用后 " + method.getName());
    return result;
});

String greeting = proxy.hello("World");
// 输出: 调用前 hello
// 输出: 调用后 hello
// greeting = "Hello, World"
```

### 接口代理

```java
Calculator calc = APS.proxy(Calculator.class, (obj, method, args) -> {
    System.out.println("正在调用 " + method.getName());
    // 实现自定义逻辑，或返回预设值
    return 42;
});

int result = calc.add(10, 20);
// 输出: 正在调用 add
// result = 42
```

## 📊 性能

JMH 基准测试 | Java 25 | 4 种实现 × 6 个场景。每行最优结果 **加粗**标注。

### 类代理

| 场景      | 直接调用 | APS       | CGLib | Java Proxy |
|-----------|----------|-----------|-------|------------|
| 空操作    | 5.72     | 1.32      | 1.06  | **1.05**   |
| 透传      | 5.66     | **5.69**  | 13.87 | 5.79       |
| 参数修改  | 5.69     | **6.11**  | 19.11 | 33.73      |
| 原语返回  | 0.67     | 2.08      | 12.58 | **1.83**   |
| void 方法 | 0.66     | 2.34      | 3.68  | **1.64**   |
| 多参数    | 58.40    | **58.76** | 71.32 | 59.30      |

*Java Proxy 无法代理类——它代理的是接口，通过 `Method.invoke()` 反射调用类实例；保留此列仅作参考。*

### 接口代理

| 场景      | APS      | Java Proxy |
|-----------|----------|------------|
| 空操作    | 1.33     | **1.05**   |
| 透传      | **5.69** | 5.77       |
| 参数修改  | 5.30     | **5.29**   |
| 原语返回  | 1.32     | **1.05**   |
| void 方法 | 1.30     | **1.05**   |
| 多参数    | 80.50    | **80.09**  |

*单位: ns/op，越低越好。完整报告：[docs/benchmark-results_cn.md](docs/benchmark-results_cn.md)*  
*Java Proxy = `java.lang.reflect.Proxy`（JDK 内置动态代理）*

APS 在拦截器中有实际工作发生的最真实场景（透传、参数修改、多参数）中类代理性能领先，接口代理性能与 `java.lang.reflect.Proxy` 接近持平。

## 📋 环境要求

- Java 24+
- ASM 9.7.1（编译依赖）

## 📦 安装

### 源码构建

```bash
git clone https://github.com/lamspace/aps.git
cd aps
mvn install -DskipTests
```

### Maven（即将上线）

```xml

<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>aps</artifactId>
    <version>1.0-SNAPSHOT</version>
</dependency>
```

Maven Central 发布已列入[路线图](docs/aps-future-roadmap.md)。

## 🆚 APS vs CGLib

| 特性               | APS                              | CGLib                       |
|--------------------|----------------------------------|-----------------------------|
| 调度机制           | hashCode 开关 + `INVOKESPECIAL`  | 生成字节码                  |
| 父类调用开销       | 零（直接 `super.method()`）      | MethodProxy + FastClass     |
| 类加载             | `defineHiddenClass()`（GC 安全） | 自定义 ClassLoader          |
| API 风格           | 函数式（`Interceptor` lambda）   | 回调 + MethodProxy          |
| 接口代理           | 支持（`APS.proxy()`）            | 不支持（需 Objenesis）      |
| 原语装箱           | 自动                             | 自动                        |
| 异常传播           | 受检异常 → `UndeclaredThrowable` | 受检异常 → InvocationTarget |
| 无默认构造方法支持 | 支持                             | 支持                        |
| final 类/方法代理  | 不支持（JVM 限制）               | 不支持（JVM 限制）          |
| Maven Central      | 规划中                           | 已有                        |

## 🆚 APS vs Java Proxy

| 特性           | APS                              | `java.lang.reflect.Proxy`        |
|----------------|----------------------------------|----------------------------------|
| 代理目标       | 类**和**接口                     | 仅接口                           |
| 调度机制       | hashCode 开关 + `INVOKESPECIAL`  | 生成字节码 + `InvocationHandler` |
| 父类调用开销   | 零（直接 `super.method()`）      | 不适用（仅接口）                 |
| 类加载         | `defineHiddenClass()`（GC 安全） | `defineClass` + 代理缓存         |
| API 风格       | 函数式（`Interceptor` lambda）   | `InvocationHandler`（单方法）    |
| 选择性拦截     | `ClassFilter` 按方法             | 全部或无                         |
| 异常传播       | 受检异常 → `UndeclaredThrowable` | 受检异常 → `InvocationTarget`    |
| 构造参数（类） | 支持                             | 不适用（仅接口）                 |
| 类代理性能     | ~5.69 ns 透传（直接调用级别）    | 不适用（无法代理类）             |
| 接口代理性能   | 接近持平（~0.25ns 差距）         | 略优（JIT 内在优化）             |
| 依赖           | 第三方（APS + ASM）              | JDK 内置                         |

## 🔄 从 CGLib 迁移

请参阅 [docs/migration-guide.md](docs/migration-guide.md) 了解从 CGLib 和 `java.lang.reflect.Proxy` 的分步迁移指南。

## 📖 文档

- [基准测试报告（中文）](docs/benchmark-results_cn.md)
- [基准测试报告（英文）](docs/benchmark-results.md)
- [迁移指南](docs/migration-guide.md)
- [设计文档](docs/superpowers/specs/2026-08-02-aps-unified-proxy-design.md)
- [未来路线图](docs/aps-future-roadmap.md)

## 📄 许可证

Apache License 2.0

# 🚀 APS — 加速代理解决方案

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-25%2B-orange)](https://jdk.java.net/)
[![JMH](https://img.shields.io/badge/benchmark-JMH%201.37-red)](https://github.com/openjdk/jmh)

[English](README.md) | [基准测试报告](docs/benchmark-results_cn.md)

高性能 Java 动态代理库，基于哈希码调度 + 直接 `INVOKESPECIAL` 父类调用，实现近零拦截开销，可作为 CGLib 的替代方案。

## ✨ 特性

- **零开销父类调度** — hashCode 驱动的 `dispatch()` 开关直接调用 `super.method(args)`；无 MethodHandle、无反射、JIT 可内联
- **统一 API** — 单一入口 `AcceleratedProxy.proxy(target, interceptor)` 同时支持类和接口
- **接口代理支持** — 运行时生成接口实现，无反射
- **无 ClassLoader 泄漏** — 使用 `Lookup.defineHiddenClass()`，代理类在无引用时可被 GC 回收
- **一行代码** — `AcceleratedProxy.proxy(MyClass.class, interceptor)` 泛型自动推导，无需手动转型
- **多拦截器 / 方法分组** — 通过 `Group.of()` 将不同方法族绑定到不同 `Interceptor`，先匹配先胜出，热路径零开销
- **多接口代理** — `AcceleratedProxy.proxy(new Class<?>[]{...}, interceptor)` 在一个代理对象中实现多个接口
- **注解驱动 API** — 声明式 `@Intercept`/`@Around` 方法匹配，运行时落到同一条 `Group` 管线
- **零开销透传** — 未匹配任何 Group 的方法直接调用父类，无任何拦截开销
- **构造参数支持** — 支持代理无默认构造方法的类
- **构造器拦截** — `ConstructorInterceptor` 钩子在父类构造器前后运行，支持参数改写与否决
- **静态方法代理** — `AcceleratedProxy.proxyStatic(target, ...)` 返回一个生成类，用同一 `Interceptor` 遮蔽目标类的 `public static` 方法（反射或 `MethodHandle` 调用）

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
// 输出: 调用前 hello
// 输出: 调用后 hello
// greeting = "Hello, World"
```

### 接口代理

```java
Calculator calc = AcceleratedProxy.proxy(Calculator.class, (obj, method, args) -> {
    System.out.println("正在调用 " + method.getName());
    // 实现自定义逻辑，或返回预设值
    return 42;
});

int result = calc.add(10, 20);
// 输出: 正在调用 add
// result = 42
```

### 多拦截器（方法分组）

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
                AcceleratedProxy.invokeSuper(obj, method, args))
);

String s = proxy.getGreeting(); // [GET] getGreeting → "hello"
proxy.

setGreeting("hi");        // [SET] setGreeting
proxy.

toString();                // 透传：不触发拦截
```

### 多接口代理

```java
Object p = AcceleratedProxy.proxy(new Class<?>[]{Greeter.class, Auditable.class},
        (obj, method, args) -> {
            System.out.println("正在调用 " + method.getName());
            return null;
        });
Greeter g = (Greeter) p;   // 一个对象，多个接口视角
Auditable a = (Auditable) p;
```

### 注解驱动 API

```java
@Intercept
class MetricsInterceptor {
    @Around("get*")
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
String s = proxy.getGreeting(); // 经由 measure() 路由
```

### 构造器拦截

```java
ConstructorInterceptor ctorInterceptor = new ConstructorInterceptor() {
    public Object[] before(Constructor<?> ctor, Object[] args) {
        System.out.println("before " + ctor.getName());
        return args; // 可改写构造参数
    }
    public void after(Object proxy, Constructor<?> ctor, Object[] args) {
        System.out.println("after " + ctor.getName());
    }
};

Greeter proxy = AcceleratedProxy.proxy(Greeter.class, interceptor, ctorInterceptor);
```

`before` 在父类构造器之前运行（可改写参数或抛异常否决）；`after` 在父类构造器之后、实例完全初始化后运行。构造器拦截仅支持类代理。

### 静态方法代理

```java
Class<?> proxyClass = AcceleratedProxy.proxyStatic(Utils.class,
        (proxy, method, args) -> {
            System.out.println("正在调用 " + method.getName());
            return method.invoke(null, args);   // 调用原静态方法
        });

// 静态方法编译期绑定 —— 需对返回的类反射调用遮蔽方法：
int result = (Integer) proxyClass.getMethod("add", int.class, int.class)
        .invoke(null, 2, 3);
```

*静态方法在编译期绑定，`Utils.add(...)` 仍调用原方法；只有对返回的类（反射或 `MethodHandle`）调用才会经过拦截器。*

## 📊 性能

JMH 基准测试 | Java 25。每行最优结果 **加粗**标注。  
*Java Proxy 无法代理类；保留此列仅作参考（代理接口，通过反射委托）。*

### 类代理（摘要）

| 场景          | 直接调用  | APS      | CGLib    |
|---------------|-----------|----------|----------|
| int 返回值    | **0.66**  | 1.83     | 12.36    |
| String 返回值 | **4.68**  | 4.71     | 19.89    |
| void 返回值   | **0.65**  | 3.94     | 3.72     |
| 0 参数透传    | **0.66**  | 2.11     | 3.96     |
| 4 参数透传    | **56.34** | 61.32    | 71.38    |
| 空操作        | —         | 1.32     | **1.05** |
| 透传          | —         | **4.76** | 14.01    |
| 参数修改      | —         | **5.33** | 18.69    |

### 接口代理（摘要）

| 场景          | APS      | Java Proxy |
|---------------|----------|------------|
| int 返回值    | 2.58     | **1.03**   |
| String 返回值 | 6.23     | **5.20**   |
| void 返回值   | 3.11     | **1.03**   |
| 空操作        | 1.30     | **1.03**   |
| 透传          | **4.61** | 4.65       |
| 参数修改      | 5.41     | **5.41**   |

*单位: ns/op，越低越好。完整报告：[docs/benchmark-results_cn.md](docs/benchmark-results_cn.md)*

### 第二阶段：多拦截器（零开销）

| 场景              | Group API | 旧版 API | 结论           |
|-------------------|-----------|----------|----------------|
| getter（类代理）  | 3.05 ns   | 3.08 ns  | ±1.1%（持平）  |
| passthrough（类） | 4.99 ns   | 5.07 ns  | 与直接调用一致 |
| getter（接口）    | 2.18 ns   | 2.19 ns  | ±0.7%（持平）  |

*基于 Group 的多拦截器热路径与单 Interceptor 字节码完全等价——零性能退化。*

## 🏗️ 工作原理

### 1. 代理类生成流程

下图展示了 APS 如何在运行时生成动态代理类 —— 从调用 `AcceleratedProxy.proxy()` 到返回就绪的代理实例。

```mermaid
flowchart TD
    A["❶ AcceleratedProxy.proxy(target, groups...)"]
    A --> A1["❷ Group 链匹配: 先匹配先胜出"]
    A1 --> B{"target.isInterface()?"}
    B -->|" ✅ 是接口 "| C["❸ InterfaceGenerator(target, interceptors[], mapping)"]
    B -->|" ❌ 是类 "| D["❸ ClassGenerator(target, interceptors[], mapping, constructorArgs)"]
    C --> E["❸ InterfaceGenerator.generate()"]
    D --> F["❸ ClassGenerator.generate()"]
    E --> G["ASM ClassWriter 初始化"]
    G --> H["定义类: extends Object<br/>implements Target, DispatchTarget"]
    H --> I["生成 _interceptor$N 字段<br/>(每个去重后的 Interceptor 一个)"]
    I --> J["生成构造函数 &lt;init&gt;<br/>存储 Interceptor 引用"]
    J --> K["遍历接口方法"]
    K --> L{"Group 链匹配<br/>先匹配先胜出"}
    L -->|" ✓ 匹配 "| M["分配到 Interceptor _iN<br/>生成方法实现"]
    L -->|" ✗ 未匹配 "| N["生成方法实现<br/>throw AbstractMethodError"]
    M --> O["将 Method 信息注册到 ClinitRegistry"]
    N --> P{"还有未处理方法?"}
    O --> P
    P -->|" 是 "| K
    P -->|" 否 "| Q["❹ Drain ClinitRegistry → MethodInfo 列表"]
    Q --> R["❺ 生成 dispatch(Method, Object[]) 方法<br/>hashCode 驱动的 if-else 分支链"]
    R --> S["❻ 生成 &lt;clinit&gt; 静态初始化块<br/>反射加载 java.lang.reflect.Method 对象"]
    S --> T["❼ ClassWriter.toByteArray() → byte[]"]
    F --> G2["ASM ClassWriter 初始化"]
    G2 --> H2["定义类: extends TargetClass<br/>implements DispatchTarget"]
    H2 --> I2["生成 _interceptor$N 字段<br/>(每个去重后的 Interceptor 一个)"]
    I2 --> J2["查找匹配的 super 构造函数"]
    J2 --> K2["生成构造函数 &lt;init&gt;<br/>super(constructorArgs) + 存储 Interceptor"]
    K2 --> L2["遍历 targetClass 声明的非 final/非 static 方法"]
    L2 --> M2{"Group 链匹配<br/>先匹配先胜出"}
    M2 -->|" ✓ 匹配 "| N2["分配到 Interceptor _iN<br/>生成 override 方法体"]
    M2 -->|" ✗ 未匹配 "| O2["生成 override 方法体<br/>直接 super.method() 零开销"]
    N2 --> P2["注册到 ClinitRegistry"]
    O2 --> Q2{"还有未处理方法?"}
    P2 --> Q2
    Q2 -->|" 是 "| L2
    Q2 -->|" 否 "| R2["❹ Drain ClinitRegistry → MethodInfo 列表"]
    R2 --> S2["❺ 生成 dispatch(Method, Object[]) 方法<br/>hashCode 驱动 if-else → INVOKESPECIAL super 调用"]
    S2 --> T2["❻ 生成 &lt;clinit&gt; 静态初始化块"]
    T2 --> T
    T --> U{"target.isInterface()?"}
    U -->|" 是接口 "| V["❽ APS 自身 Lookup<br/>defineHiddenClass(bytecode, true)"]
    U -->|" 是类 "| W["❽ LookupManager 获取<br/>目标包访问权限 Lookup<br/>defineHiddenClass(bytecode, true)"]
    V --> X["❾ 反射获取构造函数"]
    W --> X
    X --> Y["❿ Constructor.newInstance<br/>接口: (interceptor)<br/>类: (interceptor, constructorArgs...)"]
    Y --> Z["🎯 返回代理实例"]
```

### 2. 方法调用流程

对代理实例发起方法调用时，以下流程被执行 —— 从生成的字节码出发，经过用户拦截器逻辑，最终返回结果。

```mermaid
flowchart TD
    A["❶ 调用代理方法<br/>proxy.someMethod(arg1, arg2)"]
    A --> B["❷ 进入生成的 override 方法体"]
    B --> C["❸ 参数装箱<br/>基本类型 → 包装类型<br/>Object[] args = new Object[]{arg1, arg2, ...}"]
    C --> D["❹ 调用 Interceptor.intercept(proxy, method, args)<br/>this._interceptor$N.intercept(this, _method, args)"]
    D --> E["🔵 用户自定义 Interceptor 逻辑"]
    E --> F{"需要调用父类方法?"}
    F -->|" 是 "| G["❺ AcceleratedProxy.invokeSuper(proxy, method, args)"]
    F -->|" 否 "| H["返回自定义结果"]
    G --> I["❻ ((DispatchTarget) proxy).dispatch(method, args)"]
    I --> J["❼ 计算 method.hashCode()"]
    J --> K["❽ hashCode 驱动的 if-else 分支链<br/>逐条比对: hash == METHOD_N_HASH ?"]
    K --> L["❾ 命中分支 → 参数拆箱<br/>从 Object[] 取出并 unbox 为原始类型"]
    L --> M["❿ INVOKESPECIAL super.method(args...)<br/>直接字节码级父类调用<br/>零反射、零 MethodHandle"]
    M --> N["⓫ 返回值装箱 (如需要)<br/>基本类型 → 包装类型"]
    N --> H
    H --> O["⓬ 返回值拆箱 & 类型检查<br/>包装类型 → 基本类型 (if needed)<br/>CHECKCAST 引用类型"]
    O --> P{"发生异常?"}
    P -->|" RuntimeException "| Q["直接 rethrow"]
    P -->|" Error "| R["直接 rethrow"]
    P -->|" Checked Exception "| S["包装为 UndeclaredThrowableException<br/>并 throw"]
    P -->|" 无异常 "| T["🎯 返回结果给调用方"]
    Q --> U["调用方捕获异常"]
    R --> U
    S --> U
```

> **核心洞察：** `dispatch()` 方法使用编译期预计算的 `Method.hashCode()`（确定性计算：`declaringClass.hashCode() XOR methodName.hashCode()`）构建 if-else 分支链。每个分支直接生成 `INVOKESPECIAL super.method(args...)` —— 调度时 **零反射**、 **零 MethodHandle** 开销。JIT 编译器可直接内联这些父类调用，实现接近原生的调用性能。

## 📋 环境要求

- Java 25+
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
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

Maven Central 发布已列入[路线图](docs/aps-future-roadmap.md)。

## 🆚 APS vs CGLib

| 特性               | APS                                | CGLib                       |
|--------------------|------------------------------------|-----------------------------|
| 调度机制           | hashCode 开关 + `INVOKESPECIAL`    | 生成字节码                  |
| 父类调用开销       | 零（直接 `super.method()`）        | MethodProxy + FastClass     |
| 类加载             | `defineHiddenClass()`（GC 安全）   | 自定义 ClassLoader          |
| API 风格           | 函数式（`Interceptor` lambda）     | 回调 + MethodProxy          |
| 接口代理           | 支持（`AcceleratedProxy.proxy()`） | 不支持（需 Objenesis）      |
| 原语装箱           | 自动                               | 自动                        |
| 异常传播           | 受检异常 → `UndeclaredThrowable`   | 受检异常 → InvocationTarget |
| 无默认构造方法支持 | 支持                               | 支持                        |
| final 类/方法代理  | 不支持（JVM 限制）                 | 不支持（JVM 限制）          |
| Maven Central      | 规划中                             | 已有                        |

## 🆚 APS vs Java Proxy

| 特性           | APS                              | `java.lang.reflect.Proxy`        |
|----------------|----------------------------------|----------------------------------|
| 代理目标       | 类**和**接口                     | 仅接口                           |
| 多接口         | 支持（`Class<?>[]`）             | 支持（`Class<?>[]`）             |
| 调度机制       | hashCode 开关 + `INVOKESPECIAL`  | 生成字节码 + `InvocationHandler` |
| 父类调用开销   | 零（直接 `super.method()`）      | 不适用（仅接口）                 |
| 类加载         | `defineHiddenClass()`（GC 安全） | `defineClass` + 代理缓存         |
| API 风格       | 函数式（`Interceptor` lambda）   | `InvocationHandler`（单方法）    |
| 选择性拦截     | `Group.of()` 按方法族            | 全部或无                         |
| 异常传播       | 受检异常 → `UndeclaredThrowable` | 受检异常 → `InvocationTarget`    |
| 构造参数（类） | 支持                             | 不适用（仅接口）                 |
| 类代理性能     | ~4.76 ns 透传（直接调用级别）    | 不适用（无法代理类）             |
| 接口代理性能   | 无反射；字符串密集场景持平       | 轻量级场景更快（JIT 内在优化）   |
| 依赖           | 第三方（APS + ASM）              | JDK 内置                         |

## 🔄 从 CGLib 迁移

请参阅 [docs/migration-guide.md](docs/migration-guide.md) 了解从 CGLib 和 `java.lang.reflect.Proxy` 的分步迁移指南。

## 📖 文档

- [基准测试报告（中文）](docs/benchmark-results_cn.md)
- [基准测试报告（英文）](docs/benchmark-results.md)
- [迁移指南](docs/migration-guide.md)
- [APS vs CGLib/Java Proxy（设计 spec）](docs/superpowers/specs/2026-08-02-aps-unified-proxy-design.md)
- [多拦截器设计文档](docs/superpowers/specs/2026-08-09-multi-interceptor-method-grouping-design.md)
- [注解驱动 API 设计文档](docs/superpowers/specs/2026-08-15-annotation-driven-api-design.md)
- [未来路线图](docs/aps-future-roadmap.md)

## 📄 许可证

Apache License 2.0

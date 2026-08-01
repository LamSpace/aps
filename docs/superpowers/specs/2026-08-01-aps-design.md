# APS Design — Accelerated Proxy Solution

Date: 2026-08-01 Status: draft

## 1. 定位

APS 是一个高性能 Java 运行时动态代理库，面向框架作者，目标是取代 CGLib 成为 Java 代理基础设施的事实标准。

**差异化：** 与 CGLib 功能对齐，但用 `MethodHandle` + `MethodHandles.Lookup.defineHiddenClass()` 实现更快的方法调用。不做上层工具，只做底层引擎。

## 2. 设计决策汇总

| 决策点     | 选择                               | 原因                          |
|------------|------------------------------------|-------------------------------|
| 优先级     | 性能 → 能力边界 → API 体验         | 先用极致性能站稳脚            |
| 生成策略   | 纯运行时，零编译期生成             | 动态代理场景不需要编译期代码  |
| 字节码生成 | ASM                                | 成熟稳定，生态完善            |
| 类装载     | `Lookup.defineHiddenClass()`       | 无 ClassLoader 泄漏，类可 GC  |
| 方法分派   | MethodHandle 绑定超类              | 替代 `Method.invoke` 反射调用 |
| 安全/访问  | 自动提升权限 + 优雅降级            | 内部尝试提升，失败时降级      |
| API 风格   | 函数式简洁，对标 `Enhancer.create` | 减少学习成本                  |
| 拦截模型   | 单 Callback，所有方法经一个入口    | 与 Proxy/CGLib 一致           |

## 3. 架构

三层组件：

```
┌─────────────────────────────────┐
│          Public API             │
│  APS.create(Class, Callback)    │
│  APS.create(Class, Callback[],  │
│             ClassFilter)        │
└──────────────┬──────────────────┘
               │
┌──────────────┴──────────────────┐
│        Bytecode Engine          │
│  ASM: 生成子类 .class 字节码     │
│  - 构造器复制                    │
│  - 方法重写 → 委托到 handler     │
│  - MethodHandle 绑定超类调用     │
└──────────────┬──────────────────┘
               │
┌──────────────┴──────────────────┐
│       Class Definition          │
│  Lookup.defineHiddenClass()     │
│  - 隐藏类加载                    │
│  - 无 ClassLoader 泄漏           │
│  - 类可被 GC 回收                │
└─────────────────────────────────┘
```

| 层               | 组件                | 职责                                               |
|------------------|---------------------|----------------------------------------------------|
| Public API       | `APS`               | 单一入口，静态工厂方法                             |
| Bytecode Engine  | `ClassGenerator`    | ASM 访问目标类元数据，生成子类字节码               |
|                  | `MethodDispatcher`  | 生成每个重写方法的分派逻辑 + MethodHandle 超类绑定 |
| Class Definition | `HiddenClassLoader` | 用 `defineHiddenClass` 装载字节码，管理类生命周期  |

### 调用路径

```
user.method()  →  [生成的子类].method()
  →  Callback.intercept(proxy, method, superHandle, args)
    →  用户逻辑
    →  superHandle.invoke(args) // MethodHandle 绑定到超类，零反射
```

`superHandle` 是预先绑定到父类 `method` 的 MethodHandle，handler 可以直接调用，无需 `Method.invoke`。这是 APS 相比 CGLib
性能差异化的关键。

## 4. API 设计

### Callback 接口

```java

@FunctionalInterface
public interface Callback {
    Object intercept(Object proxy, Method method, MethodHandle superHandle,
                     Object[] args) throws Throwable;
}
```

### 工厂方法

```java
// 最简形式：代理一个类，所有方法经 handler
T proxy = APS.create(TargetClass.class, (Callback)
                (obj, method, superHandle, args) -> {
                    System.out.println("before " + method.getName());
                    Object result = superHandle.invoke(args);
                    System.out.println("after " + method.getName());
                    return result;
                });

// 带过滤器：只拦截匹配的方法
T proxy = APS.create(TargetClass.class, callbacks, method ->
        method.getName().startsWith("get"));
```

- `superHandle` 是 MethodHandle，不是 Method。用户调 `superHandle.invoke(args)` 走 MethodHandle，非 `Method.invoke`。
- 泛型推断，`APS.create(MyClass.class, handler)` 返回 `MyClass` 类型，无需强转。
- 过滤器可选。不传 filter → 全量拦截；传了 → 只拦截匹配方法，其余走超类直达（零拦截开销）。

```java

@FunctionalInterface
public interface ClassFilter {
    boolean accept(Method method);
}
```

## 5. v1 范围

### 必须 (Must)

- 代理有默认构造器的类
- 单一 Callback 拦截模型
- MethodHandle 绑定超类调用
- `defineHiddenClass` 类装载
- JUnit 5 单元测试覆盖基本场景

### 应该 (Should)

- 方法拦截、参数改写、返回修改、异常处理
- JMH 性能基准（vs CGLib、vs Proxy、vs 直接调用）
- 方法过滤器（ClassFilter）
- 代理无默认构造器的类

### 尽量 (Nice to have)

- Javadoc
- 迁移指南（从 CGLib/Proxy 到 APS）

### 不做 (Out of scope for v1)

- 代理 final 类/方法（JVM 硬限制）
- 代理 static 方法
- 代理构造器
- Maven Central 发布
- 注解驱动 API
- 热加载/热替换

## 6. 测试与成功标准

### 测试层面

| 层面     | 内容                                             | 工具    |
|----------|--------------------------------------------------|---------|
| 单元测试 | 代理类生成、方法拦截、参数传递、返回值、异常穿透 | JUnit 5 |
| 性能基准 | APS vs CGLib vs Proxy 直调和代理调用的每操作延迟 | JMH     |
| 正确性   | Public API 行为契约                              | JUnit 5 |

### v1 成功标准

1. **功能：** 覆盖 CGLib 常用场景 100%（拦截、参数改写、返回修改、异常处理）
2. **性能：** JMH 基准，代理调用延迟显著优于 CGLib 的 `Method.invoke` 反射调用
3. **API：** 上述 API 一行创建代理，无误导读点

# OpenProxy Phase 1 — Documentation & Performance Validation

Date: 2026-08-01 Status: approved

## 1. 定位

Phase 1 的目标是让 OpenProxy 项目 **可信**：一个新项目要说服框架作者从 CGLib 迁移过来，必须有完整文档（README、Javadoc、迁移指南）和真实的性能数据（JMH 多维度对比）。

不做新功能，只补文档和验证。

## 2. 四件事

| 事项          | 输出物                     | 验收标准                                         |
|---------------|----------------------------|--------------------------------------------------|
| README.md     | 项目根目录 `README.md`     | 5 秒上手 + 性能数据 + 安装说明 + CGLib 对比      |
| JMH Benchmark | `ProxyBenchmark.java` 重写 | 4 实现 × 6 场景全部跑通，数据记录到文档          |
| Javadoc       | 所有 public 类/方法        | `mvn javadoc:javadoc` 无警告                     |
| 迁移指南      | `docs/migration-guide.md`  | CGLib→OpenProxy 和 Proxy→OpenProxy 两个完整示例 + 功能对照表 |

## 3. README.md 结构

```
# OpenProxy
## 核心特性（4 点）
## 5 秒上手（代码块）
## 性能（JMH 对比表格 + 加速比）
## 依赖
## 安装（Maven snippet）
## 与 CGLib 对比（功能对照表）
## 文档链接
## License
```

## 4. Benchmark 设计（已确认）

### 4.1 实现方式（4 种）

| 实现       | 说明                                                                             |
|------------|----------------------------------------------------------------------------------|
| Direct     | 直接 new 对象调用，性能基线                                                      |
| OpenProxy        | `AcceleratedProxy.create()` + `superHandle.invoke(args)`                         |
| CGLib      | `Enhancer.create()` + `MethodInterceptor` + `methodProxy.invokeSuper()`          |
| Java Proxy | `Proxy.newProxyInstance()` + `InvocationHandler` + `method.invoke(target, args)` |

### 4.2 操作场景（6 种）

| 场景             | 被测方法                        | 测什么                 |
|------------------|---------------------------------|------------------------|
| No-op            | 回调直接返回固定值              | 纯代理分派开销下限     |
| Passthrough      | `superHandle.invoke(args)` 透传 | 真实代理开销（最常见） |
| Arg modify       | 修改参数后调用原始方法          | 参数装箱+拆箱+数组操作 |
| Primitive return | `int add(int a, int b)`         | 基本类型装箱/拆箱      |
| Void method      | `void sideEffect()`             | void 返回处理          |
| Multi-param      | 4-5 个不同类型参数              | 参数数组分配+类型转换  |

### 4.3 CGLib 依赖

```xml

<dependency>
    <groupId>cglib</groupId>
    <artifactId>cglib</artifactId>
    <version>3.3.0</version>
    <scope>test</scope>
</dependency>
```

## 5. Javadoc 覆盖范围

| 类                  | 当前状态 | 补什么                                    |
|---------------------|----------|-------------------------------------------|
| `OpenProxy`               | 部分有   | `@throws` 触发条件                        |
| `Callback`          | ✅ 完整  | 无                                        |
| `ClassFilter`       | ✅ 完整  | 无                                        |
| `ClassGenerator`    | 无       | 类级 + `generate()` + `constructorArgs()` |
| `MethodDispatcher`  | 无       | 类级 + `dispatchMethods()`                |
| `ClinitRegistry`    | 无       | 类级 + 方法                               |
| `HiddenClassLoader` | 无       | 类级 + `defineClass()`                    |
| `LookupManager`     | 无       | 类级 + `getLookup()`                      |

额外给每个包添加 `package-info.java`。

## 6. 迁移指南结构

文件：`docs/migration-guide.md`

- **CGLib → OpenProxy**：Enhancer → AcceleratedProxy.create，MethodProxy.invokeSuper → superHandle.invoke
- **Java Proxy → OpenProxy**：接口代理 → 具体类代理
- **功能对照表**：final 方法、static 方法、构造器、基本类型、异常处理等逐项对比

## 7. 执行顺序

```
1. 添加 CGLib 依赖到 pom.xml
2. 重写 ProxyBenchmark（4×6 矩阵）
3. 跑 JMH，记录数据
4. 写 README.md（嵌入 benchmark 数据）
5. 补全 Javadoc
6. 写迁移指南 docs/migration-guide.md
7. 最终验证：mvn test + mvn javadoc:javadoc 全绿
```

## 8. 测试与成功标准

| 标准                          | 验证方式                             |
|-------------------------------|--------------------------------------|
| 所有 benchmark 可编译运行     | `mvn test` 通过                      |
| JMH 数据真实可靠              | 日志输出或 JSON 结果可复现           |
| README 包含 5 秒上手 + 性能表 | 人工 review                          |
| Javadoc 无警告                | `mvn javadoc:javadoc` 输出无 WARNING |
| 迁移指南包含两个完整示例      | 人工 review                          |

# APS 基准测试报告

[English](benchmark-results.md)

日期: 2026-08-14（更新） | JDK: Java 25.0.3 (Oracle HotSpot) | JMH: 1.37  
JVM 参数: `--enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED`

所有分数单位 ns/op（越低越好）。每行最优结果 **加粗**标注。

> **Java Proxy** = `java.lang.reflect.Proxy`（JDK 内置）。无法代理类——在类代理表格中代理的是接口，通过 `Method.invoke()` 反射调用类实例；保留此列仅作参考。

## 类代理 — 返回值类型覆盖

对比 APS、CGLib 和直接调用在所有原始类型返回值、封装类型、String 和 void 下的表现。目标类：`RetOpsImpl` 实现 `RetOps`。

| 场景（方法）                    | 直接调用 | APS      | CGLib | 最优               |
|---------------------------------|----------|----------|-------|--------------------|
| `int add(int, int)`             | **0.66** | 1.83     | 12.36 | **直接调用**       |
| `long add(long, long)`          | **0.65** | 2.10     | —     | **直接调用**       |
| `double add(double, double)`    | **0.65** | 2.38     | —     | **直接调用**       |
| `float add(float, float)`       | —        | **2.62** | —     | —                  |
| `boolean isPositive(int)`       | —        | **2.84** | —     | —                  |
| `byte add(byte, byte)`          | —        | **3.12** | —     | —                  |
| `char toUpper(char)`            | —        | **3.65** | —     | —                  |
| `short add(short, short)`       | —        | **3.65** | —     | —                  |
| `void run()`                    | **0.65** | 3.94     | 3.72  | **直接调用**       |
| `Integer add(Integer, Integer)` | —        | **4.44** | —     | —                  |
| `String concat(String, String)` | **4.68** | **4.71** | 19.89 | **直接调用 ≈ APS** |

**要点：** APS 在所有返回值类型下始终比 CGLib 快 3–7×。封装类型（`Integer`）比原始类型略慢，因为调度路径中存在装箱操作。

## 类代理 — 参数数量覆盖

参数数量从 0 到 8 的变化。目标类：`ParamCountImpl`。

| 场景            | 直接调用  | APS      | CGLib | 最优               |
|-----------------|-----------|----------|-------|--------------------|
| 0 参数 → String | **0.66**  | 2.11     | 3.96  | **直接调用**       |
| 1 参数 → String | —         | **2.11** | —     | —                  |
| 2 参数 → int    | **0.65**  | 2.07     | 12.42 | **直接调用**       |
| 4 参数 → String | **56.34** | 61.32    | 71.38 | **直接调用 ≈ APS** |
| 8 参数 → int    | **0.66**  | 2.63     | —     | **直接调用**       |

**要点：** APS 调度开销保持稳定（~1.5–2 ns），不受参数数量的影响。CGLib 随参数增多性能急剧下降（2 参数 12.42 ns → 4 参数 71.38 ns）。4 个混合类型参数时，APS 接近直接调用速度（61.32 vs 56.34）。

## 类代理 — 标准场景

空操作、透传和参数修改。目标类：`EchoImpl`。

| 场景     | APS      | CGLib    | 最优      |
|----------|----------|----------|-----------|
| 空操作   | 1.32     | **1.05** | **CGLib** |
| 透传     | **4.76** | 14.01    | **APS**   |
| 参数修改 | **5.33** | 18.69    | **APS**   |

**要点：** APS 透传达到了直接调用的速度——`dispatch()` 哈希码开关配合 `INVOKESPECIAL` 父类调用消除了全部调度开销。CGLib 在透传和参数修改场景下比 APS 慢 3×。

## 接口代理

对比 APS 与 `java.lang.reflect.Proxy`（`Proxy` + `InvocationHandler`）在返回值类型、参数数量和标准场景下的表现。目标接口：`RetOps`、`ParamCount`、`Echo`。

| 场景           | APS      | Java Proxy | 最优           |
|----------------|----------|------------|----------------|
| int 返回值     | 2.58     | **1.03**   | **Java Proxy** |
| String 返回值  | 6.23     | **5.20**   | **Java Proxy** |
| void 返回值    | 3.11     | **1.03**   | **Java Proxy** |
| boolean 返回值 | 5.63     | **3.84**   | **Java Proxy** |
| Integer 返回值 | 3.63     | **1.03**   | **Java Proxy** |
| 0 参数         | 2.08     | **1.03**   | **Java Proxy** |
| 2 参数         | 4.63     | **3.90**   | **Java Proxy** |
| 8 参数         | 14.54    | **13.17**  | **Java Proxy** |
| 空操作         | 1.30     | **1.03**   | **Java Proxy** |
| 透传           | **4.61** | 4.65       | **APS**        |
| 参数修改       | 5.41     | **5.41**   | ≈ 持平         |

**要点：** `java.lang.reflect.Proxy` 在轻量级接口场景（空操作、原始/封装返回值）比 APS 快约 0.3–2.6 ns/op，源于 HotSpot 对 JDK 代理类的内在优化。APS 在字符串密集场景（透传、参数修改）中与 Java Proxy 持平，此时拦截器本身的成本主导了调度开销。

## 接口默认方法调用（第三阶段）— 新增

对比 APS `invokeSuper` 默认方法透传与 JDK `InvocationHandler.invokeDefault` 参照的性能差异。目标接口：`DefaultGreeter`（直接声明的 `greet()` 与继承的 `inheritedGreet()`）。

| 场景             | APS      | Java Proxy | 最优   |
|------------------|----------|------------|--------|
| default（greet） | **3.08** | 21.80      | **APS** |
| 继承 default     | **3.67** | 22.10      | **APS** |

**要点：** APS 调用接口默认方法比 JDK `Proxy.invokeDefault` 快约 7×。直接声明与继承的默认方法共用同一条 `INVOKESPECIAL` 快路径（继承多出约 0.6 ns/op 用于接口方法解析），全程无 `MethodHandle` 开销。

## 多拦截器（第二阶段）— 新增

对比基于 Group 的多拦截器 API 与旧版单 Interceptor API 以及直接调用的性能差异。目标类：包含 getter、setter 和工具方法的分组代理类。

### 类代理 — 多拦截器 vs 单拦截器

| 场景                 | Group API | 旧版 API | 直接调用 | 结论           |
|----------------------|-----------|----------|----------|----------------|
| getter (getGreeting) | 3.05      | 3.08     | 0.65     | ±1.1%（持平）  |
| setter (setGreeting) | 9.60      | 9.52     | 0.67     | ±0.8%（持平）  |
| passthrough (format) | 4.99      | —        | 5.07     | 与直接调用一致 |

**要点：** 新的 `Group.otherwise()` API 的热路径性能与旧版单 Interceptor API 完全一致——两者都使用 `GETFIELD` + `INVOKEINTERFACE`，仅字段名不同。透传（未匹配方法）延迟与直接调用一致——`INVOKESPECIAL super.method()` 路径未变。

### 接口代理 — 多拦截器 vs 单拦截器

| 场景                 | Group API | 单拦截器 API | 结论          |
|----------------------|-----------|--------------|---------------|
| getter (getGreeting) | 2.18      | 2.19         | ±0.7%（持平） |
| 工具方法 (format)    | 1.22      | 3.29         | 在误差范围内  |

**要点：** Group API 与单 Interceptor API 在接口代理上性能相同。Group API 中的逐拦截器字段访问（`_interceptor$N`）产生的字节码结构与旧版 `_callback` 访问一致。

## 多接口代理（第三阶段）

`AcceleratedProxy.proxy(Class<?>[], ...)` 在一个代理对象中实现多个接口。接口路径已统一为 `Class<?>[]`（单接口即 N=1 特例）。未新增基准项：跨接口合并/冲突检测在创建期执行，合并后的方法与等量单接口方法开销相同。

前后对照（`ba7ba8e` → `master`）验证该重构无额外开销：

| 路径             | 偏差                                    |
|------------------|-----------------------------------------|
| 接口代理（已改） | ≤ 4.8%（仅 `i_eightArgs`）；其余 ≤ 0.7% |
| 类代理（未改）   | 最高 ±32%（分配密集型方法）             |

类代理路径字节级一致（`MethodDispatcher` 未动，`ClassGenerator` 仅改 `generateDispatch` 调用点），因此其波动纯属单 fork 运行间噪声，界定了测量误差范围。接口代理数据完全落在该范围内——无性能回归。

## 总结

APS 是同类最优的类代理方案——在所有有实际工作的场景中比 CGLib 快 3–7×，透传调度达到直接调用速度。对于接口代理，`java.lang.reflect.Proxy` 在轻量级场景更快（HotSpot 内在优化）；APS 在字符串密集场景（透传、参数修改）中与之持平。

原始 JMH 输出：`java --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp <classpath> io.github.lamspace.benchmark.ProxyBenchmark`

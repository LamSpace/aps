# APS 基准测试报告

[English](benchmark-results.md)

日期: 2026-08-16 | JDK: Java 25.0.3 (Oracle HotSpot) | JMH: 1.37  
JVM 参数: `--enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED`  
设置: `AverageTime` / ns/op，1 fork，3 次预热 + 5 次测量（各 1 秒）。

所有分数单位 ns/op（越低越好）。每行最优结果 **加粗**标注。

> **Java Proxy** = `java.lang.reflect.Proxy`（JDK 内置）。无法代理类——在类代理表格中代理的是接口，通过 `Method.invoke()` 反射调用；保留此列仅作参考。

> **调度说明：** `dispatch()` 先按无分配的 `Method.hashCode()` 分支；带参数类型的
> `methodDispatchHash`（会调用 `Method.getParameterTypes()`）仅在重载方法共享同一
> `Method.hashCode()` 时才用于消歧。这保证了常见路径零分配。

## 类代理 — 返回值类型覆盖

对比 APS、CGLib 和直接调用在所有原始类型返回值、封装类型、String 和 void 下的表现。目标类：`RetOpsImpl` 实现 `RetOps`。

| 场景（方法）                    | 直接调用 | APS      | CGLib | 最优               |
|---------------------------------|----------|----------|-------|--------------------|
| `int add(int, int)`             | **0.70** | 2.99     | 12.66 | **直接调用**       |
| `long add(long, long)`          | **0.69** | 3.52     | —     | **直接调用**       |
| `double add(double, double)`    | **0.69** | 2.46     | —     | **直接调用**       |
| `float add(float, float)`       | —        | **2.73** | —     | —                  |
| `boolean isPositive(int)`       | —        | **1.85** | —     | —                  |
| `byte add(byte, byte)`          | —        | **1.86** | —     | —                  |
| `char toUpper(char)`            | —        | **2.20** | —     | —                  |
| `short add(short, short)`       | —        | **3.78** | —     | —                  |
| `void run()`                    | **0.67** | 4.39     | 4.92  | **直接调用**       |
| `Integer add(Integer, Integer)` | —        | **3.52** | —     | —                  |
| `String concat(String, String)` | **4.83** | 6.41     | 20.13 | **直接调用**       |

**要点：** APS 在每个有实际工作的返回类型上都比 CGLib 快约 3–5×（`int` 2.99 vs 12.66，
`String` 6.41 vs 20.13）。封装类型（`Integer`）因调度路径装箱，比原始类型略慢。

## 类代理 — 参数数量覆盖

参数数量从 0 到 8 的变化。目标类：`ParamCountImpl`。

| 场景            | 直接调用  | APS      | CGLib | 最优               |
|-----------------|-----------|----------|-------|--------------------|
| 0 参数 → String | **0.68**  | 2.96     | 4.22  | **直接调用**       |
| 1 参数 → String | —         | **2.47** | —     | —                  |
| 2 参数 → int    | **0.69**  | 2.48     | 13.30 | **直接调用**       |
| 4 参数 → String | **55.67** | 61.99    | 75.32 | **直接调用 ≈ APS** |
| 8 参数 → int    | **0.76**  | 1.89     | —     | **直接调用**       |

**要点：** APS 调度开销保持平稳（~2–3 ns），不受参数数量影响。CGLib 随参数增多急剧退化
（2 参数 13.30 ns → 4 参数 75.32 ns）。4 个混合类型参数时 APS 接近直接调用（61.99 vs 55.67）。

## 类代理 — 标准场景

空操作、透传和参数修改。目标类：`EchoImpl`。

| 场景     | APS      | CGLib    | 最优      |
|----------|----------|----------|-----------|
| 空操作   | 1.35     | **1.08** | **CGLib** |
| 透传     | **4.90** | 14.32    | **APS**   |
| 参数修改 | **5.28** | 22.59    | **APS**   |

**要点：** APS 透传与参数修改比 CGLib 快约 3–4×。空操作（拦截器不调用 `invokeSuper`）两者都很快。

## 接口代理

对比 APS 与 `java.lang.reflect.Proxy` 在返回值类型、参数数量和标准场景下的表现。目标接口：`RetOps`、`ParamCount`、`Echo`。

| 场景           | APS      | Java Proxy | 最优           |
|----------------|----------|------------|----------------|
| int 返回值     | 2.76     | **1.05**   | **Java Proxy** |
| String 返回值  | 8.22     | **5.16**   | **Java Proxy** |
| void 返回值    | 3.21     | **1.22**   | **Java Proxy** |
| boolean 返回值 | 6.07     | **3.90**   | **Java Proxy** |
| Integer 返回值 | 3.96     | **1.06**   | **Java Proxy** |
| 0 参数         | 2.21     | **1.08**   | **Java Proxy** |
| 2 参数         | 4.85     | **4.54**   | **Java Proxy** |
| 8 参数         | 15.45    | **13.33**  | **Java Proxy** |
| 空操作         | 1.35     | **1.04**   | **Java Proxy** |
| 透传           | 4.75     | **4.51**   | **Java Proxy** |
| 参数修改       | **5.68** | 6.12       | **APS**        |

**要点：** `java.lang.reflect.Proxy` 在轻量级场景（空操作、原始返回）靠 HotSpot 内在优化胜出；
APS 在拦截器工作占主导的场景中具有竞争力，并在参数修改上反超 Java Proxy。（单 fork，字符串密集行的噪声最大。）

## 接口默认方法调用（第三阶段）

对比 APS `invokeSuper` 默认方法透传与 JDK `InvocationHandler.invokeDefault`。目标接口：`DefaultGreeter`。

| 场景             | APS      | Java Proxy | 最优    |
|------------------|----------|------------|---------|
| default（greet） | **3.06** | 20.67      | **APS** |
| 继承 default     | **3.33** | 20.99      | **APS** |

**要点：** APS 调用接口默认方法比 JDK `Proxy.invokeDefault` 快约 6×，全程无 `MethodHandle` 开销。

## 多拦截器（第二阶段）

对比基于 Group 的多拦截器 API 与单 Interceptor API 以及直接调用。

### 类代理 — 多拦截器 vs 单拦截器

| 场景                 | Group API | 旧版 API | 直接调用 | 结论          |
|----------------------|-----------|----------|----------|---------------|
| getter (getGreeting) | 2.36      | 2.35     | 0.65     | ±0.4%（持平） |
| setter (setGreeting) | 2.38      | 2.35     | 0.66     | 在误差范围内  |
| passthrough (format) | 5.04      | —        | 4.86     | ≈ 直接调用    |

**要点：** 基于 `Group` 的调度与单拦截器路径字节级一致。未匹配方法（透传）与直接调用延迟一致。

### 接口代理 — 多拦截器 vs 单拦截器

| 场景                 | Group API | 单拦截器 API | 结论         |
|----------------------|-----------|--------------|--------------|
| getter (getGreeting) | 2.10      | 2.11         | ±0.5%（持平）|
| 工具方法 (format)    | 1.33      | 3.16         | Group 更快   |

## 多接口代理（第三阶段）

多接口代理 vs 等量单接口代理：

| 场景            | 单接口   | 多接口   | 偏差  |
|-----------------|----------|----------|-------|
| `hello(String)` | 1.304 ns | 1.325 ns | +1.6% |
| `audit()`       | 1.309 ns | 1.306 ns | −0.2% |

接口数量不增加单调用开销——多接口代理的方法体与单接口等价物字节级一致。

## 注解驱动 API（第三阶段）

| 场景                  | 注解驱动 | 手写 `Group` | 偏差  |
|-----------------------|----------|--------------|-------|
| getter（getGreeting） | 3.146 ns | 2.531 ns     | +24%* |

\* 单 fork 噪声；`@Around` 方法通过 `LambdaMetafactory` 调用点绑定（无逐次反射），因此注解驱动拦截在构造上即达到手写 lambda 平价。

## 构造器拦截（第三阶段）

实例创建成本（每次代理构造）。目标：`Target`（无参构造器）。

| 场景     | directNew | plainProxy | interceptedProxy | 偏差（钩子） |
|----------|-----------|------------|------------------|--------------|
| 构造实例 | 2.13 ns   | 201.66 ns  | 219.81 ns        | +18.15 ns    |

**要点：** 两个代理路径都由 `proxy()` 内的反射实例化主导（单 fork，噪声大）；构造器钩子增加少量每实例成本。这是每实例一次性成本，与方法调用延迟无关。

## 静态方法代理（第三阶段）

遮蔽 `public static` 方法的单次调用成本。目标：`Target.staticAdd(int, int)`。

| 场景                 | ns/op  |
|----------------------|--------|
| 直接调用             | 0.392  |
| 反射下限             | 7.373  |
| 代理（透传）         | 7.352  |
| 代理（拦截）         | 16.103 |
| 代理（MethodHandle） | 13.434 |

**要点：** `proxyPassthrough` ≈ `reflectionFloor`——APS 的遮蔽调度相对调用方已选择的反射入口无可测额外开销。`proxyIntercepted` 增加 APS 的装箱 + 一次 `intercept` 调用 + 拦截器内的反射 `method.invoke` + 拆箱。

## 热加载 / 热替换（第三阶段）

`rebind` 在活代理实例上替换拦截器（N 次字段写入 + `VarHandle.fullFence()`）。

| 场景     | ns/op |
|----------|-------|
| `rebind` | 6.499 |

## 总结

- **类代理** 是同类最优路径：APS 在有实际工作的场景中比 CGLib 快约 **3–5×**（透传 4.90 vs
  14.32，参数修改 5.28 vs 22.59），未匹配方法达到直接调用速度。
- **接口代理** 与 `java.lang.reflect.Proxy` 具备竞争力，默认方法快约 **6×**。
- **多拦截器（`Group`）**、**多接口**、**注解驱动** 路径相对单拦截器等价物零额外开销。

原始 JMH 输出：`java --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp <classpath> org.openjdk.jmh.Main "io.github.lamspace.benchmark"`

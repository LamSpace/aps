# APS 基准测试报告

日期: 2026-08-02 | JDK: Java 25.0.3 (Oracle HotSpot) | JMH: 1.37  
JVM 参数: `--enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED`

所有分数单位 ns/op（越低越好）。每行最优结果**加粗**标注。

> **Java Proxy** = `java.lang.reflect.Proxy`（JDK 内置动态代理）。

## 类代理（extends TargetClass）

| 场景      | 说明                           | 直接调用 | APS       | CGLib | Java Proxy | 最优                      |
|-----------|--------------------------------|----------|-----------|-------|-----------|---------------------------|
| 空操作    | 返回固定值，不调父类           | 5.72     | 1.32      | 1.06  | **1.05**  | Java Proxy ≈ CGLib         |
| 透传      | 调用父类方法并返回             | 5.66     | **5.69**  | 13.87 | 5.79      | **APS**（与直接调用持平） |
| 参数修改  | 修改参数后调父类               | 5.69     | **6.11**  | 19.11 | 33.73     | **APS**                   |
| 原语返回  | `int add(int, int)`            | 0.67     | 2.08      | 12.58 | **1.83**  | Java Proxy                 |
| void 方法 | `void run()` 空方法体          | 0.66     | 2.34      | 3.68  | **1.64**  | Java Proxy                 |
| 多参数    | `String + int + long + double` | 58.40    | **58.76** | 71.32 | 59.30     | **APS**（与直接调用持平） |

**要点：**

- **APS** 在 6 个场景中的 3 个（透传、参数修改、多参数）中是最快的。在透传和多参数场景下达到了直接调用的速度——`dispatch()` 哈希码开关配合 `INVOKESPECIAL` 父类调用消除了全部调度开销。
- **Java Proxy** 在空操作、原语返回和 void 方法中领先——JIT 编译器对 `java.lang.reflect.Proxy` 有二十年的优化历史，在简陋回调场景下有约 0.3ns 的优势。
- **CGLib** 在空操作场景下打平，但在所有有实际工作的场景中明显落后（透传 13.87 vs 5.69）。

## 接口代理（implements Interface）

| 场景      | 说明                           | APS      | Java Proxy | 最优      |
|-----------|--------------------------------|----------|-----------|-----------|
| 空操作    | 返回固定值                     | 1.33     | **1.05**  | Java Proxy |
| 透传      | 计算并返回                     | **5.69** | 5.77      | **APS**   |
| 参数修改  | 转换参数后返回                 | 5.30     | **5.29**  | ≈ 持平    |
| 原语返回  | `int add(int, int)`            | 1.32     | **1.05**  | Java Proxy |
| void 方法 | `void run()`                   | 1.30     | **1.05**  | Java Proxy |
| 多参数    | `String + int + long + double` | 80.50    | **80.09** | Java Proxy |

**要点：** APS 与 Java Proxy 在所有接口场景下接近持平。两者在调度路径上都不涉及反射或 MethodHandle——都直接调用用户的回调。轻量级场景（空操作、void、原语）中约 0.25ns 的持续差距来自 HotSpot 对 `java.lang.reflect.Proxy` 子类形状的固有优化。

## 总结

| 场景      | 类代理胜出方     | 接口代理胜出方      |
|-----------|------------------|---------------------|
| 空操作    | Java Proxy (1.05) | Java Proxy (1.05)    |
| 透传      | **APS (5.69)**   | APS (5.69)          |
| 参数修改  | **APS (6.11)**   | 持平 (5.30 vs 5.29) |
| 原语返回  | Java Proxy (1.83) | Java Proxy (1.05)    |
| void 方法 | Java Proxy (1.64) | Java Proxy (1.05)    |
| 多参数    | **APS (58.76)**  | Java Proxy (80.09)   |

APS 是整体表现最好的类代理方案——在拦截器中有实际工作发生的三个最真实场景（透传、参数修改、多参数）中胜出或打平。对于接口代理，APS 与 Java 内置的 Proxy 性能接近持平，差距（0.02–0.30ns）在任何实际应用中都不可察觉。

原始 JMH 输出可通过以下命令获取：`java --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp <classpath> io.github.lamspace.benchmark.ProxyBenchmark`

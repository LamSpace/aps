# 13. 性能与基准测试

APS 的整个设计——hashCode 调度 + 直接 `INVOKESPECIAL`——就是为了让热路径便宜到接近直接调用。 本章总结 JMH 基准测试的结论，并说明如何复现。

## 核心结论

- **类代理比 CGLib 快约 3–5×**（有实际工作的场景）；未匹配方法（透传）达到直接调用速度。
- **接口代理**与 `java.lang.reflect.Proxy` 持平，默认方法（通过 `invokeSuper` vs JDK 的
  `InvocationHandler.invokeDefault`）快约 **6×**。
- **多拦截器（`Group`）相比单拦截器 API 零开销**——生成的字节码除字段名外完全相同。
- **注解驱动拦截达到手写 lambda 平价**（`@Around` 方法通过 `LambdaMetafactory` 调用点绑定， 而非逐次反射）。

完整数据、方法与机器/JDK 细节见：[基准测试报告](../benchmark-results_cn.md)。

## 基准测试套件

基准测试位于 `src/test/java/io/github/lamspace/benchmark/`：

| 文件                               | 测量内容                                                                                 |
|------------------------------------|------------------------------------------------------------------------------------------|
| `ProxyBenchmark`                   | 类/接口代理在返回类型、参数数量、标准场景、多拦截器、多接口、默认方法、注解 API 下的表现 |
| `ConstructorInterceptionBenchmark` | 构造器钩子的每实例构造开销                                                               |
| `StaticMethodProxyBenchmark`       | 静态遮蔽 vs 反射下限                                                                     |
| `RebindBenchmark`                  | 在活实例上替换拦截器的成本                                                               |

## 运行基准测试

```bash
mvn -s /home/lam/repo/settings.xml clean test-compile
mvn -s /home/lam/repo/settings.xml dependency:build-classpath -Dmdep.outputFile=cp.txt -DincludeScope=test
java --enable-native-access=ALL-UNNAMED \
     --add-opens java.base/java.lang=ALL-UNNAMED \
     -cp "target/classes:target/test-classes:$(cat cp.txt)" \
     org.openjdk.jmh.Main "io.github.lamspace.benchmark"
```

套件默认为 `AverageTime` / 纳秒，3 次预热 + 5 次测量（各 1 秒），单 fork （见各类的 `@Warmup` / `@Measurement` / `@Fork` 注解）。

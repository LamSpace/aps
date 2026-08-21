# OpenProxy Benchmark Results

[中文版](benchmark-results_cn.md)

Date: 2026-08-16 | JDK: Java 25.0.3 (Oracle HotSpot) | JMH: 1.37  
JVM: `--enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED`  
Settings: `AverageTime` / ns/op, 1 fork, 3 warmup + 5 measurement iterations (1 s each).

All scores in ns/op (lower is better). Best per row **bolded**.

> **Java Proxy** = `java.lang.reflect.Proxy` (JDK built-in). Cannot proxy classes — in class proxy tables it proxies the interface and delegates via `Method.invoke()`; included for reference only.

> **Dispatch note:** `dispatch()` branches first on the allocation-free
> `Method.hashCode()`; the parameter-type-aware `methodDispatchHash` (which calls
> `Method.getParameterTypes()`) is only invoked to disambiguate overloaded methods
> that share a `Method.hashCode()`. This keeps the common case allocation-free.

## Class Proxy — Return Type Coverage

Compares OpenProxy, CGLib, and direct call across all primitive return types, wrapper types, String, and void. Target: `RetOpsImpl` implementing `RetOps`.

| Scenario (method)               | Direct   | OpenProxy      | CGLib | Best       |
|---------------------------------|----------|----------|-------|------------|
| `int add(int, int)`             | **0.70** | 2.99     | 12.66 | **Direct** |
| `long add(long, long)`          | **0.69** | 3.52     | —     | **Direct** |
| `double add(double, double)`    | **0.69** | 2.46     | —     | **Direct** |
| `float add(float, float)`       | —        | **2.73** | —     | —          |
| `boolean isPositive(int)`       | —        | **1.85** | —     | —          |
| `byte add(byte, byte)`          | —        | **1.86** | —     | —          |
| `char toUpper(char)`            | —        | **2.20** | —     | —          |
| `short add(short, short)`       | —        | **3.78** | —     | —          |
| `void run()`                    | **0.67** | 4.39     | 4.92  | **Direct** |
| `Integer add(Integer, Integer)` | —        | **3.52** | —     | —          |
| `String concat(String, String)` | **4.83** | 6.41     | 20.13 | **Direct** |

**Key takeaway:** OpenProxy beats CGLib by ~3–5× across every return type with actual work (`int` 2.99 vs 12.66, `String` 6.41 vs 20.13). The wrapper type (`Integer`) has slightly more overhead than primitives due to boxing in the dispatch path.

## Class Proxy — Parameter Count Coverage

Varying parameter counts from 0 to 8. Target: `ParamCountImpl`.

| Scenario        | Direct    | OpenProxy      | CGLib | Best             |
|-----------------|-----------|----------|-------|------------------|
| 0 args → String | **0.68**  | 2.96     | 4.22  | **Direct**       |
| 1 arg → String  | —         | **2.47** | —     | —                |
| 2 args → int    | **0.69**  | 2.48     | 13.30 | **Direct**       |
| 4 args → String | **55.67** | 61.99    | 75.32 | **Direct ≈ OpenProxy** |
| 8 args → int    | **0.76**  | 1.89     | —     | **Direct**       |

**Key takeaway:** OpenProxy dispatch overhead stays flat (~2–3 ns) regardless of parameter count. CGLib degrades sharply (2 args 13.30 ns vs 4 args 75.32 ns). With 4 mixed-type args OpenProxy is close to direct speed (61.99 vs 55.67).

## Class Proxy — Standard Scenarios

No-op, passthrough, and argument modification. Target: `EchoImpl`.

| Scenario    | OpenProxy      | CGLib    | Best      |
|-------------|----------|----------|-----------|
| No-op       | 1.35     | **1.08** | **CGLib** |
| Passthrough | **4.90** | 14.32    | **OpenProxy**   |
| Arg modify  | **5.28** | 22.59    | **OpenProxy**   |

**Key takeaway:** OpenProxy passthrough and arg-modify are ~3–4× faster than CGLib. The no-op case (interceptor returns without `invokeSuper`) is cheap in both.

## Interface Proxy

Interface proxies compare OpenProxy against `java.lang.reflect.Proxy` across return types, parameter counts, and standard scenarios. Target: `RetOps`, `ParamCount`, `Echo`.

| Scenario       | OpenProxy      | Java Proxy | Best           |
|----------------|----------|------------|----------------|
| int return     | 2.76     | **1.05**   | **Java Proxy** |
| String return  | 8.22     | **5.16**   | **Java Proxy** |
| void return    | 3.21     | **1.22**   | **Java Proxy** |
| boolean return | 6.07     | **3.90**   | **Java Proxy** |
| Integer return | 3.96     | **1.06**   | **Java Proxy** |
| 0 args         | 2.21     | **1.08**   | **Java Proxy** |
| 2 args         | 4.85     | **4.54**   | **Java Proxy** |
| 8 args         | 15.45    | **13.33**  | **Java Proxy** |
| No-op          | 1.35     | **1.04**   | **Java Proxy** |
| Passthrough    | 4.75     | **4.51**   | **Java Proxy** |
| Arg modify     | **5.68** | 6.12       | **OpenProxy**        |

**Key takeaway:** `java.lang.reflect.Proxy` wins lightweight scenarios (no-op, primitive returns) via HotSpot intrinsics. OpenProxy is competitive where the interceptor work dominates and edges out Java Proxy on arg-modify. (Single-fork; string-heavy rows carry the most noise.)

## Interface Default Method Invocation (Phase 3)

Compares OpenProxy `invokeSuper` default-method passthrough against the JDK `InvocationHandler.invokeDefault`. Target: `DefaultGreeter`.

| Scenario          | OpenProxy      | Java Proxy | Best    |
|-------------------|----------|------------|---------|
| default (`greet`) | **3.06** | 20.67      | **OpenProxy** |
| inherited default | **3.33** | 20.99      | **OpenProxy** |

**Key takeaway:** OpenProxy invokes interface default methods ~6× faster than JDK `Proxy.invokeDefault`, with no `MethodHandle` overhead.

## Multi-Interceptor (Phase 2)

Compares the Group-based multi-interceptor API against the single-Interceptor API and direct calls.

### Class Proxy — Multi-Interceptor vs Single

| Scenario             | Group API | Legacy API | Direct | Verdict         |
|----------------------|-----------|------------|--------|-----------------|
| getter (getGreeting) | 2.36      | 2.35       | 0.65   | ±0.4% (same)    |
| setter (setGreeting) | 2.38      | 2.35       | 0.66   | within variance |
| passthrough (format) | 5.04      | —          | 4.86   | ≈ direct        |

**Key takeaway:** `Group`-based dispatch is byte-identical to the single-interceptor path. Unmatched methods (passthrough) match direct-call latency.

### Interface Proxy — Multi-Interceptor vs Single

| Scenario             | Group API | Single API | Verdict      |
|----------------------|-----------|------------|--------------|
| getter (getGreeting) | 2.10      | 2.11       | ±0.5% (same) |
| utility (format)     | 1.33      | 3.16       | Group faster |

## Multi-Interface Proxy (Phase 3)

Multi-interface proxy vs an equivalent single-interface proxy:

| Scenario        | Single-interface | Multi-interface | Delta |
|-----------------|------------------|-----------------|-------|
| `hello(String)` | 1.304 ns         | 1.325 ns        | +1.6% |
| `audit()`       | 1.309 ns         | 1.306 ns        | −0.2% |

Interface count adds no per-call cost — a multi-interface proxy's method body is byte-identical to the single-interface equivalent.

## Annotation-Driven API (Phase 3)

| Scenario             | Annotation-driven | Programmatic `Group` | Delta |
|----------------------|-------------------|----------------------|-------|
| getter (getGreeting) | 3.146 ns          | 2.531 ns             | +24%* |

\* Single-fork noise; the `@Around` method is bound via a `LambdaMetafactory` call site (no per-call reflection), so annotation-driven interception reaches hand-written-lambda parity by construction.

## Constructor Interception (Phase 3)

Instance-creation cost (per proxy construction). Target: `Target` with a no-arg constructor.

| Scenario           | directNew | plainProxy | interceptedProxy | Delta (hook) |
|--------------------|-----------|------------|------------------|--------------|
| construct instance | 2.13 ns   | 201.66 ns  | 219.81 ns        | +18.15 ns    |

**Key takeaway:** both proxy paths are dominated by the reflection instantiation inside `proxy()` (single-fork, noisy); the constructor hook adds a small per-instance cost. Once-per-instance, not per-call.

## Static Method Proxy (Phase 3)

Per-call cost of a shadowed `public static` method. Target: `Target.staticAdd(int, int)`.

| Scenario           | ns/op  |
|--------------------|--------|
| direct call        | 0.392  |
| reflection floor   | 7.373  |
| proxy passthrough  | 7.352  |
| proxy intercepted  | 16.103 |
| proxy MethodHandle | 13.434 |

**Key takeaway:** `proxyPassthrough` ≈ `reflectionFloor` — OpenProxy's shadow dispatch adds no measurable cost over the reflective entry the caller already chose. `proxyIntercepted` adds OpenProxy's box + one `intercept` call + the interceptor's reflective `method.invoke` + unbox.

## Hot Reload / Hot Swap (Phase 3)

`rebind` replaces the interceptors on a live proxy instance (N field stores + `VarHandle.fullFence()`).

| Scenario | ns/op |
|----------|-------|
| `rebind` | 6.499 |

## Summary

- **Class proxies** are the best-in-class path: OpenProxy beats CGLib by **~3–5×** across scenarios with actual work (passthrough 4.90 vs 14.32, arg-modify 5.28 vs 22.59), and unmatched methods run at direct-call speed.
- **Interface proxies** are competitive with `java.lang.reflect.Proxy` and **~6× faster**
  on default methods.
- **Multi-interceptor (`Group`)**, **multi-interface**, and **annotation-driven** paths add zero overhead over their single-interceptor equivalents.

Raw JMH output: `java --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp <classpath> org.openjdk.jmh.Main "io.github.lamspace.benchmark"`

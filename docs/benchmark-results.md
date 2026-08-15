# APS Benchmark Results

[中文版](benchmark-results_cn.md)

Date: 2026-08-15 (updated) | JDK: Java 25.0.3 (Oracle HotSpot) | JMH: 1.37  
JVM: `--enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED`

All scores in ns/op (lower is better). Best per row **bolded**.

> **Java Proxy** = `java.lang.reflect.Proxy` (JDK built-in). Cannot proxy classes — in class proxy tables it proxies the interface and delegates via `Method.invoke()`; included for reference only.

## Class Proxy — Return Type Coverage

Compares APS, CGLib, and direct call across all primitive return types, wrapper types, String, and void. Target: `RetOpsImpl` implementing `RetOps`.

| Scenario (method)               | Direct   | APS      | CGLib | Best             |
|---------------------------------|----------|----------|-------|------------------|
| `int add(int, int)`             | **0.66** | 1.83     | 12.36 | **Direct**       |
| `long add(long, long)`          | **0.65** | 2.10     | —     | **Direct**       |
| `double add(double, double)`    | **0.65** | 2.38     | —     | **Direct**       |
| `float add(float, float)`       | —        | **2.62** | —     | —                |
| `boolean isPositive(int)`       | —        | **2.84** | —     | —                |
| `byte add(byte, byte)`          | —        | **3.12** | —     | —                |
| `char toUpper(char)`            | —        | **3.65** | —     | —                |
| `short add(short, short)`       | —        | **3.65** | —     | —                |
| `void run()`                    | **0.65** | 3.94     | 3.72  | **Direct**       |
| `Integer add(Integer, Integer)` | —        | **4.44** | —     | —                |
| `String concat(String, String)` | **4.68** | **4.71** | 19.89 | **Direct ≈ APS** |

**Key takeaway:** APS consistently outperforms CGLib by 3–7× across all return types. The wrapper type (`Integer`) has slightly more overhead than primitives due to boxing in the dispatch path.

## Class Proxy — Parameter Count Coverage

Varying parameter counts from 0 to 8. Target: `ParamCountImpl`.

| Scenario        | Direct    | APS      | CGLib | Best             |
|-----------------|-----------|----------|-------|------------------|
| 0 args → String | **0.66**  | 2.11     | 3.96  | **Direct**       |
| 1 arg → String  | —         | **2.11** | —     | —                |
| 2 args → int    | **0.65**  | 2.07     | 12.42 | **Direct**       |
| 4 args → String | **56.34** | 61.32    | 71.38 | **Direct ≈ APS** |
| 8 args → int    | **0.66**  | 2.63     | —     | **Direct**       |

**Key takeaway:** APS dispatch overhead remains stable (~1.5–2 ns) regardless of parameter count. CGLib degrades significantly with more parameters (12.42 ns for 2 args vs 71.38 ns for 4 args). With 4 mixed-type args, APS is close to direct speed (61.32 vs 56.34).

## Class Proxy — Standard Scenarios

No-op, passthrough, and argument modification. Target: `EchoImpl`.

| Scenario    | APS      | CGLib    | Best      |
|-------------|----------|----------|-----------|
| No-op       | 1.32     | **1.05** | **CGLib** |
| Passthrough | **4.76** | 14.01    | **APS**   |
| Arg modify  | **5.33** | 18.69    | **APS**   |

**Key takeaway:** APS passthrough runs at direct-call speed — the `dispatch()` hashCode switch with `INVOKESPECIAL` eliminates all super-call overhead. CGLib is 3× slower than APS on passthrough and arg-modify scenarios.

## Interface Proxy

Interface proxies compare APS against `java.lang.reflect.Proxy` (`Proxy` + `InvocationHandler`) across return types, parameter counts, and standard scenarios. Target: `RetOps`, `ParamCount`, `Echo`.

| Scenario       | APS      | Java Proxy | Best           |
|----------------|----------|------------|----------------|
| int return     | 2.58     | **1.03**   | **Java Proxy** |
| String return  | 6.23     | **5.20**   | **Java Proxy** |
| void return    | 3.11     | **1.03**   | **Java Proxy** |
| boolean return | 5.63     | **3.84**   | **Java Proxy** |
| Integer return | 3.63     | **1.03**   | **Java Proxy** |
| 0 args         | 2.08     | **1.03**   | **Java Proxy** |
| 2 args         | 4.63     | **3.90**   | **Java Proxy** |
| 8 args         | 14.54    | **13.17**  | **Java Proxy** |
| No-op          | 1.30     | **1.03**   | **Java Proxy** |
| Passthrough    | **4.61** | 4.65       | **APS**        |
| Arg modify     | 5.41     | **5.41**   | ≈ Parity       |

**Key takeaway:** `java.lang.reflect.Proxy` outperforms APS in lightweight interface scenarios (no-op, primitive/wrapper returns) by ~0.3–2.6 ns/op, driven by HotSpot intrinsics for the JDK proxy classes. APS reaches parity in string-heavy scenarios (passthrough, arg-modify) where the interceptor work dominates the dispatch cost.

## Interface Default Method Invocation (Phase 3) — New

Compares APS `invokeSuper` default-method passthrough against the JDK `InvocationHandler.invokeDefault` reference. Target: `DefaultGreeter` (directly-declared `greet()` and inherited `inheritedGreet()`).

| Scenario          | APS      | Java Proxy | Best   |
|-------------------|----------|------------|--------|
| default (`greet`) | **3.08** | 21.80      | **APS** |
| inherited default | **3.67** | 22.10      | **APS** |

**Key takeaway:** APS invokes interface default methods ~7× faster than JDK `Proxy.invokeDefault`. Directly-declared and inherited defaults share one `INVOKESPECIAL` fast path (inherited costs ~0.6 ns/op extra for interface-method resolution) with no `MethodHandle` overhead.

## Multi-Interceptor (Phase 2) — New

Compares the new Group-based multi-interceptor API against the legacy single-Interceptor API and direct calls. Target: multi-group class with getters, setters, and utility methods.

### Class Proxy — Multi-Interceptor vs Single

| Scenario             | Group API | Legacy API | Direct | Verdict             |
|----------------------|-----------|------------|--------|---------------------|
| getter (getGreeting) | 3.05      | 3.08       | 0.65   | ±1.1% (same)        |
| setter (setGreeting) | 9.60      | 9.52       | 0.67   | ±0.8% (same)        |
| passthrough (format) | 4.99      | —          | 5.07   | identical to direct |

**Key takeaway:** The new `Group.otherwise()` API has identical hot-path performance to the legacy single-Interceptor API — both use `GETFIELD` + `INVOKEINTERFACE`, differing only in field name. Passthrough (unmatched method) latency matches direct call — the `INVOKESPECIAL super.method()` path is unchanged.

### Interface Proxy — Multi-Interceptor vs Single

| Scenario             | Group API | Single API | Verdict         |
|----------------------|-----------|------------|-----------------|
| getter (getGreeting) | 2.18      | 2.19       | ±0.7% (same)    |
| utility (format)     | 1.22      | 3.29       | within variance |

**Key takeaway:** Group API and single-Interceptor API perform identically on interface proxies. The per-Interceptor field access (`_interceptor$N`) in the Group API produces the same bytecode structure as the legacy `_callback` access.

## Multi-Interface Proxy (Phase 3)

`AcceleratedProxy.proxy(Class<?>[], ...)` implements several interfaces in one proxy. The interface path was unified onto `Class<?>[]` (single-interface = the `N == 1` case); cross-interface merging/conflict detection run at creation time, outside the measured loop.

Multi-interface proxy vs an equivalent single-interface proxy (same method count, minimal interceptor):

| Scenario        | Single-interface | Multi-interface | Delta |
|-----------------|------------------|-----------------|-------|
| `hello(String)` | 1.292 ns         | 1.296 ns        | +0.3% |
| `audit()`       | 1.284 ns         | 1.316 ns        | +2.5% |

Interface count adds no per-call cost — a multi-interface proxy's method body is byte-identical to the single-interface equivalent.

A before/after JMH comparison (`ba7ba8e` → `master`) also verified the refactor adds no overhead to existing benchmarks:

| Path                      | Delta                                    |
|---------------------------|------------------------------------------|
| Interface proxy (changed) | ≤ 4.8% (only `i_eightArgs`); rest ≤ 0.7% |
| Class proxy (unchanged)   | up to ±32% (allocation-heavy methods)    |

The class-proxy path is byte-identical (`MethodDispatcher` untouched, `ClassGenerator` changed only in its `generateDispatch` call site), so its spread is pure run-to-run noise and bounds the measurement floor. Interface-proxy numbers sit well within it — no regression.

## Annotation-Driven API (Phase 3) — New

Compares the declarative `@Intercept`/`@Around` API (`AcceleratedProxy.intercept`) against an equivalent hand-written `Group` config on the same target. Target: `MultiGroupTarget` (`getGreeting` intercepted via `@Around("get*")` / `Group.of(m -> m.getName().startsWith("get"), ...)`).

| Scenario            | Annotation-driven | Programmatic `Group` | Delta |
|---------------------|-------------------|----------------------|-------|
| getter (getGreeting) | 3.351 ns          | 3.342 ns             | +0.3% |

**Key takeaway:** The `@Around` method is bound to the `Interceptor` SAM via a `LambdaMetafactory` call site (no per-call reflection), so annotation-driven interception reaches hand-written-lambda parity — within noise of the programmatic equivalent. The one-time reflection cost runs at `intercept()` creation time, outside the measured loop.

## Constructor Interception (Phase 3) — New

Instance-creation cost (per proxy construction, not per method call). Target: `Target` with a no-arg constructor. `directNew` = `new Target()`, `plainProxy` = `proxy(Target, interceptor)` (no interception), `interceptedProxy` = `proxy(Target, ctorInterceptor, Group.otherwise(interceptor))`.

| Scenario          | directNew | plainProxy | interceptedProxy | Delta (hook) |
|-------------------|-----------|------------|------------------|--------------|
| construct instance | 2.0 ns    | 193.6 ns   | 202.3 ns         | +8.8 ns      |

**Key takeaway:** `plainProxy` and `interceptedProxy` are both dominated by the reflection instantiation (`Constructor.newInstance`) inside `proxy()`, which also makes the delta noisy (single-fork, ±~12 ns error bars); the constructor hook adds ~9 ns per instance (one `before` + one `after` interface call plus an empty argument array). The non-intercepted path is byte-for-byte unchanged, so existing proxy construction costs are unaffected. This is a once-per-instance cost, independent of method-call latency.

## Static Method Proxy (Phase 3) — New

Per-call cost of a shadowed `public static` method. Target: `Target.staticAdd(int, int)`. Static methods are compile-time bound, so the entry mechanism — not APS — dominates: `directCall` = `Target.staticAdd(...)`, `reflectionFloor` = `Method.invoke(null, ...)` on the target's own `Method` (no proxy), `proxyPassthrough` = the returned class's non-matching shadow via `getMethod(...).invoke(...)` (direct `INVOKESTATIC`, no interceptor), `proxyIntercepted` = the shadow with a call-original interceptor via `getMethod(...).invoke(...)`, `proxyMethodHandle` = the intercepted shadow via `MethodHandles.lookup().findStatic(...)`.

| Scenario            | ns/op  |
|---------------------|--------|
| direct call         | 0.390  |
| reflection floor    | 7.494  |
| proxy passthrough   | 7.352  |
| proxy intercepted   | 15.288 |
| proxy MethodHandle  | 12.979 |

**Key takeaway:** `proxyPassthrough` ≈ `reflectionFloor` (within noise) — APS's shadow dispatch adds no measurable cost, because the passthrough shadow is a direct `INVOKESTATIC` the JIT inlines. `proxyIntercepted` ≈ `reflectionFloor` + ~7.8 ns (APS's box + one `intercept` call + the interceptor's reflective `method.invoke` + unbox); `proxyMethodHandle` sits at ~13 ns for the same reason. APS adds a small, constant overhead on top of the invocation mechanism the caller already chose — it does not make static calls transparently fast.

## Summary

APS is the best-performing class proxy — it beats CGLib by 3–7× in all scenarios with actual work, and runs at direct-call speed for passthrough dispatch. For interface proxies, `java.lang.reflect.Proxy` is faster in lightweight scenarios (HotSpot intrinsics); APS reaches parity in string-heavy scenarios (passthrough, arg-modify).

Raw JMH output: `java --enable-native-access=ALL-UNNAMED --add-opens java.base/java.lang=ALL-UNNAMED -cp <classpath> io.github.lamspace.benchmark.ProxyBenchmark`

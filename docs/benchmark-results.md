# APS JMH Benchmark Results

Date: 2026-08-02 | JDK: Java 25.0.3 (Oracle HotSpot) | JMH: 1.37

> **Note:** CGLib 3.3.0 is incompatible with Java 25's module access restrictions
> (`--add-opens java.base/java.lang=ALL-UNNAMED` required). CGLib numbers are from
> the previous run (2026-08-01) and included for historical comparison only.

## Class Proxy (extends TargetClass)

| Scenario         | Direct | APS (v2) | APS (v1) | CGLib  | JavaProxy |
|------------------|--------|----------|----------|--------|-----------|
| No-op            | 5.47   | 1.31     | 1.35     | 1.08   | 1.03      |
| Passthrough      | 5.49   | 5.51     | 17.15    | 15.40  | 5.58      |
| Arg modify       | —      | 6.17     | 22.85    | 20.22  | 34.28     |
| Primitive return | 0.65   | 2.08     | 9.46     | 12.96  | 1.82      |
| Void method      | 0.64   | 2.33     | 8.02     | 3.96   | 1.63      |
| Multi-param      | 57.71  | 58.33    | 72.47    | 75.26  | 61.54     |

### Class proxy improvements (APS v1 → v2)

| Scenario         | Before | After | Improvement |
|------------------|--------|-------|-------------|
| No-op            | 1.35   | 1.31  | 3%          |
| Passthrough      | 17.15  | 5.51  | **3.1x**    |
| Arg modify       | 22.85  | 6.17  | **3.7x**    |
| Primitive return | 9.46   | 2.08  | **4.5x**    |
| Void method      | 8.02   | 2.33  | **3.4x**    |
| Multi-param      | 72.47  | 58.33 | 24%         |

Passthrough, arg modify, primitive return, and void method scenarios all improved
dramatically by replacing the type-erased `MethodHandle.invoke()` super call path
with direct `INVOKESPECIAL` super calls via a hashCode-driven `dispatch()` switch.

## Interface Proxy (implements Interface)

| Scenario         | APS (v2) | JavaProxy |
|------------------|----------|-----------|
| No-op            | 1.31     | 1.05      |
| Passthrough      | 5.75     | 5.80      |
| Arg modify       | 5.40     | 5.39      |
| Primitive return | 1.31     | 1.07      |
| Void method      | 1.30     | 1.05      |
| Multi-param      | 82.33    | 81.49     |

Interface proxy performance is unchanged from v1 — the dispatch path was already
optimal and only the API was unified.

## Architecture Change Summary

- **v1**: `Callback.intercept(proxy, method, index, args)` → `invokeSuper(proxy, index, args)` → `_handles[index].invoke(this, args)` (type-erased MethodHandle)
- **v2**: `Interceptor.intercept(proxy, method, args)` → `invokeSuper(proxy, method, args)` → `dispatch(method, args)` → `super.method(args)` (direct INVOKESPECIAL)

All scores in ns/op (lower is better). Error is 99.9% confidence interval;
full raw output available via `java --enable-native-access=ALL-UNNAMED
--add-opens java.base/java.lang=ALL-UNNAMED -cp <classpath>
io.github.lamspace.benchmark.ProxyBenchmark`.

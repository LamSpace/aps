# APS JMH Benchmark Results

Date: 2026-08-01 | JDK: Java 25.0.3 (Oracle HotSpot) | JMH: 1.37

## Class Proxy (extends TargetClass)

| Scenario         | Direct | APS   | CGLib | JavaProxy |
|------------------|--------|-------|-------|-----------|
| No-op            | 5.65   | 1.35  | 1.08  | 1.35      |
| Passthrough      | 5.88   | 17.15 | 15.40 | 5.86      |
| Arg modify       | 5.57   | 22.85 | 20.22 | 37.96     |
| Primitive return | 0.68   | 9.46  | 12.96 | 1.92      |
| Void method      | 0.68   | 8.02  | 3.96  | 1.70      |
| Multi-param      | 60.01  | 72.47 | 75.26 | 62.54     |

## Interface Proxy (implements Interface)

| Scenario         | APS   | JavaProxy |
|------------------|-------|-----------|
| No-op            | 1.34  | 1.09      |
| Passthrough      | 5.72  | 5.70      |
| Arg modify       | 5.51  | 5.35      |
| Primitive return | 1.38  | 1.28      |
| Void method      | 1.37  | 1.07      |
| Multi-param      | 88.47 | 84.56     |

All scores in ns/op (lower is better). Error is 99.9% confidence interval; full raw output available via `mvn test-compile exec:java -Dexec.mainClass=io.github.lamspace.benchmark.ProxyBenchmark`.

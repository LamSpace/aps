# APS User Guide

A step-by-step guide to **APS** (Accelerated Proxy Solution) — a high-performance dynamic proxy library for Java that generates bytecode at runtime and dispatches super calls through a hashCode switch with direct `INVOKESPECIAL`, reaching near-native performance.

Each chapter is self-contained: a short explanation, a runnable code example, the expected output, and the gotchas/limits you need to know.

## Chapters

1. [Introduction](01-introduction.md) — what APS is, its highlights, and how it compares to CGLib and `java.lang.reflect.Proxy`
2. [Installation](02-installation.md) — build from source and declare the dependency
3. [Quick Start](03-quick-start.md) — your first class and interface proxies
4. [Interceptor API](04-interceptor-api.md) — the `Interceptor` contract, `invokeSuper`, return values, and exceptions
5. [Method Grouping](05-method-grouping.md) — bind different interceptors to different methods with `Group`
6. [Annotation-Driven API](06-annotation-api.md) — declarative matching with `@Intercept` / `@Around`
7. [Constructor Interception](07-constructor-interception.md) — hooks around the superclass constructor
8. [Static Method Proxy](08-static-method-proxy.md) — shadowing `public static` methods
9. [Multi-Interface Proxy](09-multi-interface-proxy.md) — one proxy, several interfaces (including non-public ones)
10. [Hot Reload / Hot Swap](10-hot-reload.md) — `evict`, `evictClassLoader`, and `rebind`
11. [JPMS / Strong Encapsulation](11-jpms.md) — proxying classes in encapsulated modules
12. [Migration](12-migration.md) — move from CGLib and `java.lang.reflect.Proxy`
13. [Performance & Benchmarking](13-performance.md) — what the benchmarks show and how to run them

## Related documents

- [Benchmark Results](../benchmark-results.md)
- [Migration Guide](../migration-guide.md)
- [Main README](../../README.md)

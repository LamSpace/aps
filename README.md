# 🚀 APS — Accelerated Proxy Solution

[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)
[![Java](https://img.shields.io/badge/java-25%2B-orange)](https://jdk.java.net/)
[![JMH](https://img.shields.io/badge/benchmark-JMH%201.37-red)](https://github.com/openjdk/jmh)

[中文版](README_CN.md) | [User Guide](docs/guide/README.md) | [Benchmark Results](docs/benchmark-results.md)

A high-performance dynamic proxy library for Java. APS generates proxy classes at runtime with ASM and dispatches every intercepted call through a **hashCode-driven switch with a direct `INVOKESPECIAL` super call** — no reflection, no
`MethodHandle`, JIT-inlinable. Interface proxies run at `java.lang.reflect.Proxy`
parity; default methods are ~6.5× faster.

## ✨ Why APS

- **Direct `super` dispatch** — `invokeSuper` compiles to a direct
  `super.method(args)`; no reflection, no `MethodHandle`, JIT-inlinable.
- **Beats CGLib by ~3–5×** on class proxies; interface proxies at
  `java.lang.reflect.Proxy` parity and ~6× faster on default methods.
- **One API for classes and interfaces** — `AcceleratedProxy.proxy(...)` with generic type inference, no casts.
- **GC-safe** — proxy classes use `Lookup.defineHiddenClass()`, so there is no
  `ClassLoader` leak.

## 📋 Features

**Core**

- Unified `AcceleratedProxy.proxy(target, interceptor)` entry point for classes *and* interfaces
- Functional `Interceptor` API — a single-method interface, use a lambda
- `invokeSuper(proxy, method, args)` for zero-overhead super dispatch
- `WeakCache`-backed proxy-class caching keyed on the method-to-interceptor mapping

**Selective interception**

- `Group.of(predicate, interceptor)` + `Group.otherwise(...)` — first-match-wins with zero hot-path overhead
- Methods matching no group pass through with **zero** interception cost

**Proxy capabilities**

- **Interface proxy** — runtime interface implementations without reflection
- **Multi-interface proxy** — one object, several interfaces, with conflict detection
- **Non-public interface proxy** — package-private interfaces, defined in the interface's own package
- **Constructor arguments** — proxy classes without a no-arg constructor
- **Constructor interception** — `ConstructorInterceptor` hooks before/after the superclass constructor, with argument rewriting and veto
- **Static method proxy** — `proxyStatic` returns a class shadowing `public static` methods
- **Annotation-driven API** — `@Intercept` / `@Around` declarative matching at lambda speed
- **Hot reload / hot swap** — `evict` / `evictClassLoader` for hot-deployed classes, `rebind` to swap interceptors on a live instance

## ⚡ Quick Start

### Class proxy

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
    System.out.println("before " + method.getName());
    Object result = AcceleratedProxy.invokeSuper(obj, method, args);
    System.out.println("after " + method.getName());
    return result;
});

String greeting = proxy.hello("World");
// before hello
// after hello
// greeting == "Hello, World"
```

### Interface proxy

```java
Calculator calc = AcceleratedProxy.proxy(Calculator.class, (obj, method, args) -> {
    System.out.println("calling " + method.getName());
    return (int) args[0] + (int) args[1];
});

int result = calc.add(10, 20);   // 30
```

### Method grouping

```java
Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
        Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
        Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
        Group.otherwise(fallbackInterceptor));
```

### Annotation-driven

```java

@Intercept
class MetricsInterceptor {
    @Around("get*")
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
```

## 📊 Performance

JMH benchmarks on Java 25 (all scores in ns/op, lower is better). Full tables, methodology, and run instructions: [docs/benchmark-results.md](docs/benchmark-results.md).

- **Class proxies beat CGLib by ~3–5×** on scenarios with actual work; unmatched methods run at direct-call speed.
- **Interface proxies** run at parity with `java.lang.reflect.Proxy` and are **~6× faster** on default methods.
- **Multi-interceptor (`Group`)** has byte-identical hot paths to the single-interceptor API — **zero** degradation.
- **Annotation-driven** interception reaches hand-written-lambda parity.

## 🏗️ How it works

1. `AcceleratedProxy.proxy(...)` matches each proxyable method to an interceptor via a `Group` chain.
2. A generator emits bytecode: one `_interceptor$N` field per distinct interceptor, one override per method, and a `dispatch(Method, Object[])` method.
3. On each call, the override boxes the arguments and calls `Interceptor.intercept(...)`. If the interceptor calls `invokeSuper`, `dispatch()` branches on `method.hashCode()` and jumps straight to `INVOKESPECIAL super.method(...)`.

The key insight: dispatch uses a deterministic `Method.hashCode()` to build an if-else chain whose branches are **direct `super` calls** — no reflection, no
`MethodHandle`, fully JIT-inlinable. See the [user guide](docs/guide/README.md)
for the full picture.

## 📋 Requirements

- Java 25+
- ASM 9.7.1 (compile dependency)

## 🧩 JPMS / Strong Encapsulation

Class proxies are defined in the target's package via `MethodHandles.privateLookupIn`. If the target lives in a strongly encapsulated module (any non-`open` package, including `java.base` packages such as `java.util`), `proxy()` fails fast with an actionable `--add-opens` hint. Interface proxies use a public lookup and support
`public` interfaces only (same as `java.lang.reflect.Proxy`). See
[JPMS](docs/guide/11-jpms.md).

## 📦 Installation

```bash
git clone https://github.com/lamspace/aps.git
cd aps
mvn install -DskipTests
```

Maven Central publishing is in progress; until then, depend on the artifact from your local repository:

```xml

<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>aps</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 🆚 APS vs the alternatives

| Feature                  | APS                    | CGLib                       | `java.lang.reflect.Proxy` |
|--------------------------|------------------------|-----------------------------|---------------------------|
| Proxies concrete classes | ✅                     | ✅                          | ❌                        |
| Super-call mechanism     | direct `INVOKESPECIAL` | `MethodProxy` + `FastClass` | N/A                       |
| GC-safe (hidden class)   | ✅                     | ❌                          | ✅                        |
| Selective interception   | ✅ `Group.of`          | ✅ `CallbackFilter`         | ❌                        |
| Multi-interface proxy    | ✅                     | ❌                          | ✅                        |
| Constructor interception | ✅                     | ✅                          | ❌                        |
| Static method proxy      | ✅                     | ❌                          | ❌                        |
| Hot reload / rebind      | ✅                     | ❌                          | ❌                        |
| Annotation-driven API    | ✅                     | ❌                          | ❌                        |
| Functional API           | ✅ lambda              | ✅                          | ✅                        |
| Maven Central            | Coming soon            | ✅                          | Built-in                  |

## 📖 Documentation

- [User Guide](docs/guide/README.md) — 13 chapters with runnable examples
- [Benchmark Results (EN)](docs/benchmark-results.md) / [中文](docs/benchmark-results_cn.md)
- [Migration Guide](docs/migration-guide.md)

## 📄 License

Apache License 2.0

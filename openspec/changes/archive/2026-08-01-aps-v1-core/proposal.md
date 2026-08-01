## Why

CGLib is the de-facto standard for class-based dynamic proxies in the Java ecosystem, relied upon by Spring, Hibernate,
MyBatis, and countless other frameworks. However, it uses ASM + `Method.invoke` reflection for method dispatching, which
incurs measurable overhead on every proxy call. Java 15+ introduced `MethodHandles.Lookup.defineHiddenClass()` (no
ClassLoader leaks, GC-eligible) and MethodHandle offers invocation performance approaching direct calls. The time is
right for a modern alternative that matches CGLib's capability set while delivering significantly better performance.

## What Changes

- Introduce a new dynamic proxy library (APS) capable of proxying concrete classes (not just interfaces)
- Replace CGLib's `Method.invoke` dispatch with pre-computed MethodHandle super-call bindings
- Use `Lookup.defineHiddenClass()` for class loading instead of custom ClassLoaders
- Provide a simple, familiar API: a single functional-interface `Callback` (matching the `InvocationHandler` /
  `MethodInterceptor` mental model)
- Support optional method filtering (`ClassFilter`) for zero-overhead non-intercept paths
- Support proxying classes without default constructors

## Capabilities

### New Capabilities

- `aps-core`: MethodHandle-powered class-based dynamic proxy engine — subclass generation via ASM, hidden-class loading
  via `Lookup.defineHiddenClass()`, single-Callback interception model with MethodHandle super-call binding

### Modified Capabilities

<!-- No existing capabilities to modify — this is a new project -->

## Impact

- **New project** — no existing code, APIs, or dependencies affected
- **Dependencies:** ASM 9.7.1 (bytecode generation), JUnit 5.11 (test), JMH 1.37 (benchmarks)
- **Target runtime:** Java 25+ (uses `defineHiddenClass` and MethodHandle APIs)
- **Package:** `io.github.lamspace`

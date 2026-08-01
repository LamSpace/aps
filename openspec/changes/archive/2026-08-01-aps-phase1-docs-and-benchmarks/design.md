## Context

See proposal.md for motivation. APS v1 core is implemented and tested. The codebase uses Maven + Java 25, ASM 9.7.1 for bytecode generation, JUnit 5 + JMH 1.37 for testing. No existing CGLib comparison exists — the current `ProxyBenchmark.java` only compares APS against Java Proxy, and both skip the actual super-method invocation (measuring "null proxy" rather than real dispatch).

## Goals / Non-Goals

**Goals:**
- Produce real, reproducible JMH data showing APS vs CGLib vs Java Proxy vs direct call across 6 scenarios
- Fill the empty README.md with quick-start code, performance table, and CGLib comparison
- Achieve zero-warning `mvn javadoc:javadoc`
- Provide step-by-step migration examples from both CGLib and Java Proxy to APS

**Non-Goals:**
- New APS features or API changes
- Publishing to Maven Central
- CI/CD setup
- Any code changes outside docs, pom.xml, and benchmark test file

## Decisions

### 1. Benchmark structure: 4 implementations × 6 scenarios

Chose 6 inner `@State` classes, each with 4 `@Benchmark` methods (24 total). Each state class sets up all 4 proxy approaches for one scenario. This keeps each scenario self-contained — setup isolation prevents cross-contamination — and the method naming convention (`noop_direct`, `noop_aps`, etc.) makes the JMH output table self-documenting.

**Alternatives considered:**
- `@Param` annotation to switch between impls → rejected because `@Param` works poorly with complex setup logic (different proxy creation APIs for each impl)
- 6 separate benchmark classes → rejected because it scatters related code without benefit
- Fewer scenarios → rejected because the user explicitly requested multi-dimensional comparison

### 2. CGLib API usage in benchmarks

Each scenario benchmark uses the real CGLib API: `Enhancer.create()`, `MethodInterceptor`, `MethodProxy.invokeSuper()`. This ensures fair comparison — CGLib pays its full dispatch cost just like APS pays `superHandle.invoke()`.

For Java Proxy passthrough scenarios, `method.invoke(new Impl(), args1)` is used (fresh target instance each call). This is the standard Java Proxy pattern — there is no built-in "invoke super" for interface proxies.

### 3. CGLib dependency: test scope only

CGLib is added as `test` scope dependency — it is only needed for benchmarks, never shipped with APS. The APS library itself has no dependency on CGLib.

### 4. README structure

Follows the design spec exactly: core features → 5-second quick-start → performance table → requirements → installation → CGLib comparison → doc links. The performance table embeds actual JMH numbers from Task 3. A placeholder "X.X× faster" summary is filled with real data.

### 5. Javadoc scope

Only cover public API: `APS.java` (enhance existing), `Callback.java` (already complete), `ClassFilter.java` (already complete), `ClassGenerator.java` (has class-level, add method-level), `MethodDispatcher.java` (has class-level, add method-level), `ClinitRegistry.java` (has class-level), `HiddenClassLoader.java` (has class-level, add method-level), `LookupManager.java` (has class-level, add method-level). Plus 4 `package-info.java`. Internal classes like `ClinitRegistry.Entry` are package-private — no Javadoc needed.

### 6. Migration guide structure

Two independent sections: CGLib→APS and Java Proxy→APS. Each shows before/after code side-by-side. A feature comparison table at the bottom lets users quickly check what APS supports vs competitors.

## Risks / Trade-offs

- **CGLib 3.3.0 may fail on Java 25** → Mitigation: CGLib 3.3.0 is the latest stable release and targets modern JDKs. If it fails, use `cglib-nodep` with updated ASM, or skip CGLib in benchmarks and note the limitation.
- **JMH numbers vary by hardware** → Mitigation: Document the hardware and JDK version alongside results. Relative ratios (APS vs CGLib) are more stable than absolute numbers.
- **READEME performance numbers go stale** → Mitigation: Pin results to the JMH run date and JDK version. Future runs can update the table.

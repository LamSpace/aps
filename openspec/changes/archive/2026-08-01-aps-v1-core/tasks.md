## 1. Project Setup

- [x] 1.1 Add ASM 9.7.1, JUnit 5.11, JMH 1.37 dependencies to pom.xml
- [x] 1.2 Remove skeleton Main.java
- [x] 1.3 Create package directories (generator, loader, internal)
- [x] 1.4 Verify `mvn compile` succeeds with empty sources

## 2. Core API Interfaces

- [x] 2.1 Implement `Callback` functional interface
  (`intercept(Object proxy, Method method, MethodHandle superHandle, Object[] args)`)
- [x] 2.2 Implement `ClassFilter` functional interface (`accept(Method method)`)
- [x] 2.3 Verify `mvn compile` succeeds

## 3. Access Control

- [x] 3.1 Implement `LookupManager.getLookup(Class<?>)` with `privateLookupIn` first, fallback to `MethodHandles.lookup()`
  `MethodHandles.lookup()`
- [x] 3.2 Verify `mvn compile` succeeds

## 4. Hidden Class Loading

- [x] 4.1 Write `MinimalClassGenerator` test utility
- [x] 4.2 Write `HiddenClassLoaderTest`
- [x] 4.3 Implement `HiddenClassLoader.defineClass()`
  `Lookup.defineHiddenClass(byte[], true)`
- [x] 4.4 Run tests: `mvn test -Dtest=HiddenClassLoaderTest`

## 5. Method Dispatch Generation

- [x] 5.1 Implement `ClinitRegistry`
- [x] 5.2 Write `MethodDispatcherTest`
- [x] 5.3 Write `MethodDispatcher.dispatchMethods()` implementation
- [x] 5.4 Implement `MethodDispatcher.dispatchMethods()`
  clinit entries
- [x] 5.5 Run tests: `mvn test -Dtest=MethodDispatcherTest`

## 6. Subclass Generation

- [x] 6.1 Write `ClassGeneratorTest`
- [x] 6.2 Implement `ClassGenerator`
- [x] 6.3 Implement `generateClinitStatic()`
  `getDeclaredMethod`)
- [x] 6.4 Run tests: `mvn test -Dtest=ClassGeneratorTest`

## 7. Public API & Integration

- [x] 7.1 Write `APSFunctionalTest`
  RuntimeException, checked exception wrapping
- [x] 7.2 Implement `APS.create()` methods
- [x] 7.3 Run tests: `mvn test -Dtest=APSFunctionalTest`
- [x] 7.4 Run full test suite: `mvn test`

## 8. No-Default-Constructor Support

- [x] 8.1 Add test for proxying class without default constructor
- [x] 8.2 Extend `ClassGenerator` for constructor arguments
- [x] 8.3 Add `APS.create()` constructor args overload
- [x] 8.4 Run full test suite: `mvn test`

## 9. Performance Benchmarks

- [x] 9.1 Create `ProxyBenchmark`
- [x] 9.2 Verify benchmarks compile and run
- [x] 9.3 Document baseline numbers (see JMH output)

## 10. Final Verification

- [x] 10.1 Run `mvn test` — all tests green
- [x] 10.2 Run `mvn compile -Xlint:all` — clean
- [x] 10.3 Review git log — 4 focused commits

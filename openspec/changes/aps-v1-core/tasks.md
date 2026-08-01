## 1. Project Setup

- [ ] 1.1 Add ASM 9.7.1, JUnit 5.11, JMH 1.37 dependencies to pom.xml
- [ ] 1.2 Remove skeleton Main.java
- [ ] 1.3 Create package directories (generator, loader, internal)
- [ ] 1.4 Verify `mvn compile` succeeds with empty sources

## 2. Core API Interfaces

- [ ] 2.1 Implement `Callback` functional interface (`intercept(Object proxy, Method method, MethodHandle superHandle, Object[] args)`)
- [ ] 2.2 Implement `ClassFilter` functional interface (`accept(Method method)`)
- [ ] 2.3 Verify `mvn compile` succeeds

## 3. Access Control

- [ ] 3.1 Implement `LookupManager.getLookup(Class<?>)` with `privateLookupIn` first, fallback to `MethodHandles.lookup()`
- [ ] 3.2 Verify `mvn compile` succeeds

## 4. Hidden Class Loading

- [ ] 4.1 Write `MinimalClassGenerator` test utility (generates minimal valid Object subclass via ASM)
- [ ] 4.2 Write `HiddenClassLoaderTest` — should define hidden class, should create instance
- [ ] 4.3 Implement `HiddenClassLoader.defineClass(Class<?> targetClass, byte[] bytecode)` wrapping `Lookup.defineHiddenClass(byte[], true)`
- [ ] 4.4 Run tests: `mvn test -Dtest=HiddenClassLoaderTest`

## 5. Method Dispatch Generation

- [ ] 5.1 Implement `ClinitRegistry` — collects (targetClass, method, generatedInternal, fieldNames) tuples
- [ ] 5.2 Write `MethodDispatcherTest` — should generate overrides for non-final instance methods, skip final/static
- [ ] 5.3 Write `TestBytecodeBuilder` test helper
- [ ] 5.4 Implement `MethodDispatcher.dispatchMethods()` — generates static fields + method override bodies + registers clinit entries
- [ ] 5.5 Run tests: `mvn test -Dtest=MethodDispatcherTest`

## 6. Subclass Generation

- [ ] 6.1 Write `ClassGeneratorTest` — should generate valid bytecode, skip final methods, respect ClassFilter
- [ ] 6.2 Implement `ClassGenerator` — constructor, `generate()`, `constructorArgs()`
- [ ] 6.3 Implement `generateClinitStatic()` — emits `<clinit>` from `ClinitRegistry` entries (`Lookup.findSpecial` + `getDeclaredMethod`)
- [ ] 6.4 Run tests: `mvn test -Dtest=ClassGeneratorTest`

## 7. Public API & Integration

- [ ] 7.1 Write `APSFunctionalTest` — intercept + modify, pass-through, primitive return, void method, ClassFilter, RuntimeException, checked exception wrapping
- [ ] 7.2 Implement `APS.create(Class<T>, Callback)` and `APS.create(Class<T>, Callback, ClassFilter)`
- [ ] 7.3 Run tests: `mvn test -Dtest=APSFunctionalTest`
- [ ] 7.4 Run full test suite: `mvn test`

## 8. No-Default-Constructor Support

- [ ] 8.1 Add test for proxying class without default constructor (bean with String name arg)
- [ ] 8.2 Extend `ClassGenerator` to accept and forward constructor arguments to super()
- [ ] 8.3 Add `APS.create(Class<T>, Callback, ClassFilter, Object... constructorArgs)` overload
- [ ] 8.4 Run full test suite: `mvn test`

## 9. Performance Benchmarks

- [ ] 9.1 Create `ProxyBenchmark` — direct call vs Java Proxy vs APS proxy vs direct concrete
- [ ] 9.2 Verify benchmarks compile and run (spot-check with `-wi 1 -i 2 -f 1`)
- [ ] 9.3 Document baseline numbers in benchmark output

## 10. Final Verification

- [ ] 10.1 Run `mvn test` — all tests green
- [ ] 10.2 Run `mvn compile -Xlint:all` — no warnings
- [ ] 10.3 Review git log for clean, focused commit history

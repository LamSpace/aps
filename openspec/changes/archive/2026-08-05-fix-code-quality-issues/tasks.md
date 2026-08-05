## 1. Core Fixes

- [x] 1.1 Make `ClinitRegistry` instance-based (not static) — convert `entries`, `register()`, `drain()` to instance members; instantiate per `generate()` call in `ClassGenerator` and `InterfaceGenerator`; pass instance to `MethodDispatcher.dispatchMethods()` and `InterfaceDispatcher.dispatchMethods()`
- [x] 1.2 Integrate `WeakCache` into `AcceleratedProxy.proxy()` — add `CacheParams` record as composite key `{targetClass, filter, constructorArgs}`; wrap bytecode generation + class loading in `WeakCache.get()`; use `WeakReference`-based eviction for GC safety
- [x] 1.3 Fix null constructor argument handling in `ClassGenerator.findConstructor()` — skip `isAssignableFrom` check when `constructorArgs[i]` is null and target param is a reference type; reject null for primitive params
- [x] 1.4 Add logging to `LookupManager` fallback path — log a `WARNING` when `privateLookupIn` fails and fallback is used; improve error message in `AcceleratedProxy.proxy()` catch block to include root cause
- [x] 1.5 Minor cleanups — remove unused `methodCount` parameter from `ClassGenerator.generateClinit()`; fix step numbering in `MethodDispatcher` comments (step 6 → 6); harmonize static field naming in `MethodDispatcher` to include method name (match `InterfaceDispatcher` pattern `_method$name$index`)

## 2. Test Hygiene

- [x] 2.1 Rename `APSClassProxyTest` class to `AcceleratedProxyClassProxyTest` (file already has correct name); rename `APSInterfaceProxyTest` to `AcceleratedProxyInterfaceProxyTest`

## 3. New Unit Tests

- [x] 3.1 Write `WeakCacheTest` — test put/get, cache hit on repeated key, GC eviction of weakly-referenced values, `containsValue()`, `size()`, thread-safety of `get()`
- [x] 3.2 Write `ClinitRegistryTest` — test register/drain lifecycle (register → drain returns entries → cleared), instance isolation (two instances have independent entries)
- [x] 3.3 Write `LookupManagerTest` — test `getLookup()` returns non-null `MethodHandles.Lookup` for standard classes; verify fallback path when module is not open
- [x] 3.4 Write `BytecodeUtilsTest` — test `pushInt` for all value ranges (-1, 0, 5, 6, 127, 128, 32767, 32768); test `loadOpcode` for each primitive type; test `boxPrimitive`/`unboxPrimitive` type coverage
- [x] 3.5 Write `DispatchGeneratorTest` — test `methodDispatchHash` determinism (same method → same hash); test hash differs for overloaded methods; test `resolveHashes` collision detection and fallback

## 4. Verification

- [x] 4.1 Run full unit test suite — `mvn test -s /home/lam/repo/settings.xml`; all tests pass with no regressions
- [x] 4.2 Run JMH benchmark — execute `ProxyBenchmark.main()` or `java -jar target/benchmarks.jar`; confirm no performance regression in dispatch hot path
- [x] 4.3 Verify cache behavior — write a targeted test or manual check confirming repeated `proxy()` calls with same params reuse the cached class (class identity check, not value equality)

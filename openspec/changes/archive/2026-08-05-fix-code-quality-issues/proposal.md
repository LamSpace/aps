## Why

Code review identified 9 issues in the OpenProxy codebase: a critical functional gap (WeakCache implemented but not integrated, violating the `openproxy-core` and `openproxy-unified-proxy` caching specification), correctness edge cases (null constructor arguments break `findConstructor`, `ClinitRegistry` is not thread-safe), test hygiene problems (class names mismatching file names), and missing test coverage for internal components (`WeakCache`, `ClinitRegistry`, `LookupManager`, `BytecodeUtils`, `DispatchGenerator`). Fixing these ensures the codebase meets its own spec requirements and is production-ready.

## What Changes

- **Integrate WeakCache** into `AcceleratedProxy.proxy()` so repeated proxy creation with the same `{targetClass, filter, constructorArgs}` reuses cached proxy classes instead of generating new bytecode every time
- **Fix null constructor argument handling** in `ClassGenerator.constructorArgs()` and `findConstructor()` so `null` values are accepted as valid constructor arguments for nullable parameters
- **Make ClinitRegistry thread-safe** by using per-generator instance state instead of a static `ArrayList` shared across threads
- **Improve LookupManager fallback** — add warning logging and better error messages when module access is denied
- **Rename test classes** to match current API names (`AcceleratedProxyClassProxyTest`, `AcceleratedProxyInterfaceProxyTest`)
- **Add unit tests** for `WeakCache`, `ClinitRegistry`, `LookupManager`, `BytecodeUtils`, and `DispatchGenerator`
- **Remove unused `methodCount` parameter** from `ClassGenerator.generateClinit()`
- **Fix step numbering** in `MethodDispatcher` comments
- **Harmonize field naming** between `MethodDispatcher` and `InterfaceDispatcher`

## Capabilities

### New Capabilities

<!-- None — this is a bug-fix and implementation completion change. Existing specs already define caching and behavior requirements. -->

### Modified Capabilities

<!-- None — spec requirements are unchanged. Only the implementation is being fixed to match existing specs. -->

## Impact

- **Affected code**: `AcceleratedProxy.java`, `ClassGenerator.java`, `ClinitRegistry.java`, `LookupManager.java`, `MethodDispatcher.java`, `InterfaceDispatcher.java`, test files
- **New tests**: ~5 new test classes for previously untested internal components
- **Breaking changes**: None — all public APIs remain identical
- **Risk**: Low — changes are focused on filling implementation gaps and fixing edge cases; existing 41 tests pass

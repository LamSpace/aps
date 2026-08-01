## Context

APS currently generates subclasses of concrete classes using ASM bytecode generation with `ClassGenerator` (orchestration) and `MethodDispatcher` (method bodies). The generated class extends the target class, the constructor delegates to `super(args)`, and `<clinit>` pre-computes `findSpecial` MethodHandles for super-method invocation. Interface support requires a parallel but distinct generation path: the generated class extends `Object` and `implements` the interface, with no `super()` args and no MethodHandle binding in `<clinit>`.

## Goals / Non-Goals

**Goals:**
- Generate `implements Interface` bytecode alongside existing `extends Class` bytecode
- Provide a 3-arg `InterfaceCallback` (no superHandle) distinct from the 4-arg `Callback`
- Reuse shared infrastructure: `ClinitRegistry`, `HiddenClassLoader`, `LookupManager`
- Extract shared bytecode helpers (`pushInt`, `boxPrimitive`, etc.) to avoid duplication

**Non-Goals:**
- Multiple interface implementation (`implements A, B, C`)
- Default method superHandle invocation
- Static or private interface methods
- Auto-detecting interface vs class in `create()` (explicit `createInterface` keeps API clear)

## Decisions

### Decision 1: Separate generator classes rather than parameterizing ClassGenerator

**Choice:** New `InterfaceGenerator` and `InterfaceDispatcher` classes, parallel to `ClassGenerator`/`MethodDispatcher`.

**Rationale:** The two generation paths differ at every level — superclass (`Object` vs `TargetClass`), constructor (always no-arg vs configurable), `<clinit>` (Method-only vs Method+MethodHandle), and method bodies (3-arg callback vs 4-arg callback with superHandle). Parameterizing `ClassGenerator` with conditionals for all of these would create a tangled control flow. Separate classes keep each path linear and easy to reason about.

**Alternative considered:** Add an `isInterface` flag to `ClassGenerator` with conditional branches. Rejected — the differences are structural, not configurational.

### Decision 2: Extract shared bytecode utilities to BytecodeUtils

**Choice:** New package-private `BytecodeUtils` class with static helpers: `pushInt`, `loadOpcode`, `boxPrimitive`, `unboxPrimitive`, `pushClassConstant`.

**Rationale:** `pushInt` and `pushClassConstant` were already duplicated between `ClassGenerator` and `MethodDispatcher`. Adding `InterfaceDispatcher` would create a third copy. Extraction eliminates duplication without changing any public API.

**Alternative considered:** Copy helpers into `InterfaceDispatcher`. Rejected — three copies of the same methods is maintenance debt.

### Decision 3: Filter-rejected interface methods throw AbstractMethodError

**Choice:** When `ClassFilter` rejects an interface method, the generated body throws `AbstractMethodError`.

**Rationale:** Unlike class proxies (where filter-rejected methods call `super.method()` directly), interface proxies have no fallback implementation. Throwing `AbstractMethodError` is the standard JVM behavior for unimplemented interface methods and clearly communicates the issue.

**Alternative considered:** Ignore the filter for interface proxies (always intercept all methods). Rejected — inconsistent with the class-proxy API and removes user choice.

### Decision 4: `interfaceClass.getMethods()` for method discovery

**Choice:** Use `getMethods()` (returns all public methods including inherited) rather than `getDeclaredMethods()`.

**Rationale:** Interface methods are all public by definition. `getMethods()` correctly includes methods from superinterfaces, and also surfaces Object methods (`toString`, `hashCode`, `equals`) that Java's own `java.lang.reflect.Proxy` also intercepts. Final methods are filtered out with a `Modifier.isFinal` check, matching the pattern in `MethodDispatcher`.

## Risks / Trade-offs

- [Risk] `getMethods()` may include unexpected Object methods → Mitigation: `Modifier.isFinal` check filters out `getClass`, `notify`, `notifyAll`, and final `wait` variants
- [Risk] `ClinitRegistry` is a static global — concurrent `generate()` calls from different threads could mix entries → Mitigation: Same risk exists in current codebase; each `generate()` call drains the registry. Not a regression.

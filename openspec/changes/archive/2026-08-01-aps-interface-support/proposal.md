## Why

APS currently only supports proxying concrete classes (generating subclasses). Users needing interface proxies must fall back to JDK's `java.lang.reflect.Proxy` — which uses reflection-based dispatch, undermining APS's core value of MethodHandle-based performance. Adding interface support makes APS a unified alternative to both JDK Proxy and CGLib in a single API.

## What Changes

- New `InterfaceCallback` functional interface — a 3-arg callback (proxy, method, args) for interface proxies, mirroring `java.lang.reflect.InvocationHandler`
- New `AcceleratedProxy.createInterface()` public entry point — generates and returns an interface proxy implementation
- New `InterfaceGenerator` and `InterfaceDispatcher` — ASM-based bytecode generators for `implements Interface` (vs existing `extends Class`)
- Shared bytecode utilities extracted from `MethodDispatcher` and `ClassGenerator` into `BytecodeUtils` to avoid duplication
- No changes to existing `Callback`, `AcceleratedProxy.create()`, or class-proxy behavior

## Capabilities

### New Capabilities

- `aps-interface-proxy`: Generate runtime proxy implementations for Java interfaces, routing all method calls through a user-provided `InterfaceCallback` handler backed by MethodHandle-based dispatch (not reflection)

### Modified Capabilities

<!-- None — existing class-proxy behavior is unchanged -->

## Impact

- **New files:** `InterfaceCallback.java`, `InterfaceDispatcher.java`, `InterfaceGenerator.java`, `BytecodeUtils.java`
- **Modified files:** `APS.java` (add `createInterface()` overloads), `MethodDispatcher.java` (delegate to BytecodeUtils), `ClassGenerator.java` (delegate to BytecodeUtils)
- **Dependencies:** No new external dependencies
- **API surface:** Two new public types (`InterfaceCallback`, `AcceleratedProxy.createInterface()` overloads); existing API unchanged

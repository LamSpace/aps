## Why

The `openproxy-unified-proxy` spec already requires hash collision detection at bytecode generation time ("Hash collision detection at generation time" scenario), but `DispatchGenerator.resolveHashes()` exists only as dead code — it is never called from `ClassGenerator.generate()` or `InterfaceGenerator.generate()`. Both generators use `computeHash()` directly with no collision check. Since `Method.hashCode()` equals `declaringClass.getName().hashCode() ^ methodName.hashCode()` (parameter types excluded), overloaded methods in the target class produce identical hashes, causing the if-else chain to silently route all colliding methods to the first matching branch and leave the rest unreachable.

## What Changes

- Wire `resolveHashes()` into the `ClassGenerator` and `InterfaceGenerator` generation pipelines so collisions are detected at generation time rather than silently ignored
- Fix the secondary hash formula: the current `hash * 31 + m.getName().hashCode()` fails for overloaded methods (same name, same secondary hash). Replace with a discriminator that includes parameter types (e.g., `method.toGenericString().hashCode()` or `Arrays.hashCode(method.getParameterTypes())`)
- Add unit tests covering overloaded methods (same name, different parameter lists) to verify correct dispatch for all methods
- Add unit tests for the case where a non-overloaded collision occurs (different method names with identical name hashCodes)

## Capabilities

### New Capabilities

<!-- No new capabilities — this change fixes existing behavior to match the already-specified requirement -->

### Modified Capabilities

- `openproxy-unified-proxy`: The "Hash collision detection at generation time" scenario is already specified but the implementation does not deliver it. No spec change needed — this change brings the code into compliance with the existing requirement.

## Impact

- `DispatchGenerator.java`: `resolveHashes()` called from generators; secondary hash formula changed
- `ClassGenerator.java`: use `resolveHashes()` output instead of raw `computeHash()`
- `InterfaceGenerator.java`: use `resolveHashes()` output instead of raw `computeHash()`
- `MethodInfo.java`: hash values may differ for overloaded methods (behavior change only for previously-broken cases)
- Test suite: new test cases for overloaded method dispatch
- No API changes, no breaking changes for any correctly-functioning proxy

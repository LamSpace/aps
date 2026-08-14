## Context

See `proposal.md` for motivation. The relevant current state:

- `AcceleratedProxy.invokeSuper` delegates to the generated class's `dispatch(Method, Object[])` method.
- In `DispatchGenerator.generateDispatch`, the interface-proxy branch emits `INVOKESPECIAL java/lang/Object` for `Object` methods and throws `AbstractMethodError` for every other method.
- The generated interface-proxy class directly `implements` the target interface plus `DispatchTarget`.
- JVM rule that shapes the approach: `INVOKESPECIAL` on an interface `default` method requires the interface **named in the symbolic reference** to be a *direct superinterface* of the current class. The named interface need not be the method's declaring interface — method resolution walks the superinterface chain to find the default implementation.

## Goals / Non-Goals

**Goals:**

- `invokeSuper` invokes the default implementation for default methods (both directly-declared and inherited) with zero `MethodHandle` overhead, using a single generated `INVOKESPECIAL`.

**Non-Goals:**

- Multi-interface proxies and cross-interface default-method conflict resolution (Phase 3 item 2).
- Bridge methods and covariant-override edge cases for interface defaults.
- Changing the `AbstractMethodError` behavior for non-default interface methods.

## Decisions

### Decision 1: One `INVOKESPECIAL` path for all default methods

Emit `INVOKESPECIAL <target-interface>.<method>` (ASM `itf = true`) for every `method.isDefault()` case. Because the generated class directly implements the target interface, the named interface is a direct superinterface; method resolution finds the default implementation whether it is declared directly on the target interface or inherited from a parent.

- **Alternative considered**: `findSpecial` fallback for inherited defaults (via a `DefaultMethodInvoker` helper). Rejected and removed — a `findSpecial`/`INVOKESPECIAL` whose symbolic reference names the *declaring* interface fails for inherited defaults (`IncompatibleClassChangeError`: declaring interface is not a direct superinterface). Naming the *target* interface instead makes the single `INVOKESPECIAL` correct for both cases, so the fallback adds code without adding capability.
- **Alternative considered**: `findSpecial` for *all* default methods. Rejected — slower (`MethodHandle` + `invokeWithArguments` boxing) than a direct `INVOKESPECIAL`, and unnecessary.

### Decision 2: Name the target interface via a new parameter

`generateDispatch` gains a `String interfaceInternalName` parameter (null for class proxies) so the interface-proxy branch can emit `INVOKESPECIAL` against the target interface. Class proxies keep the existing direct-super-call behavior via `superInternal`.

The generated branch is:

1. `isClassProxy || isObjectMethod` → direct super call (unchanged).
2. `method.isDefault()` → `INVOKESPECIAL interfaceInternalName.<method>` (new fast path).
3. otherwise → throw `AbstractMethodError` (unchanged).

`method.isDefault()` alone distinguishes default from non-default; no directly-declared-vs-inherited distinction is needed.

## Risks / Trade-offs

- **[Reliance on method resolution finding the inherited default through the named interface]** → Verified empirically (`findSpecial(Child, "inheritedGreet", ...)` resolves a default declared on `Parent`); the integration test exercises the generated-bytecode path for an inherited default directly.
- **[No change to the throw path for non-default methods]** → Existing test `invokeSuperShouldThrowForInterfaceMethod` continues to pass, preventing regression.

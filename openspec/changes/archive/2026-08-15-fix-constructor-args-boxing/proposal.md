## Why

`AcceleratedProxy.proxy(Class, Object[], Group...)` crashes with a `VerifyError` when a constructor argument is a boxed primitive (e.g. `42` → `Integer`). The generated proxy constructor emits a primitive load opcode (`ILOAD`/`LLOAD`/`FLOAD`/`DLOAD`) for a boxed, reference-typed local variable, so the JVM rejects the class. This was discovered while benchmarking constructor interception, which revealed the pre-existing constructor-arguments path is broken for boxed primitives.

## What Changes

- Fix `ClassGenerator.generateConstructor` so the non-intercepted constructor-arguments path loads each argument as a reference (`ALOAD`) and unboxes / casts it to the superclass constructor's declared parameter type — the same logic the new intercepted path already uses.
- The fix covers boxed primitives (`Integer`, `Long`, `Double`, `Float`, `Boolean`, `Byte`, `Character`, `Short`), reference types, and `null`.

## Capabilities

### New Capabilities

<!-- None — this corrects existing behavior. -->

### Modified Capabilities

- `aps-core`: the **No-default-constructor support** requirement now explicitly covers unboxing boxed primitive arguments and `null`/reference arguments, and its scenario uses the current `proxy(Class, Object[], Group...)` API.

## Impact

- **Code:** `io.github.lamspace.generator.ClassGenerator.generateConstructor` (non-intercepted branch only).
- **Tests:** new `ConstructorArgsTest` covering boxed primitive, reference, and `null` constructor arguments.
- **Docs:** `README.md`/`README_CN.md` already advertise "Constructor arguments" — no change needed, but the feature now actually works for boxed primitives.

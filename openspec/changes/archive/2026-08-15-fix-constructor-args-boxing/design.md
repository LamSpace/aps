## Context

`ClassGenerator.generateConstructor` builds the generated subclass constructor descriptor from the *value* types of `constructorArgs` (`arg.getClass()`, or `Object.class` for `null`), which are therefore always reference types. The non-intercepted super-call branch, however, emits a primitive load opcode (`ILOAD`/`LLOAD`/`FLOAD`/`DLOAD`) whenever the value type is a boxed wrapper (`Integer.class`, `Long.class`, …), so the generated class fails verification. The new intercepted path (added with constructor interception) already loads and unboxes correctly. See proposal.md for motivation.

## Goals / Non-Goals

**Goals:**

- Make `proxy(Class, Object[], Group...)` correctly delegate boxed primitive, reference, and `null` constructor arguments to `super(...)`.
- Reuse the same load-then-unbox approach the intercepted path uses, so the two paths stay conceptually aligned.

**Non-Goals:**

- Changing the constructor-argument matching rules (overload selection stays as-is via `findConstructor`).
- Changing the generated constructor's public descriptor (still keyed on value types).
- Touching the intercepted constructor path (already correct).

## Decisions

1. **Load as reference, then unbox/cast to the declared type.** For each argument, emit `ALOAD` (the value type is always a reference), then `BytecodeUtils.unboxPrimitive` for a primitive declared parameter or `CHECKCAST` for a reference declared parameter, exactly as `generateInterceptedConstructor` does. This replaces the buggy primitive-opcode branch.

2. **Uniform one-slot argument layout.** Because value types are always references, every constructor argument occupies one local-variable slot; the existing `long`/`double` two-slot branches are dead code and are removed.

3. **Inline the fix rather than extract a shared helper.** The non-intercepted path loads arguments from local-variable slots while the intercepted path loads them from the rewritten `Object[]` array, so the load step differs; only the ~4-line unbox/cast step is shared, which is not worth a new helper.

## Risks / Trade-offs

- **[Bytecode correctness] The unbox/cast sequence is the highest-risk code.** → Mitigation: reuse `BytecodeUtils.unboxPrimitive`; cover with tests across all boxed primitive types, references, and `null`.
- **[Regression] The reference-only path (e.g. `String`) currently works and must keep working.** → Mitigation: keep a reference-argument test and run the full suite.
- **[Scope] The spec's scenario previously used a stale `create`/`callback` API.** → Mitigation: the MODIFIED requirement updates it to the current `proxy(Class, Object[], Group...)` API.

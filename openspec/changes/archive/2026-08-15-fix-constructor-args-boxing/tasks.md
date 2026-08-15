## 1. Tests

- [x] 1.1 Add `ConstructorArgsTest` covering boxed primitive args (int/long/double/float/boolean/byte/char/short), a reference arg, and a `null` arg
- [x] 1.2 Run it to confirm the boxed-primitive cases fail with `VerifyError` (reproduces the bug)

## 2. Fix

- [x] 2.1 Replace the non-intercepted super-call branch in `ClassGenerator.generateConstructor` with load-`ALOAD`-then-unbox/`CHECKCAST` to the declared superclass constructor types
- [x] 2.2 Run `ConstructorArgsTest` — all pass
- [x] 2.3 Run the full suite (`mvn -s /home/lam/repo/settings.xml test`) — no regressions

## 3. Commit

- [x] 3.1 Commit the fix + tests

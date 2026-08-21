## 1. InterfaceCallback Functional Interface

- [x] 1.1 Create `InterfaceCallback.java` in `io.github.lamspace` package with `@FunctionalInterface` and 3-arg `intercept(Object proxy, Method method, Object[] args)` method
- [x] 1.2 Run `mvn -s /home/lam/repo/settings.xml compile` to verify compilation
- [x] 1.3 Commit: `git commit -m "feat: add InterfaceCallback functional interface"`

## 2. Shared Bytecode Utilities

- [x] 2.1 Create `BytecodeUtils.java` in `io.github.lamspace.generator` with static helpers: `pushInt`, `loadOpcode`, `boxPrimitive`, `unboxPrimitive`, `pushClassConstant`
- [x] 2.2 Refactor `MethodDispatcher.java` — replace private `pushInt`, `loadOpcode`, `boxPrimitive`, `unboxPrimitive` methods with `BytecodeUtils` calls; remove the now-unused private methods
- [x] 2.3 Refactor `ClassGenerator.java` — replace private `pushInt`, `pushClassConstant`, `getWrapperInternalName` methods with `BytecodeUtils` calls; remove the now-unused private methods
- [x] 2.4 Run `mvn -s /home/lam/repo/settings.xml test` — all 16 existing tests must pass
- [x] 2.5 Commit: `git commit -m "refactor: extract shared bytecode utilities to BytecodeUtils"`

## 3. InterfaceDispatcher

- [x] 3.1 Create `InterfaceDispatcher.java` in `io.github.lamspace.generator` with `dispatchMethods(ClassWriter, Class<?>, String, ClassFilter)` generating method bodies that call `InterfaceCallback.intercept(proxy, method, args)` for intercepted methods and `throw new AbstractMethodError` for filtered-out methods
- [x] 3.2 Ensure `getMethods()` is used (includes superinterface + Object methods) with `Modifier.isStatic` and `Modifier.isFinal` guards
- [x] 3.3 Run `mvn -s /home/lam/repo/settings.xml compile` to verify compilation
- [x] 3.4 Commit: `git commit -m "feat: add InterfaceDispatcher for interface method body generation"`

## 4. InterfaceGenerator

- [x] 4.1 Create `InterfaceGenerator.java` in `io.github.lamspace.generator` that generates `class X extends Object implements Interface` bytecode with constructor `super(); this._callback = cb` and `<clinit>` storing only `Method` objects (no MethodHandle)
- [x] 4.2 Run `mvn -s /home/lam/repo/settings.xml compile` to verify compilation
- [x] 4.3 Commit: `git commit -m "feat: add InterfaceGenerator for interface proxy bytecode"`

## 5. AcceleratedProxy.createInterface () Public API

- [x] 5.1 Add `createInterface(Class<T>, InterfaceCallback)` and `createInterface(Class<T>, InterfaceCallback, ClassFilter)` methods to `OpenProxy.java` with `isInterface()` validation and `InterfaceGenerator` wiring
- [x] 5.2 Run `mvn -s /home/lam/repo/settings.xml test` — existing tests must still pass
- [x] 5.3 Commit: `git commit -m "feat: add AcceleratedProxy.createInterface() for interface proxies"`

## 6. Integration Tests

- [x] 6.1 Create `APSInterfaceFunctionalTest.java` covering: noop callback, arg modification, primitive return, void methods, ClassFilter routing, default methods, non-interface rejection, null arg rejection, RuntimeException propagation, checked Exception wrapping, no-arg methods
- [x] 6.2 Run `mvn -s /home/lam/repo/settings.xml test` — all tests pass (16 existing + new interface tests)
- [x] 6.3 Commit: `git commit -m "test: add integration tests for AcceleratedProxy.createInterface()"`

## 7. Final Verification

- [x] 7.1 Run `mvn -s /home/lam/repo/settings.xml test` — confirm BUILD SUCCESS with all tests passing
- [x] 7.2 Run `git status` — confirm working tree clean

## Purpose

Replace per-call MethodHandle binding with an index-based static lookup table, eliminating the BoundMethodHandle allocation from the class proxy hot path.

## ADDED Requirements

### Requirement: Static MethodHandle dispatch table

The system SHALL pre-compute a `MethodHandle[]` array in the generated class's `<clinit>` initializer, where each element is the `findSpecial`-derived handle for one proxyable method, type-erased to `(Object, Object[])Object` via `asSpreader` and `asType`.

#### Scenario: Dispatch table populated at class load time

- **WHEN** the generated proxy class is loaded
- **THEN** a static `MethodHandle[]` field is populated with one handle per proxyable method
- **AND** each handle is ready for uniform invocation with signature `(Object, Object[])Object`

#### Scenario: No MethodHandle creation per call

- **WHEN** a proxy method is invoked
- **THEN** no `MethodHandle.bindTo`, `MethodHandle.asType`, or `MethodHandle.asSpreader` call occurs on the hot path
- **AND** the only MethodHandle operations are array indexing and `invoke`/`invokeExact`

### Requirement: Index-based Callback dispatching

The system SHALL pass an integer method index (not a MethodHandle) as the third parameter to `Callback.intercept(Object proxy, Method method, int index, Object[] args)`.

#### Scenario: Callback receives method index

- **WHEN** a proxy method is invoked
- **THEN** the callback's `intercept` method is called with `index` set to the zero-based position of the method in the proxy's dispatch table
- **AND** the index SHALL be stable across all invocations of the same proxy class

### Requirement: invokeSuper with index dispatch

The generated proxy class SHALL expose a `public Object invokeSuper(int index, Object[] args)` method that looks up the MethodHandle by index and invokes it with `this` as the receiver.

#### Scenario: Super invocation via index

- **WHEN** a callback calls `((GeneratedProxy) proxy).invokeSuper(0, args)`
- **THEN** the system looks up `_handles[0]` and calls `invoke(this, args)` on the pre-computed MethodHandle
- **AND** the original superclass method executes with the provided arguments
- **AND** no new MethodHandle instances are allocated

#### Scenario: Index out of bounds

- **WHEN** an invalid index is passed to `invokeSuper`
- **THEN** an `ArrayIndexOutOfBoundsException` is thrown

### Requirement: Uniform MethodHandle type signature

All MethodHandles in the dispatch table SHALL be type-erased to the common signature `(Object, Object[])Object` to enable uniform invocation from `invokeSuper`.

#### Scenario: Primitive return type adaptation

- **WHEN** the original method returns `int`
- **THEN** the dispatch table handle, after `asType` adaptation, returns `Object` (boxed `Integer`)
- **AND** the generated method override unboxes the result before returning to the caller

#### Scenario: Void return type adaptation

- **WHEN** the original method returns `void`
- **THEN** the dispatch table handle, after `asType` adaptation, returns `null`
- **AND** `invokeSuper` returns `null` to the callback

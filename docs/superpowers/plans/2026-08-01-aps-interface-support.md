# APS Interface Proxy Support — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Add interface proxy support to APS via `APS.createInterface()`, complementing the existing class proxy `APS.create()`.

**Architecture:** New `InterfaceGenerator` and `InterfaceDispatcher` mirror the existing `ClassGenerator`/`MethodDispatcher` pair, generating bytecode for `class X extends Object implements Interface` instead of `class X extends TargetClass`. Shared bytecode utilities (`pushInt`, `boxPrimitive`, etc.) are extracted to a new `BytecodeUtils` class to avoid duplication. The existing `Callback` interface is unchanged; interface proxies use a new 3-arg `InterfaceCallback` without `superHandle`.

**Tech Stack:** ASM 9.7.1, Java 25, JUnit 5, same as existing codebase.

## Global Constraints

- Packaging: `io.github.lamspace` (main API), `io.github.lamspace.generator` (bytecode generation)
- No new external dependencies
- Existing public API (`APS.create()`, `Callback`) must not change signature
- Tests mirror existing `APSFunctionalTest` patterns
- Maven settings: `-s /home/lam/repo/settings.xml`

---

### Task 1: Create InterfaceCallback functional interface

**Files:**
- Create: `src/main/java/io/github/lamspace/InterfaceCallback.java`

**Interfaces:**
- Produces: `InterfaceCallback.intercept(Object proxy, Method method, Object[] args) throws Throwable`

- [ ] **Step 1: Write InterfaceCallback.java**

```java
package io.github.lamspace;

import java.lang.reflect.Method;

/**
 * Intercepts method calls on an interface proxy instance.
 * A single InterfaceCallback receives all method invocations for
 * interface proxies — mirroring the model of
 * {@link java.lang.reflect.InvocationHandler} but backed by
 * MethodHandle-based dispatch instead of reflection.
 *
 * <p>Unlike {@link Callback} (used for class proxies), there is no
 * {@code superHandle} parameter — interface methods have no super
 * implementation to delegate to.
 */
@FunctionalInterface
public interface InterfaceCallback {

    /**
     * Called for every method invocation on the interface proxy.
     *
     * @param proxy  the proxy instance
     * @param method the intercepted method (for metadata: name, annotations, etc.)
     * @param args   the method arguments, boxed; empty array for no-arg methods
     * @return the method's return value (null for void methods, boxed for primitives)
     * @throws Throwable any throwable the interceptor wishes to propagate
     */
    Object intercept(Object proxy, Method method, Object[] args) throws Throwable;
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/InterfaceCallback.java
git commit -m "feat: add InterfaceCallback functional interface"
```

---

### Task 2: Extract shared bytecode utilities

**Files:**
- Create: `src/main/java/io/github/lamspace/generator/BytecodeUtils.java`
- Modify: `src/main/java/io/github/lamspace/generator/MethodDispatcher.java` — replace private helpers with BytecodeUtils calls
- Modify: `src/main/java/io/github/lamspace/generator/ClassGenerator.java` — replace private `pushInt`, `pushClassConstant`, `getWrapperInternalName` with BytecodeUtils calls

**Interfaces:**
- Produces:
  - `BytecodeUtils.pushInt(MethodVisitor, int)` — push int constant
  - `BytecodeUtils.loadOpcode(Class<?>)` — get load instruction for type
  - `BytecodeUtils.boxPrimitive(MethodVisitor, Class<?>)` — box primitive on stack
  - `BytecodeUtils.unboxPrimitive(MethodVisitor, Class<?>)` — unbox wrapper on stack
  - `BytecodeUtils.pushClassConstant(MethodVisitor, Class<?>)` — push Class constant (uses TYPE field for primitives)

- [ ] **Step 1: Create BytecodeUtils.java**

```java
package io.github.lamspace.generator;

import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

/**
 * Shared bytecode emission helpers used by both class and interface
 * proxy generators. All methods are static and stateless.
 */
final class BytecodeUtils {

    private BytecodeUtils() {}

    static void pushInt(MethodVisitor mv, int value) {
        if (value >= -1 && value <= 5) {
            mv.visitInsn(Opcodes.ICONST_0 + value);
        } else if (value >= Byte.MIN_VALUE && value <= Byte.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.BIPUSH, value);
        } else if (value >= Short.MIN_VALUE && value <= Short.MAX_VALUE) {
            mv.visitIntInsn(Opcodes.SIPUSH, value);
        } else {
            mv.visitLdcInsn(value);
        }
    }

    static int loadOpcode(Class<?> type) {
        if (type == double.class) return Opcodes.DLOAD;
        if (type == float.class) return Opcodes.FLOAD;
        if (type == long.class) return Opcodes.LLOAD;
        if (type == int.class || type == boolean.class || type == byte.class
                || type == char.class || type == short.class)
            return Opcodes.ILOAD;
        return Opcodes.ALOAD;
    }

    static void boxPrimitive(MethodVisitor mv, Class<?> type) {
        String wrapper;
        String desc;
        if (type == boolean.class) {
            wrapper = "java/lang/Boolean";
            desc = "(Z)Ljava/lang/Boolean;";
        } else if (type == byte.class) {
            wrapper = "java/lang/Byte";
            desc = "(B)Ljava/lang/Byte;";
        } else if (type == char.class) {
            wrapper = "java/lang/Character";
            desc = "(C)Ljava/lang/Character;";
        } else if (type == short.class) {
            wrapper = "java/lang/Short";
            desc = "(S)Ljava/lang/Short;";
        } else if (type == int.class) {
            wrapper = "java/lang/Integer";
            desc = "(I)Ljava/lang/Integer;";
        } else if (type == float.class) {
            wrapper = "java/lang/Float";
            desc = "(F)Ljava/lang/Float;";
        } else if (type == long.class) {
            wrapper = "java/lang/Long";
            desc = "(J)Ljava/lang/Long;";
        } else if (type == double.class) {
            wrapper = "java/lang/Double";
            desc = "(D)Ljava/lang/Double;";
        } else {
            return;
        }
        mv.visitMethodInsn(Opcodes.INVOKESTATIC, wrapper, "valueOf", desc, false);
    }

    static void unboxPrimitive(MethodVisitor mv, Class<?> type) {
        String wrapper;
        String unboxMethod;
        String desc;
        if (type == boolean.class) {
            wrapper = "java/lang/Boolean";
            unboxMethod = "booleanValue";
            desc = "()Z";
        } else if (type == byte.class) {
            wrapper = "java/lang/Byte";
            unboxMethod = "byteValue";
            desc = "()B";
        } else if (type == char.class) {
            wrapper = "java/lang/Character";
            unboxMethod = "charValue";
            desc = "()C";
        } else if (type == short.class) {
            wrapper = "java/lang/Short";
            unboxMethod = "shortValue";
            desc = "()S";
        } else if (type == int.class) {
            wrapper = "java/lang/Integer";
            unboxMethod = "intValue";
            desc = "()I";
        } else if (type == float.class) {
            wrapper = "java/lang/Float";
            unboxMethod = "floatValue";
            desc = "()F";
        } else if (type == long.class) {
            wrapper = "java/lang/Long";
            unboxMethod = "longValue";
            desc = "()J";
        } else if (type == double.class) {
            wrapper = "java/lang/Double";
            unboxMethod = "doubleValue";
            desc = "()D";
        } else {
            return;
        }
        mv.visitTypeInsn(Opcodes.CHECKCAST, wrapper);
        mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, wrapper, unboxMethod, desc, false);
    }

    /**
     * Pushes a Class constant onto the stack.
     * For primitives, uses wrapper.TYPE (e.g., Integer.TYPE).
     * For reference types, uses LDC.
     */
    static void pushClassConstant(MethodVisitor mv, Class<?> type) {
        if (type.isPrimitive()) {
            String wrapper = getWrapperInternalName(type);
            mv.visitFieldInsn(Opcodes.GETSTATIC, wrapper, "TYPE", "Ljava/lang/Class;");
        } else {
            mv.visitLdcInsn(Type.getType(type));
        }
    }

    private static String getWrapperInternalName(Class<?> primitiveType) {
        if (primitiveType == int.class) return "java/lang/Integer";
        if (primitiveType == long.class) return "java/lang/Long";
        if (primitiveType == double.class) return "java/lang/Double";
        if (primitiveType == float.class) return "java/lang/Float";
        if (primitiveType == boolean.class) return "java/lang/Boolean";
        if (primitiveType == byte.class) return "java/lang/Byte";
        if (primitiveType == char.class) return "java/lang/Character";
        if (primitiveType == short.class) return "java/lang/Short";
        if (primitiveType == void.class) return "java/lang/Void";
        throw new IllegalArgumentException("Unknown primitive: " + primitiveType);
    }
}
```

- [ ] **Step 2: Refactor MethodDispatcher — replace private helpers with BytecodeUtils calls**

Remove these private methods from `MethodDispatcher.java`:
- `pushInt` (lines 246-256)
- `loadOpcode` (lines 258-266)
- `boxPrimitive` (lines 268-299)
- `unboxPrimitive` (lines 301-342)

Replace calls:
- `pushInt(mv, ...)` → `BytecodeUtils.pushInt(mv, ...)`
- `loadOpcode(type)` → `BytecodeUtils.loadOpcode(type)`
- `boxPrimitive(mv, type)` → `BytecodeUtils.boxPrimitive(mv, type)`
- `unboxPrimitive(mv, type)` → `BytecodeUtils.unboxPrimitive(mv, type)`

Note: `loadArguments` stays in MethodDispatcher (only used for non-intercept direct-super-call path).

- [ ] **Step 3: Refactor ClassGenerator — replace private helpers with BytecodeUtils calls**

Remove these private methods from `ClassGenerator.java`:
- `pushInt` (lines 319-327)
- `pushClassConstant` (lines 334-341)
- `getWrapperInternalName` (lines 343-354)

Replace calls:
- `pushInt(mv, ...)` → `BytecodeUtils.pushInt(mv, ...)`
- `pushClassConstant(mv, type)` → `BytecodeUtils.pushClassConstant(mv, type)`

- [ ] **Step 4: Run existing tests to verify refactoring**

Run: `mvn -s /home/lam/repo/settings.xml test`
Expected: All 16 tests pass, BUILD SUCCESS

- [ ] **Step 5: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/BytecodeUtils.java
git add src/main/java/io/github/lamspace/generator/MethodDispatcher.java
git add src/main/java/io/github/lamspace/generator/ClassGenerator.java
git commit -m "refactor: extract shared bytecode utilities to BytecodeUtils"
```

---

### Task 3: Create InterfaceDispatcher

**Files:**
- Create: `src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java`

**Interfaces:**
- Consumes: `BytecodeUtils.pushInt`, `BytecodeUtils.loadOpcode`, `BytecodeUtils.boxPrimitive`, `BytecodeUtils.unboxPrimitive`
- Consumes: `ClinitRegistry.register(targetClass, method, generatedInternal, methodFieldName, handleFieldName)`
- Produces: `InterfaceDispatcher.dispatchMethods(ClassWriter, Class<?>, String, ClassFilter)` — returns `List<String>` dispatched method names

- [ ] **Step 1: Write InterfaceDispatcher.java**

```java
package io.github.lamspace.generator;

import io.github.lamspace.ClassFilter;
import org.objectweb.asm.*;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.List;

/**
 * Generates method implementation bodies for an interface proxy class.
 * Each implementation marshals arguments into an {@code Object[]} and
 * calls {@code InterfaceCallback.intercept(...)}. No MethodHandle binding
 * is needed — interface methods have no super implementation.
 */
final class InterfaceDispatcher {

    private static final String CALLBACK_FIELD = "_callback";

    private InterfaceDispatcher() {}

    /**
     * Generates method implementations (no overrides — these implement
     * the interface methods), static Method fields, and registers clinit
     * entries for all interface methods.
     *
     * @param cw                the ClassWriter for the generated class
     * @param interfaceClass    the interface being implemented
     * @param generatedInternal ASM internal name of the generated class
     * @param filter            if non-null, only methods accepted by the
     *                          filter are routed through the callback;
     *                          others throw AbstractMethodError
     * @return list of method names for which dispatchers were generated
     */
    static List<String> dispatchMethods(ClassWriter cw, Class<?> interfaceClass,
                                         String generatedInternal,
                                         ClassFilter filter) {
        List<String> dispatchedMethods = new ArrayList<>();

        for (Method method : interfaceClass.getMethods()) {
            int mods = method.getModifiers();
            // Skip static and final methods — they can't be overridden
            if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                continue;
            }

            boolean shouldIntercept = (filter == null) || filter.accept(method);

            String suffix = "$" + dispatchedMethods.size();
            String methodFieldName = "_method$" + method.getName() + suffix;
            // No handle field for interfaces, pass empty string
            String handleFieldName = "";

            addStaticField(cw, methodFieldName, "Ljava/lang/reflect/Method;");

            ClinitRegistry.register(interfaceClass, method, generatedInternal,
                    methodFieldName, handleFieldName);

            generateImplementation(cw, method, generatedInternal,
                    shouldIntercept, methodFieldName);

            dispatchedMethods.add(method.getName());
        }

        return dispatchedMethods;
    }

    private static void addStaticField(ClassWriter cw, String name, String desc) {
        var fv = cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_STATIC,
                name, desc, null, null);
        fv.visitEnd();
    }

    private static void generateImplementation(ClassWriter cw, Method method,
                                                String generatedInternal,
                                                boolean shouldIntercept,
                                                String methodFieldName) {
        String name = method.getName();
        String desc = Type.getMethodDescriptor(method);

        Class<?>[] exceptions = method.getExceptionTypes();
        String[] exceptionNames = new String[exceptions.length];
        for (int i = 0; i < exceptions.length; i++) {
            exceptionNames[i] = Type.getInternalName(exceptions[i]);
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, name, desc,
                null, exceptionNames);
        mv.visitCode();

        if (!shouldIntercept) {
            // Filter rejected: throw AbstractMethodError
            mv.visitTypeInsn(Opcodes.NEW, "java/lang/AbstractMethodError");
            mv.visitInsn(Opcodes.DUP);
            mv.visitLdcInsn("Method " + name + " is not intercepted");
            mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                    "java/lang/AbstractMethodError",
                    "<init>", "(Ljava/lang/String;)V", false);
            mv.visitInsn(Opcodes.ATHROW);
            mv.visitMaxs(3, 1);
            mv.visitEnd();
            return;
        }

        Class<?>[] paramTypes = method.getParameterTypes();
        int totalParamSlots = 0;
        for (Class<?> paramType : paramTypes) {
            totalParamSlots += (paramType == double.class
                    || paramType == long.class) ? 2 : 1;
        }

        // -- Try block: call callback --
        Label tryStart = new Label();
        Label tryEnd = new Label();
        Label catchRuntime = new Label();
        Label catchError = new Label();
        Label catchChecked = new Label();

        mv.visitTryCatchBlock(tryStart, tryEnd, catchRuntime,
                "java/lang/RuntimeException");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchError,
                "java/lang/Error");
        mv.visitTryCatchBlock(tryStart, tryEnd, catchChecked,
                "java/lang/Exception");

        mv.visitLabel(tryStart);

        // 1. Load callback: this._callback
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitFieldInsn(Opcodes.GETFIELD, generatedInternal,
                CALLBACK_FIELD, "Lio/github/lamspace/InterfaceCallback;");

        // 2. Arg 1: proxy = this
        mv.visitVarInsn(Opcodes.ALOAD, 0);

        // 3. Arg 2: _method$X
        mv.visitFieldInsn(Opcodes.GETSTATIC, generatedInternal,
                methodFieldName, "Ljava/lang/reflect/Method;");

        // 4. Arg 3: new Object[]{ args... }
        BytecodeUtils.pushInt(mv, paramTypes.length);
        mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Object");

        int slot = 1;
        for (int i = 0; i < paramTypes.length; i++) {
            mv.visitInsn(Opcodes.DUP);
            BytecodeUtils.pushInt(mv, i);
            Class<?> type = paramTypes[i];
            if (type.isPrimitive()) {
                mv.visitVarInsn(BytecodeUtils.loadOpcode(type), slot);
                BytecodeUtils.boxPrimitive(mv, type);
            } else {
                mv.visitVarInsn(Opcodes.ALOAD, slot);
            }
            mv.visitInsn(Opcodes.AASTORE);
            slot += (type == double.class || type == long.class) ? 2 : 1;
        }

        // 5. Call InterfaceCallback.intercept(proxy, method, args)
        mv.visitMethodInsn(Opcodes.INVOKEINTERFACE,
                "io/github/lamspace/InterfaceCallback",
                "intercept",
                "(Ljava/lang/Object;Ljava/lang/reflect/Method;"
                        + "[Ljava/lang/Object;)Ljava/lang/Object;",
                true);

        // 6. Unbox return value
        Class<?> returnType = method.getReturnType();
        if (returnType == void.class) {
            mv.visitInsn(Opcodes.POP);
            mv.visitInsn(Opcodes.RETURN);
        } else if (returnType.isPrimitive()) {
            BytecodeUtils.unboxPrimitive(mv, returnType);
            mv.visitInsn(Type.getReturnType(desc).getOpcode(Opcodes.IRETURN));
        } else {
            mv.visitTypeInsn(Opcodes.CHECKCAST,
                    Type.getInternalName(returnType));
            mv.visitInsn(Opcodes.ARETURN);
        }

        mv.visitLabel(tryEnd);

        // -- Catch RuntimeException: rethrow directly --
        mv.visitLabel(catchRuntime);
        mv.visitInsn(Opcodes.ATHROW);

        // -- Catch Error: rethrow directly --
        mv.visitLabel(catchError);
        mv.visitInsn(Opcodes.ATHROW);

        // -- Catch checked Exception: wrap in UndeclaredThrowableException --
        mv.visitLabel(catchChecked);
        int exceptionSlot = totalParamSlots + 1;
        mv.visitVarInsn(Opcodes.ASTORE, exceptionSlot);
        mv.visitTypeInsn(Opcodes.NEW,
                "java/lang/reflect/UndeclaredThrowableException");
        mv.visitInsn(Opcodes.DUP);
        mv.visitVarInsn(Opcodes.ALOAD, exceptionSlot);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/reflect/UndeclaredThrowableException",
                "<init>", "(Ljava/lang/Throwable;)V", false);
        mv.visitInsn(Opcodes.ATHROW);

        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceDispatcher.java
git commit -m "feat: add InterfaceDispatcher for interface method body generation"
```

---

### Task 4: Create InterfaceGenerator

**Files:**
- Create: `src/main/java/io/github/lamspace/generator/InterfaceGenerator.java`

**Interfaces:**
- Consumes: `InterfaceDispatcher.dispatchMethods(cw, interfaceClass, generatedInternal, filter)`
- Consumes: `ClinitRegistry.drain()` — returns entries; only `method`, `methodFieldName`, `targetClass` fields used
- Consumes: `BytecodeUtils.pushInt`, `BytecodeUtils.pushClassConstant`
- Produces: `InterfaceGenerator.generate()` — returns `byte[]`

- [ ] **Step 1: Write InterfaceGenerator.java**

```java
package io.github.lamspace.generator;

import io.github.lamspace.ClassFilter;
import io.github.lamspace.InterfaceCallback;
import org.objectweb.asm.ClassWriter;
import org.objectweb.asm.MethodVisitor;
import org.objectweb.asm.Opcodes;
import org.objectweb.asm.Type;

import java.lang.reflect.Method;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Generates a proxy implementation class that {@code extends Object}
 * and {@code implements} the target interface.
 * <p>
 * For each interface method, generates an implementation that delegates
 * to {@link InterfaceCallback#intercept}. No MethodHandle binding is
 * performed — interface methods have no super implementation.
 */
public class InterfaceGenerator {

    private static final AtomicInteger COUNTER = new AtomicInteger(0);

    private final Class<?> interfaceClass;
    private final ClassFilter filter;

    /**
     * Creates a generator for the given interface.
     *
     * @param interfaceClass the interface to implement
     * @param filter         method filter; {@code null} means all methods
     *                       are routed through the callback
     */
    public InterfaceGenerator(Class<?> interfaceClass, ClassFilter filter) {
        this.interfaceClass = interfaceClass;
        this.filter = filter;
    }

    /**
     * Generates the implementation class bytecode. The class is placed in
     * the same runtime package as the target interface.
     *
     * @return valid JVM classfile bytes
     */
    public byte[] generate() {
        String targetInternal = Type.getInternalName(interfaceClass);
        String packagePrefix = targetInternal.contains("/")
                ? targetInternal.substring(0, targetInternal.lastIndexOf('/') + 1)
                : "";
        String simpleName = targetInternal.substring(
                targetInternal.lastIndexOf('/') + 1);
        String generatedInternal = packagePrefix + simpleName
                + "$$APS$$" + COUNTER.getAndIncrement();

        ClassWriter cw = new ClassWriter(ClassWriter.COMPUTE_FRAMES);

        cw.visit(Opcodes.V24, Opcodes.ACC_PUBLIC | Opcodes.ACC_SUPER,
                generatedInternal, null, "java/lang/Object",
                new String[]{targetInternal});

        // -- InterfaceCallback field --
        String callbackDesc = Type.getDescriptor(InterfaceCallback.class);
        cw.visitField(Opcodes.ACC_PRIVATE | Opcodes.ACC_FINAL,
                "_callback", callbackDesc, null, null);

        // -- Constructor: stores callback, calls super() --
        generateConstructor(cw, generatedInternal, callbackDesc);

        // -- Method implementations + static Method fields --
        InterfaceDispatcher.dispatchMethods(cw, interfaceClass,
                generatedInternal, filter);

        // -- <clinit> for Method objects --
        generateClinit(cw, generatedInternal);

        cw.visitEnd();
        return cw.toByteArray();
    }

    private void generateConstructor(ClassWriter cw, String generatedInternal,
                                      String callbackDesc) {
        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_PUBLIC, "<init>",
                "(" + callbackDesc + ")V", null, null);
        mv.visitCode();
        // super()
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitMethodInsn(Opcodes.INVOKESPECIAL,
                "java/lang/Object", "<init>", "()V", false);
        // this._callback = callback (slot 1)
        mv.visitVarInsn(Opcodes.ALOAD, 0);
        mv.visitVarInsn(Opcodes.ALOAD, 1);
        mv.visitFieldInsn(Opcodes.PUTFIELD, generatedInternal,
                "_callback", callbackDesc);
        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(2, 2);
        mv.visitEnd();
    }

    private void generateClinit(ClassWriter cw, String generatedInternal) {
        List<ClinitRegistry.Entry> entries = ClinitRegistry.drain();
        if (entries.isEmpty()) {
            return;
        }

        MethodVisitor mv = cw.visitMethod(Opcodes.ACC_STATIC,
                "<clinit>", "()V", null, null);
        mv.visitCode();

        for (ClinitRegistry.Entry entry : entries) {
            Method method = entry.method();
            String methodField = entry.methodFieldName();
            String targetInternal = Type.getInternalName(
                    entry.targetClass());

            // Store the Method object via reflection
            mv.visitLdcInsn(Type.getType("L" + targetInternal + ";"));
            mv.visitLdcInsn(method.getName());

            Class<?>[] paramTypes = method.getParameterTypes();
            BytecodeUtils.pushInt(mv, paramTypes.length);
            mv.visitTypeInsn(Opcodes.ANEWARRAY, "java/lang/Class");
            for (int i = 0; i < paramTypes.length; i++) {
                mv.visitInsn(Opcodes.DUP);
                BytecodeUtils.pushInt(mv, i);
                BytecodeUtils.pushClassConstant(mv, paramTypes[i]);
                mv.visitInsn(Opcodes.AASTORE);
            }
            mv.visitMethodInsn(Opcodes.INVOKEVIRTUAL, "java/lang/Class",
                    "getDeclaredMethod",
                    "(Ljava/lang/String;[Ljava/lang/Class;)"
                            + "Ljava/lang/reflect/Method;",
                    false);
            mv.visitFieldInsn(Opcodes.PUTSTATIC, generatedInternal,
                    methodField, "Ljava/lang/reflect/Method;");
            // No MethodHandle storage — interfaces have no super
            // implementation to bind.
        }

        mv.visitInsn(Opcodes.RETURN);
        mv.visitMaxs(0, 0);
        mv.visitEnd();
    }
}
```

- [ ] **Step 2: Verify compilation**

Run: `mvn -s /home/lam/repo/settings.xml compile -q`
Expected: BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/generator/InterfaceGenerator.java
git commit -m "feat: add InterfaceGenerator for interface proxy bytecode"
```

---

### Task 5: Add createInterface() to APS

**Files:**
- Modify: `src/main/java/io/github/lamspace/APS.java` — add two `createInterface` overloads + `isInterface()` guard

**Interfaces:**
- Consumes: `InterfaceGenerator(interfaceClass, filter).generate()` → `byte[]`
- Consumes: `HiddenClassLoader.defineClass(targetClass, bytecode)` → `Class<?>`
- Produces: `APS.createInterface(Class<T>, InterfaceCallback)` → `T`
- Produces: `APS.createInterface(Class<T>, InterfaceCallback, ClassFilter)` → `T`

- [ ] **Step 1: Add createInterface methods to APS.java**

Insert after the existing `create(...)` methods and before the closing `}` of the class:

```java
    /**
     * Creates a proxy implementation for the given interface. All interface
     * methods (including defaults) are routed through the callback.
     * <p>
     * This is the interface counterpart to {@link #create(Class, Callback)}.
     * Unlike class proxies, there is no {@code superHandle} — interface
     * methods have no super implementation.
     *
     * @param interfaceClass the interface to implement
     * @param callback       invoked for every method call on the proxy
     * @param <T>            the proxy type
     * @return a proxy instance implementing {@code T}
     * @throws IllegalArgumentException if the target is not an interface
     * @throws RuntimeException         if bytecode generation or class loading fails
     */
    public static <T> T createInterface(Class<T> interfaceClass,
                                         InterfaceCallback callback) {
        return createInterface(interfaceClass, callback, null);
    }

    /**
     * Creates a proxy implementation for the given interface. Only methods
     * accepted by the {@code filter} are routed through the callback;
     * all other methods throw {@code AbstractMethodError} when called.
     *
     * @param interfaceClass the interface to implement (must be non-null)
     * @param callback       invoked for every FILTERED method call on the proxy;
     *                       must not be {@code null}
     * @param filter         decides which methods pass through the callback;
     *                       {@code null} means all methods are intercepted
     * @param <T>            the proxy type
     * @return a proxy instance implementing {@code T}
     * @throws IllegalArgumentException if {@code interfaceClass} is not an
     *                                  interface, or either arg is null
     * @throws RuntimeException         if bytecode generation or hidden-class
     *                                  loading fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T createInterface(Class<T> interfaceClass,
                                         InterfaceCallback callback,
                                         ClassFilter filter) {
        if (interfaceClass == null) {
            throw new IllegalArgumentException(
                    "interfaceClass must not be null");
        }
        if (!interfaceClass.isInterface()) {
            throw new IllegalArgumentException(
                    "interfaceClass must be an interface: "
                            + interfaceClass.getName());
        }
        if (callback == null) {
            throw new IllegalArgumentException(
                    "callback must not be null");
        }

        try {
            InterfaceGenerator generator = new InterfaceGenerator(
                    interfaceClass, filter);
            byte[] bytecode = generator.generate();

            HiddenClassLoader loader = new HiddenClassLoader();
            Class<?> proxyClass = loader.defineClass(interfaceClass,
                    bytecode);

            return (T) proxyClass.getConstructor(InterfaceCallback.class)
                    .newInstance(callback);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create interface proxy for "
                            + interfaceClass.getName(), e);
        }
    }
```

Add the import at the top of APS.java:

```java
import io.github.lamspace.generator.InterfaceGenerator;
```

- [ ] **Step 2: Run existing tests to verify no regression**

Run: `mvn -s /home/lam/repo/settings.xml test`
Expected: All 16 tests pass, BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/main/java/io/github/lamspace/APS.java
git commit -m "feat: add APS.createInterface() for interface proxies"
```

---

### Task 6: Write integration tests

**Files:**
- Create: `src/test/java/io/github/lamspace/APSInterfaceFunctionalTest.java`
- Modify: `src/test/java/io/github/lamspace/APSFunctionalTest.java` — add test for `createInterface` with non-interface guard

**Interfaces:**
- Consumes: `APS.createInterface(Class<T>, InterfaceCallback)`
- Consumes: `APS.createInterface(Class<T>, InterfaceCallback, ClassFilter)`

- [ ] **Step 1: Write APSInterfaceFunctionalTest.java**

```java
package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.*;

class APSInterfaceFunctionalTest {

    // ---------------------------------------------------------------
    // Test interfaces
    // ---------------------------------------------------------------

    interface Greeter {
        String hello(String name);
    }

    interface Calculator {
        int add(int a, int b);
    }

    interface SideEffectRunner {
        void run();
    }

    interface MultiMethod {
        String greet(String name);
        int compute(int x, int y);
    }

    interface GreeterWithDefault {
        String hello(String name);

        default String greet() {
            return "Hello, World";
        }
    }

    // ---------------------------------------------------------------
    // Tests
    // ---------------------------------------------------------------

    @Test
    void shouldInterceptAndReturnModifiedValue() {
        Greeter proxy = APS.createInterface(Greeter.class,
                (obj, method, args) -> {
                    String original = (String) args[0];
                    return "[intercepted] " + original;
                });

        String result = proxy.hello("World");
        assertEquals("[intercepted] World", result);
    }

    @Test
    void shouldReturnFixedValueFromCallback() {
        Greeter proxy = APS.createInterface(Greeter.class,
                (obj, method, args) -> "fixed");

        assertEquals("fixed", proxy.hello("anything"));
    }

    @Test
    void shouldHandlePrimitiveReturnType() {
        Calculator proxy = APS.createInterface(Calculator.class,
                (obj, method, args) -> {
                    int a = (int) args[0];
                    int b = (int) args[1];
                    return a + b;
                });

        assertEquals(7, proxy.add(3, 4));
    }

    @Test
    void shouldHandleVoidReturnType() {
        AtomicBoolean called = new AtomicBoolean(false);
        SideEffectRunner proxy = APS.createInterface(
                SideEffectRunner.class, (obj, method, args) -> {
                    called.set(true);
                    return null;
                });

        proxy.run();
        assertTrue(called.get());
    }

    @Test
    void shouldModifyArgumentsInCallback() {
        Greeter proxy = APS.createInterface(Greeter.class,
                (obj, method, args) -> {
                    args[0] = "[" + args[0] + "]";
                    return args[0];
                });

        assertEquals("[World]", proxy.hello("World"));
    }

    @Test
    void shouldWorkWithClassFilter() {
        MultiMethod proxy = APS.createInterface(MultiMethod.class,
                (obj, method, args) -> "[filtered] " + args[0],
                method -> method.getName().startsWith("greet"));

        assertEquals("[filtered] Alice", proxy.greet("Alice"));
        // compute() is filtered out — should throw AbstractMethodError
        assertThrows(AbstractMethodError.class,
                () -> proxy.compute(3, 4));
    }

    @Test
    void shouldHandleDefaultMethodAsRegularCallbackInvocation() {
        GreeterWithDefault proxy = APS.createInterface(
                GreeterWithDefault.class,
                (obj, method, args) -> {
                    if (method.getName().equals("greet")) {
                        return "[overridden] default";
                    }
                    return "Hello, " + args[0];
                });

        // Default method goes through callback, not the interface default
        assertEquals("[overridden] default", proxy.greet());
        assertEquals("Hello, Bob", proxy.hello("Bob"));
    }

    @Test
    void shouldThrowForNonInterfaceClass() {
        assertThrows(IllegalArgumentException.class, () ->
                APS.createInterface(String.class,
                        (obj, method, args) -> null));
    }

    @Test
    void shouldThrowForNullInterfaceClass() {
        assertThrows(IllegalArgumentException.class, () ->
                APS.createInterface(null, (obj, method, args) -> null));
    }

    @Test
    void shouldThrowForNullCallback() {
        assertThrows(IllegalArgumentException.class, () ->
                APS.createInterface(Runnable.class, null));
    }

    @Test
    void shouldPropagateRuntimeExceptionFromCallback() {
        Greeter proxy = APS.createInterface(Greeter.class,
                (obj, method, args) -> {
                    throw new RuntimeException("test exception");
                });

        assertThrows(RuntimeException.class,
                () -> proxy.hello("fail"));
    }

    @Test
    void shouldWrapCheckedExceptionInUndeclaredThrowable() {
        Greeter proxy = APS.createInterface(Greeter.class,
                (obj, method, args) -> {
                    throw new Exception("checked from interceptor");
                });

        assertThrows(
                java.lang.reflect.UndeclaredThrowableException.class,
                () -> proxy.hello("x"));
    }

    @Test
    void shouldHandleNoArgMethod() {
        Runnable proxy = APS.createInterface(Runnable.class,
                (obj, method, args) -> {
                    assertNotNull(method);
                    assertEquals("run", method.getName());
                    assertEquals(0, args.length);
                    return null;
                });

        proxy.run(); // no exception = pass
    }
}
```

- [ ] **Step 2: Run tests to verify they pass**

Run: `mvn -s /home/lam/repo/settings.xml test`
Expected: All 29 tests pass (16 existing + 13 new), BUILD SUCCESS

- [ ] **Step 3: Commit**

```bash
git add src/test/java/io/github/lamspace/APSInterfaceFunctionalTest.java
git commit -m "test: add integration tests for APS.createInterface()"
```

---

### Task 7: Final verification

- [ ] **Step 1: Run full test suite**

Run: `mvn -s /home/lam/repo/settings.xml test`
Expected: BUILD SUCCESS, all tests pass

- [ ] **Step 2: Verify git status is clean**

Run: `git status`
Expected: nothing to commit, working tree clean

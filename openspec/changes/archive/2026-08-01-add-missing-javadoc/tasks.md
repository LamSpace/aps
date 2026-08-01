## 1. BytecodeUtils.java — package-private methods lacking Javadoc

- [x] 1.1 Add Javadoc to `pushInt(MethodVisitor, int)` — describes pushing an int constant onto the stack with the minimal instruction
- [x] 1.2 Add Javadoc to `loadOpcode(Class<?>)` — describes returning the appropriate XLOAD opcode for a given primitive type
- [x] 1.3 Add Javadoc to `boxPrimitive(MethodVisitor, Class<?>)` — describes auto-boxing a primitive value on the stack
- [x] 1.4 Add Javadoc to `unboxPrimitive(MethodVisitor, Class<?>)` — describes auto-unboxing a boxed value on the stack

## 2. ClinitRegistry.java — package-private members lacking Javadoc

- [x] 2.1 Add Javadoc to `Entry` record — describes what each field represents in the clinit registration
- [x] 2.2 Add Javadoc to `register(Class<?>, Method, String, String, int)` — describes registering a method for clinit dispatch
- [x] 2.3 Add Javadoc to `drain()` — describes draining all registered entries for code generation

## 3. Verification

- [x] 3.1 Build the project with `mvn -s /home/lam/repo/settings.xml compile` to verify no compilation errors introduced
- [x] 3.2 Run `mvn -s /home/lam/repo/settings.xml javadoc:javadoc` to verify Javadoc generates without warnings

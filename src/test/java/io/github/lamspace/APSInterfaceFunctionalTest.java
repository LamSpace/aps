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

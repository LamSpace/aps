package io.github.lamspace;

import io.github.lamspace.Callback;
import io.github.lamspace.ClassFilter;
import io.github.lamspace.generator.ClassGenerator;
import io.github.lamspace.generator.InterfaceGenerator;
import io.github.lamspace.loader.HiddenClassLoader;

import java.lang.reflect.Constructor;

/**
 * Entry point for creating dynamic proxies.
 *
 * <p>All {@code create(...)} overloads generate a runtime subclass of the
 * target class, load it via
 * {@code MethodHandles.Lookup.defineHiddenClass(byte[], boolean)},
 * and route method calls through the provided {@link Callback}.
 *
 * <pre>{@code
 *   Greeter proxy = APS.create(Greeter.class, (obj, method, superHandle, args) -> {
 *       System.out.println("before " + method.getName());
 *       return superHandle.invoke(args);
 *   });
 * }</pre>
 *
 * @see Callback
 * @see ClassFilter
 */
public final class APS {

    private APS() {
    }

    /**
     * Creates a proxy for the given class. All non-final instance methods
     * are routed through the callback.
     *
     * @param targetClass the class to proxy (must have an accessible no-arg constructor)
     * @param callback    invoked for every method call on the proxy
     * @param <T>         the proxy type
     * @return a proxy instance of type {@code T}
     * @throws IllegalArgumentException if the target class cannot be proxied
     * @throws RuntimeException         if bytecode generation or class loading fails
     */
    public static <T> T create(Class<T> targetClass, Callback callback) {
        return create(targetClass, callback, null);
    }

    /**
     * Creates a proxy for the given class. Only methods accepted by the
     * {@code filter} are routed through the callback; all other methods
     * call the superclass implementation directly with zero interception
     * overhead.
     *
     * @param targetClass the class to proxy (must have an accessible no-arg constructor)
     * @param callback    invoked for every FILTERED method call on the proxy
     * @param filter      decides which methods pass through the callback;
     *                    {@code null} means all methods are intercepted
     * @param <T>         the proxy type
     * @return a proxy instance of type {@code T}
     * @throws IllegalArgumentException if the target class cannot be proxied
     * @throws RuntimeException         if bytecode generation or class loading fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> targetClass, Callback callback,
                               ClassFilter filter) {
        return create(targetClass, callback, filter, new Object[0]);
    }

    /**
     * Creates a proxy for the given class with optional constructor arguments
     * for classes that lack a no-arg constructor.
     *
     * @param targetClass     the class to proxy; must be non-final and have
     *                        an accessible constructor matching the provided
     *                        constructor arguments
     * @param callback        invoked for every FILTERED method call on the proxy;
     *                        must not be {@code null}
     * @param filter          decides which methods pass through the callback;
     *                        {@code null} means all methods are intercepted
     * @param constructorArgs arguments to pass to the superclass constructor;
     *                        empty array (default) for the no-arg constructor
     * @param <T>             the proxy type, inferred from {@code targetClass}
     * @return a proxy instance of type {@code T}
     * @throws IllegalArgumentException if {@code targetClass} is {@code null},
     *                                  {@code callback} is {@code null}, the
     *                                  target class cannot be proxied, or the
     *                                  module does not open its package for
     *                                  reflection
     * @throws RuntimeException         if bytecode generation or hidden-class
     *                                  loading fails
     */
    @SuppressWarnings("unchecked")
    public static <T> T create(Class<T> targetClass, Callback callback,
                               ClassFilter filter, Object... constructorArgs) {
        if (targetClass == null) {
            throw new IllegalArgumentException("targetClass must not be null");
        }
        if (callback == null) {
            throw new IllegalArgumentException("callback must not be null");
        }

        try {
            ClassGenerator generator = new ClassGenerator(targetClass, filter,
                    constructorArgs);
            byte[] bytecode = generator.generate();

            HiddenClassLoader loader = new HiddenClassLoader();
            Class<?> proxyClass = loader.defineClass(targetClass, bytecode);

            Constructor<?> ctor = proxyClass.getConstructor(generator.constructorArgs());
            Object[] initArgs = new Object[1 + constructorArgs.length];
            initArgs[0] = callback;
            System.arraycopy(constructorArgs, 0, initArgs, 1, constructorArgs.length);
            return (T) ctor.newInstance(initArgs);
        } catch (IllegalAccessException e) {
            throw new IllegalArgumentException(
                    "Cannot access target class: " + targetClass.getName()
                            + ". The package may not be open for reflection.", e);
        } catch (Exception e) {
            throw new RuntimeException(
                    "Failed to create proxy for " + targetClass.getName(), e);
        }
    }

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
}

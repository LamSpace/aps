/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.*;

class MultiInterceptorClassProxyTest {

    public static class Greeter {
        public String getGreeting() {
            return "hello";
        }

        public void setGreeting(String g) { /* no-op */ }

        @Override
        public String toString() {
            return "Greeter";
        }
    }

    @Test
    void getterAndSetterUseDifferentInterceptors() {
        AtomicReference<String> getterCalled = new AtomicReference<>();
        AtomicReference<String> setterCalled = new AtomicReference<>();

        Interceptor getterInterceptor = (proxy, method, args) -> {
            getterCalled.set(method.getName());
            return OpenProxy.invokeSuper(proxy, method, args);
        };
        Interceptor setterInterceptor = (proxy, method, args) -> {
            setterCalled.set(method.getName());
            return OpenProxy.invokeSuper(proxy, method, args);
        };

        Greeter proxy = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"),
                        getterInterceptor),
                Group.of(m -> m.getName().startsWith("set"),
                        setterInterceptor));

        assertEquals("hello", proxy.getGreeting());
        assertEquals("getGreeting", getterCalled.get());
        assertNull(setterCalled.get());

        proxy.setGreeting("hi");
        assertEquals("setGreeting", setterCalled.get());
    }

    @Test
    void passthroughMethodBypassesInterceptor() {
        AtomicInteger interceptorCalls = new AtomicInteger(0);
        Interceptor counting = (proxy, method, args) -> {
            interceptorCalls.incrementAndGet();
            return OpenProxy.invokeSuper(proxy, method, args);
        };

        Greeter proxy = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("set"), counting));

        assertEquals("hello", proxy.getGreeting());
        assertEquals(0, interceptorCalls.get());

        proxy.setGreeting("test");
        assertEquals(1, interceptorCalls.get());

        proxy.toString();
        assertEquals(1, interceptorCalls.get());
    }

    @Test
    void invokeSuperWorksInAnyGroup() {
        Interceptor a = (proxy, method, args) ->
                OpenProxy.invokeSuper(proxy, method, args);
        Interceptor b = (proxy, method, args) ->
                OpenProxy.invokeSuper(proxy, method, args);

        Greeter proxy = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().equals("getGreeting"), a),
                Group.otherwise(b));

        assertEquals("hello", proxy.getGreeting());
        proxy.setGreeting("x");
        assertEquals("Greeter", proxy.toString());
    }

    @Test
    void sharedInterceptorDedup() {
        Interceptor shared = (proxy, method, args) ->
                OpenProxy.invokeSuper(proxy, method, args);

        Greeter proxy = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), shared),
                Group.of(m -> m.getName().startsWith("set"), shared));

        assertEquals("hello", proxy.getGreeting());
        proxy.setGreeting("world");
    }

    @Test
    void statefulInterceptorPerGroup() {
        AtomicInteger getterCount = new AtomicInteger(0);
        AtomicInteger setterCount = new AtomicInteger(0);

        Interceptor getter = (proxy, method, args) -> {
            getterCount.incrementAndGet();
            return OpenProxy.invokeSuper(proxy, method, args);
        };
        Interceptor setter = (proxy, method, args) -> {
            setterCount.incrementAndGet();
            return OpenProxy.invokeSuper(proxy, method, args);
        };

        Greeter proxy = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), getter),
                Group.of(m -> m.getName().startsWith("set"), setter));

        proxy.getGreeting();
        proxy.getGreeting();
        proxy.setGreeting("a");

        assertEquals(2, getterCount.get());
        assertEquals(1, setterCount.get());
    }

    @Test
    void cacheHitWithSameGroups() {
        Interceptor a = (p, m, args) -> null;
        Interceptor b = (p, m, args) -> null;

        Greeter p1 = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("set"), b));

        Greeter p2 = OpenProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("set"), b));

        assertSame(p1.getClass(), p2.getClass());
    }

    @Test
    void oldApiStillWorks() {
        AtomicInteger calls = new AtomicInteger(0);
        Interceptor interceptor = (proxy, method, args) -> {
            calls.incrementAndGet();
            return OpenProxy.invokeSuper(proxy, method, args);
        };

        Greeter proxy = OpenProxy.proxy(Greeter.class, interceptor);
        proxy.getGreeting();
        proxy.setGreeting("x");
        assertEquals(2, calls.get());
    }
}

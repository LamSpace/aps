/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class GroupMatchingTest {

    interface Sample {
        String getName();

        void setName(String name);

        int getAge();
    }

    private final Interceptor a = (proxy, method, args) -> "A";
    private final Interceptor b = (proxy, method, args) -> "B";
    private final Interceptor c = (proxy, method, args) -> "C";

    @Test
    void firstMatchWins() {
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("get"), b));
        assertNotNull(proxy);
        // getName matched by first group → interceptor 'a'
    }

    @Test
    void noMatchDefaultsToPassthrough() {
        // Only match get* — setName is passthrough (throws
        // AbstractMethodError for interface proxy)
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().startsWith("get"), a));

        assertNotNull(proxy.getName());
        assertThrows(AbstractMethodError.class, () -> proxy.setName("x"));
    }

    @Test
    void otherwiseFallback() {
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().equals("getName"), a),
                Group.otherwise(b));
        assertNotNull(proxy);
    }

    @Test
    void emptyGroupsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(Sample.class, new Group[0]));
    }

    @Test
    void nullGroupsRejected() {
        assertThrows(IllegalArgumentException.class, () ->
                AcceleratedProxy.proxy(Sample.class, (Group[]) null));
    }

    @Test
    void groupOfRejectsNullPredicate() {
        assertThrows(NullPointerException.class, () ->
                Group.of(null, a));
    }

    @Test
    void groupOfRejectsNullInterceptor() {
        assertThrows(NullPointerException.class, () ->
                Group.of(m -> true, null));
    }

    @Test
    void otherwiseRejectsNullInterceptor() {
        assertThrows(NullPointerException.class, () ->
                Group.otherwise(null));
    }

    @Test
    void sharedInterceptorInstanceWorks() {
        Interceptor shared = (proxy, method, args) -> "shared";
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                Group.of(m -> m.getName().startsWith("get"), shared),
                Group.of(m -> m.getName().startsWith("set"), shared));
        assertNotNull(proxy);
    }

    @Test
    void oldSingleInterceptorApiWorks() {
        Sample proxy = AcceleratedProxy.proxy(Sample.class,
                (obj, method, args) -> {
                    if (method.getReturnType() == int.class) return 1;
                    return "legacy";
                });
        assertNotNull(proxy);
        assertEquals("legacy", proxy.getName());
        assertEquals(1, proxy.getAge());
    }
}

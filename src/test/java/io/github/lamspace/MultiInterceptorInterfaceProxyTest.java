/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class MultiInterceptorInterfaceProxyTest {

    interface Calculator {
        int add(int a, int b);

        int subtract(int a, int b);

        int multiply(int a, int b);
    }

    @Test
    void differentGroupsForDifferentOperations() {
        AtomicInteger addCount = new AtomicInteger(0);
        AtomicInteger mulCount = new AtomicInteger(0);

        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().equals("add"),
                        (p, method, args) -> {
                            addCount.incrementAndGet();
                            return 42;
                        }),
                Group.of(m -> m.getName().equals("multiply"),
                        (p, method, args) -> {
                            mulCount.incrementAndGet();
                            return 99;
                        }));

        assertEquals(42, proxy.add(2, 3));
        assertEquals(1, addCount.get());
        assertEquals(0, mulCount.get());

        assertEquals(99, proxy.multiply(2, 3));
        assertEquals(1, addCount.get());
        assertEquals(1, mulCount.get());
    }

    @Test
    void passthroughMethodThrowsAbstractMethodError() {
        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().equals("add"),
                        (p, method, args) -> 42));

        assertEquals(42, proxy.add(1, 2));
        assertThrows(AbstractMethodError.class, () ->
                proxy.subtract(5, 3));
    }

    @Test
    void otherwiseCoversAllRemainingMethods() {
        AtomicInteger defaultCount = new AtomicInteger(0);

        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                Group.of(m -> m.getName().equals("add"),
                        (p, method, args) -> 42),
                Group.otherwise((p, method, args) -> {
                    defaultCount.incrementAndGet();
                    return 0;
                }));

        proxy.subtract(5, 3);
        proxy.multiply(2, 3);
        assertEquals(2, defaultCount.get());
    }

    @Test
    void oldApiStillWorksForInterfaces() {
        AtomicInteger calls = new AtomicInteger(0);

        Calculator proxy = AcceleratedProxy.proxy(Calculator.class,
                (p, method, args) -> {
                    calls.incrementAndGet();
                    return 0;
                });

        proxy.add(1, 2);
        proxy.subtract(5, 3);
        proxy.multiply(2, 3);
        assertEquals(3, calls.get());
    }
}

/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * ...
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;
import java.util.ArrayList;
import java.util.List;
import java.util.logging.Handler;
import java.util.logging.Level;
import java.util.logging.LogRecord;
import java.util.logging.Logger;
import static org.junit.jupiter.api.Assertions.*;

class DuplicateMatchWarningTest {

    interface OverlapTarget {
        String getUserName();
        void setUserName(String name);
        int getAge();
    }

    @Test
    void duplicateMatchLogsWarning() {
        Logger logger = Logger.getLogger(
                AcceleratedProxy.class.getName());
        TestHandler handler = new TestHandler();
        handler.setLevel(Level.WARNING);
        logger.addHandler(handler);

        Interceptor a = (p, m, args) -> null;
        Interceptor b = (p, m, args) -> null;

        AcceleratedProxy.proxy(OverlapTarget.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("getUser"), b));

        boolean hasWarning = handler.records().stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING
                        && r.getMessage().contains("getUserName")
                        && r.getMessage().contains("multiple Groups"));
        assertTrue(hasWarning,
                "Should log WARNING for overlapping predicates");

        logger.removeHandler(handler);
    }

    @Test
    void otherwiseDoesNotTriggerWarning() {
        Logger logger = Logger.getLogger(
                AcceleratedProxy.class.getName());
        TestHandler handler = new TestHandler();
        handler.setLevel(Level.WARNING);
        logger.addHandler(handler);

        Interceptor a = (p, m, args) -> null;
        Interceptor fallback = (p, m, args) -> null;

        AcceleratedProxy.proxy(OverlapTarget.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.otherwise(fallback));

        boolean hasWarning = handler.records().stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING
                        && r.getMessage().contains("multiple Groups"));
        assertFalse(hasWarning,
                "otherwise Group should not trigger duplicate warning");

        logger.removeHandler(handler);
    }

    @Test
    void distinctPredicatesNoWarning() {
        Logger logger = Logger.getLogger(
                AcceleratedProxy.class.getName());
        TestHandler handler = new TestHandler();
        handler.setLevel(Level.WARNING);
        logger.addHandler(handler);

        Interceptor a = (p, m, args) -> null;
        Interceptor b = (p, m, args) -> null;

        AcceleratedProxy.proxy(OverlapTarget.class,
                Group.of(m -> m.getName().startsWith("get"), a),
                Group.of(m -> m.getName().startsWith("set"), b));

        boolean hasWarning = handler.records().stream()
                .anyMatch(r -> r.getLevel() == Level.WARNING
                        && r.getMessage().contains("multiple Groups"));
        assertFalse(hasWarning,
                "Non-overlapping predicates should not generate warning");

        logger.removeHandler(handler);
    }

    private static class TestHandler extends Handler {
        private final List<LogRecord> records = new ArrayList<>();

        @Override
        public void publish(LogRecord record) {
            if (isLoggable(record)) {
                records.add(record);
            }
        }

        @Override
        public void flush() {}

        @Override
        public void close() {}

        List<LogRecord> records() {
            return records;
        }
    }
}

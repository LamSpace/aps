package io.github.lamspace.generator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects (target class, method, generated class, field name, index) tuples
 * during method dispatch generation. ClassGenerator reads this registry
 * after all methods are generated to emit a single {@code <clinit>} block
 * that fills the {@code _methods} and {@code _handles} arrays.
 */
final class ClinitRegistry {

    record Entry(Class<?> targetClass, Method method, String generatedInternal,
                 String methodFieldName, int index) {
    }

    private static final List<Entry> entries = new ArrayList<>();

    private ClinitRegistry() {
    }

    static void register(Class<?> targetClass, Method method,
                         String generatedInternal,
                         String methodFieldName, int index) {
        entries.add(new Entry(targetClass, method, generatedInternal,
                methodFieldName, index));
    }

    static List<Entry> drain() {
        List<Entry> result = new ArrayList<>(entries);
        entries.clear();
        return result;
    }
}

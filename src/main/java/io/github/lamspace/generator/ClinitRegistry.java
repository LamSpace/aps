package io.github.lamspace.generator;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;

/**
 * Collects (target class, method, generated class, field names) tuples
 * during method dispatch generation. ClassGenerator reads this registry
 * after all methods are generated to emit a single {@code <clinit>} block.
 */
final class ClinitRegistry {

    record Entry(Class<?> targetClass, Method method, String generatedInternal,
                 String methodFieldName, String handleFieldName) {
    }

    private static final List<Entry> entries = new ArrayList<>();

    private ClinitRegistry() {
    }

    static void register(Class<?> targetClass, Method method,
                         String generatedInternal,
                         String methodFieldName, String handleFieldName) {
        entries.add(new Entry(targetClass, method, generatedInternal,
                methodFieldName, handleFieldName));
    }

    static List<Entry> drain() {
        List<Entry> result = new ArrayList<>(entries);
        entries.clear();
        return result;
    }
}

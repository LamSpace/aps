/*
 * Copyright 2026 Lam Tong
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package io.github.lamspace;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.*;

class WeakCacheTest {

    @Test
    void shouldReturnCachedValueOnRepeatedGet() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );

        String v1 = cache.get("k", 1);
        String v2 = cache.get("k", 1);

        assertEquals("k:1", v1);
        assertSame(v1, v2, "Repeated get should return same cached instance");
    }

    @Test
    void shouldComputeDifferentValuesForDifferentParams() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );

        String v1 = cache.get("k", 1);
        String v2 = cache.get("k", 2);

        assertEquals("k:1", v1);
        assertEquals("k:2", v2);
        assertNotSame(v1, v2);
    }

    @Test
    void shouldComputeDifferentValuesForDifferentKeys() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );

        String v1 = cache.get("a", 1);
        String v2 = cache.get("b", 1);

        assertEquals("a:1", v1);
        assertEquals("b:1", v2);
    }

    @Test
    void shouldSupportNullKey() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> "null:" + param
        );

        String v = cache.get(null, 42);
        assertEquals("null:42", v);
    }

    @Test
    void shouldTrackSize() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );

        assertEquals(0, cache.size());
        cache.get("k", 1);
        assertEquals(1, cache.size());
        cache.get("k", 2);
        assertEquals(2, cache.size());
    }

    @Test
    void containsValueShouldReturnTrueForCachedValue() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );

        String v = cache.get("k", 1);
        assertTrue(cache.containsValue(v));
    }

    @Test
    void containsValueShouldReturnFalseForNonCachedValue() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );

        cache.get("k", 1);
        assertFalse(cache.containsValue("not-cached"));
    }

    @Test
    void shouldRejectNullParameter() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> "value"
        );

        assertThrows(NullPointerException.class, () -> cache.get("k", null));
    }

    @Test
    void removeIfRemovesMatchingKeysOnly() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );
        String va = cache.get("a", 1);
        String vb = cache.get("b", 1);

        cache.removeIf(k -> "a".equals(k));

        assertFalse(cache.containsValue(va));
        assertTrue(cache.containsValue(vb));
        assertEquals(1, cache.size());
    }

    @Test
    void removeIfCausesReevaluationOnNextGet() {
        AtomicInteger calls = new AtomicInteger();
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> {
                    calls.incrementAndGet();
                    return key + ":" + param;
                }
        );
        cache.get("a", 1);
        cache.get("a", 1);
        assertEquals(1, calls.get());

        cache.removeIf(k -> "a".equals(k));
        cache.get("a", 1);
        assertEquals(2, calls.get());
    }

    @Test
    void removeIfOnEmptyCacheIsNoOp() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> key + ":" + param
        );
        assertDoesNotThrow(() -> cache.removeIf(k -> true));
        assertEquals(0, cache.size());
    }

    @Test
    void removeIfSkipsNullSentinel() {
        WeakCache<String, Integer, String> cache = new WeakCache<>(
                (key, param) -> param,
                (key, param) -> "null:" + param
        );
        cache.get(null, 1);
        AtomicBoolean sawNull = new AtomicBoolean(false);
        cache.removeIf(k -> {
            if (k == null) sawNull.set(true);
            return false;
        });
        assertFalse(sawNull.get());
    }
}

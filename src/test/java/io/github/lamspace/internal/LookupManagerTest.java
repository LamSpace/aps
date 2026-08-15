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

package io.github.lamspace.internal;

import org.junit.jupiter.api.Test;

import java.lang.invoke.MethodHandles;

import static org.junit.jupiter.api.Assertions.*;

class LookupManagerTest {

    @Test
    void shouldReturnNonNullLookupForStandardClass() {
        // A classpath class lives in the unnamed module, which is always open.
        MethodHandles.Lookup lookup = LookupManager.getLookup(LookupManager.class);
        assertNotNull(lookup);
    }

    @Test
    void shouldFallBackGracefullyForPrimitiveType() {
        // Primitive types are rejected by privateLookupIn —
        // getLookup must fall back gracefully
        MethodHandles.Lookup lookup = LookupManager.getLookup(int.class);
        assertNotNull(lookup);
    }

    @Test
    void shouldFallBackGracefullyForArrayType() {
        // Array types are rejected by privateLookupIn —
        // getLookup must fall back gracefully
        MethodHandles.Lookup lookup =
                LookupManager.getLookup(String[].class);
        assertNotNull(lookup);
    }

    @Test
    void shouldReturnLookupForInnerClass() {
        MethodHandles.Lookup lookup =
                LookupManager.getLookup(LookupManagerTest.class);
        assertNotNull(lookup);
    }

    @Test
    void shouldThrowActionableErrorForStronglyEncapsulatedClass() {
        // java.util is exported but not open, so privateLookupIn is denied.
        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> LookupManager.getLookup(java.util.ArrayList.class));

        assertTrue(ex.getMessage().contains("--add-opens"),
                "message should contain a --add-opens hint: "
                        + ex.getMessage());
    }
}

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

import java.util.Arrays;

/**
 * Maps each method (by stable-sorted index) to its assigned Interceptor
 * index in the deduped {@code Interceptor[]} array, or {@code -1} for
 * passthrough. Internal type — not part of the public API.
 */
public final class MethodMapping {

    private final int[] indices;

    /**
     * @param indices {@code indices[i]} = interceptor index, or -1 for passthrough
     */
    public MethodMapping(int[] indices) {
        this.indices = Arrays.copyOf(indices, indices.length);
    }

    /**
     * Returns the number of distinct Interceptor slots needed.
     * Equal to {@code max(indices) + 1}, or 0 if all passthrough.
     */
    public int interceptorCount() {
        int max = -1;
        for (int idx : indices) {
            if (idx > max) max = idx;
        }
        return max + 1;
    }

    /**
     * Returns a copy of the method → interceptor index array, where
     * {@code -1} denotes passthrough.
     *
     * @return a defensive copy of the index array
     */
    public int[] indices() {
        return indices.clone();
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof MethodMapping other)) return false;
        return Arrays.equals(indices, other.indices);
    }

    @Override
    public int hashCode() {
        return Arrays.hashCode(indices);
    }

    @Override
    public String toString() {
        return "MethodMapping" + Arrays.toString(indices);
    }
}

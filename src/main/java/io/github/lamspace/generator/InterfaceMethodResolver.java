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

package io.github.lamspace.generator;

import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Resolves the merged, deduplicated, conflict-checked method set for a list of
 * interfaces. This is the single source of truth for the method order used by
 * both {@code AcceleratedProxy.matchMethods} and {@code InterfaceGenerator},
 * which is what keeps the {@code MethodMapping} indices aligned with the
 * emitted method implementations.
 *
 * <p>Conflict rules: a signature (name + parameter types) may appear in several
 * interfaces. Identical return types merge; differing return types throw
 * {@link IllegalArgumentException}; two {@code default} implementations from
 * distinct interfaces throw; a single {@code default} plus abstract
 * declarations merge with the default as the {@code invokeSuper} target.
 */
public final class InterfaceMethodResolver {

    /**
     * A method in the merged interface method set.
     *
     * @param canonical    the first {@code Method} object found for the
     *                     signature, used for the generated implementation and
     *                     the {@code intercept} argument
     * @param owner        the array interface that yielded {@code canonical},
     *                     used as the {@code <clinit>} {@code getMethod} target
     * @param variants     all distinct {@code Method} objects sharing the
     *                     signature, used for dispatch hash routing
     * @param defaultOwner the array interface holding the {@code default}
     *                     implementation, or {@code null} if abstract
     */
    public record ResolvedMethod(Method canonical, Class<?> owner,
                                 List<Method> variants, Class<?> defaultOwner) {
        /**
         * Validates the required components and defensively copies
         * {@code variants}.
         */
        public ResolvedMethod {
            canonical = Objects.requireNonNull(canonical, "canonical");
            owner = Objects.requireNonNull(owner, "owner");
            variants = List.copyOf(variants);
            if (variants.isEmpty()) {
                throw new IllegalArgumentException("variants must not be empty");
            }
        }
    }

    private InterfaceMethodResolver() {
    }

    /**
     * Resolves the merged method set for the given interfaces.
     *
     * @param interfaces the interfaces to implement; each must be an interface
     * @return the resolved methods, sorted by (name, parameter types)
     * @throws IllegalArgumentException if an element is not an interface, or a
     *                                  cross-interface method conflict is found
     */
    public static List<ResolvedMethod> resolve(Class<?>[] interfaces) {
        Map<String, Method> canonical = new LinkedHashMap<>();
        Map<String, Class<?>> owner = new LinkedHashMap<>();
        Map<String, List<Method>> variants = new LinkedHashMap<>();
        Map<String, Class<?>> defaultOwner = new LinkedHashMap<>();

        for (Class<?> itf : interfaces) {
            if (!itf.isInterface()) {
                throw new IllegalArgumentException(
                        "not an interface: " + itf.getName());
            }
            for (Method m : itf.getMethods()) {
                int mods = m.getModifiers();
                if (Modifier.isStatic(mods) || Modifier.isFinal(mods)) {
                    continue;
                }
                String key = signatureKey(m);
                Method existing = canonical.get(key);
                if (existing == null) {
                    canonical.put(key, m);
                    owner.put(key, itf);
                    List<Method> vs = new ArrayList<>();
                    vs.add(m);
                    variants.put(key, vs);
                    defaultOwner.put(key, m.isDefault() ? itf : null);
                } else if (existing != m) {
                    if (existing.getReturnType() != m.getReturnType()) {
                        throw new IllegalArgumentException(
                                "Conflicting return types for method '" + key
                                        + "': "
                                        + existing.getReturnType().getName()
                                        + " vs " + m.getReturnType().getName());
                    }
                    if (existing.isDefault() && m.isDefault()) {
                        throw new IllegalArgumentException(
                                "Ambiguous default method '" + key + "' in "
                                        + existing.getDeclaringClass().getName()
                                        + " and " + m.getDeclaringClass().getName());
                    }
                    List<Method> vs = variants.get(key);
                    if (!vs.contains(m)) {
                        vs.add(m);
                    }
                    if (m.isDefault() && !existing.isDefault()) {
                        defaultOwner.put(key, itf);
                    }
                }
            }
        }

        List<ResolvedMethod> result = new ArrayList<>();
        for (Map.Entry<String, Method> e : canonical.entrySet()) {
            String key = e.getKey();
            result.add(new ResolvedMethod(e.getValue(), owner.get(key),
                    variants.get(key), defaultOwner.get(key)));
        }
        result.sort(Comparator.comparing(
                        (ResolvedMethod r) -> r.canonical().getName())
                .thenComparing(r -> Arrays.toString(
                        r.canonical().getParameterTypes())));
        return result;
    }

    /**
     * Builds the {@code name(paramTypeName,...)} key identifying a method
     * signature across interfaces.
     */
    private static String signatureKey(Method m) {
        StringBuilder sb = new StringBuilder(m.getName()).append('(');
        for (Class<?> p : m.getParameterTypes()) {
            sb.append(p.getName()).append(',');
        }
        return sb.append(')').toString();
    }
}

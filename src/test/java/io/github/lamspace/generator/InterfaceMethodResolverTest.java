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

import io.github.lamspace.generator.InterfaceMethodResolver.ResolvedMethod;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class InterfaceMethodResolverTest {

    interface A {
        String hello(String name);

        default String greet() {
            return "hi";
        }
    }

    interface B {
        String hello(String name);          // same signature + same return -> merge

        int count();                        // distinct
    }

    interface C {
        Integer hello(String name);         // same signature, DIFFERENT return -> conflict
    }

    interface D {
        default String greet() {
            return "yo";
        }
    }

    interface Parent {
        String inherited();
    }

    interface Child extends Parent {
        String own();
    }

    @Test
    void mergesSameSignatureSameReturn() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(
                new Class<?>[]{A.class, B.class});
        ResolvedMethod hello = rs.stream()
                .filter(r -> r.canonical().getName().equals("hello"))
                .findFirst().orElseThrow();
        assertEquals(2, hello.variants().size());
        assertNull(hello.defaultOwner());   // A.hello and B.hello are abstract
        assertEquals(A.class, hello.owner()); // first array interface
    }

    @Test
    void objectMethodsNotIncludedInResolvedSet() {
        // Object methods (toString/equals/hashCode) are not part of an
        // interface's getMethods(); they are inherited by the generated class.
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(
                new Class<?>[]{A.class, B.class});
        assertFalse(rs.stream().anyMatch(
                r -> r.canonical().getDeclaringClass() == Object.class));
    }

    @Test
    void oneDefaultPlusAbstractResolvesDefaultOwner() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(
                new Class<?>[]{A.class, B.class});
        ResolvedMethod greet = rs.stream()
                .filter(r -> r.canonical().getName().equals("greet"))
                .findFirst().orElseThrow();
        assertEquals(A.class, greet.defaultOwner());
    }

    @Test
    void differentReturnTypeThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> InterfaceMethodResolver.resolve(
                        new Class<?>[]{A.class, C.class}));
    }

    @Test
    void twoDefaultsThrows() {
        assertThrows(IllegalArgumentException.class,
                () -> InterfaceMethodResolver.resolve(
                        new Class<?>[]{A.class, D.class}));
    }

    @Test
    void parentChildDedupsInheritedMethod() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(
                new Class<?>[]{Child.class, Parent.class});
        long inherited = rs.stream()
                .filter(r -> r.canonical().getName().equals("inherited"))
                .count();
        assertEquals(1, inherited);
        ResolvedMethod m = rs.stream()
                .filter(r -> r.canonical().getName().equals("inherited"))
                .findFirst().orElseThrow();
        assertEquals(Child.class, m.owner());
    }

    @Test
    void singleInterfaceReturnsAllPublicMethodsSorted() {
        List<ResolvedMethod> rs = InterfaceMethodResolver.resolve(
                new Class<?>[]{A.class});
        assertFalse(rs.isEmpty());
        for (int i = 1; i < rs.size(); i++) {
            String prev = rs.get(i - 1).canonical().getName();
            String cur = rs.get(i).canonical().getName();
            assertTrue(prev.compareTo(cur) <= 0 || prev.equals(cur));
        }
    }

    @Test
    void rejectsNonInterface() {
        assertThrows(IllegalArgumentException.class,
                () -> InterfaceMethodResolver.resolve(
                        new Class<?>[]{String.class}));
    }
}

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

package io.github.lamspace.benchmark;

import org.openjdk.jmh.annotations.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Compares raw method-dispatch mechanisms: direct call, reflection
 * ({@code Method.invoke}), and MethodHandle ({@code invoke}, {@code invokeExact},
 * type-erased {@code invokeExact}).
 *
 * <p>Measures the cost of each dispatch channel in isolation — no proxy
 * generation, no callback wrapping.  Use this to understand the inherent
 * overhead each channel adds on top of the method body.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class DispatchBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    // ---- Target interfaces & impls ----------------------------------------

    public interface StringOp {
        String call(String input);
    }

    public interface IntOp {
        int add(int a, int b);
    }

    public interface VoidOp {
        void run();
    }

    public interface MultiOp {
        String multi(String a, int b, long c, double d);
    }

    static class StringOpImpl implements StringOp {
        public String call(String input) {
            return "Hello, " + input;
        }
    }

    static class IntOpImpl implements IntOp {
        public int add(int a, int b) {
            return a + b;
        }
    }

    static class VoidOpImpl implements VoidOp {
        public void run() {
            /* no-op */
        }
    }

    static class MultiOpImpl implements MultiOp {
        public String multi(String a, int b, long c, double d) {
            return a + "-" + b + "-" + c + "-" + d;
        }
    }

    // ================================================================
    // Scenario 1: String call(String) — 1 ref arg, ref return
    // ================================================================

    @State(Scope.Thread)
    public static class StringOpState {
        StringOp target;
        Method reflectMethod;
        MethodHandle nativeMH;   // (StringOp,String)String
        MethodHandle erasedMH;   // (Object,Object[])Object   APS-style

        @Setup
        public void setup() throws Throwable {
            target = new StringOpImpl();
            reflectMethod = StringOp.class.getMethod("call", String.class);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = MethodType.methodType(String.class, String.class);

            nativeMH = lookup.findVirtual(StringOp.class, "call", mt);

            // APS-style type erasure: asSpreader + asType → uniform sig
            MethodHandle raw = lookup.findVirtual(StringOp.class, "call", mt);
            erasedMH = raw.asSpreader(Object[].class, 1)
                    .asType(MethodType.methodType(Object.class,
                            Object.class, Object[].class));
        }
    }

    @Benchmark
    public String call_direct(StringOpState s) {
        return s.target.call("World");
    }

    @Benchmark
    public String call_reflect(StringOpState s) throws Throwable {
        return (String) s.reflectMethod.invoke(s.target, "World");
    }

    @Benchmark
    public String call_mhInvoke(StringOpState s) throws Throwable {
        return (String) s.nativeMH.invoke(s.target, "World");
    }

    @Benchmark
    public String call_mhInvokeExact(StringOpState s) throws Throwable {
        return (String) s.nativeMH.invokeExact(s.target, "World");
    }

    @Benchmark
    public String call_mhErased(StringOpState s) throws Throwable {
        return (String) s.erasedMH.invoke((Object) s.target,
                new Object[]{"World"});
    }

    // ================================================================
    // Scenario 2: int add(int, int) — 2 prim args, prim return
    // ================================================================

    @State(Scope.Thread)
    public static class IntOpState {
        IntOp target;
        Method reflectMethod;
        MethodHandle nativeMH;   // (IntOp,int,int)int
        MethodHandle erasedMH;   // (Object,Object[])Object

        @Setup
        public void setup() throws Throwable {
            target = new IntOpImpl();
            reflectMethod = IntOp.class.getMethod("add", int.class, int.class);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = MethodType.methodType(int.class, int.class, int.class);

            nativeMH = lookup.findVirtual(IntOp.class, "add", mt);

            MethodHandle raw = lookup.findVirtual(IntOp.class, "add", mt);
            erasedMH = raw.asSpreader(Object[].class, 2)
                    .asType(MethodType.methodType(Object.class,
                            Object.class, Object[].class));
        }
    }

    @Benchmark
    public int add_direct(IntOpState s) {
        return s.target.add(3, 4);
    }

    @Benchmark
    public int add_reflect(IntOpState s) throws Throwable {
        return (int) s.reflectMethod.invoke(s.target, 3, 4);
    }

    @Benchmark
    public int add_mhInvoke(IntOpState s) throws Throwable {
        return (int) s.nativeMH.invoke(s.target, 3, 4);
    }

    @Benchmark
    public int add_mhInvokeExact(IntOpState s) throws Throwable {
        return (int) s.nativeMH.invokeExact(s.target, 3, 4);
    }

    @Benchmark
    public int add_mhErased(IntOpState s) throws Throwable {
        return (int) s.erasedMH.invoke((Object) s.target,
                new Object[]{3, 4});
    }

    // ================================================================
    // Scenario 3: void run() — no args, void return
    // ================================================================

    @State(Scope.Thread)
    public static class VoidOpState {
        VoidOp target;
        Method reflectMethod;
        MethodHandle nativeMH;   // (VoidOp)void
        MethodHandle erasedMH;   // (Object,Object[])Object

        @Setup
        public void setup() throws Throwable {
            target = new VoidOpImpl();
            reflectMethod = VoidOp.class.getMethod("run");

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = MethodType.methodType(void.class);

            nativeMH = lookup.findVirtual(VoidOp.class, "run", mt);

            MethodHandle raw = lookup.findVirtual(VoidOp.class, "run", mt);
            erasedMH = raw.asSpreader(Object[].class, 0)
                    .asType(MethodType.methodType(Object.class,
                            Object.class, Object[].class));
        }
    }

    @Benchmark
    public void void_direct(VoidOpState s) {
        s.target.run();
    }

    @Benchmark
    public void void_reflect(VoidOpState s) throws Throwable {
        s.reflectMethod.invoke(s.target);
    }

    @Benchmark
    public void void_mhInvoke(VoidOpState s) throws Throwable {
        s.nativeMH.invoke(s.target);
    }

    @Benchmark
    public void void_mhInvokeExact(VoidOpState s) throws Throwable {
        s.nativeMH.invokeExact(s.target);
    }

    @Benchmark
    public void void_mhErased(VoidOpState s) throws Throwable {
        s.erasedMH.invoke((Object) s.target, new Object[0]);
    }

    // ================================================================
    // Scenario 4: String multi(String, int, long, double) — mixed args
    // ================================================================

    @State(Scope.Thread)
    public static class MultiOpState {
        MultiOp target;
        Method reflectMethod;
        MethodHandle nativeMH;   // (MultiOp,String,int,long,double)String
        MethodHandle erasedMH;   // (Object,Object[])Object

        @Setup
        public void setup() throws Throwable {
            target = new MultiOpImpl();
            reflectMethod = MultiOp.class.getMethod("multi",
                    String.class, int.class, long.class, double.class);

            MethodHandles.Lookup lookup = MethodHandles.lookup();
            MethodType mt = MethodType.methodType(String.class,
                    String.class, int.class, long.class, double.class);

            nativeMH = lookup.findVirtual(MultiOp.class, "multi", mt);

            MethodHandle raw = lookup.findVirtual(MultiOp.class, "multi", mt);
            erasedMH = raw.asSpreader(Object[].class, 4)
                    .asType(MethodType.methodType(Object.class,
                            Object.class, Object[].class));
        }
    }

    @Benchmark
    public String multi_direct(MultiOpState s) {
        return s.target.multi("a", 1, 2L, 3.0);
    }

    @Benchmark
    public String multi_reflect(MultiOpState s) throws Throwable {
        return (String) s.reflectMethod.invoke(s.target,
                "a", 1, 2L, 3.0);
    }

    @Benchmark
    public String multi_mhInvoke(MultiOpState s) throws Throwable {
        return (String) s.nativeMH.invoke(s.target,
                "a", 1, 2L, 3.0);
    }

    @Benchmark
    public String multi_mhInvokeExact(MultiOpState s) throws Throwable {
        return (String) s.nativeMH.invokeExact(s.target,
                "a", 1, 2L, 3.0);
    }

    @Benchmark
    public String multi_mhErased(MultiOpState s) throws Throwable {
        return (String) s.erasedMH.invoke((Object) s.target,
                new Object[]{"a", 1, 2L, 3.0});
    }
}

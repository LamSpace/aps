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

import io.github.lamspace.AcceleratedProxy;
import io.github.lamspace.Group;
import io.github.lamspace.Interceptor;
import org.openjdk.jmh.annotations.*;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * Measures the cost of APS static-method proxying on top of the caller's
 * chosen invocation mechanism (reflection or MethodHandle). Static methods
 * are compile-time bound, so the entry mechanism — not APS — dominates.
 *
 * <p>{@code proxyPassthrough} isolates APS's shadow-dispatch overhead (a
 * non-matching shadow that direct-{@code INVOKESTATIC}s the original, with no
 * interceptor); {@code proxyIntercepted}/{@code proxyMethodHandle} measure the
 * full intercepted round trip (box + one {@code intercept} call + the
 * interceptor's reflective {@code method.invoke} + unbox).
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class StaticMethodProxyBenchmark {

    public static class Target {
        public static int staticAdd(int a, int b) {
            return a + b;
        }
    }

    private static final Interceptor CALL_ORIGINAL =
            (obj, method, args) -> method.invoke(null, args);

    private Method reflectionMethod;
    private Method passthroughMethod;
    private Method interceptedMethod;
    private MethodHandle interceptedHandle;

    @Setup
    public void setup() throws Exception {
        reflectionMethod = Target.class.getMethod("staticAdd",
                int.class, int.class);

        Class<?> passthroughClass = AcceleratedProxy.proxyStatic(Target.class,
                Group.of(m -> false, (o, m, a) -> null));
        passthroughMethod = passthroughClass.getMethod("staticAdd",
                int.class, int.class);

        Class<?> interceptedClass = AcceleratedProxy.proxyStatic(Target.class,
                Group.otherwise(CALL_ORIGINAL));
        interceptedMethod = interceptedClass.getMethod("staticAdd",
                int.class, int.class);
        interceptedHandle = MethodHandles.lookup().findStatic(interceptedClass,
                "staticAdd",
                MethodType.methodType(int.class, int.class, int.class));
    }

    @Benchmark
    public int directCall() {
        return Target.staticAdd(2, 3);
    }

    @Benchmark
    public int reflectionFloor() throws Exception {
        return (Integer) reflectionMethod.invoke(null, 2, 3);
    }

    @Benchmark
    public int proxyPassthrough() throws Exception {
        return (Integer) passthroughMethod.invoke(null, 2, 3);
    }

    @Benchmark
    public int proxyIntercepted() throws Exception {
        return (Integer) interceptedMethod.invoke(null, 2, 3);
    }

    @Benchmark
    public int proxyMethodHandle() throws Throwable {
        return (Integer) interceptedHandle.invoke(2, 3);
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}

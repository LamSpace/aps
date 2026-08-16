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
import io.github.lamspace.Interceptor;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.Setup;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.util.concurrent.TimeUnit;

/**
 * Measures the cost of a single {@link AcceleratedProxy#rebind} call on an
 * existing class proxy. This is a rare management operation, not the hot
 * path — the number is informational, not a comparison against
 * {@code reflect.Proxy}.
 */
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
@State(Scope.Benchmark)
public class RebindBenchmark {

    public static class Target {
        public String hello(String name) {
            return "Hello, " + name;
        }
    }

    private static final Interceptor NOOP_A = (o, m, a) -> null;
    private static final Interceptor NOOP_B = (o, m, a) -> null;

    private Target proxy;

    @Setup
    public void setup() {
        proxy = AcceleratedProxy.proxy(Target.class, NOOP_A);
    }

    @Benchmark
    public void rebind() {
        AcceleratedProxy.rebind(proxy, NOOP_B);
    }

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }
}

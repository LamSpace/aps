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
import io.github.lamspace.ConstructorInterceptor;
import io.github.lamspace.Group;
import io.github.lamspace.Interceptor;
import org.openjdk.jmh.annotations.*;

import java.lang.reflect.Constructor;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ConstructorInterceptionBenchmark {

    // A CGLib comparison is intentionally omitted: the pinned cglib 3.3.0
    // Enhancer.setInterceptDuringConstruction(true) did not invoke the
    // MethodInterceptor during construction in either no-arg or with-args
    // create() calls, so a faithful constructor-callback reference could not
    // be reproduced. The direct/plain/intercepted three-way comparison is the
    // authoritative measure of the hook's per-instance cost.

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    public static class Target {
        public int sum(int a, int b) {
            return a + b;
        }
    }

    private static final Interceptor NOOP =
            (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args);

    private static final ConstructorInterceptor NOOP_CTOR =
            new ConstructorInterceptor() {
                @Override
                public Object[] before(Constructor<?> ctor, Object[] args) {
                    return args;
                }
            };

    @Benchmark
    public Object directNew() {
        return new Target();
    }

    @Benchmark
    public Object plainProxy() {
        return AcceleratedProxy.proxy(Target.class, NOOP);
    }

    @Benchmark
    public Object interceptedProxy() {
        return AcceleratedProxy.proxy(Target.class, NOOP_CTOR,
                Group.otherwise(NOOP));
    }
}

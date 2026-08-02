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

import io.github.lamspace.APS;
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.openjdk.jmh.annotations.*;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ProxyBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    // ---------------------------------------------------------------
    // Shared interfaces & concrete classes
    // ---------------------------------------------------------------

    public interface StringOp {
        String call(String input);
    }

    public interface IntOp {
        int call(int a, int b);
    }

    public interface VoidOp {
        void call();
    }

    public interface MultiOp {
        String call(String a, int b, long c, double d);
    }

    static class StringOpImpl implements StringOp {
        public String call(String input) {
            return "Hello, " + input;
        }
    }

    static class IntOpImpl implements IntOp {
        public int call(int a, int b) {
            return a + b;
        }
    }

    static class VoidOpImpl implements VoidOp {
        public void call() {
            /* side effect target */
        }
    }

    static class MultiOpImpl implements MultiOp {
        public String call(String a, int b, long c, double d) {
            return a + "-" + b + "-" + c + "-" + d;
        }
    }

    // ===============================================================
    // Scenario 1: No-op — callback returns fixed value, no super call
    // ===============================================================

    @State(Scope.Thread)
    public static class NoopState {
        StringOp direct;
        StringOp javaProxy;
        StringOpImpl apsProxy;
        StringOpImpl cglibProxy;

        @Setup
        public void setup() {
            direct = new StringOpImpl();

            // Java Proxy (interface-based)
            javaProxy = (StringOp) Proxy.newProxyInstance(
                    StringOp.class.getClassLoader(),
                    new Class<?>[]{StringOp.class},
                    (proxy, method, args1) -> "fixed"
            );

            // APS — no super call
            apsProxy = APS.proxy(StringOpImpl.class,
                    (obj, method, args) -> "fixed");

            // CGLib — no invokeSuper call
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(StringOpImpl.class);
            enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> "fixed");
            cglibProxy = (StringOpImpl) enhancer.create();
        }
    }

    @Benchmark
    public String noop_direct(NoopState s) {
        return s.direct.call("World");
    }

    @Benchmark
    public String noop_javaProxy(NoopState s) {
        return s.javaProxy.call("World");
    }

    @Benchmark
    public String noop_aps(NoopState s) {
        return s.apsProxy.call("World");
    }

    @Benchmark
    public String noop_cglib(NoopState s) {
        return s.cglibProxy.call("World");
    }

    // ===============================================================
    // Scenario 2: Passthrough — callback calls super method
    // ===============================================================

    @State(Scope.Thread)
    public static class PassthroughState {
        StringOp direct;
        StringOp javaProxy;
        StringOpImpl apsProxy;
        StringOpImpl cglibProxy;

        @Setup
        public void setup() {
            direct = new StringOpImpl();

            javaProxy = (StringOp) Proxy.newProxyInstance(
                    StringOp.class.getClassLoader(),
                    new Class<?>[]{StringOp.class},
                    (proxy, method, args1) -> {
                        return method.invoke(new StringOpImpl(), args1);
                    }
            );

            // APS — APS.invokeSuper to call original method
            apsProxy = APS.proxy(StringOpImpl.class,
                    (obj, method, args) -> APS.invokeSuper(obj, method, args));

            // CGLib — proxy.invokeSuper(obj, args) to call original method
            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(StringOpImpl.class);
            enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
                    proxy.invokeSuper(obj, args));
            cglibProxy = (StringOpImpl) enhancer.create();
        }
    }

    @Benchmark
    public String passthrough_direct(PassthroughState s) {
        return s.direct.call("World");
    }

    @Benchmark
    public String passthrough_javaProxy(PassthroughState s) {
        return s.javaProxy.call("World");
    }

    @Benchmark
    public String passthrough_aps(PassthroughState s) {
        return s.apsProxy.call("World");
    }

    @Benchmark
    public String passthrough_cglib(PassthroughState s) {
        return s.cglibProxy.call("World");
    }

    // ===============================================================
    // Scenario 3: Arg modify — modify argument then call super
    // ===============================================================

    @State(Scope.Thread)
    public static class ArgModifyState {
        StringOp direct;
        StringOp javaProxy;
        StringOpImpl apsProxy;
        StringOpImpl cglibProxy;

        @Setup
        public void setup() {
            direct = new StringOpImpl();

            javaProxy = (StringOp) Proxy.newProxyInstance(
                    StringOp.class.getClassLoader(),
                    new Class<?>[]{StringOp.class},
                    (proxy, method, args1) -> {
                        args1[0] = "[" + args1[0] + "]";
                        return method.invoke(new StringOpImpl(), args1);
                    }
            );

            apsProxy = APS.proxy(StringOpImpl.class,
                    (obj, method, args) -> {
                        args[0] = "[" + args[0] + "]";
                        return APS.invokeSuper(obj, method, args);
                    });

            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(StringOpImpl.class);
            enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
                args[0] = "[" + args[0] + "]";
                return proxy.invokeSuper(obj, args);
            });
            cglibProxy = (StringOpImpl) enhancer.create();
        }
    }

    @Benchmark
    public String argmod_direct(ArgModifyState s) {
        return s.direct.call("World");
    }

    @Benchmark
    public String argmod_javaProxy(ArgModifyState s) {
        return s.javaProxy.call("World");
    }

    @Benchmark
    public String argmod_aps(ArgModifyState s) {
        return s.apsProxy.call("World");
    }

    @Benchmark
    public String argmod_cglib(ArgModifyState s) {
        return s.cglibProxy.call("World");
    }

    // ===============================================================
    // Scenario 4: Primitive return — int add(int, int)
    // ===============================================================

    @State(Scope.Thread)
    public static class PrimitiveState {
        IntOp direct;
        IntOp javaProxy;
        IntOpImpl apsProxy;
        IntOpImpl cglibProxy;

        @Setup
        public void setup() {
            direct = new IntOpImpl();

            javaProxy = (IntOp) Proxy.newProxyInstance(
                    IntOp.class.getClassLoader(),
                    new Class<?>[]{IntOp.class},
                    (proxy, method, args1) ->
                            method.invoke(new IntOpImpl(), args1)
            );

            apsProxy = APS.proxy(IntOpImpl.class,
                    (obj, method, args) -> APS.invokeSuper(obj, method, args));

            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(IntOpImpl.class);
            enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
                    proxy.invokeSuper(obj, args));
            cglibProxy = (IntOpImpl) enhancer.create();
        }
    }

    @Benchmark
    public int primitive_direct(PrimitiveState s) {
        return s.direct.call(3, 4);
    }

    @Benchmark
    public int primitive_javaProxy(PrimitiveState s) {
        return s.javaProxy.call(3, 4);
    }

    @Benchmark
    public int primitive_aps(PrimitiveState s) {
        return s.apsProxy.call(3, 4);
    }

    @Benchmark
    public int primitive_cglib(PrimitiveState s) {
        return s.cglibProxy.call(3, 4);
    }

    // ===============================================================
    // Scenario 5: Void method — void sideEffect()
    // ===============================================================

    @State(Scope.Thread)
    public static class VoidState {
        VoidOp direct;
        VoidOp javaProxy;
        VoidOpImpl apsProxy;
        VoidOpImpl cglibProxy;

        @Setup
        public void setup() {
            direct = new VoidOpImpl();

            javaProxy = (VoidOp) Proxy.newProxyInstance(
                    VoidOp.class.getClassLoader(),
                    new Class<?>[]{VoidOp.class},
                    (proxy, method, args1) -> {
                        method.invoke(new VoidOpImpl(), args1);
                        return null;
                    }
            );

            apsProxy = APS.proxy(VoidOpImpl.class,
                    (obj, method, args) -> APS.invokeSuper(obj, method, args));

            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(VoidOpImpl.class);
            enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
                    proxy.invokeSuper(obj, args));
            cglibProxy = (VoidOpImpl) enhancer.create();
        }
    }

    @Benchmark
    public void void_direct(VoidState s) {
        s.direct.call();
    }

    @Benchmark
    public void void_javaProxy(VoidState s) {
        s.javaProxy.call();
    }

    @Benchmark
    public void void_aps(VoidState s) {
        s.apsProxy.call();
    }

    @Benchmark
    public void void_cglib(VoidState s) {
        s.cglibProxy.call();
    }

    // ===============================================================
    // Scenario 6: Multi-param — String process(String, int, long, double)
    // ===============================================================

    @State(Scope.Thread)
    public static class MultiParamState {
        MultiOp direct;
        MultiOp javaProxy;
        MultiOpImpl apsProxy;
        MultiOpImpl cglibProxy;

        @Setup
        public void setup() {
            direct = new MultiOpImpl();

            javaProxy = (MultiOp) Proxy.newProxyInstance(
                    MultiOp.class.getClassLoader(),
                    new Class<?>[]{MultiOp.class},
                    (proxy, method, args1) ->
                            method.invoke(new MultiOpImpl(), args1)
            );

            apsProxy = APS.proxy(MultiOpImpl.class,
                    (obj, method, args) -> APS.invokeSuper(obj, method, args));

            Enhancer enhancer = new Enhancer();
            enhancer.setSuperclass(MultiOpImpl.class);
            enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
                    proxy.invokeSuper(obj, args));
            cglibProxy = (MultiOpImpl) enhancer.create();
        }
    }

    @Benchmark
    public String multiparam_direct(MultiParamState s) {
        return s.direct.call("a", 1, 2L, 3.0);
    }

    @Benchmark
    public String multiparam_javaProxy(MultiParamState s) {
        return s.javaProxy.call("a", 1, 2L, 3.0);
    }

    @Benchmark
    public String multiparam_aps(MultiParamState s) {
        return s.apsProxy.call("a", 1, 2L, 3.0);
    }

    @Benchmark
    public String multiparam_cglib(MultiParamState s) {
        return s.cglibProxy.call("a", 1, 2L, 3.0);
    }

    // ===============================================================
    // Scenario 7: Interface no-op — callback returns fixed value
    // ===============================================================

    @State(Scope.Thread)
    public static class IfaceNoopState {
        StringOp direct;
        StringOp javaProxy;
        StringOp apsIface;

        @Setup
        public void setup() {
            direct = new StringOpImpl();

            javaProxy = (StringOp) Proxy.newProxyInstance(
                    StringOp.class.getClassLoader(),
                    new Class<?>[]{StringOp.class},
                    (proxy, method, args1) -> "fixed"
            );

            apsIface = APS.proxy(StringOp.class,
                    (obj, method, args) -> "fixed");
        }
    }

    @Benchmark
    public String iface_noop_direct(IfaceNoopState s) {
        return s.direct.call("World");
    }

    @Benchmark
    public String iface_noop_javaProxy(IfaceNoopState s) {
        return s.javaProxy.call("World");
    }

    @Benchmark
    public String iface_noop_aps(IfaceNoopState s) {
        return s.apsIface.call("World");
    }

    // ===============================================================
    // Scenario 8: Interface passthrough — callback returns computed value
    // ===============================================================

    @State(Scope.Thread)
    public static class IfacePassthroughState {
        StringOp javaProxy;
        StringOp apsIface;

        @Setup
        public void setup() {
            javaProxy = (StringOp) Proxy.newProxyInstance(
                    StringOp.class.getClassLoader(),
                    new Class<?>[]{StringOp.class},
                    (proxy, method, args1) ->
                            "Hello, " + args1[0]
            );

            apsIface = APS.proxy(StringOp.class,
                    (obj, method, args) -> "Hello, " + args[0]);
        }
    }

    @Benchmark
    public String iface_passthrough_javaProxy(IfacePassthroughState s) {
        return s.javaProxy.call("World");
    }

    @Benchmark
    public String iface_passthrough_aps(IfacePassthroughState s) {
        return s.apsIface.call("World");
    }

    // ===============================================================
    // Scenario 9: Interface arg modify — modify argument in callback
    // ===============================================================

    @State(Scope.Thread)
    public static class IfaceArgModifyState {
        StringOp javaProxy;
        StringOp apsIface;

        @Setup
        public void setup() {
            javaProxy = (StringOp) Proxy.newProxyInstance(
                    StringOp.class.getClassLoader(),
                    new Class<?>[]{StringOp.class},
                    (proxy, method, args1) -> {
                        args1[0] = "[" + args1[0] + "]";
                        return args1[0];
                    }
            );

            apsIface = APS.proxy(StringOp.class,
                    (obj, method, args) -> {
                        args[0] = "[" + args[0] + "]";
                        return args[0];
                    });
        }
    }

    @Benchmark
    public String iface_argmod_javaProxy(IfaceArgModifyState s) {
        return s.javaProxy.call("World");
    }

    @Benchmark
    public String iface_argmod_aps(IfaceArgModifyState s) {
        return s.apsIface.call("World");
    }

    // ===============================================================
    // Scenario 10: Interface primitive return
    // ===============================================================

    @State(Scope.Thread)
    public static class IfacePrimitiveState {
        IntOp javaProxy;
        IntOp apsIface;

        @Setup
        public void setup() {
            javaProxy = (IntOp) Proxy.newProxyInstance(
                    IntOp.class.getClassLoader(),
                    new Class<?>[]{IntOp.class},
                    (proxy, method, args1) ->
                            (int) args1[0] + (int) args1[1]
            );

            apsIface = APS.proxy(IntOp.class,
                    (obj, method, args) ->
                            (int) args[0] + (int) args[1]);
        }
    }

    @Benchmark
    public int iface_primitive_javaProxy(IfacePrimitiveState s) {
        return s.javaProxy.call(3, 4);
    }

    @Benchmark
    public int iface_primitive_aps(IfacePrimitiveState s) {
        return s.apsIface.call(3, 4);
    }

    // ===============================================================
    // Scenario 11: Interface void method
    // ===============================================================

    @State(Scope.Thread)
    public static class IfaceVoidState {
        VoidOp javaProxy;
        VoidOp apsIface;

        @Setup
        public void setup() {
            javaProxy = (VoidOp) Proxy.newProxyInstance(
                    VoidOp.class.getClassLoader(),
                    new Class<?>[]{VoidOp.class},
                    (proxy, method, args1) -> null
            );

            apsIface = APS.proxy(VoidOp.class,
                    (obj, method, args) -> null);
        }
    }

    @Benchmark
    public void iface_void_javaProxy(IfaceVoidState s) {
        s.javaProxy.call();
    }

    @Benchmark
    public void iface_void_aps(IfaceVoidState s) {
        s.apsIface.call();
    }

    // ===============================================================
    // Scenario 12: Interface multi-param
    // ===============================================================

    @State(Scope.Thread)
    public static class IfaceMultiParamState {
        MultiOp javaProxy;
        MultiOp apsIface;

        @Setup
        public void setup() {
            javaProxy = (MultiOp) Proxy.newProxyInstance(
                    MultiOp.class.getClassLoader(),
                    new Class<?>[]{MultiOp.class},
                    (proxy, method, args1) ->
                            args1[0] + "-" + args1[1] + "-" + args1[2]
                                    + "-" + args1[3]
            );

            apsIface = APS.proxy(MultiOp.class,
                    (obj, method, args) ->
                            args[0] + "-" + args[1] + "-" + args[2]
                                    + "-" + args[3]);
        }
    }

    @Benchmark
    public String iface_multiparam_javaProxy(IfaceMultiParamState s) {
        return s.javaProxy.call("a", 1, 2L, 3.0);
    }

    @Benchmark
    public String iface_multiparam_aps(IfaceMultiParamState s) {
        return s.apsIface.call("a", 1, 2L, 3.0);
    }
}

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

    interface StringOp {
        String call(String input);
    }

    interface IntOp {
        int call(int a, int b);
    }

    interface VoidOp {
        void call();
    }

    interface MultiOp {
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

            // APS — no superHandle call
            apsProxy = APS.create(StringOpImpl.class,
                    (obj, method, superHandle, args) -> "fixed");

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

            // APS — superHandle.invoke(args) to call original method
            apsProxy = APS.create(StringOpImpl.class,
                    (obj, method, superHandle, args) -> superHandle.invoke(args));

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

            apsProxy = APS.create(StringOpImpl.class,
                    (obj, method, superHandle, args) -> {
                        args[0] = "[" + args[0] + "]";
                        return superHandle.invoke(args);
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

            apsProxy = APS.create(IntOpImpl.class,
                    (obj, method, superHandle, args) -> superHandle.invoke(args));

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

            apsProxy = APS.create(VoidOpImpl.class,
                    (obj, method, superHandle, args) -> superHandle.invoke(args));

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

            apsProxy = APS.create(MultiOpImpl.class,
                    (obj, method, superHandle, args) -> superHandle.invoke(args));

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
}

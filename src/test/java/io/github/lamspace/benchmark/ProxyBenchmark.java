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
import net.sf.cglib.proxy.Enhancer;
import net.sf.cglib.proxy.MethodInterceptor;
import org.openjdk.jmh.annotations.*;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ProxyBenchmark {

    public static void main(String[] args) throws Exception {
        org.openjdk.jmh.Main.main(args);
    }

    // ================================================================
    // Target: Return-type coverage (2 params, varying return)
    // ================================================================

    interface RetOps {
        int intOp(int a, int b);

        long longOp(long a, long b);

        double doubleOp(double a, double b);

        float floatOp(float a, float b);

        boolean boolOp(int n);

        byte byteOp(byte a, byte b);

        char charOp(char c);

        short shortOp(short a, short b);

        void voidOp();

        Integer intWrapOp(Integer a, Integer b);

        String strOp(String a, String b);
    }

    static class RetOpsImpl implements RetOps {
        public int intOp(int a, int b) {
            return a + b;
        }

        public long longOp(long a, long b) {
            return a + b;
        }

        public double doubleOp(double a, double b) {
            return a + b;
        }

        public float floatOp(float a, float b) {
            return a + b;
        }

        public boolean boolOp(int n) {
            return n > 0;
        }

        public byte byteOp(byte a, byte b) {
            return (byte) (a + b);
        }

        public char charOp(char c) {
            return Character.toUpperCase(c);
        }

        public short shortOp(short a, short b) {
            return (short) (a + b);
        }

        public void voidOp() {
        }

        public Integer intWrapOp(Integer a, Integer b) {
            return a + b;
        }

        public String strOp(String a, String b) {
            return a + b;
        }
    }

    // ---------------------------------------------------------------
    // Class proxy — Return types
    // ---------------------------------------------------------------

    @State(Scope.Thread)
    public static class RetTypeState {
        RetOpsImpl direct;
        RetOpsImpl aps;
        RetOpsImpl cglib;

        @Setup
        public void setup() {
            direct = new RetOpsImpl();
            aps = AcceleratedProxy.proxy(RetOpsImpl.class,
                    (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));
            Enhancer e = new Enhancer();
            e.setSuperclass(RetOpsImpl.class);
            e.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
                    proxy.invokeSuper(obj, args));
            cglib = (RetOpsImpl) e.create();
        }
    }

    @Benchmark
    public int c_intOp(RetTypeState s) {
        return s.aps.intOp(3, 4);
    }

    @Benchmark
    public long c_longOp(RetTypeState s) {
        return s.aps.longOp(3L, 4L);
    }

    @Benchmark
    public double c_doubleOp(RetTypeState s) {
        return s.aps.doubleOp(3.0, 4.0);
    }

    @Benchmark
    public float c_floatOp(RetTypeState s) {
        return s.aps.floatOp(3f, 4f);
    }

    @Benchmark
    public boolean c_boolOp(RetTypeState s) {
        return s.aps.boolOp(5);
    }

    @Benchmark
    public byte c_byteOp(RetTypeState s) {
        return s.aps.byteOp((byte) 3, (byte) 4);
    }

    @Benchmark
    public char c_charOp(RetTypeState s) {
        return s.aps.charOp('a');
    }

    @Benchmark
    public short c_shortOp(RetTypeState s) {
        return s.aps.shortOp((short) 3, (short) 4);
    }

    @Benchmark
    public void c_voidOp(RetTypeState s) {
        s.aps.voidOp();
    }

    @Benchmark
    public Integer c_intWrapOp(RetTypeState s) {
        return s.aps.intWrapOp(3, 4);
    }

    @Benchmark
    public String c_strOp(RetTypeState s) {
        return s.aps.strOp("a", "b");
    }

    @Benchmark
    public int c_direct_intOp(RetTypeState s) {
        return s.direct.intOp(3, 4);
    }

    @Benchmark
    public long c_direct_longOp(RetTypeState s) {
        return s.direct.longOp(3L, 4L);
    }

    @Benchmark
    public double c_direct_doubleOp(RetTypeState s) {
        return s.direct.doubleOp(3.0, 4.0);
    }

    @Benchmark
    public void c_direct_voidOp(RetTypeState s) {
        s.direct.voidOp();
    }

    @Benchmark
    public String c_direct_strOp(RetTypeState s) {
        return s.direct.strOp("a", "b");
    }

    @Benchmark
    public int c_cglib_intOp(RetTypeState s) {
        return s.cglib.intOp(3, 4);
    }

    @Benchmark
    public void c_cglib_voidOp(RetTypeState s) {
        s.cglib.voidOp();
    }

    @Benchmark
    public String c_cglib_strOp(RetTypeState s) {
        return s.cglib.strOp("a", "b");
    }

    // ---------------------------------------------------------------
    // Interface proxy — Return types
    // ---------------------------------------------------------------

    @State(Scope.Thread)
    public static class IfaceRetTypeState {
        RetOps aps;
        RetOps javaProxy;

        @Setup
        public void setup() {
            aps = AcceleratedProxy.proxy(RetOps.class,
                    (obj, method, args) -> {
                        if (method.getName().equals("intOp")) return (int) args[0] + (int) args[1];
                        if (method.getName().equals("strOp")) return (String) args[0] + (String) args[1];
                        if (method.getName().equals("voidOp")) return null;
                        if (method.getName().equals("boolOp")) return (int) args[0] > 0;
                        if (method.getName().equals("intWrapOp")) return (Integer) args[0] + (Integer) args[1];
                        return null;
                    });
            javaProxy = (RetOps) Proxy.newProxyInstance(
                    RetOps.class.getClassLoader(), new Class<?>[]{RetOps.class},
                    (proxy, method, args1) -> {
                        if (method.getName().equals("intOp")) return (int) args1[0] + (int) args1[1];
                        if (method.getName().equals("strOp")) return (String) args1[0] + (String) args1[1];
                        if (method.getName().equals("voidOp")) return null;
                        if (method.getName().equals("boolOp")) return (int) args1[0] > 0;
                        if (method.getName().equals("intWrapOp")) return (Integer) args1[0] + (Integer) args1[1];
                        return null;
                    });
        }
    }

    @Benchmark
    public int i_intOp(IfaceRetTypeState s) {
        return s.aps.intOp(3, 4);
    }

    @Benchmark
    public String i_strOp(IfaceRetTypeState s) {
        return s.aps.strOp("a", "b");
    }

    @Benchmark
    public void i_voidOp(IfaceRetTypeState s) {
        s.aps.voidOp();
    }

    @Benchmark
    public boolean i_boolOp(IfaceRetTypeState s) {
        return s.aps.boolOp(5);
    }

    @Benchmark
    public Integer i_intWrapOp(IfaceRetTypeState s) {
        return s.aps.intWrapOp(3, 4);
    }

    @Benchmark
    public int i_jp_intOp(IfaceRetTypeState s) {
        return s.javaProxy.intOp(3, 4);
    }

    @Benchmark
    public String i_jp_strOp(IfaceRetTypeState s) {
        return s.javaProxy.strOp("a", "b");
    }

    @Benchmark
    public void i_jp_voidOp(IfaceRetTypeState s) {
        s.javaProxy.voidOp();
    }

    @Benchmark
    public boolean i_jp_boolOp(IfaceRetTypeState s) {
        return s.javaProxy.boolOp(5);
    }

    @Benchmark
    public Integer i_jp_intWrapOp(IfaceRetTypeState s) {
        return s.javaProxy.intWrapOp(3, 4);
    }

    // ================================================================
    // Target: Parameter-count coverage
    // ================================================================

    interface ParamCount {
        String zeroArg();

        String oneArg(String a);

        int twoArgs(int a, int b);

        String fourArgs(String a, int b, long c, double d);

        int eightArgs(int a, int b, int c, int d, int e, int f, int g, int h);
    }

    static class ParamCountImpl implements ParamCount {
        public String zeroArg() {
            return "ok";
        }

        public String oneArg(String a) {
            return a;
        }

        public int twoArgs(int a, int b) {
            return a + b;
        }

        public String fourArgs(String a, int b, long c, double d) {
            return a + "-" + b + "-" + c + "-" + d;
        }

        public int eightArgs(int a, int b, int c, int d, int e, int f, int g, int h) {
            return a + b + c + d + e + f + g + h;
        }
    }

    // Class proxy
    @State(Scope.Thread)
    public static class ParamCountState {
        ParamCountImpl direct;
        ParamCountImpl aps;
        ParamCountImpl cglib;

        @Setup
        public void setup() {
            direct = new ParamCountImpl();
            aps = AcceleratedProxy.proxy(ParamCountImpl.class,
                    (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));
            Enhancer e = new Enhancer();
            e.setSuperclass(ParamCountImpl.class);
            e.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
                    proxy.invokeSuper(obj, args));
            cglib = (ParamCountImpl) e.create();
        }
    }

    @Benchmark
    public String c_zeroArg(ParamCountState s) {
        return s.aps.zeroArg();
    }

    @Benchmark
    public String c_oneArg(ParamCountState s) {
        return s.aps.oneArg("x");
    }

    @Benchmark
    public int c_twoArgs(ParamCountState s) {
        return s.aps.twoArgs(3, 4);
    }

    @Benchmark
    public String c_fourArgs(ParamCountState s) {
        return s.aps.fourArgs("a", 1, 2L, 3.0);
    }

    @Benchmark
    public int c_eightArgs(ParamCountState s) {
        return s.aps.eightArgs(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Benchmark
    public String c_direct_zeroArg(ParamCountState s) {
        return s.direct.zeroArg();
    }

    @Benchmark
    public int c_direct_twoArgs(ParamCountState s) {
        return s.direct.twoArgs(3, 4);
    }

    @Benchmark
    public String c_direct_fourArgs(ParamCountState s) {
        return s.direct.fourArgs("a", 1, 2L, 3.0);
    }

    @Benchmark
    public int c_direct_eightArgs(ParamCountState s) {
        return s.direct.eightArgs(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Benchmark
    public String c_cglib_zeroArg(ParamCountState s) {
        return s.cglib.zeroArg();
    }

    @Benchmark
    public int c_cglib_twoArgs(ParamCountState s) {
        return s.cglib.twoArgs(3, 4);
    }

    @Benchmark
    public String c_cglib_fourArgs(ParamCountState s) {
        return s.cglib.fourArgs("a", 1, 2L, 3.0);
    }

    // Interface proxy
    @State(Scope.Thread)
    public static class IfaceParamCountState {
        ParamCount aps;
        ParamCount javaProxy;

        @Setup
        public void setup() {
            aps = AcceleratedProxy.proxy(ParamCount.class, (obj, method, args) -> {
                if (method.getName().equals("zeroArg")) return "ok";
                if (method.getName().equals("oneArg")) return args[0];
                if (method.getName().equals("twoArgs")) return (int) args[0] + (int) args[1];
                if (method.getName().equals("fourArgs")) return args[0] + "-" + args[1] + "-" + args[2] + "-" + args[3];
                if (method.getName().equals("eightArgs"))
                    return (int) args[0] + (int) args[1] + (int) args[2] + (int) args[3] + (int) args[4] + (int) args[5] + (int) args[6] + (int) args[7];
                return null;
            });
            javaProxy = (ParamCount) Proxy.newProxyInstance(
                    ParamCount.class.getClassLoader(), new Class<?>[]{ParamCount.class},
                    (proxy, method, args1) -> {
                        if (method.getName().equals("zeroArg")) return "ok";
                        if (method.getName().equals("oneArg")) return args1[0];
                        if (method.getName().equals("twoArgs")) return (int) args1[0] + (int) args1[1];
                        if (method.getName().equals("fourArgs"))
                            return args1[0] + "-" + args1[1] + "-" + args1[2] + "-" + args1[3];
                        if (method.getName().equals("eightArgs"))
                            return (int) args1[0] + (int) args1[1] + (int) args1[2] + (int) args1[3] + (int) args1[4] + (int) args1[5] + (int) args1[6] + (int) args1[7];
                        return null;
                    });
        }
    }

    @Benchmark
    public String i_zeroArg(IfaceParamCountState s) {
        return s.aps.zeroArg();
    }

    @Benchmark
    public String i_oneArg(IfaceParamCountState s) {
        return s.aps.oneArg("x");
    }

    @Benchmark
    public int i_twoArgs(IfaceParamCountState s) {
        return s.aps.twoArgs(3, 4);
    }

    @Benchmark
    public int i_eightArgs(IfaceParamCountState s) {
        return s.aps.eightArgs(1, 2, 3, 4, 5, 6, 7, 8);
    }

    @Benchmark
    public String i_jp_zeroArg(IfaceParamCountState s) {
        return s.javaProxy.zeroArg();
    }

    @Benchmark
    public int i_jp_twoArgs(IfaceParamCountState s) {
        return s.javaProxy.twoArgs(3, 4);
    }

    @Benchmark
    public int i_jp_eightArgs(IfaceParamCountState s) {
        return s.javaProxy.eightArgs(1, 2, 3, 4, 5, 6, 7, 8);
    }

    // ================================================================
    // Target: Standard scenarios (no-op, passthrough, arg-modify)
    // ================================================================

    interface Echo {
        String echo(String in);
    }

    static class EchoImpl implements Echo {
        public String echo(String in) {
            return "Hello, " + in;
        }
    }

    // Class proxy
    @State(Scope.Thread)
    public static class StandardState {
        EchoImpl apsNoop;
        EchoImpl apsPassthrough;
        EchoImpl apsArgMod;
        EchoImpl cglibNoop;
        EchoImpl cglibPassthrough;
        EchoImpl cglibArgMod;

        @Setup
        public void setup() {
            apsNoop = AcceleratedProxy.proxy(EchoImpl.class,
                    (obj, method, args) -> "fixed");
            apsPassthrough = AcceleratedProxy.proxy(EchoImpl.class,
                    (obj, method, args) -> AcceleratedProxy.invokeSuper(obj, method, args));
            apsArgMod = AcceleratedProxy.proxy(EchoImpl.class, (obj, method, args) -> {
                args[0] = "[" + args[0] + "]";
                return AcceleratedProxy.invokeSuper(obj, method, args);
            });

            Enhancer e1 = new Enhancer();
            e1.setSuperclass(EchoImpl.class);
            e1.setCallback((MethodInterceptor) (obj, method, args, proxy) -> "fixed");
            cglibNoop = (EchoImpl) e1.create();

            Enhancer e2 = new Enhancer();
            e2.setSuperclass(EchoImpl.class);
            e2.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
                    proxy.invokeSuper(obj, args));
            cglibPassthrough = (EchoImpl) e2.create();

            Enhancer e3 = new Enhancer();
            e3.setSuperclass(EchoImpl.class);
            e3.setCallback((MethodInterceptor) (obj, method, args, proxy) -> {
                args[0] = "[" + args[0] + "]";
                return proxy.invokeSuper(obj, args);
            });
            cglibArgMod = (EchoImpl) e3.create();
        }
    }

    @Benchmark
    public String c_noop(StandardState s) {
        return s.apsNoop.echo("W");
    }

    @Benchmark
    public String c_passthrough(StandardState s) {
        return s.apsPassthrough.echo("W");
    }

    @Benchmark
    public String c_argmod(StandardState s) {
        return s.apsArgMod.echo("W");
    }

    @Benchmark
    public String c_cglib_noop(StandardState s) {
        return s.cglibNoop.echo("W");
    }

    @Benchmark
    public String c_cglib_passthrough(StandardState s) {
        return s.cglibPassthrough.echo("W");
    }

    @Benchmark
    public String c_cglib_argmod(StandardState s) {
        return s.cglibArgMod.echo("W");
    }

    // Interface proxy
    @State(Scope.Thread)
    public static class IfaceStandardState {
        Echo apsNoop;
        Echo apsPassthrough;
        Echo apsArgMod;
        Echo jpNoop;
        Echo jpPassthrough;
        Echo jpArgMod;

        @Setup
        public void setup() {
            apsNoop = AcceleratedProxy.proxy(Echo.class, (obj, method, args) -> "fixed");
            apsPassthrough = AcceleratedProxy.proxy(Echo.class,
                    (obj, method, args) -> "Hello, " + args[0]);
            apsArgMod = AcceleratedProxy.proxy(Echo.class, (obj, method, args) -> {
                args[0] = "[" + args[0] + "]";
                return "Hello, " + args[0];
            });

            jpNoop = (Echo) Proxy.newProxyInstance(Echo.class.getClassLoader(),
                    new Class<?>[]{Echo.class}, (p, m, a) -> "fixed");
            jpPassthrough = (Echo) Proxy.newProxyInstance(Echo.class.getClassLoader(),
                    new Class<?>[]{Echo.class}, (p, m, a) -> "Hello, " + a[0]);
            jpArgMod = (Echo) Proxy.newProxyInstance(Echo.class.getClassLoader(),
                    new Class<?>[]{Echo.class}, (p, m, a) -> {
                        a[0] = "[" + a[0] + "]";
                        return "Hello, " + a[0];
                    });
        }
    }

    @Benchmark
    public String i_noop(IfaceStandardState s) {
        return s.apsNoop.echo("W");
    }

    @Benchmark
    public String i_passthrough(IfaceStandardState s) {
        return s.apsPassthrough.echo("W");
    }

    @Benchmark
    public String i_argmod(IfaceStandardState s) {
        return s.apsArgMod.echo("W");
    }

    @Benchmark
    public String i_jp_noop(IfaceStandardState s) {
        return s.jpNoop.echo("W");
    }

    @Benchmark
    public String i_jp_passthrough(IfaceStandardState s) {
        return s.jpPassthrough.echo("W");
    }

    @Benchmark
    public String i_jp_argmod(IfaceStandardState s) {
        return s.jpArgMod.echo("W");
    }

    // ================================================================
    // Target: Multi-Interceptor (Phase 2)
    // ================================================================

    static class MultiGroupTarget {
        public String getGreeting() { return "hello"; }
        public void setGreeting(String g) { /* no-op */ }
        public int getCount() { return 42; }
        public String format(String prefix) { return prefix + ":ok"; }
    }

    // -- Single-interceptor (Group.otherwise) vs old single-Interceptor API --

    @State(Scope.Thread)
    public static class SingleInterceptorState {
        MultiGroupTarget groupApi;
        MultiGroupTarget oldApi;
        MultiGroupTarget direct;

        @Setup
        public void setup() {
            Interceptor passthrough = (obj, method, args) ->
                    AcceleratedProxy.invokeSuper(obj, method, args);

            // New API: Group.otherwise (functionally equivalent to old single-Interceptor)
            groupApi = AcceleratedProxy.proxy(MultiGroupTarget.class,
                    Group.otherwise(passthrough));

            // Old API: single Interceptor
            oldApi = AcceleratedProxy.proxy(MultiGroupTarget.class, passthrough);

            direct = new MultiGroupTarget();
        }
    }

    @Benchmark
    public String mg_single_groupApi(SingleInterceptorState s) {
        return s.groupApi.getGreeting();
    }

    @Benchmark
    public String mg_single_oldApi(SingleInterceptorState s) {
        return s.oldApi.getGreeting();
    }

    @Benchmark
    public String mg_single_direct(SingleInterceptorState s) {
        return s.direct.getGreeting();
    }

    // -- Multi-group (getter + setter + default) vs manual dispatch --

    @State(Scope.Thread)
    public static class MultiGroupState {
        MultiGroupTarget groups;
        MultiGroupTarget manualDispatch;
        MultiGroupTarget direct;

        @Setup
        public void setup() {
            // 3 Groups: getter, setter, otherwise
            groups = AcceleratedProxy.proxy(MultiGroupTarget.class,
                    Group.of(m -> m.getName().startsWith("get"),
                            (obj, method, args) -> AcceleratedProxy.invokeSuper(
                                    obj, method, args)),
                    Group.of(m -> m.getName().startsWith("set"),
                            (obj, method, args) -> AcceleratedProxy.invokeSuper(
                                    obj, method, args)),
                    Group.otherwise((obj, method, args) -> AcceleratedProxy.invokeSuper(
                            obj, method, args)));

            // Manual dispatch in single interceptor (old pattern)
            manualDispatch = AcceleratedProxy.proxy(MultiGroupTarget.class,
                    (obj, method, args) -> AcceleratedProxy.invokeSuper(
                            obj, method, args));

            direct = new MultiGroupTarget();
        }
    }

    @Benchmark
    public String mg_multi_getGreeting(MultiGroupState s) {
        return s.groups.getGreeting();
    }

    @Benchmark
    public void mg_multi_setGreeting(MultiGroupState s) {
        s.groups.setGreeting("x");
    }

    @Benchmark
    public int mg_multi_getCount(MultiGroupState s) {
        return s.groups.getCount();
    }

    @Benchmark
    public String mg_multi_otherwise(MultiGroupState s) {
        return s.groups.format("p");
    }

    @Benchmark
    public String mg_manual_getGreeting(MultiGroupState s) {
        return s.manualDispatch.getGreeting();
    }

    @Benchmark
    public void mg_manual_setGreeting(MultiGroupState s) {
        s.manualDispatch.setGreeting("x");
    }

    @Benchmark
    public String mg_direct_getGreeting(MultiGroupState s) {
        return s.direct.getGreeting();
    }

    @Benchmark
    public void mg_direct_setGreeting(MultiGroupState s) {
        s.direct.setGreeting("x");
    }

    // -- Passthrough (unmatched method) vs direct call --

    @State(Scope.Thread)
    public static class PassthroughState {
        MultiGroupTarget passthrough;
        MultiGroupTarget direct;

        @Setup
        public void setup() {
            // Only intercept get* — format() is unmatched → passthrough
            passthrough = AcceleratedProxy.proxy(MultiGroupTarget.class,
                    Group.of(m -> m.getName().startsWith("get"),
                            (obj, method, args) -> "intercepted"));

            direct = new MultiGroupTarget();
        }
    }

    @Benchmark
    public String mg_passthrough_format(PassthroughState s) {
        return s.passthrough.format("p");
    }

    @Benchmark
    public String mg_direct_format(PassthroughState s) {
        return s.direct.format("p");
    }

    // -- Interface proxy: multi-group --

    public interface MultiGroupIface {
        String getGreeting();
        void setGreeting(String g);
        int getCount();
        String format(String prefix);
    }

    @State(Scope.Thread)
    public static class MultiGroupIfaceState {
        MultiGroupIface groups;
        MultiGroupIface single;

        @Setup
        public void setup() {
            groups = AcceleratedProxy.proxy(MultiGroupIface.class,
                    Group.of(m -> m.getName().startsWith("get"),
                            (obj, method, args) -> {
                                if (method.getName().equals("getGreeting"))
                                    return "hello";
                                if (method.getName().equals("getCount")) return 42;
                                return null;
                            }),
                    Group.of(m -> m.getName().startsWith("set"),
                            (obj, method, args) -> null),
                    Group.otherwise((obj, method, args) -> "p:ok"));

            single = AcceleratedProxy.proxy(MultiGroupIface.class,
                    (obj, method, args) -> {
                        if (method.getName().equals("getGreeting"))
                            return "hello";
                        if (method.getName().equals("getCount")) return 42;
                        if (method.getName().equals("format")) return "p:ok";
                        return null;
                    });
        }
    }

    @Benchmark
    public String mg_iface_groups_getGreeting(MultiGroupIfaceState s) {
        return s.groups.getGreeting();
    }

    @Benchmark
    public String mg_iface_single_getGreeting(MultiGroupIfaceState s) {
        return s.single.getGreeting();
    }

    @Benchmark
    public String mg_iface_groups_format(MultiGroupIfaceState s) {
        return s.groups.format("p");
    }

    @Benchmark
    public String mg_iface_single_format(MultiGroupIfaceState s) {
        return s.single.format("p");
    }
}

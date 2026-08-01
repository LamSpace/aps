package io.github.lamspace.benchmark;

import io.github.lamspace.APS;
import org.openjdk.jmh.annotations.*;

import java.lang.reflect.Proxy;
import java.util.concurrent.TimeUnit;

@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@State(Scope.Thread)
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
@Fork(1)
public class ProxyBenchmark {

    interface Greeter {
        String hello(String name);
    }

    static class GreeterImpl implements Greeter {
        @Override
        public String hello(String name) {
            return "Hello, " + name;
        }
    }

    static class ConcreteGreeter {
        public String hello(String name) {
            return "Hello, " + name;
        }
    }

    private Greeter directInterface;
    private Greeter javaProxy;
    private ConcreteGreeter apsProxy;
    private ConcreteGreeter directConcrete;

    @Setup
    public void setup() {
        directInterface = new GreeterImpl();

        // Java Proxy (interface-based only)
        javaProxy = (Greeter) Proxy.newProxyInstance(
                Greeter.class.getClassLoader(),
                new Class<?>[]{Greeter.class},
                (proxy, method, args1) -> {
                    if (method.getName().equals("hello")) {
                        return "Hello, " + args1[0];
                    }
                    return null;
                }
        );

        // APS proxy (class-based)
        apsProxy = APS.create(ConcreteGreeter.class,
                (obj, method, superHandle, args) -> {
                    if (method.getName().equals("hello")) {
                        return "Hello, " + args[0];
                    }
                    return null;
                });

        directConcrete = new ConcreteGreeter();
    }

    @Benchmark
    public String directCall() {
        return directInterface.hello("World");
    }

    @Benchmark
    public String javaProxyCall() {
        return javaProxy.hello("World");
    }

    @Benchmark
    public String apsProxyCall() {
        return apsProxy.hello("World");
    }

    @Benchmark
    public String directConcreteCall() {
        return directConcrete.hello("World");
    }
}

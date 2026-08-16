# 3. Quick Start

This chapter gets you from zero to a working proxy in two examples: one for a concrete class and one for an interface.

## Class proxy

```java
import io.github.lamspace.AcceleratedProxy;

public class Greeter {
    private String greeting = "Hello";

    public String hello(String name) {
        return greeting + ", " + name;
    }
}

Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
    System.out.println("before " + method.getName());
    Object result = AcceleratedProxy.invokeSuper(obj, method, args);
    System.out.println("after " + method.getName());
    return result;
});

String greeting = proxy.hello("World");
```

Output:

```
before hello
after hello
```

`greeting` is `"Hello, World"`. The interceptor wraps the real `hello` call:
`invokeSuper` re-dispatches to the original method via a direct `super` call.

## Interface proxy

```java
public interface Calculator {
    int add(int a, int b);
}

Calculator calc = AcceleratedProxy.proxy(Calculator.class, (obj, method, args) -> {
    System.out.println("calling " + method.getName());
    return (int) args[0] + (int) args[1];
});

int result = calc.add(10, 20);
```

Output:

```
calling add
```

`result` is `30`. There is no superclass to call — the interceptor *is* the implementation. (You can also return a canned value, log, or delegate anywhere.)

## Key points

- The same `AcceleratedProxy.proxy(...)` entry point handles both cases.
- The target's type is inferred from the argument — **no cast**.
- In the class case, call `AcceleratedProxy.invokeSuper(obj, method, args)` to run the original method; omit it to short-circuit.

Next: [Interceptor API](04-interceptor-api.md).

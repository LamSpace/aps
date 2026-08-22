# 1. Introduction

OpenProxy is a high-performance dynamic proxy library for Java. It generates a proxy class at runtime with ASM and routes every intercepted method call through a hashCode-driven `dispatch()` switch that emits a direct `INVOKESPECIAL super.method(args)` — **no reflection and no MethodHandle on the hot path**.

OpenProxy class proxies beat CGLib by ~3–5× across scenarios with actual work, interface proxies run at `java.lang.reflect.Proxy` parity, and default methods are ~6× faster than the JDK.

## Highlights

| Highlight                        | What it means for you                                                                      |
|----------------------------------|--------------------------------------------------------------------------------------------|
| **Zero-overhead super dispatch** | `invokeSuper` compiles to a direct `super.method()` call; the JIT inlines it               |
| **One unified API**              | `OpenProxy.proxy(...)` handles classes *and* interfaces                             |
| **GC-safe class loading**        | proxy classes use `Lookup.defineHiddenClass()`, so there is no ClassLoader leak            |
| **Functional API**               | `Interceptor` is a single-method interface — use a lambda                                  |
| **Selective interception**       | `Group.of(...)` intercepts only the methods you name; the rest pass through with zero cost |
| **No cast required**             | `proxy(MyClass.class, i)` returns `MyClass` via generic type inference                     |
| **Advanced hooks**               | constructor interception, static-method shadowing, annotation-driven matching, hot swap    |

## At a glance

```java
Greeter proxy = OpenProxy.proxy(Greeter.class, (obj, method, args) -> {
    System.out.println("before " + method.getName());
    Object result = OpenProxy.invokeSuper(obj, method, args);
    System.out.println("after " + method.getName());
    return result;
});

String greeting = proxy.hello("World");
// before hello
// after hello
// greeting == "Hello, World"
```

## How it works (30-second version)

1. `OpenProxy.proxy(...)` builds a `Group` chain and matches each proxyable method to an interceptor (`first-match-wins`).
2. A generator (`ClassGenerator` for classes, `InterfaceGenerator` for interfaces)
   emits bytecode: one `_interceptor$N` field per distinct interceptor, one override per method, and a `dispatch(Method, Object[])` method.
3. The generated class is defined with `defineHiddenClass` and instantiated.
4. On each call, the override boxes the arguments, invokes
   `Interceptor.intercept(proxy, method, args)`, and unboxes the result. If your interceptor calls `invokeSuper`, the `dispatch()` method branches on
   `method.hashCode()` and jumps straight to `INVOKESPECIAL super.method(...)`.

## OpenProxy vs the alternatives

|                          | OpenProxy                    | CGLib                       | `java.lang.reflect.Proxy` |
|--------------------------|------------------------|-----------------------------|---------------------------|
| Proxies concrete classes | ✅                     | ✅                          | ❌ (interfaces only)      |
| Super-call mechanism     | direct `INVOKESPECIAL` | `MethodProxy` + `FastClass` | N/A                       |
| GC-safe (hidden class)   | ✅                     | ❌                          | ✅                        |
| Selective interception   | ✅ `Group.of`          | ✅ `CallbackFilter`         | ❌ all-or-nothing         |
| Constructor interception | ✅                     | ✅                          | ❌                        |
| Static method proxy      | ✅                     | ❌                          | ❌                        |
| Functional API           | ✅ lambda              | ✅                          | ✅                        |
| Java 25+                 | ✅                     | ✅ (limited)                | ✅                        |

Next: [Installation](02-installation.md).

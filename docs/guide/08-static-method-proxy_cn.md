# 8. 静态方法代理

静态方法在编译期绑定，子类无法重写。OpenProxy 因此生成一个 **遮蔽类**，其 `public static` 方法 镜像目标类的静态方法，并把它们路由到同一个 `Interceptor`。

## `AcceleratedProxy.proxyStatic`

```java
public class Utils {
    public static int add(int a, int b) { return a + b; }
}

Class<?> proxyClass = AcceleratedProxy.proxyStatic(Utils.class,
        (proxy, method, args) -> {
            System.out.println("正在调用 " + method.getName());
            return method.invoke(null, args);   // 调用原静态方法
        });
```

返回的 `Class` 有一个静态 `add(int, int)`，以 `proxy == null` 调用你的拦截器。要调用它 （静态方法不参与虚方法分派，必须调用 **返回的类**）：

```java
int result = (Integer) proxyClass
        .getMethod("add", int.class, int.class)
        .invoke(null, 2, 3);          // -> 5，打印 "正在调用 add"
```

或用 `MethodHandle`：

```java
MethodHandle mh = MethodHandles.lookup()
        .findStatic(proxyClass, "add", MethodType.methodType(int.class, int.class, int.class));
int result = (int) mh.invoke(2, 3);
```

## 分组支持

```java
Class<?> cls = AcceleratedProxy.proxyStatic(Utils.class,
        Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
        Group.otherwise(otherInterceptor));
```

未匹配任何组的方法通过直接 `INVOKESTATIC` 直通原方法（无拦截器）。方法从目标类及其父类收集， 子类优先去重。

## 注意事项与限制

- **`Utils.add(...)` 不可被拦截**——调用方针对原类编译，只有对返回 `Class` 的调用才会经过 拦截器。
- 生成的类 **不缓存**（静态字段是类级状态）；每次 `proxyStatic` 调用都返回新类。
- 在拦截的静态方法内，用 `method.invoke(null, args)` 调用原方法。

下一章：[多接口代理](09-multi-interface-proxy_cn.md)。

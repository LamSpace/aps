# 12. 迁移

本章展示从 CGLib 和 `java.lang.reflect.Proxy` 迁移到 OpenProxy 的最短路径。更详细的指南见
[docs/migration-guide.md](../migration-guide.md)。

## 从 CGLib 迁移

**迁移前：**

```java
Enhancer enhancer = new Enhancer();
enhancer.setSuperclass(MyService.class);
enhancer.setCallback((MethodInterceptor) (obj, method, args, proxy) ->
        proxy.invokeSuper(obj, args));
MyService proxy = (MyService) enhancer.create();
```

**迁移后：**

```java
MyService proxy = OpenProxy.proxy(MyService.class,
        (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
```

差异：

| CGLib                          | OpenProxy                                               |
|--------------------------------|---------------------------------------------------|
| `Enhancer` 构造器              | `OpenProxy.proxy()` 工厂                   |
| `MethodInterceptor`（4 参数）  | `Interceptor`（3 参数）                           |
| `proxy.invokeSuper(obj, args)` | `OpenProxy.invokeSuper(obj, method, args)` |
| 需显式转型                     | 泛型推导，无需转型                                |
| 自定义 ClassLoader             | 隐藏类，GC 安全                                   |

## 从 `java.lang.reflect.Proxy` 迁移

**迁移前：**

```java
Service proxy = (Service) Proxy.newProxyInstance(
        Service.class.getClassLoader(),
        new Class<?>[]{Service.class},
        (p, m, a) -> m.invoke(new ServiceImpl(), a));
```

**迁移后（直接代理具体类）：**

```java
ServiceImpl proxy = OpenProxy.proxy(ServiceImpl.class,
        (obj, method, args) -> OpenProxy.invokeSuper(obj, method, args));
```

最大收益：OpenProxy 代理的是 **实现类**，拦截器通过 `invokeSuper` 调用真正的方法，而不用你手写委托。

## 特性对比

| 特性            | OpenProxy           | CGLib               | Java Proxy |
|-----------------|---------------|---------------------|------------|
| 代理具体类      | ✅            | ✅                  | ❌         |
| 选择性拦截      | ✅ `Group.of` | ✅ `CallbackFilter` | ❌         |
| 构造器拦截      | ✅            | ✅                  | ❌         |
| 静态方法代理    | ✅            | ❌                  | ❌         |
| 热加载 / rebind | ✅            | ❌                  | ❌         |
| GC 安全类加载   | ✅            | ❌                  | ✅         |

下一章：[性能与基准测试](13-performance_cn.md)。

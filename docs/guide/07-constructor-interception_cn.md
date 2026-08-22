# 7. 构造器拦截

构造器拦截在 *父类构造器* 前后运行钩子——适用于注入依赖、校验构造或追踪对象创建。 **仅支持类代理**（接口没有父类构造器）。

## `ConstructorInterceptor` 接口

```java
@FunctionalInterface
public interface ConstructorInterceptor {
    Object[] before(Constructor<?> ctor, Object[] args) throws Throwable;
    default void after(Object proxy, Constructor<?> ctor, Object[] args) {}
}
```

- `before` 在 **父类构造器之前**运行。可以改写参数（返回新数组），或抛异常否决构造。
- `after` 在构造器完成、实例完全初始化 **之后**运行，仅用于观察（默认空实现）。

## 示例

```java
ConstructorInterceptor ctorInterceptor = new ConstructorInterceptor() {
    public Object[] before(Constructor<?> ctor, Object[] args) {
        System.out.println("before " + ctor.getName());
        return args;                       // 可改写
    }

    public void after(Object proxy, Constructor<?> ctor, Object[] args) {
        System.out.println("after " + ctor.getName());
    }
};

Greeter proxy = OpenProxy.proxy(Greeter.class, interceptor, ctorInterceptor);
```

构造时输出：

```
before io.github.lamspace.Greeter
after io.github.lamspace.Greeter
```

## 入口

```java
proxy(Class<T>, Interceptor, ConstructorInterceptor)
proxy(Class<T>, ConstructorInterceptor, Group...)
proxy(Class<T>, Object[] constructorArgs, ConstructorInterceptor, Group...)
```

## 注意事项与限制

- `before` 不接收代理实例——父类构造器之前 `this` 尚未初始化。
- `after` 接收完全初始化后的 `proxy`。
- `before` 抛出的受检异常以 `UndeclaredThrowableException` 形式抛给调用方。
- 构造器钩子每实例只增加约 9 ns 的一次性成本；每次方法调用的热路径逐字节不变。

下一章：[静态方法代理](08-static-method-proxy_cn.md)。

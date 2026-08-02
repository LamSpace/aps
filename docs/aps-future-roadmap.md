# APS 未来路线图

## 第一阶段：核心统一（已完成）

| 优先级 | 事项              | 说明                                                                          |
|--------|-------------------|-------------------------------------------------------------------------------|
| P0     | **统一 API**      | `AcceleratedProxy.proxy()` 单一入口，同时支持类和接口                         |
| P0     | **统一回调**      | `Interceptor` 接口替代 `Callback` + `InterfaceCallback`                       |
| P0     | **hashCode 调度** | `dispatch()` 哈希开关 + 直接 `INVOKESPECIAL` 父类调用，消除 MethodHandle 开销 |
| P0     | **类缓存**        | `WeakCache` 按 `{targetClass, filter}` 缓存已生成的代理类                     |
| P0     | **接口代理**      | 与 `java.lang.reflect.Proxy` 性能接近持平                                     |

---

## 第二阶段：功能扩展

| 优先级 | 事项                          | 说明                                                |
|--------|-------------------------------|-----------------------------------------------------|
| P2     | **注解驱动 API**              | 如 `@Intercept` 标注方法，减少样板代码              |
| P2     | **Maven Central 发布**        | 让其他项目能通过 Maven/Gradle 依赖引入              |
| P2     | **多 Interceptor / 方法分组** | 当前只有单 Interceptor + filter，更细粒度的拦截控制 |

### 注解驱动 API 草图

```java
// 当前 API
Greeter proxy = AcceleratedProxy.proxy(Greeter.class, (obj, method, args) -> {
            System.out.println("before " + method.getName());
            return AcceleratedProxy.invokeSuper(obj, method, args);
        });

// 注解驱动 API（v2 设想）
@Intercept
class MyInterceptor {
    @Around("get*")
    Object log(Object proxy, Method method, Object[] args) {
        System.out.println("before " + method.getName());
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MyInterceptor());
```

### Maven Central 发布

- GroupId: `io.github.lamspace`
- ArtifactId: `aps`
- 版本: 当前 `0.1.0-SNAPSHOT`，正式发布时升级至 `1.0.0`
- 需要：Sonatype OSSRH 账号、GPG 签名、发布流水线

### 多 Interceptor / 方法分组

```java
// 设想：不同方法组绑定不同 Interceptor
Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
                Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
                Group.otherwise(defaultInterceptor)
        );
```

---

## 第三阶段：高级特性

| 优先级 | 事项                 | 说明                                                   |
|--------|----------------------|--------------------------------------------------------|
| P3     | **接口默认方法调用** | 在拦截器中调用接口 `default` 方法，需 `findSpecial`    |
| P3     | **多接口代理**       | 一个代理类实现多个接口                                 |
| P3     | **静态方法代理**     | 需生成委托代码 — 静态方法不参与虚方法分派              |
| P3     | **构造器拦截**       | 对象创建时的 hook，类似 CGLib 的 `Enhancer` 构造器回调 |
| P3     | **热加载/热替换**    | 运行时重新生成代理类，适合长期运行的框架场景           |

### 接口默认方法调用

- 当前 `AcceleratedProxy.invokeSuper()` 在接口代理上对接口方法抛出 `AbstractMethodError`
- 需使用 `MethodHandles.Lookup.findSpecial()` 绑定接口默认实现
- 对 `default` 方法和非 `default` 接口方法需区分处理

### 静态方法代理挑战

- 静态方法不参与 vtable，无法通过生成子类重写
- 需要在生成的子类中创建同名静态方法委托
- 使用场景有限（测试 mock、日志注入）

### 构造器拦截挑战

- `defineHiddenClass` 生成的类构造器必须调用 `super()`
- 但可以在 super 调用前后插入字节码
- 需要新的 `ConstructorInterceptor` 接口

### 热加载挑战

- 隐藏类一旦定义不可修改
- 需要生成新的类名并重新装载
- 旧实例继续使用旧类，新实例使用新类

---

## 永久不做

- **代理 final 类/方法** — JVM 规范禁止在运行时子类化 final 类或重写 final 方法，任何实现都会抛出 `VerifyError`
- **代理 static final 字段** — JVM 规范限制

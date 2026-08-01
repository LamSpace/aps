# APS 未来路线图

## 第二阶段：v2 功能扩展

| 优先级 | 事项                       | 说明                                             |
|--------|----------------------------|--------------------------------------------------|
| P2     | **注解驱动 API**           | 如 `@Intercept` 标注方法，减少样板代码           |
| P2     | **Maven Central 发布**     | 让其他项目能通过 Maven/Gradle 依赖引入           |
| P2     | **多 Callback / 方法分组** | 当前只有单 Callback + filter，更细粒度的拦截控制 |

### 注解驱动 API 草图

```java
// 当前 API（v1）
Greeter proxy = APS.create(Greeter.class, (obj, method, superHandle, args) -> {
            System.out.println("before " + method.getName());
            return superHandle.invoke(args);
        });

// 注解驱动 API（v2 设想）
@Intercept
class MyInterceptor {
    @Around("get*")
    Object log(Proxy proxy, Method method, MethodHandle superHandle, Object[] args) {
        System.out.println("before " + method.getName());
        return superHandle.invoke(args);
    }
}

Greeter proxy = APS.intercept(Greeter.class, new MyInterceptor());
```

### Maven Central 发布

- GroupId: `io.github.lamspace`
- ArtifactId: `aps`
- 需要：Sonatype OSSRH 账号、GPG 签名、发布流水线

### 多 Callback / 方法分组

```java
// 设想：不同方法组绑定不同 Callback
APS.create(Greeter .class,
           Group.of(m ->m.

getName().

startsWith("get"),getterCallback),
        Group.

of(m ->m.

getName().

startsWith("set"),setterCallback),
        Group.

otherwise(defaultCallback)
);
```

---

## 第三阶段：高级特性

| 优先级 | 事项              | 说明                                                            |
|--------|-------------------|-----------------------------------------------------------------|
| P3     | **静态方法代理**  | 需要不同的字节码策略 — 静态方法不参与虚方法分派，需生成委托代码 |
| P3     | **构造器拦截**    | 对象创建时的 hook，类似 CGLib 的 `Enhancer` 构造器回调          |
| P3     | **热加载/热替换** | 运行时重新生成代理类，适合长期运行的框架场景                    |

### 静态方法代理挑战

- 静态方法不参与 vtable，MethodHandle 绑定方式不同
- 需要在生成的子类中创建同名静态方法委托
- 使用场景有限（测试 mock、日志注入）

### 构造器拦截挑战

- `defineHiddenClass` 生成的类构造器必须调用 `super()`
- 但可以在 super 调用前后插入字节码
- 需要新的 `ConstructorCallback` 接口

### 热加载挑战

- 隐藏类一旦定义不可修改
- 需要生成新的类名并重新装载
- 旧实例继续使用旧类，新实例使用新类

---

## 永久不做

- **代理 final 类/方法** — JVM 规范禁止在运行时子类化 final 类或重写 final 方法，任何实现都会抛出 `VerifyError`
- **代理 static final 字段** — JVM 规范限制

# APS 未来路线图

## 第一阶段：核心统一（已完成）

| 优先级 | 事项              | 说明                                                                                                  |
|--------|-------------------|-------------------------------------------------------------------------------------------------------|
| P0     | **统一 API**      | `AcceleratedProxy.proxy()` 单一入口，同时支持类和接口                                                 |
| P0     | **统一回调**      | `Interceptor` 接口替代 `Callback` + `InterfaceCallback`                                               |
| P0     | **hashCode 调度** | `dispatch()` 哈希开关 + 直接 `INVOKESPECIAL` 父类调用，消除 MethodHandle 开销；已修复重载方法哈希碰撞 |
| P0     | **类缓存**        | `WeakCache` 按 `{targetClass, filter}` 缓存已生成的代理类                                             |
| P0     | **接口代理**      | 与 `java.lang.reflect.Proxy` 性能接近持平                                                             |

---

## 第二阶段：功能扩展（已完成）

| 优先级 | 事项                          | 说明                                                |
|--------|-------------------------------|-----------------------------------------------------|
| P2     | **多 Interceptor / 方法分组** | 当前只有单 Interceptor + filter，更细粒度的拦截控制 |

### 多 Interceptor / 方法分组

```java
// 不同方法组绑定不同 Interceptor
Greeter proxy = AcceleratedProxy.proxy(Greeter.class,
                Group.of(m -> m.getName().startsWith("get"), getterInterceptor),
                Group.of(m -> m.getName().startsWith("set"), setterInterceptor),
                Group.otherwise(defaultInterceptor)
        );
```

---

## 第三阶段：高级特性

| 顺序 | 优先级 | 事项                   | 说明                                                                  |
|------|--------|------------------------|-----------------------------------------------------------------------|
| 1    | P3     | **接口默认方法调用**   | 在拦截器中调用接口 `default` 方法，需 `findSpecial`                   |
| 2    | P3     | **多接口代理**         | 一个代理类实现多个接口                                                |
| 3    | P3     | **注解驱动 API**       | 如 `@Intercept` 标注方法，减少样板代码，声明式方法匹配                |
| 4    | P3     | **构造器拦截**         | 对象创建时的 hook，类似 CGLib 的 `Enhancer` 构造器回调                |
| 5    | P3     | **静态方法代理**       | 需生成委托代码 — 静态方法不参与虚方法分派                             |
| 6    | P3     | **热加载/热替换**      | 运行时重新生成代理类，适合长期运行的框架场景                          |
| 7    | P3     | **虚拟线程兼容性**     | 验证 APS 代理在虚拟线程上的行为，确认不 pin 载体线程                  |
| 8    | P3     | **JPMS 强封装模块**    | 处理 `java.base` 等强封装模块中类的代理访问                           |
| 9    | P3     | **Maven Central 发布** | 让其他项目能通过 Maven/Gradle 依赖引入，GroupId: `io.github.lamspace` |

### 接口默认方法调用

- 当前 `AcceleratedProxy.invokeSuper()` 在接口代理上对接口方法抛出 `AbstractMethodError`
- 需使用 `MethodHandles.Lookup.findSpecial()` 绑定接口默认实现
- 对 `default` 方法和非 `default` 接口方法需区分处理

### 多接口代理

- 当前 `InterfaceGenerator` 只接收单个 `interfaceClass`，生成类仅实现该接口 + `DispatchTarget`
- 需扩展为接收 `Class<?>[]`，生成类的 `implements` 列表包含全部接口，`proxy()` 入口同步支持多接口
- 需处理跨接口方法冲突：相同签名 + 相同返回类型可合并为一个实现；`default` 方法冲突需明确解析优先级

### 注解驱动 API

- 声明式方法匹配替代编程式 `if-else`，减少样板代码
- 适用于大型项目中跨多个代理类复用同一拦截模式的场景
- 命名考虑：`intercept()` vs `proxy()` — 统一使用 `proxy()` 更简洁，`intercept()` 明确表达"注解驱动拦截"语义

```java

@Intercept
class MetricsInterceptor {
    @Around("get*")
    Object measure(Object proxy, Method method, Object[] args) {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}
```

### 构造器拦截挑战

- `defineHiddenClass` 生成的类构造器必须调用 `super()`
- 但可以在 super 调用前后插入字节码
- 需要新的 `ConstructorInterceptor` 接口

### 静态方法代理挑战

- 静态方法不参与 vtable，无法通过生成子类重写
- 需要在生成的子类中创建同名静态方法委托
- 使用场景有限（测试 mock、日志注入）

### 热加载挑战

- 隐藏类一旦定义不可修改
- 需要生成新的类名并重新装载
- 旧实例继续使用旧类，新实例使用新类

### 虚拟线程兼容性

- 热路径为 hashCode 开关 + 直接 `INVOKESPECIAL` 父类调用，无 `synchronized`、无阻塞 I/O，理论上不 pin 载体线程
- 需新增虚拟线程下的并发测试，验证大量代理调用不触发 pinning（可用 `-Djdk.tracePinnedThreads` 或 JFR 观测）
- 确认生成的隐藏类与 `MethodHandles` 调用在虚拟线程调度下行为一致

### JPMS 强封装模块

- 接口代理用 `MethodHandles.lookup()`、类代理用 `LookupManager.getLookup(target)` 定义隐藏类，强封装模块（如 `java.base` 内部包）下可能抛 `IllegalAccessException`
- 隐藏类定义在与 Lookup 相同的包/模块中，访问强封装模块需该模块 `open` 或取得 `MethodHandles.privateLookupIn(targetClass, lookup)`
- 需明确策略：自动尝试 `privateLookupIn`，失败时给出可操作的报错（提示 `--add-opens`）

### Maven Central 发布

- GroupId: `io.github.lamspace`，ArtifactId: `aps`
- 版本: 发布时升级至 `1.0.0`
- 需要：Sonatype OSSRH 账号、GPG 签名、发布流水线

---

## 永久不做

- **代理 final 类/方法** — JVM 规范禁止在运行时子类化 final 类或重写 final 方法，任何实现都会抛出 `VerifyError`
- **代理 static final 字段** — JVM 规范限制
- **代理 Record 类** — Record 是隐式 final 的，无法被子类化
- **代理 sealed 类的非许可子类** — sealed 类在编译期限制了可扩展的子类集合，运行时无法绕过

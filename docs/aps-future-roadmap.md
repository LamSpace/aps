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

| 顺序 | 优先级 | 事项                           | 说明                                                                  |
|------|--------|--------------------------------|-----------------------------------------------------------------------|
| 1    | P3     | **接口默认方法调用**（已完成） | 在拦截器中调用接口 `default` 方法，`INVOKESPECIAL` 直调默认实现       |
| 2    | P3     | **多接口代理**（已完成）       | 一个代理类实现多个接口                                                |
| 3    | P3     | **注解驱动 API**（已完成）     | 如 `@Intercept` 标注方法，减少样板代码，声明式方法匹配                |
| 4    | P3     | **构造器拦截**（已完成）       | 对象创建时的 hook，类似 CGLib 的 `Enhancer` 构造器回调                |
| 5    | P3     | **静态方法代理**（已完成）     | 需生成委托代码 — 静态方法不参与虚方法分派                             |
| 6    | P3     | **热加载/热替换**（部分完成）  | 类热重载（`evict`/`evictClassLoader`）+ 拦截器热替换（`rebind`）；跨 ClassLoader 热部署待 item 8 |
| 7    | P3     | **虚拟线程兼容性**（已完成）   | 验证 APS 代理在虚拟线程上的行为，确认不 pin 载体线程                  |
| 8    | P3     | **JPMS 强封装模块**            | 处理 `java.base` 等强封装模块中类的代理访问                           |
| 9    | P3     | **Maven Central 发布**         | 让其他项目能通过 Maven/Gradle 依赖引入，GroupId: `io.github.lamspace` |

### 接口默认方法调用（已完成）

- `AcceleratedProxy.invokeSuper()` 对接口 `default` 方法现会调用其默认实现（直接声明与继承统一走 `INVOKESPECIAL` 快路径，零 `MethodHandle` 开销）
- 非 `default` 接口方法仍抛 `AbstractMethodError`
- 附带修复：`<clinit>` 改用 `getMethod` 支持继承方法解析

### 多接口代理（已完成）

- `AcceleratedProxy.proxy(Class<?>[] interfaces, Interceptor)` / `proxy(Class<?>[], Group...)` 生成一个实现全部接口的代理类，返回 `Object`，调用方按需 cast
- 内部接口路径统一为 `Class<?>[]`，单接口即长度 1 的特例（字节级一致，不影响基准）
- 冲突规则：相同签名 + 相同返回类型合并；不同返回类型抛 `IllegalArgumentException`；两个 `default` 抛 `IllegalArgumentException`；一个 `default` + 一个抽象合并并调用该 default

### 注解驱动 API（已完成）

- `@Intercept`（类级）+ `@Around`（方法级）注解声明式匹配方法，替代编程式 `Group.of(m -> ...)`
- 三个匹配维度 AND 组合、维度内 OR：`value`/`glob`（方法名 glob）、`regex`（方法名正则）、`annotatedWith`（按方法注解）
- `@Around` 方法契约：实例方法，签名固定 `(Object, Method, Object[])`，返回引用类型
- 入口 `AcceleratedProxy.intercept(target, interceptor)`，未匹配方法透传，与程序化行为一致
- 注解驱动与等价手写 `Group` 生成相同代理类（同一缓存项），稳态开销 ≈ 手写 lambda（`LambdaMetafactory` 调用点）

```java

@Intercept
class MetricsInterceptor {
    @Around(value = "get*", annotatedWith = Tx.class)
    Object measure(Object proxy, Method method, Object[] args) throws Throwable {
        return AcceleratedProxy.invokeSuper(proxy, method, args);
    }
}

Greeter proxy = AcceleratedProxy.intercept(Greeter.class, new MetricsInterceptor());
```

### 构造器拦截（已完成）

- 新 `ConstructorInterceptor` 接口：`before(Constructor<?>, Object[])` 在父类构造器前运行，可改写参数、可抛异常否决；`after(Object, Constructor<?>, Object[])` 在父类构造器后运行，纯观察（默认空实现）
- 新入口：`proxy(Class<T>, Interceptor, ConstructorInterceptor)`、`proxy(Class<T>, ConstructorInterceptor, Group...)`、`proxy(Class<T>, Object[], ConstructorInterceptor, Group...)`（仅类代理）
- JVM 约束：`super()` 之前 `this` 不可用，故 `before` 不传 proxy；`Constructor` 对象经 `<clinit>` 一次性反射解析为静态字段
- 未启用拦截时生成字节码逐字节不变，零开销

### 静态方法代理（已完成）

- 新入口 `AcceleratedProxy.proxyStatic(Class<?>, Group...)` 返回一个生成的代理 `Class`，其 `public static` 方法遮蔽目标类的静态方法并路由到 `Interceptor`（`proxy = null`）；另有单拦截器便捷重载
- 覆盖本类声明 + 继承链的 `public static` 非 `final` 方法（子类优先去重），走 Group 匹配，未匹配方法直通 `INVOKESTATIC` 原实现
- 拦截器内调用原方法：`method.invoke(null, args)`
- 限制：`INVOKESTATIC` 编译期绑定，`Target.staticMethod()` 无法透明拦截，只能对返回的 `Class` 反射或 `MethodHandle` 调用
- 生成类 `extends Object`，静态代理 **不缓存**（静态字段是类级状态），不影响实例代理路径

### 静态方法代理挑战

- 静态方法不参与 vtable，无法通过生成子类重写
- 需要在生成的子类中创建同名静态方法委托
- 使用场景有限（测试 mock、日志注入）

### 类热重载（已完成）

- 缓存键按 `Class` 身份、类名按 `COUNTER` 唯一：对新的 `Class` 对象（或驱逐后重建）再次 `proxy()` 会生成新代理类，旧实例继续用旧类、新实例用新类
- 显式生命周期控制：`AcceleratedProxy.evict(Class)` / `evictClassLoader(ClassLoader)` 确定性驱逐缓存项，下次 `proxy()` 重新生成
- 缓存键不对称：接口代理以「第一个接口」为键，`evict` 针对该键
- 限制：目标类在子 `ClassLoader`（APS 位于共享父加载器）时无法代理，需 JPMS `--add-opens` 策略（item 8）

### 拦截器热替换（已完成）

- `AcceleratedProxy.rebind(proxy, interceptor)` / `rebind(proxy, Interceptor[])` 原地替换已创建代理实例上的拦截器，不重建实例、不换类
- 拦截器字段由 `final` 改为 plain，`rebind` 末尾 `VarHandle.fullFence()`；单写者 + 调用方负责 happens-before
- `ConstructorInterceptor` 不可热替换（仅构造期使用，不落实例字段）

### 虚拟线程兼容性（已完成）

- 目标 JDK 25：自 JDK 24 起 JEP 491 已消除 `synchronized` 的载体线程 pinning，仅 native 方法 / FFM downcall 仍会 pin
- 实例代理热路径（hashCode 开关 + `INVOKESPECIAL` 直调）为纯字节码，无 native、无锁、无阻塞 I/O，稳态调用零 pin（JFR `jdk.VirtualThreadPinned` 事件计数 == 0 验证）
- `defineHiddenClass` 类定义是 native 调用，仅创建时瞬时 pin、随后缓存，不影响稳态；虚拟线程内创建代理已验证可用
- 静态方法代理拦截器内的 `method.invoke(null, args)` 反射冷调用（约前 15 次）走 native accessor 会 pin，inflate 成生成 accessor 后消失——已知限制，非热路径
- 验证：`VirtualThreadCompatibilityTest`（虚拟线程并发正确性 + JFR 零 pin 断言 + 虚拟线程内建代理）；手动诊断可用 `-Djdk.tracePinnedThreads=full`

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

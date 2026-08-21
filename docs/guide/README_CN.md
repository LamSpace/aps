# OpenProxy 用户指导

**OpenProxy**是一个高性能 Java 动态代理库： 它在运行时用 ASM 生成字节码，通过 hashCode 驱动的 `dispatch()` 开关直接 `INVOKESPECIAL`
父类调用，实现近原生的调用性能。

每一章都是独立的：一段简短说明、一个可运行的代码示例、预期输出，以及需要注意的限制。

## 章节

1. [简介](01-introduction_cn.md) — OpenProxy 是什么、核心亮点，以及与 CGLib / `java.lang.reflect.Proxy` 的对比
2. [安装](02-installation_cn.md) — 源码构建与依赖声明
3. [快速开始](03-quick-start_cn.md) — 第一个类代理与接口代理
4. [拦截器 API](04-interceptor-api_cn.md) — `Interceptor` 契约、`invokeSuper`、返回值与异常
5. [方法分组](05-method-grouping_cn.md) — 用 `Group` 把不同拦截器绑定到不同方法
6. [注解驱动 API](06-annotation-api_cn.md) — 用 `@Intercept` / `@Around` 声明式匹配
7. [构造器拦截](07-constructor-interception_cn.md) — 父类构造器前后的钩子
8. [静态方法代理](08-static-method-proxy_cn.md) — 遮蔽 `public static` 方法
9. [多接口代理](09-multi-interface-proxy_cn.md) — 一个代理实现多个接口（含非 public 接口）
10. [热加载 / 热替换](10-hot-reload_cn.md) — `evict`、`evictClassLoader` 与 `rebind`
11. [JPMS / 强封装](11-jpms_cn.md) — 代理强封装模块中的类
12. [迁移](12-migration_cn.md) — 从 CGLib 和 `java.lang.reflect.Proxy` 迁移
13. [性能与基准测试](13-performance_cn.md) — 基准测试结论与运行方法

## 相关文档

- [基准测试报告](../benchmark-results_cn.md)
- [迁移指南](../migration-guide.md)
- [项目 README](../../README_CN.md)

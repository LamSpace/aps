# 11. JPMS / 强封装

类代理通过 `MethodHandles.privateLookupIn` 定义在目标类所在包内。当目标位于强封装模块 （任何未 `open` 的包，含 `java.util` 等 `java.base` 包）时，该 lookup 会被拒绝，
`proxy()` 会快速失败并给出可操作报错：

```text
Cannot access java.util.ArrayList in module java.base (package java.util):
the package is not open to the unnamed module. Add --add-opens
java.base/java.util=ALL-UNNAMED to the JVM arguments, ...
```

## 解决办法

1. 加上提示的 JVM 参数：

   ```bash
   java --add-opens java.base/java.util=ALL-UNNAMED ...
   ```

2. 或在目标模块的 `module-info.java` 中声明开放包：

   ```java
   module my.module {
       opens com.example.internal;
   }
   ```

## 接口代理

接口代理使用公共 `Lookup`，仅支持 **public** 接口（与 `java.lang.reflect.Proxy` 的约束一致）。 非 public 接口代理使用 `LookupManager` 把类定义到接口自身包内——见
[多接口代理](09-multi-interface-proxy_cn.md)。

下一章：[迁移](12-migration_cn.md)。

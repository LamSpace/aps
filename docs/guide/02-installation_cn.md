# 2. 安装

## 环境要求

- **Java 25+**（APS 目标类文件版本 24+，并使用 Java 15 起提供的
  `MethodHandles.Lookup.defineHiddenClass`）
- **ASM 9.7.1**（作为编译依赖声明，无需额外配置）

## 源码构建

```bash
git clone https://github.com/lamspace/aps.git
cd aps
mvn install -DskipTests
```

这会把 `aps` 构件安装到你的本地 Maven 仓库。

## Maven 依赖（即将上线）

APS 尚未发布到 Maven Central。在此之前，请从本地仓库引用：

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>aps</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## 快速自检

```bash
mvn test
```

运行完整测试套件（220+ 个测试）并编译 JMH 基准测试。

下一章：[快速开始](03-quick-start_cn.md)。

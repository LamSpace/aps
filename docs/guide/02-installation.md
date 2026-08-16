# 2. Installation

## Requirements

- **Java 25+** (APS targets class-file version 24+ and uses
  `MethodHandles.Lookup.defineHiddenClass`, available since Java 15)
- **ASM 9.7.1** (declared as a compile dependency — no extra setup needed)

## Build from source

```bash
git clone https://github.com/lamspace/aps.git
cd aps
mvn install -DskipTests
```

This installs the `aps` artifact into your local Maven repository.

## Maven dependency (coming soon)

APS is not yet published to Maven Central. Until then, depend on it from your local repository:

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>aps</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick self-check

```bash
mvn test
```

Runs the full test suite (220+ tests) and compiles the JMH benchmarks.

Next: [Quick Start](03-quick-start.md).

# 2. Installation

## Requirements

- **Java 25+** (OpenProxy targets class-file version 24+ and uses
  `MethodHandles.Lookup.defineHiddenClass`, available since Java 15)
- **ASM 9.7.1** (declared as a compile dependency — no extra setup needed)

## Build from source

```bash
git clone https://github.com/lamspace/openproxy.git
cd openproxy
mvn install -DskipTests
```

This installs the `openproxy` artifact into your local Maven repository.

## Maven dependency (coming soon)

OpenProxy is not yet published to Maven Central. Until then, depend on it from your local repository:

```xml
<dependency>
    <groupId>io.github.lamspace</groupId>
    <artifactId>openproxy</artifactId>
    <version>0.1.0-SNAPSHOT</version>
</dependency>
```

## Quick self-check

```bash
mvn test
```

Runs the full test suite (220+ tests) and compiles the JMH benchmarks.

Next: [Quick Start](03-quick-start.md).

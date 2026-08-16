# 11. JPMS / Strong Encapsulation

Class proxies are defined in the target class's package via
`MethodHandles.privateLookupIn`. When the target lives in a strongly encapsulated module — any package that is not `open`, including `java.base`
packages such as `java.util` — that lookup is denied and `proxy()` fails fast with an actionable message:

```text
Cannot access java.util.ArrayList in module java.base (package java.util):
the package is not open to the unnamed module. Add --add-opens
java.base/java.util=ALL-UNNAMED to the JVM arguments, ...
```

## Fixes

1. Add the suggested JVM flag:

   ```bash
   java --add-opens java.base/java.util=ALL-UNNAMED ...
   ```

2. Or declare the package open in the target module's `module-info.java`:

   ```java
   module my.module {
       opens com.example.internal;
   }
   ```

## Interface proxies

Interface proxies use a public `Lookup` and support **public** interfaces only (the same constraint as `java.lang.reflect.Proxy`). Non-public interface proxies use `LookupManager` to define the class in the interface's own package — see
[Multi-Interface Proxy](09-multi-interface-proxy.md).

Next: [Migration](12-migration.md).

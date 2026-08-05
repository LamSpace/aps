## Context

See proposal.md for motivation. The current code has `DispatchGenerator.resolveHashes()` defined but never called — both `ClassGenerator.generate()` and `InterfaceGenerator.generate()` use `DispatchGenerator.computeHash()` directly, bypassing collision detection entirely.

The existing secondary hash formula (`hash * 31 + m.getName().hashCode()`) cannot resolve overloaded methods because they share the same name, so the secondary hash is identical.

## Goals / Non-Goals

**Goals:**
- Wire `resolveHashes()` into both generation pipelines so every proxy class has collision-free dispatch hashes
- Fix the secondary hash to handle overloaded methods (same name, different parameter types)
- Add test coverage for overloaded method dispatch

**Non-Goals:**
- Changing the `Method.hashCode()`-based primary hash (it's deterministic and JVM-guaranteed)
- Changing the if-else chain structure of `dispatch()`
- Handling hash collisions on interface proxy dispatch (interface proxies never call `invokeSuper` on interface methods — only Object methods which can't collide)

## Decisions

### Decision 1: Wire `resolveHashes()` at the infos construction site

**What:** In `ClassGenerator.generate()` and `InterfaceGenerator.generate()`, replace the per-method `DispatchGenerator.computeHash()` call with a single `DispatchGenerator.resolveHashes()` call that processes all methods at once.

**Why:** `resolveHashes()` needs visibility of ALL methods to detect collisions. Calling it per-method would defeat the purpose.

**How:** After draining `ClinitRegistry`, collect all `Method` objects from the entries, pass them to `resolveHashes()`, then construct `MethodInfo` objects with the resolved hashes.

**Before (ClassGenerator.java:130-139):**
```java
List<ClinitRegistry.Entry> entries = ClinitRegistry.drain();
List<MethodInfo> infos = new ArrayList<>();
for (ClinitRegistry.Entry entry : entries) {
    infos.add(new MethodInfo(entry.method(), entry.methodFieldName(),
            DispatchGenerator.computeHash(entry.method())));
}
```

**After:**
```java
List<ClinitRegistry.Entry> entries = ClinitRegistry.drain();
List<Method> methods = entries.stream().map(ClinitRegistry.Entry::method).toList();
Map<Method, Integer> hashMap = DispatchGenerator.resolveHashes(methods);
List<MethodInfo> infos = new ArrayList<>();
for (ClinitRegistry.Entry entry : entries) {
    infos.add(new MethodInfo(entry.method(), entry.methodFieldName(),
            hashMap.get(entry.method())));
}
```

Same pattern applies to `InterfaceGenerator.generate()`.

### Decision 2: Fix secondary hash with parameter type discriminator

**What:** Replace `hash * 31 + m.getName().hashCode()` with `hash * 31 + Arrays.hashCode(m.getParameterTypes())`.

**Why:** Overloaded methods have the same name but different parameter types. `Arrays.hashCode(m.getParameterTypes())` produces distinct values for each unique parameter signature, resolving the overload case. For non-overload collisions (different names with same `String.hashCode()`), parameter types almost certainly differ as well, making this a strong discriminator.

**Alternatives considered:**

| Approach | Pros | Cons |
|----------|------|------|
| `m.getName().hashCode()` (current) | Simple | Fails for overloads — same name, same hash |
| `Arrays.hashCode(m.getParameterTypes())` | Resolves overloads; deterministic | Theoretical triple collision with identical param types AND name hash |
| `m.toGenericString().hashCode()` | Unique per signature | Allocates a string; overkill |
| Sequential counter for collisions | Guaranteed unique | Non-deterministic; dispatch hash depends on iteration order |

**Fallback:** If even the secondary hash collides (theoretical edge case: unrelated methods with both identical `method.hashCode()` AND identical `Arrays.hashCode(parameterTypes)`), iterate with incrementing counter until unique. This is a safety net, not expected to trigger in practice.

**Final `resolveHashes()` logic:**
```java
static Map<Method, Integer> resolveHashes(List<Method> methods) {
    Map<Method, Integer> result = new LinkedHashMap<>();
    Set<Integer> seen = new HashSet<>();
    for (Method m : methods) {
        int hash = computeHash(m);
        if (!seen.add(hash)) {
            // Collision: use parameter types as secondary discriminator
            hash = hash * 31 + Arrays.hashCode(m.getParameterTypes());
            int salt = 1;
            while (!seen.add(hash)) {
                // Fallback: incrementing counter for extreme edge cases
                hash = hash * 31 + salt++;
            }
        }
        result.put(m, hash);
    }
    return result;
}
```

### Decision 3: Unit test strategy

**What:** Add a test class with overloaded methods to `AcceleratedProxyClassProxyTest`.

**Test class structure:**
```java
static class OverloadedTarget {
    public String greet(String name) { return "Hello, " + name; }
    public String greet(int count) { return "Count: " + count; }
    public String greet(String name, int count) { return name + " x" + count; }
}
```

**Test:** Create a proxy, call each overloaded variant via `invokeSuper`, and verify the correct method body executed.

## Risks / Trade-offs

- **Risk:** `Arrays.hashCode(m.getParameterTypes())` uses `Class.hashCode()` which is deterministic per JVM instance but not across JVM restarts — same risk as the primary hash. → **Mitigation:** Hashes are computed at generation time and embedded in bytecode; they only need to be consistent within a single class, not across restarts.
- **Risk:** The fallback `while` loop could theoretically be infinite if every hash value is occupied. → **Mitigation:** This requires 2^32 hash collisions which is impossible with the limited number of methods in a single class (JVM method limit is 65535).

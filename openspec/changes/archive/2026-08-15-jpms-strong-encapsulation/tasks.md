## 1. LookupManager fail-fast

- [x] 1.1 In `LookupManagerTest.java`, change `shouldReturnNonNullLookupForStandardClass` to use `LookupManager.class` instead of `String.class`, and add `shouldThrowActionableErrorForStronglyEncapsulatedClass` (assert `getLookup(java.util.ArrayList.class)` throws `IllegalArgumentException` with `--add-opens` in the message)
- [x] 1.2 Run `mvn -s /home/lam/repo/settings.xml -q test -Dtest=LookupManagerTest` and confirm it fails (nothing thrown)
- [x] 1.3 Implement the fail-fast branch in `LookupManager.getLookup` (throw `IllegalArgumentException` with the `--add-opens` hint on `IllegalAccessException`; keep the primitive/array fallback), and update the class + method Javadoc
- [x] 1.4 Run `mvn -s /home/lam/repo/settings.xml -q test -Dtest=LookupManagerTest` and confirm all 5 tests pass

## 2. Transparent error propagation through generateProxyClass

- [x] 2.1 Create `src/test/java/io/github/lamspace/JpmsStrongEncapsulationTest.java` with a test asserting `proxy(ArrayList.class, (o, m, a) -> null)` throws `RuntimeException` whose direct cause is `IllegalArgumentException` containing `--add-opens`
- [x] 2.2 Run `mvn -s /home/lam/repo/settings.xml -q test -Dtest=JpmsStrongEncapsulationTest` and confirm it fails (cause is currently `Failed to generate proxy class`)
- [x] 2.3 In `AcceleratedProxy.generateProxyClass`, add `catch (IllegalArgumentException e) { throw e; }` before the existing `catch (Exception e)`
- [x] 2.4 Run `mvn -s /home/lam/repo/settings.xml -q test -Dtest=JpmsStrongEncapsulationTest,LookupManagerTest` and confirm they pass
- [x] 2.5 Run the full suite `mvn -s /home/lam/repo/settings.xml -q test` and confirm no regression

## 3. Documentation

- [x] 3.1 Update `docs/openproxy-future-roadmap.md`: mark item 8 done with a `### JPMS 强封装模块（已完成）` subsection, add item 10 `非 public 接口代理`, and fix the item 6 cross-reference (`跨 ClassLoader 热部署待 item 8` → `跨 ClassLoader 热部署（独立待办）`)
- [x] 3.2 Add a "JPMS / Strong Encapsulation" section to `README.md` and its Chinese equivalent to `README_CN.md` (the `--add-opens` requirement + an example error)

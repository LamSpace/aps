## 1. Core non-public interface proxying

- [x] 1.1 Create package-private test interface `src/test/java/io/github/lamspace/pkgprivate/SecretService.java`
- [x] 1.2 Write failing test `NonPublicInterfaceProxyTest` (package-private interception + `invokeSuper` on a `default` method)
- [x] 1.3 Add `nonPublicAnchor` helper to `AcceleratedProxy` and wire it into `proxyInterfaces` (validation) and `generateProxyClass` (package + lookup)
- [x] 1.4 Add `packagePrefix` parameter to `InterfaceGenerator` and use it in `generate()`
- [x] 1.5 Run `NonPublicInterfaceProxyTest` and the full `mvn test`; commit

## 2. Conflict and mixed-array scenarios

- [x] 2.1 Create `src/test/java/io/github/lamspace/otherpkg/OtherSecretService.java`
- [x] 2.2 Add mixed public + package-private, cross-package conflict, cache-identity, and evict tests
- [x] 2.3 Run `NonPublicInterfaceProxyTest`; commit

## 3. Public JDK interface regression guard

- [x] 3.1 Create `PublicJdkInterfaceProxyTest` (proxy `java.util.function.Function` without `--add-opens`)
- [x] 3.2 Run the test; commit

## 4. Documentation

- [ ] 4.1 Update `docs/aps-future-roadmap.md` (mark item 10 已完成 + add a detailed section)
- [ ] 4.2 Update `README.md` and `README_CN.md` (feature bullet + example)
- [ ] 4.3 Add `openspec/specs/non-public-interface-proxy/spec.md`
- [ ] 4.4 Update the `proxy(Class<?>[], …)` Javadoc
- [ ] 4.5 Run the full suite; commit

## 1. Root package — `io.github.lamspace`

- [x] 1.1 `AcceleratedProxy`: rewrite the class-javadoc cache statement and the `PROXY_CLASS_CACHE` field comment to match the actual `CacheParams` key (`{targetClass or first interface, interfaces, mapping, constructor-arg types, ctorIntercept}`, interceptors explicitly excluded)
- [x] 1.2 `AcceleratedProxy`: add summary javadoc to the 6 undocumented private helpers — `sneakyThrow`, `buildGlobs`, `matchesAnyGlob`, `globMatches`, `matchesAnyRegex`, `hasAnyAnnotation`
- [x] 1.3 `WeakCache`: replace `{@link java.lang.reflect.WeakCache}` and `@see java.lang.reflect.WeakCache` with plain `{@code}` text references; remove the misused `@author copied from ...` tag and move attribution into the class description; add `removeIf` to the expunge-method list in the class comment
- [x] 1.4 `WeakCache`: document undocumented members — `expungeStaleEntries`, `CacheKey` constructor/`valueOf`/`expungeFrom`, `LookupValue`/`CacheValue`/`Factory` constructors
- [x] 1.5 `Group`: add one-line javadoc to the package-private accessors `predicate()`, `interceptor()`, `isOtherwise()`

## 2. Generator package — `io.github.lamspace.generator`

- [x] 2.1 `ClassGenerator`: add javadoc to the 8 undocumented private methods — `generateClinit`, `generateConstructor`, `generateInterceptedConstructor`, `storeInterceptorFields`, `generateRebindMethod`, `superParamTypes`, `findConstructor`, `wrap`
- [x] 2.2 `InterfaceGenerator`: add javadoc to `generateConstructor`, `generateRebindMethod`, `generateClinit`
- [x] 2.3 `InterfaceDispatcher`: add javadoc to `addStaticField`, `generateImplementation`
- [x] 2.4 `MethodDispatcher`: add javadoc to `addStaticField`, `generateOverride`, `loadArguments`
- [x] 2.5 `StaticMethodGenerator`: add javadoc to the 8 undocumented private methods — `generatePassthrough`, `generateIntercepted`, `generateBind`, `generateClinit`, `exceptionNames`, `loadArguments`, `returnResult`, `returnFromObject`
- [x] 2.6 `BytecodeUtils`: add missing `@param` tags to `pushClassConstant`; document `getWrapperInternalName`
- [x] 2.7 `DispatchGenerator`: add missing `@param`/`@return` to `methodDispatchHash` and missing `@param`s to `emitDispatchBody`
- [x] 2.8 `InterfaceMethodResolver`: document private `signatureKey`
- [x] 2.9 `MethodInfo`: add one-line comments to the secondary and compact record constructors

## 3. Internal package — `io.github.lamspace.internal`

- [x] 3.1 `LookupManager`: de-duplicate `getLookup`'s javadoc — one-line summary + tags, pointing to the class-level comment for the resolution strategy

## 4. Verification

- [x] 4.1 Run `mvn -q compile` — still green (no accidental code change)
- [x] 4.2 Run `javadoc -Xdoclint:all -package` over all three packages with ASM on the classpath (command in design.md §5) — zero errors and zero warnings; fix any residual finding in the same pass (comments only)
- [x] 4.3 Review `git diff` — confirm changes are confined to comment blocks and no code line changed

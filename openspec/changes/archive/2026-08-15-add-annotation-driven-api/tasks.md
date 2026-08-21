# Implementation Tasks

Detailed TDD steps (exact test code and implementation code per task) are in
`docs/superpowers/plans/2026-08-15-annotation-driven-api.md`. This checklist
tracks the same work at the task level.

## 1. Annotations

- [x] 1.1 Create `@Intercept` (type-level, RUNTIME) in `io.github.lamspace`
- [x] 1.2 Create `@Around` (method-level, RUNTIME) with `value`/`glob`/`regex`/`annotatedWith` elements and their defaults
- [x] 1.3 Add `AnnotationDrivenApiTest` with reflection tests for retention, target, and element defaults

## 2. `intercept()` end-to-end with glob matching

- [x] 2.1 Add `AcceleratedProxy.intercept(Class<T>, Object)` entry point
- [x] 2.2 Add `resolveAnnotationGroups` (null/@Intercept/no-@Around validation + name-sorted collection)
- [x] 2.3 Add `validateAroundMethod` (non-static, `(Object, Method, Object[])`, reference return)
- [x] 2.4 Add glob `toPredicate` + `buildGlobs`/`matchesAnyGlob`/`globMatches`
- [x] 2.5 Add `toInterceptor` via `LambdaMetafactory` captured-argument form
- [x] 2.6 Test: single glob routes matched methods; unmatched methods passthrough

## 3. Regex and `annotatedWith` dimensions

- [x] 3.1 Extend `toPredicate` to AND-combine glob + regex + annotatedWith (OR within each)
- [x] 3.2 Add `matchesAnyRegex` and `hasAnyAnnotation` helpers
- [x] 3.3 Add invalid-regex validation (empty + `PatternSyntaxException` → `IllegalArgumentException`)
- [x] 3.4 Test: regex matching, annotatedWith matching, glob+annotatedWith AND, multi-glob OR, invalid regex

## 4. Adapter fidelity and validation coverage

- [x] 4.1 Test: adapter passes `(proxy, method, args)` and can call `invokeSuper`
- [x] 4.2 Test: subtype return type widened to `Object`
- [x] 4.3 Test: null target/interceptor, non-@Intercept class, no @Around, wrong params, void return, static method all fail fast

## 5. Equivalence and determinism

- [x] 5.1 Test: annotation-driven and equivalent programmatic `Group` share the same generated class
- [x] 5.2 Test: overlapping `@Around` patterns use deterministic name-sorted first-match

## 6. Benchmark

- [x] 6.1 Add annotation-driven vs programmatic state and `ann_*` benchmark methods to `ProxyBenchmark`
- [x] 6.2 Compile and run the `ann_.*` benchmark; confirm parity within noise

## 7. Documentation

- [x] 7.1 Mark Phase 3 item 3 as 已完成 in `docs/openproxy-future-roadmap.md` and replace its section
- [x] 7.2 Add feature bullet + Quick Start + spec link to `README.md` and `README_CN.md`

## Context

The APS → OpenProxy rename (`b79fd7f`) touched only documentation; the public entry class `AcceleratedProxy` was never renamed, leaving error messages ("not an OpenProxy-generated proxy") and the API surface inconsistent. Full blast radius was mapped in explore mode: ~10 semantic spots in `src/main` (the class itself + 3 runtime-visible generated-name markers), ~11 comment/javadoc spots, 342 test references, 46 benchmark references, and ~597 doc/spec references. No test asserts on the `AcceleratedProxy` or `$$AcceleratedProxy$$` strings; the only string-based usage (`DuplicateMatchWarningTest` uses `AcceleratedProxy.class.getName()`) follows the class name automatically.

## Goals / Non-Goals

**Goals:**

- One consistent public entry point named `OpenProxy` across code, tests, benchmarks, live docs, and specs.
- Generated class names carry the `$$OpenProxy$$` marker.
- Zero behavior change: identical test count all green, doclint-clean, benchmarks within noise.

**Non-Goals:**

- No deprecated `AcceleratedProxy` compatibility shim (user decision: hard rename; artifact is unpublished `0.1.0-SNAPSHOT`).
- No changes to `docs/superpowers/` historical plan/design archives (user decision: historical record keeps the old name).
- No fix of known stale spec text (the `ClassFilter`-based "Method filtering" requirements in `openproxy-core` / `openproxy-interface-proxy` describe a removed API) — deltas rename only; a separate change can rewrite those requirements.

## Decisions

### 1. Hard rename, no shim

The artifact is unpublished, so a compatibility shell buys nothing and would itself need deletion later. All call sites are in-repo.

### 2. Rename the generated-class marker to `$$OpenProxy$$`

The marker appears in hidden class names (stack traces, `toString`, heap dumps). Keeping the old marker would leave the rename half-done at runtime. Verified no test depends on the marker string.

### 3. Mechanical substitution, layer by layer

Order: (a) `git mv` the class file and rename class/constructor/logger/self-references; (b) the three marker literals; (c) remaining `src/main` comments; (d) `src/test` global substitution + rename `AcceleratedProxyClassProxyTest`/`AcceleratedProxyInterfaceProxyTest` files; (e) benchmarks; (f) live docs; (g) main-spec Purpose (see below). Each layer is a pure find-replace of the exact token `AcceleratedProxy` — the token has no substring collisions in this codebase.

### 4. Specs: MODIFIED deltas (rename only) + one direct Purpose edit

Eight capabilities carry the class name in requirement text, so the change ships eight MODIFIED deltas that copy each affected requirement block verbatim with the name substituted — no other wording changes. One Purpose (`openproxy-unified-proxy`) mentions the entry point; deltas cannot carry Purpose changes, so that main-spec Purpose is edited directly as a task in this change.

### 5. Verification gates

- Baseline captured **before** any code change: `mvn test` run (record test count) and one JMH run of the 4 benchmarks.
- After the rename: `mvn test` green with the **same test count** (the existing suite is the regression net — no new functional tests needed for a symbolic rename); `javadoc -Xdoclint:all -package` zero warnings (gate from the previous change); benchmarks re-run and compared — all OpenProxy items must stay within benchmark noise of baseline (a rename cannot change performance; this verifies nothing was structurally broken); final grep sweep proving zero `AcceleratedProxy` occurrences outside `docs/superpowers/` and archived changes.
- Optional single new assertion (guarded as a task): generated proxy class name contains `$$OpenProxy$$` — pins the new marker.

## Risks / Trade-offs

- [Missed reference] → final repo-wide grep sweep is the closing gate; doclint catches missed javadoc links at compile-of-docs level.
- [Benchmark comparison drowned in machine noise] → compare order-of-magnitude and relative ranking vs CGLib/JDK baselines rather than exact nanoseconds; the real gate is "compiles, runs, no structural regression".
- [`git mv` + edits split the rename from content changes in blame] → acceptable; done as one commit so the rename is reviewable atomically.
- [Stale `ClassFilter` spec text survives under the new name] → accepted and documented in Non-Goals; flagging it here for a follow-up change.

## Migration Plan

None for users (unpublished artifact). `docs/migration-guide.md` gains a short note documenting the `AcceleratedProxy` → `OpenProxy` rename for anyone tracking snapshots. Rollback is `git revert`.

## Open Questions

None — the three explore-mode decisions (hard rename, `$$OpenProxy$$` marker, untouched historical archives) are confirmed by the user.

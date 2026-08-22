## Why

The project was renamed from APS (Accelerated Proxy Solution) to OpenProxy in `b79fd7f`, but that change only touched documentation — the public entry class is still `AcceleratedProxy`, leaving the product half-renamed (error messages already say "not an OpenProxy-generated proxy" while users call `AcceleratedProxy.proxy(...)`). The project is `0.1.0-SNAPSHOT` with no external users, so a hard rename is free right now and will be a breaking migration cost later.

## What Changes

- **BREAKING**: Rename the public entry class `AcceleratedProxy` → `OpenProxy` (file, class, private constructor, logger, self-references). Hard rename — no deprecated shim.
- Rename the runtime-visible generated-class marker `$$AcceleratedProxy$$` → `$$OpenProxy$$` in `ClassGenerator`, `InterfaceGenerator`, `StaticMethodGenerator` (appears in hidden class names and stack traces).
- Update all remaining `AcceleratedProxy` references in `src/main` javadoc and comments (~11 spots across 9 files).
- Rename all references in `src/test` (342 spots / 23 files), including renaming the two test classes `AcceleratedProxyClassProxyTest` / `AcceleratedProxyInterfaceProxyTest`.
- Update the 4 JMH benchmark classes.
- Update live documentation: `README.md`, `README_CN.md`, `docs/guide/` (20 files), `docs/migration-guide.md` (add a rename note). Historical archives under `docs/superpowers/` are intentionally left untouched.
- Update 8 main specs via MODIFIED deltas (name substitution only, no requirement-text changes).

## Capabilities

### New Capabilities

None.

### Modified Capabilities

- `openproxy-core`: requirements referencing the entry class renamed to `OpenProxy`
- `openproxy-unified-proxy`: same
- `openproxy-interface-proxy`: same
- `multi-interceptor-grouping`: same
- `annotation-driven-api`: same
- `hot-reload`: same
- `multi-interface-proxy`: same
- `static-method-proxy`: same

## Impact

- **API**: one public class renamed (`io.github.lamspace.AcceleratedProxy` → `io.github.lamspace.OpenProxy`). Breaking in principle, but the artifact is unpublished (`0.1.0-SNAPSHOT`).
- **Runtime-visible**: generated hidden class names change from `Target$$AcceleratedProxy$$N` to `Target$$OpenProxy$$N` (stack traces, `toString`).
- **Code**: ~970 textual replacements across 17 source/test files, 23 docs, 8 specs. Behavior, bytecode structure, and performance are unchanged — the rename is purely symbolic.
- **Out of scope**: `docs/superpowers/` historical plan/design archives keep the old name as historical record.

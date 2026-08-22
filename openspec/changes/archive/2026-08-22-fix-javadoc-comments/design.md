## Context

All 25 files under `src/main/java/io/github/lamspace/` were audited file-by-file (full list and findings in proposal.md). Public API classes are well documented; the defects cluster in three places: undocumented private/package-private helpers (bytecode generators dominate), one class (`WeakCache`, copied from a JDK package-private class) with non-standard tags and unresolvable references, and two stale cache-key descriptions in `AcceleratedProxy`. The project builds with Maven on Java 25; no maven-javadoc-plugin is configured.

## Goals / Non-Goals

**Goals:**

- Every class, and every non-trivial method/constructor at any visibility level, carries a JavaDoc comment consistent with the established house style (third-person summary — "Generates...", "Returns..." — plus `@param`/`@return`/`@throws` where semantics are not self-evident from the signature).
- Zero unresolvable `{@link}`/`@see` references; zero non-standard tag usage.
- All comments factually match the current code.
- `javadoc -Xdoclint:all -package` over `src/main/` completes with no errors and no warnings.

**Non-Goals:**

- No changes to code, signatures, formatting, or imports.
- No build changes (no maven-javadoc-plugin addition, no CI gate) — verification is an ad-hoc command; tooling can be a follow-up change.
- No rewriting of comments that are already compliant (the audit table in proposal.md is the exhaustive work list).
- No translation of comments; everything stays English.

## Decisions

### 1. Documentation bar by visibility

- **public/protected** (including public members of package-private classes): full JavaDoc — summary, `@param` for each parameter, `@return`, `@throws`. (Already satisfied today; only tag-completion fixes needed.)
- **package-private/private helpers**: at least a one-sentence summary describing *what* the method produces/does; `@param`/`@return` only when the meaning is not obvious from names. Trivial one-liners (e.g. `wrap(Class)`, `matchesAnyGlob`) get a one-line summary.
- **`Object` overrides** (`equals`/`hashCode`/`toString`) and self-explanatory private constructors: exempt — matches existing codebase convention (none are documented today, and JavaDoc inherits semantics).
- **Alternatives considered**: full `@param`/`@return` on every private method — rejected as boilerplate noise for self-evident helpers and inconsistent with the existing style (e.g. `matchMethods`, `nonPublicAnchor` already use summary-first style). Documenting only public API — rejected: the generators are intricate ASM code where private helpers (`generateInterceptedConstructor`, `emitDispatchBody`) carry the real logic.

### 2. WeakCache reference fix

Replace `{@link java.lang.reflect.WeakCache}` and `@see java.lang.reflect.WeakCache` with plain `{@code java.lang.reflect.WeakCache}` text references — the JDK class is package-private, so the doc tool can never resolve the link. Remove the misused `@author copied from ...` tag; move attribution into the class description ("Copied from the JDK's package-private ..."). Also extend the expunge-method list in the class comment to include `removeIf`.
**Alternative considered**: deleting the provenance note — rejected, the JDK origin is genuinely useful context.

### 3. AcceleratedProxy cache-key descriptions

Rewrite both stale spots (class javadoc and `PROXY_CLASS_CACHE` field) to match `CacheParams`: key is `{targetClass or first interface, interfaces, mapping, constructor-arg types, ctorIntercept}` and explicitly *excludes* interceptor instances (share generated classes across interceptor identities). Align the wording with the accurate `CacheParams` record comment instead of inventing new phrasing.

### 4. LookupManager duplication

Shorten `getLookup`'s javadoc to a one-line summary plus tags ("see the class-level comment for the resolution strategy") instead of the current verbatim copy of the class doc.

### 5. Verification gate

Ad-hoc invocation (no pom change):

```bash
mvn -q compile                      # sanity: no accidental code change
javadoc -Xdoclint:all -package \
  -cp ~/.m2/repository/org/ow2/asm/asm/9.7.1/asm-9.7.1.jar \
  -sourcepath src/main/java \
  -d /tmp/openproxy-javadoc \
  io.github.lamspace io.github.lamspace.generator io.github.lamspace.internal
```

`-package` visibility is chosen deliberately so package-private classes (`WeakCache`, `BytecodeUtils`, `ClinitRegistry`, `InterfaceDispatcher`, `MethodInfo`) are also checked; doclint then catches both unresolvable links and missing tags on package-private members. Exit gate: zero errors and zero warnings. Additionally, `git diff` after implementation must show comment-block changes only.

**Alternative considered**: adding maven-javadoc-plugin with doclint — rejected as a build change beyond the docs scope (can be a follow-up).

### 6. Work order

Batch by package, verify once at the end (not per file): root package (`AcceleratedProxy`, `WeakCache`, `Group`) → `generator` package (`ClassGenerator`, `InterfaceGenerator`, `InterfaceDispatcher`, `MethodDispatcher`, `StaticMethodGenerator`, `BytecodeUtils`, `DispatchGenerator`, `InterfaceMethodResolver`, `MethodInfo`) → `internal` (`LookupManager`) → final full doclint run + diff review.

## Risks / Trade-offs

- [Comment edits accidentally touch code] → Mitigation: final `git diff` review restricted to comment lines; `mvn -q compile` still green. Comments have zero bytecode effect.
- [Private-method docs drift from behavior over time] → Accepted trade-off; summaries are kept at contract level (what, not how) to age well.
- [Doclint at `-package` may surface additional undocumented package-private members not in the audit] → Mitigation: the audit covered all 25 files; any residual warning found at verification time is fixed in the same pass (still comments only).
- [`javadoc` run fails on the Java 25 preview-free sources for unrelated reasons] → Mitigation: sources are plain Java (no preview features); the gate command is self-contained and reproducible.

## Migration Plan

None — documentation only. Rollback is `git revert`.

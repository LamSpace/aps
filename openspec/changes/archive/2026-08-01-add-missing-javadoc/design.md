## Context

The OpenProxy project has 13 main Java source files under `src/main/java/io/github/lamspace/`. Some public/protected members have Javadoc; others do not. The existing Javadoc follows standard conventions: brief description, `@param`, `@return`, `@throws` tags where applicable. Class-level Javadoc includes an `@author` tag.

No existing tooling enforces Javadoc presence (no Checkstyle or similar lint rules).

## Goals / Non-Goals

**Goals:**

- Ensure every public and protected method, constructor, and field across all 13 main source files has a Javadoc comment
- Ensure every class has a class-level Javadoc
- Match the style and conventions of existing Javadoc in the codebase
- Cover package-private methods where their purpose is non-obvious from context

**Non-Goals:**

- Test files (`src/test/java/`) — test methods are self-documenting by name and assertion
- `package-info.java` files — these already have package-level documentation
- Adding Javadoc linting/Checkstyle rules
- Rewriting or "improving" existing Javadoc comments
- Adding Javadoc to private methods (unless the method is complex and would benefit)

## Decisions

1. **File-by-file audit approach** — Review each main source file individually rather than bulk-generating comments. This ensures each Javadoc is accurate to the method's actual behavior.

2. **Follow existing conventions** — Use `@param`, `@return`, `@throws` tags only where they add value (non-obvious parameters, non-void returns, declared checked exceptions). Keep first-line descriptions concise and imperative ("Returns the..." not "This method returns the...").

3. **No test file changes** — Test files are excluded. Test method names describe intent; Javadoc on test helpers adds maintenance burden without proportional value.

4. **No tooling changes** — This is a one-time documentation pass, not a process change. Adding Checkstyle or CI enforcement is out of scope.

## Risks / Trade-offs

- **Risk**: Generated Javadoc may be inaccurate if method behavior is misunderstood → **Mitigation**: Read each method body before writing its Javadoc; verify against existing documented methods in same class for consistency
- **Trade-off**: Manual audit takes more time than bulk generation → Acceptable given the small file count (13 files) and the need for accuracy

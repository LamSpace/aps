## Why

Several public and protected methods, constructors, and fields across the APS codebase lack Javadoc documentation. This reduces code readability, makes the public API harder to understand for consumers, and undermines the professionalism of the library. Adding comprehensive Javadoc ensures that all public-facing API elements are self-documenting and consistent with the already-documented portions of the codebase.

## What Changes

- Add Javadoc to all public and protected methods, constructors, and fields currently missing documentation across all main source files (13 Java files under `src/main/java/`)
- Add Javadoc to package-private members where their purpose is non-obvious
- Ensure class-level Javadoc exists for all classes that lack it
- Follow existing Javadoc conventions already established in the codebase (e.g., `@param`, `@return`, `@throws` tags where applicable)

## Capabilities

### New Capabilities

<!-- No new capabilities — this is a documentation-only change -->

### Modified Capabilities

<!-- No requirement changes — this is a documentation-only change -->

## Impact

- All 13 main Java source files under `src/main/java/io/github/lamspace/` and its sub-packages
- No API or behavioral changes — documentation only
- No dependency changes
- No breaking changes

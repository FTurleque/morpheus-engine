# Proposal

## Why

AUD-QUA-12 (sprint 4) reports that most `@SuppressWarnings` carry no justification while the SpotBugs exclusion file
requires one. Re-read on `develop` at `bd2294b6` on 2026-10-13, the finding **holds, with different numbers**:

- 53 `@SuppressWarnings` in the Java sources today: 20 under `src/main`, 33 under `src/test` (the audit counted 47 in
  all).
- Under `src/main`: 10 `java:S1181` (catching `Throwable`), 4 `java:S106` (standard streams), 4 `unchecked`, 2
  `java:S107` (parameter count).
- A rough scan finds a comment on the same line or the line above for 14 of the 20; the lot reads each one, because a
  comment nearby is not always the justification.
- Six unchecked casts of a `Map` remain at the MCP and HTTP boundaries, behind helpers such as `castMap`.
- `config/spotbugs-exclude.xml` says an entry "carries a comment saying why the finding is wrong for that code".

Two standards for one decision: silencing a warning in the exclusion file needs a written reason, silencing it inline
needs nothing. A reviewer cannot tell a cast the call site proves from a cast someone assumed.

## What Changes

- A `@SuppressWarnings` in the product's sources carries its reason next to it, and a test refuses one that does not.
- The unchecked casts at the MCP and HTTP boundaries become validations that refuse by name.

## Capabilities

### New Capabilities

- `warning-suppression-justification`: a suppressed warning says why, where it is suppressed.

### Modified Capabilities

(none)

## Impact

- The 20 sites under `src/main`; the helpers that cast a `Map` in `morpheus-mcp` and `morpheus-api`.
- One textual architecture test.
- Independent of the other changes of sprint 4.

# Design

## Context

- The Reactor BOM versions are calendar trains: `2024.0.x` is one compatible line (`reactor-core` 3.7.x); `2025.0.x` is
  the next. Dependabot classifies `2024.0.0 -> 2024.0.18` as a patch and `2024.0.x -> 2025.0.x` as a major.
- The MCP SDK declares the Reactor line it was built on; MORPHEUS imports the BOM to pin the transitive version.
- `.claude/rules/build.md` lists the current pinned versions and says to re-read them from the POM.

## Goals / Non-Goals

**Goals**: the shipped Reactor is the patched one; the bot proposes the next patch and not the next train.

**Non-Goals**: moving to the 2025 train (it follows an MCP SDK upgrade, a separate decision); auditing every other
pinned line.

## Decisions

1. **`2024.0.18`, the last of the line on the day of the change** - re-read from Maven Central when the change is made,
   not copied from this page.

2. **`ignore` with `update-types`, not a version range.** An entry for `io.projectreactor:reactor-bom` ignoring
   `version-update:semver-major` and `version-update:semver-minor` leaves patches open and needs no edit at each
   release. *Rejected*: `versions: [">=2025"]` - correct today, silently wrong when the line changes.

3. **The rule and the comment are held together by a test.** The POM comment is the reason the bot rule exists; the test
   asserts that while the POM says the transport stays on a Reactor line, `dependabot.yml` carries the ignore entry for
   the Reactor BOM with both update types. Textual by nature (`.xml`, `.yml`). When the SDK moves and the comment goes,
   the entry goes with it, and the test says which to remove.

4. **No ADR.**

## Risks / Trade-offs

- Eighteen patch releases at once: behaviour of the STDIO client transport may differ. The real-process tests of
  `BoundedStdioClientTransport` and the MCP client timeout tests are the proof; M19 does not exercise this path.
- A patch release of Reactor can itself regress; the bot now proposes them one by one, each through CI.

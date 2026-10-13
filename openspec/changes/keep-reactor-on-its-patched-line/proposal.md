# Proposal

## Why

AUD-DEP-02 (sprint 3) reports that Reactor is pinned to the first release of its line and that the update bot cannot
propose the fix. Re-read on `develop` at `0f3b0ad0` on 2026-10-12, the finding **holds**:

- `pom.xml:53` is `<reactor-bom.version>2024.0.0</reactor-bom.version>`; the comment at `:109` says the MCP SDK 2.0.1 is
  built on the Reactor 2024.0.x / 3.7.x line and that the transport must stay on it.
- Maven Central lists `2024.0.18` as the last release of that line (`maven-metadata.xml`, read today); the newest
  release overall is a 2026.0.0 milestone.
- `.github/dependabot.yml:3-11` declares the Maven ecosystem with no `ignore` rule. The only update the bot can open for
  Reactor is the newest train, which the POM comment says to refuse - so Reactor is refused as a whole, patches
  included.

`reactor-core` is a runtime dependency shipped in the distributed JAR, under the MCP client that starts the MINOS and
NEXUS processes. Eighteen patch releases of the compatible line are not applied, and the mechanism meant to notice them
cannot.

## What Changes

- Reactor moves to the last release of the 2024.0 line.
- The update bot is told to ignore major and minor updates of the Reactor BOM, so that it proposes the patches of the
  compatible line and nothing else.
- A test holds the two together: a dependency the POM pins to a line "to stay compatible" has a matching bot rule.

## Capabilities

### New Capabilities

- `dependency-line-currency`: a dependency deliberately held on a release line still receives that line's patches, and
  the update bot is configured to propose exactly those.

### Modified Capabilities

(none)

## Impact

- `pom.xml` (one property), `.github/dependabot.yml`, `.claude/rules/build.md` (it restates the Reactor version),
  the generated `THIRD-PARTY-NOTICES` follows by itself.
- `distribution/third-party/components.tsv` needs no edit: a version bump is not a new component.
- The CVE gate scans the new version; `dependency:analyze` must stay at zero warning.

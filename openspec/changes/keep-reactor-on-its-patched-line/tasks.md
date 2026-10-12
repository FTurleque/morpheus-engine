# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Read the last release of the 2024.0 line on Maven Central. Proof: the value and the date; on 2026-10-12 it was
      2024.0.18.
- [ ] 1.2 Read which versions of `reactor-core` and `reactive-streams` that BOM brings. Proof: `dependency:tree` before
      and after.

## 2. Reproduce before fixing

- [ ] 2.1 The test of the second requirement. Proof: red on `HEAD` (no ignore entry).

## 3. The change

- [ ] 3.1 `pom.xml`: the property. Proof: `./mvnw dependency:analyze`, zero warning; the transport tests of the first
      requirement.
- [ ] 3.2 `.github/dependabot.yml`: the ignore entry. Proof: 2.1 passes; the workflow contract tests that read this
      file stay green.
- [ ] 3.3 `.claude/rules/build.md`: the restated version. Proof: the documentation gates.
- [ ] 3.4 Break once, then revert: delete the ignore entry. Proof: the named failure.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`, then the CVE scan (`-Pd2-security`, `-DautoUpdate=false`). Proof: green; no new finding
      at or above the threshold.
- [ ] 4.2 The Windows packaging: `THIRD-PARTY-NOTICES` states the new Reactor version without an edit. Proof: the line.
- [ ] 4.3 Pull request description: the finding, the measured versions, the justification for the bot and test edits.

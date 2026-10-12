# Tasks

Implemented on 2026-10-12 by pull request 452.

## 1. Measure before changing

- [x] 1.1 Read the last release of the 2024.0 line on Maven Central. Proof: the value and the date; on 2026-10-12 it was
      2024.0.18.
- [ ] 1.2 Read which versions of `reactor-core` and `reactive-streams` that BOM brings. Proof: `dependency:tree` before
      and after.
      *Not recorded: the two trees were not captured. The versions are stated by the generated `THIRD-PARTY-NOTICES`
      of the next packaging.*

## 2. Reproduce before fixing

- [x] 2.1 The test of the second requirement. Proof: red on `HEAD` (no ignore entry).
      *`DependencyLineCurrencyTest`, red before the change on the version and on the missing bot entry.*

## 3. The change

- [x] 3.1 `pom.xml`: the property. Proof: `./mvnw dependency:analyze`, zero warning; the transport tests of the first
      requirement.
      *The real-process transport tests ran in the required checks of the pull request, on Linux and Windows.*
- [x] 3.2 `.github/dependabot.yml`: the ignore entry. Proof: 2.1 passes; the workflow contract tests that read this
      file stay green.
- [x] 3.3 `.claude/rules/build.md`: the restated version. Proof: the documentation gates.
- [ ] 3.4 Break once, then revert: delete the ignore entry. Proof: the named failure.
      *Not replayed as a separate step: the red run of 2.1 is that state, the entry not existing yet.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`, then the CVE scan (`-Pd2-security`, `-DautoUpdate=false`). Proof: green; no new finding
      at or above the threshold.
      *Both by the required checks of the pull request (`exact-head` on two platforms, `dependency-check`), not locally.*
- [ ] 4.2 The Windows packaging: `THIRD-PARTY-NOTICES` states the new Reactor version without an edit. Proof: the line.
      *Not checked: no packaging has been built since the change.*
- [x] 4.3 Pull request description: the finding, the measured versions, the justification for the bot and test edits.

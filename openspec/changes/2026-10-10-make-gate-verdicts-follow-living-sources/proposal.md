# Proposal

## Why

The second half of the sprint-1 exit criterion of the 2026-10-09 audit is that **no validator renders PASS on a value
that is out of date**. Two findings break it, both re-read on `origin/develop` at `fe88c856` on 2026-10-10.

- **AUD-DEP-06 — `validate-d2` compares the test counts to literals, and the test that pins them cannot see a correct
  update.** `scripts/validate-d2.sh:110-115` (`TESTS < 820`, `ARCH_TESTS < 258`) and `scripts/validate-d2.ps1:132-133`
  hold floors that predate the living ratchets; `config/m21-quality-ratchets.properties` declares `testsMinimum` and
  `architectureTestsMinimum` at 3 820 / 585 (read on 2026-10-10 with `.claude/skills/live-numbers`). `grep -c
  m21-quality-ratchets` over `validate-d2.sh`, `validate-d2.ps1`, `validate-m21.sh`, `validate-m21.ps1` gives 0, 0, 1,
  1. A D2 run therefore passes a repository that has lost three thousand tests. The lock is worse than the lie it
  locks: `D2RepositoryHardeningArchitectureTest.java:111-112` asserts `script.contains("820")`, and `"3820".contains
  ("820")` is true — the test named `d2ScriptsKeepCurrentPresenceRatchets` would accept a script updated to the *correct*
  minimum, and would accept a script left at the stale one; it distinguishes neither.
  **The rule is wrong too.** `.claude/rules/governance.md:102-106` says `validate-m21.*` and `validate-d2.*` "lisent ce
  fichier et assertent le nombre de tests, le nombre de tests d'architecture, la couverture ligne/branche". `validate-d2`
  reads none of it. That is the divergence `.claude/rules/meta.md` asks to be signalled; it is corrected in this change.
  A second divergence sits one level down: the repository documents the D2 literals as intentional
  (`scripts/README.md:79-80`, "baseline Surefire >= 820") and pins that documentation
  (`RepositoryDocumentationCoherenceTest.operatorFacingD2GateDocumentationMirrorsWhatTheD2ValidatorsEnforce`, whose
  javadoc says the D2 validators "carry the baseline as a literal"). Two recorded intentions contradict each other; this
  change picks one and says why (`design.md`, decision 1).
- **AUD-TST-02 — the per-module coverage gate accepts a JaCoCo report older than the code it measures.**
  `CoverageQualityGateTest.java:270-288` classifies a module by the existence of its report and of `target/classes`
  only; the refusal test (`:184-204`) covers "never built" and "built without a report". The command the rules give for
  this gate, `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest` (`.claude/rules/testing.md:197`),
  reaches the unguarded state on any tree built once. CI is not affected — `scripts/validate-m21.sh:66` runs
  `./mvnw clean verify` itself — so the exposure is a **false assurance on a developer machine**, no more.
  Re-reading on `HEAD` adds two facts. The audit's remedy, comparing the report with the `target/classes` directory,
  would not work: measured on this checkout, `morpheus-domain/target/classes` has a modification time of 2026-10-08
  22:35 while its newest `.class` is from 2026-10-09 23:48 and its report from 2026-10-09 23:51 — a directory's time
  moves only when an entry is added or removed in it, not when a file below it is rewritten. And the aggregate gate
  (`AggregateCoverageGateTest.java:80-81`) is exposed identically: it checks `Files.isRegularFile(report)` and nothing
  else.

## What Changes

- No validator holds a literal copy of a value that `config/m21-quality-ratchets.properties` declares. `validate-d2.sh`
  and `validate-d2.ps1` read `testsMinimum` and `architectureTestsMinimum` from it, through one reader shared with
  `validate-m21.*`, and refuse with the key name and both values.
- The assertion that guards this stops being a substring test: the architecture suite refuses a numeric literal
  anywhere a D2 script compares an observed count, whatever digits it ends with, and requires the properties path and
  both keys to be present.
- The three mirrors of the old literals are removed with them (`scripts/README.md:79-80`, the D2 test method, the
  coherence test's regular expressions over `TESTS < n`); D2's two **absolute coverage floors** (0.40 / 0.35) stay and
  are named as floors.
- Both coverage gates refuse a JaCoCo report older than the newest source or test file of the module(s) it measures, and
  name that cause beside `never-built` and `built-without-report`.
- `.claude/rules/governance.md:102-106` is corrected now (it describes today's behaviour) and rewritten when the scripts
  change.

## Capabilities

### New Capabilities

- `gate-verdict-currency`: a validator or gate never renders PASS on a value older than its source of truth or older
  than the code it measures, and says which of its causes of refusal it hit.

### Modified Capabilities

(none)

## Impact

- `scripts/validate-d2.sh`, `scripts/validate-d2.ps1`, `scripts/validate-m21.sh`, `scripts/validate-m21.ps1`, and a new
  shared reader under `scripts/lib/`. Three existing tests pin strings of the M21 scripts and must keep passing:
  `CoverageScaleSeparationTest.java:90` (the scripts and their `lib/` helpers),
  `D2RepositoryHardeningArchitectureTest.java:97-102` (the literal path `config/m21-quality-ratchets.properties` and the
  version in `validate-m21.*`) and `RepositoryDocumentationCoherenceTest.java:383-394` (the aggregate key names in the
  script, and the absence of the old `read_ratchet lineCoverageMinimum`). The path and the key names therefore stay in
  each script and are passed to the shared reader.
- `D2RepositoryHardeningArchitectureTest` (one method replaced by a stricter one), `RepositoryDocumentationCoherenceTest`
  (the presence half of one method), `CoverageQualityGateTest`, `AggregateCoverageGateTest` (module
  `morpheus-coverage-report`).
- `scripts/README.md`, `.claude/rules/governance.md` (this change, one sentence now), `.claude/rules/testing.md`
  (lines 197-198), and a short amendment to `ADR-0104` (the `stale` cause on both scales).
- Governance files touched, to be justified in the pull requests as `rules/governance.md` requires: tests under
  `morpheus-architecture-tests/` (replaced and added), `.claude/rules/*`. No `config/*ratchets*.properties`, no
  `contracts/public-surfaces.tsv`, no `docs/openapi/*`.
- No CLI command, MCP tool or HTTP route moves. `config/m21-quality-ratchets.properties` is **read, not changed**:
  nothing here raises or lowers a ratchet.

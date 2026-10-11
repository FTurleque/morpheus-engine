# Design

## Context

Facts read from the repository at `fe88c856` on 2026-10-10:

- `validate-m21.{sh,ps1}` read `config/m21-quality-ratchets.properties` (a bash `read_ratchet` and a PowerShell
  `Get-M21QualityRatchets`, each refusing a missing key with "Missing M21 quality ratchet: <key>") and compare the
  Surefire totals to `testsMinimum` and `architectureTestsMinimum`. `validate-d2.{sh,ps1}` count the same Surefire
  totals the same way and compare them to the literals `820` and `258`. Both run `./mvnw clean verify` first.
- D2's coverage check (`validate-d2.sh` ~l.123-129) compares the aggregate ratios to the literals `0.40` / `0.35`, which
  `rules/testing.md` calls the "plancher D2": the historical absolute minimum, kept as a floor on purpose. The Java
  gates that D2's own `clean verify` runs (`AggregateCoverageGateTest`, `CoverageQualityGateTest`) already apply
  `max(floor, ratchet)`, so D2's coverage literal is shadowed by a stricter living check inside the same run. **No
  Java gate compares Surefire counts to the presence ratchets** — those comparisons exist in the scripts only.
- Two documents of intent disagree: `.claude/rules/governance.md:102-106` (D2 reads the file) against
  `scripts/README.md:79-80` and `RepositoryDocumentationCoherenceTest`'s `operatorFacingD2GateDocumentation…` (D2
  carries literals, pinned and mirrored on purpose; the guide had previously announced the M21 ratchets and "an operator
  reading it expected D2 to refuse a build that D2 accepts").
- `CoverageQualityGateTest.reportPopulation` and `AggregateCoverageGateTest` consult file existence only.

## Goals / Non-Goals

**Goals**
- A presence check in a validator moves with the file that declares it.
- The test that guards that cannot be satisfied by an accident of digits.
- A coverage gate does not conclude from a report it cannot show to be newer than the code.

**Non-Goals**
- Raising, lowering or requalifying any ratchet or ceiling (`coverage-ratchet` is not in play: no value changes).
- Making D2 a CI gate (`scripts/README.md`: it stays a local, specialised gate).
- A build-marker or git-hash scheme for coverage reports (decision 3).

## Decisions

1. **D2 reads the living file for the two presence minimums — not "rename the literals to a floor".**
   Both are defensible and the finding says so. What decides it:
   - Only one is compatible with `governance.md` as written. The other would need the rule rewritten to bless a second
     source of truth, against the principle of `rules/meta.md` ("tout chiffre présent dans `.claude/rules/*.md` est une
     copie, jamais l'original") and against the sprint criterion itself: a floor 4.6x below the ratchet still prints
     "D2 tests: PASS" for a repository that lost three thousand tests, and D2 is a manual qualification step with no
     CI behind it (it is not run by `ci.yml`).
   - The presence comparison has no Java twin, unlike coverage; if D2 does not do it, nobody does it in that gate.
   - It deletes surface instead of adding it: the literals in two scripts, `scripts/README.md:79-80`, the D2 test method
     and the coherence test's two regular expressions all disappear, replaced by one reader and one rule.
   Rejected alternative (B): keep `820` / `258`, rename the constants and `d2ScriptsKeepCurrentPresenceRatchets` to
   `…HistoricalPresenceFloor`, and correct `governance.md`. Cost S, honest in naming, consistent with the recorded
   intention of `scripts/README.md` and the coherence test — and it leaves a decorative check. **This reverses a
   recorded choice (the coherence test's javadoc), so it is flagged as an open question for the maintainer**; if B is
   preferred, tasks 2.x–3.x are replaced by the rename and one sentence of `governance.md`, and decisions 2–5 are
   unaffected.
   D2's **coverage floors stay literal and are named as floors** (`rules/testing.md` table "Plancher D2"): the stricter
   check that shadows them lives in the same run, and `rules/testing.md` and `D2RepositoryHardeningArchitectureTest`
   (`coverageRatchetCannotSilentlyReturnToTheD2Floor`) treat them as the floor they are. `governance.md` is corrected accordingly: D2 reads the presence keys and
   keeps a coverage floor.
   The reader is **extracted from `validate-m21`**, not copied a third time: `scripts/lib/` already holds the shared
   helpers for the coverage evidence (`require-aggregate-coverage-evidence.sh`, `Require-AggregateCoverageEvidence.ps1`).
   Three tests pin strings of the M21 scripts and constrain the extraction: `CoverageScaleSeparationTest.java:90`,
   `D2RepositoryHardeningArchitectureTest.java:97-102` (the literal path of the properties file and the version) and
   `RepositoryDocumentationCoherenceTest.java:383-394` (the aggregate key names present, the old `read_ratchet
   lineCoverageMinimum` absent). So the **path and the key names stay in each script** and are passed to the helper,
   which owns only the parsing and the refusal. A missing key, a non-integer value or a value below 1 refuses with the
   key's name (today's "M21 test ratchets must be positive integers" names no key and is rewritten); there is no default
   (`rules/code-style.md`). The helper strips a trailing carriage return: the properties file is CRLF in a Windows
   checkout (`* text=auto`), and `sed "s/^key=//p"` keeps the `\r`, which turns `(( TESTS < TESTS_MINIMUM ))` into an
   arithmetic error under Linux or WSL on that checkout (the defect `scripts/lib/python.sh` already documents for another
   case); Git Bash's `sed` happens to hide it, so only a test with an explicit CRLF input proves the fix. The new
   `.ps1` stays ASCII (`RepositoryTextHygieneContractTest`, `rules/tooling.md`). The PowerShell reader is **not exercised
   by CI** (`ci.yml` runs `validate-m21.sh` and the Python tests on Linux only, and the Windows lane runs
   `validate-m28.ps1`): it is verified by hand on Windows, which the delivery states instead of implying coverage.

2. **The guard rejects a literal minimum by pattern, requires the source by presence, and keeps the version assertion.** `contains("820")` fails the
   moment the assertion is read against the data. The replacement says what is meant: in `validate-d2.sh` and
   `validate-d2.ps1`, (a) the properties path and both keys appear, and (b) no comparison of an observed count — the
   `TESTS` / `ARCH_TESTS` variables of the shell script, `$tests` / `$architectureTests` of the PowerShell one — has an
   integer literal as its right operand, and (c) the assertion the replaced method also carried is kept: each D2 script
   still contains the current product version (`script.contains("1.2.1")` at `D2RepositoryHardeningArchitectureTest.java:113`),
   because `governance.md` forbids removing a rule without an equivalent or stricter one and no other test pins the
   version in these two scripts. (b) is stated on the operation, not on a value, so it keeps working at any
   future minimum. This is a text rule by nature (`.sh`, `.ps1`; `enforcement-choice`). Shown broken:

   ```text
                                                   old contains("820")   new literal rule
   the script as it is today (TESTS < 820)               passes              FAILS
   the script "fixed" to the right value (< 3820)        passes              FAILS
   the script reading the file (< TESTS_MINIMUM)         FAILS               passes
   ```

   (Replayed with a throw-away script on 2026-10-10; the old assertion accepts both wrong scripts and rejects the right
   one.) The rule must be written so a variable renamed in a script makes it fail loudly rather than pass on an empty
   match — assert that each script contains at least one comparison of an observed count at all.

3. **Staleness is measured against the module's sources and tests, not against `target/classes`.**
   The audit proposes comparing the report with the `target/classes` of the module. Measured on this checkout:

   ```text
   morpheus-domain/target/classes   (directory)        2026-10-08 22:35:11
   newest .class under it                              2026-10-09 23:48:11
   morpheus-domain/target/site/jacoco/jacoco.xml       2026-10-09 23:51:02
   ```

   A directory's time changes only when an entry is created, removed or renamed in it, not when a class file below it
   is rewritten, so the report would always look newer than the directory: the check would pass on exactly the trees it
   exists to catch. Alternatives considered:
   - *newest class file* — better, but a recompilation (`-pl <module> test`) rewrites classes without regenerating the
     report (`jacoco:report` is bound to `verify`), and recompiling unchanged sources is not a staleness;
   - *the JaCoCo `.exec` file's time* — answers "was the report generated after the last test run", not "do the sources
     still match the report";
   - *a build marker or a git hash stamped by the reactor* — needs plugin configuration and a second source of truth;
   - **chosen: the newest regular file under `src/main/java` and `src/test/java` of the module.** Sources are the
     authority; tests count because removing a test lowers coverage while leaving the main sources untouched, and a
     report that predates that removal would hide the drop.
   Accepted cost: a `git checkout` or `git stash pop` that rewrites unchanged content refuses a valid report. The refusal
   says what to run (`./mvnw clean verify`); a false refusal is cheap and a false pass is the defect.
   Timestamps are compared strictly (`report < newest`); tests set modification times explicitly
   (`Files.setLastModifiedTime`) and never sleep to let the clock tick — the repository already lost a test to that
   (`93da6280`, "waits for the file system clock to tick").
   The refusal names a third cause, `stale`, beside `never-built` and `built-without-report`, with the module and the two
   times, and the same instruction (`./mvnw clean verify`).

4. **The aggregate gate gets the same guard (flagged as an extension).** The finding names the per-module gate. The
   aggregate gate (`morpheus-coverage-report`) concludes on `jacoco-aggregate/jacoco.xml` after only an existence check;
   it is the canonical scale (`rules/testing.md`) and `CoverageScaleSeparationTest` exists precisely so the two scales
   do not diverge in rigour. Its reference is the newest source or test file across the modules of its
   population **and `morpheus-architecture-tests`**, whose tests execute across modules and feed the aggregate report even
   though it carries no main class and is outside the population. Its gate runs only in `verify`
   (`morpheus-coverage-report/pom.xml`: `default-test` is skipped, the gate is an execution bound to `verify`), so
   `rules/testing.md:198`, which documents `./mvnw test -pl morpheus-coverage-report`, is corrected and the proofs use
   `verify`.
   scenario of the specification covers it and can be struck without touching the rest. Implemented in each module
   (a few lines duplicated) rather than in a shared module: a new dependency edge to share test support is what the
   repository avoids (`duplication over gated boundaries`).

5. **`governance.md:102-106` is corrected now, in the pull request that introduces this change.** The sentence is false
   today; leaving it until delivery leaves a false rule in the tree for the length of the lot. The new sentence states
   today's behaviour without numbers (so no coherence test can pin a second copy) and the delivery commit rewrites it to
   the post-change behaviour — task 4.1 says so, to keep the rule from becoming false in the other direction.

## Verification of the proposed rules (broken once)

Replayed on 2026-10-10 on the working tree without modifying it (see decision 2 for the literal rule). For the
staleness rule the measured timestamps of decision 3 show the audit's reference failing and the chosen one succeeding:
the newest source of `morpheus-domain` (`src/main/java` 2026-10-09 21:10:32, `src/test/java` 2026-10-09 21:10:32) is
older than its report (2026-10-09 23:51:02) on this tree — the fresh case — and touching one source file would put it
ahead, which is the refusal case. The corresponding tests set the times explicitly.

## Non-duplication check

Done by hand against the eight capabilities of `openspec/specs/` on 2026-10-10. None mentions ratchets, coverage, Surefire
counts or JaCoCo reports. The three new requirements (a validator reads the presence ratchets from the living file; a
coverage gate refuses a report older than the sources; a stale report is refused under its own named cause) are absent from
all eight. `sqlite-schema-history`'s "Released migrations are pinned" is the nearest in spirit (a value pinned by the
build) but concerns migration checksums.

## Supervision objections and their treatment

Two read-only reviewers read the four changes on 2026-10-10: the `architect` agent (layer rules, existing tests, feasibility) and the `contract-guardian` agent (public surface, ADR need). Neither found a public surface touched, a layer rule violated, or a duplicate of the eight existing capabilities. Their objections and what became of each:


| # | Objection (reviewer) | Treatment |
|---|---|---|
| 1 | Extracting the reader risks `D2RepositoryHardeningArchitectureTest:97-102` and `RepositoryDocumentationCoherenceTest:383-394` (both) | **Accepted.** Path and key names stay in each script (decision 1, task 2.1, proposal Impact) |
| 2 | Replacing `d2ScriptsKeepCurrentPresenceRatchets` drops the `1.2.1` assertion (both) | **Accepted.** Decision 2(c), task 1.1, spec scenario |
| 3 | CRLF properties file breaks the shell reader under Linux/WSL (architect) | **Accepted.** Decision 1, task 2.2 (explicit CRLF case), spec scenario |
| 4 | The PowerShell half has no CI lane; the refusal message names no key (architect) | **Accepted.** Messages rewritten; the `.ps1` half is stated as verified by hand |
| 5 | Aggregate reference ignores `morpheus-architecture-tests`; the documented aggregate command skips the gate (architect) | **Accepted.** Reference extended; `testing.md:198` corrected; proofs use `verify` |
| 6 | ADR-0104 should receive a short amendment (convergence) | **Accepted.** ADR section, task 3.5 |
| 7 | The `governance.md` sentence is accurate but provisional and omits D2's version and floors (convergence) | **Accepted.** Sentence extended; rewritten at delivery (task 4.1); open question if option B is chosen |
| 8 | New `.ps1` must stay ASCII (convergence) | **Accepted.** Task 2.1 |
| 9 | PR must justify governance files and replaced rules (convergence) | **Accepted.** In the PR description |


## Risks / Trade-offs

- [D2's presence check becomes as strict as M21's and a D2 run on an older SHA fails] → intended; D2 qualifies a commit
  against the ratchets of that commit's tree, because the file is read from the checkout under test.
- [Extracting the M21 reader touches a gate that works] → `CoverageScaleSeparationTest` and
  `RepositoryDocumentationCoherenceTest` pin the M21 scripts; the extraction is verified by `validate-m21` on both
  platforms before and after, with identical output lines.
- [A freshness check could refuse a valid report on a developer machine after a checkout] → the message names the cause
  and the command; CI (`clean verify`) cannot be affected, since sources predate the build.
- [The aggregate guard on a population of 16 modules costs a directory walk] → bounded by the number of source files
  (the audit counts 1 185 `.java` files in the whole repository); negligible next to the JaCoCo parse.

## ADR

A short dated amendment to ADR-0104, no new ADR (the convergence reviewer's verdict, after reading the ADR): ADR-0104
enumerates how each scale refuses a report that does not measure the population ("Assumé"); `stale` is a third refusal on
both scales, and a reader of the ADR would otherwise take the list as closed. The amendment follows the form of the existing
ones (`## Amendement du <date>`), keeps the verdict "Acceptée" and therefore leaves the ADR index unchanged
(`AdrIndexCoherenceTest` compares verdicts). The numbering was read from `docs/adr/` (109 records, highest `0109`); nothing is
attributed.

## Open Questions

- Decision 1: confirm A (D2 follows the living file) over B (named historical floor).
- If option B of decision 1 is chosen, the sentence added to `governance.md` by this pull request becomes permanent and its
  clause "tant que le correctif n'est pas livré" becomes false: rewrite it then.
- Decision 3: should `pom.xml` changes also count as "newer than the report"? Excluded here — a dependency edit that
  changes coverage also changes sources or tests in practice — but it is a one-line addition if wanted.

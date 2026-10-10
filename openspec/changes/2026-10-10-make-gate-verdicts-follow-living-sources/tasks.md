# Tasks

Implementation is a separate lot, started after this specification is reviewed and decision 1 of `design.md` (D2
follows the living file) is confirmed by the maintainer. Nothing below is done by the pull request that introduces this
change, except the one-sentence correction of `governance.md` named in 4.1.

## 1. Reproduce before fixing

- [ ] 1.1 `D2RepositoryHardeningArchitectureTest`: add the failing guard of decision 2 beside the method it replaces
      (the current product version still present in each D2 script, as `:113` asserts today; properties path and both keys
      present in each D2 script; no integer literal as the right operand of a comparison
      of `TESTS` / `ARCH_TESTS` / `$tests` / `$architectureTests`; at least one such comparison exists so the rule
      cannot pass on an empty match). Proof: it fails on `HEAD` naming `validate-d2.sh` line 110 and 114 and
      `validate-d2.ps1` lines 132 and 133.
- [ ] 1.2 Break the new guard once, in a scratch copy, three ways, and observe: the script as it is (fails), the
      script edited to `< 3820` (fails — and `contains("820")` passes it), the script reading the file (passes).
      Proof: the three outcomes pasted in the pull request, as in `design.md` decision 2.
- [ ] 1.3 `CoverageQualityGateTest`: write `aReportOlderThanTheSourcesItMeasuresIsRefused` beside the existing
      population test (`:184-204`) using the same synthetic reactor, setting modification times with
      `Files.setLastModifiedTime` (never a sleep). Cases: stale main source, stale test source, nested package edit with
      an older `target/classes` directory, fresh report accepted, the three causes listed apart. Proof: the stale cases
      fail on `HEAD` because `reportPopulation` accepts the report.
- [ ] 1.4 `AggregateCoverageGateTest`: the aggregate counterpart of 1.3 on its synthetic reactor (`:176-188`). Proof:
      fails on `HEAD`.

## 2. D2 follows the living ratchets

- [ ] 2.1 Extract the parsing of `validate-m21` into `scripts/lib/` — `read-quality-ratchets.sh` and
      `Read-QualityRatchets.ps1`, the latter ASCII — keeping the properties path and the key names in each calling script
      and passing them in, because three tests pin those strings (`CoverageScaleSeparationTest.java:90`,
      `D2RepositoryHardeningArchitectureTest.java:97-102`, `RepositoryDocumentationCoherenceTest.java:383-394`). Refuse with
      `Missing M21 quality ratchet: <key>` and, for a non-integer or non-positive value, a message that names the key
      (today's text names none). Strip a trailing carriage return. Proof: those three tests unchanged and green;
      `RepositoryTextHygieneContractTest` green; `validate-m21` output lines identical before and
      after on the same SHA, on both platforms.
- [ ] 2.2 Add a Python `unittest` in `scripts/tests/` for the shell reader (temporary properties file: ok, missing
      key, non-integer, zero, **CRLF line endings**). `ci.yml` already runs `python3 -m unittest discover -s scripts/tests`
      on Linux. Proof: passes on Linux CI, and fails on the CRLF case when the carriage-return stripping is removed. The
      PowerShell reader has no CI lane: run the same cases by hand with `powershell -File` on Windows and record that this
      half is verified manually.
- [ ] 2.3 `validate-d2.sh` and `validate-d2.ps1`: replace the two comparisons by the shared reader; message
      `D2 test baseline regression: <observed> < <required> (testsMinimum)` and the architecture twin. Keep the
      coverage floors (`0.40` / `0.35`) as they are and label them `floor` in the PASS line. Proof: 1.1 passes; with
      `testsMinimum` raised past the observed count in a throw-away commit and
      `MORPHEUS_D2_SKIP_SECURITY_SCAN=true MORPHEUS_D2_SKIP_PORTABLE=true`, `validate-d2` refuses on both platforms,
      naming the key; revert.
- [ ] 2.4 Remove the three mirrors of the old literals: `scripts/README.md:79-80` (the D2 block states that the
      presence minimums are those of `config/m21-quality-ratchets.properties`), `d2ScriptsKeepCurrentPresenceRatchets`
      (replaced by 1.1), and the two `TESTS < n` / `ARCH_TESTS < n` regular expressions of
      `RepositoryDocumentationCoherenceTest.operatorFacingD2GateDocumentationMirrorsWhatTheD2ValidatorsEnforce`, which
      keeps its coverage-floor half. Proof: `./mvnw test -pl morpheus-architecture-tests` green; `grep -rnE "\b(820|258)\b"
      scripts docs/developer` returns nothing (the dated account in `.claude/rules/meta.md:5` stays as written).

## 3. Coverage gates refuse a stale report

- [ ] 3.1 `CoverageQualityGateTest.reportPopulation`: add the `stale` cause (newest regular file under `src/main/java` and
      `src/test/java` of the module, strictly later than the report), carried by `ReportPopulation` and named in
      `assertComplete` next to the existing two causes, with both times and `./mvnw clean verify`. Proof: 1.3 passes;
      the two existing causes' assertions are untouched and pass.
- [ ] 3.2 `AggregateCoverageGateTest`: the same guard against the sources and tests of every population module and of
      `morpheus-architecture-tests`, placed before the evidence file is written. Proof: 1.4 passes (run through
      `./mvnw verify`: the gate is bound to `verify` in `morpheus-coverage-report/pom.xml` and `default-test` is skipped
      there).
- [ ] 3.3 On a real tree: `./mvnw clean verify` then `./mvnw test -pl morpheus-architecture-tests
      -Dtest=CoverageQualityGateTest` passes; touch one `src/main/java` file and rerun the same command: it refuses with
      `stale`; `./mvnw clean verify` restores the pass. Proof: the three outputs in the pull request. Record, per
      `coverage-ratchet`, that no ratchet or ceiling moved.
- [ ] 3.4 `.claude/rules/testing.md:197-198`: the per-module line says the gate now refuses a report older than the
      sources and what to run then; the aggregate line, which documents `./mvnw test -pl morpheus-coverage-report`, is
      corrected to the `verify` invocation that actually runs that gate.
- [ ] 3.5 `docs/adr/0104-two-coverage-scales-share-one-population.md`: add a dated `## Amendement du <date>` of a few lines
      (`stale` as a third refusal on both scales, reference = newest source or test file, not the `target/classes`
      directory), verdict unchanged. Proof: `AdrIndexCoherenceTest` green without touching the index.

## 4. Rules that described the code wrongly

- [ ] 4.1 `.claude/rules/governance.md:102-106` — corrected by the pull request that introduces this change to state
      today's behaviour (D2 does not read the file). At delivery, rewrite it once more, without the temporal clause: `validate-m21.*` and
      `validate-d2.*` read `testsMinimum` and `architectureTestsMinimum`; coverage is concluded by the Java gates and by
      `validate-m21.*`, `validate-d2.*` keeping a coverage floor. Do not copy numbers into it (`rules/meta.md`).
- [ ] 4.2 Run `RepositoryDocumentationCoherenceTest` and `ProductionIntegrityContractTest` and let the failures list any
      destination that still describes the old literals (the method `rules/meta.md` prescribes). Proof: green.
- [ ] 4.3 Integration on both platforms: `scripts\validate.cmd m21 -Version <current>` on Windows and
      `bash ./scripts/validate-m21.sh <current>` on Linux, both PASS on the same SHA.

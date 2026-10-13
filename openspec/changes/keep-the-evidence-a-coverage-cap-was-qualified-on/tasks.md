# Tasks

## 0. Before 23 October 2026, whatever else happens

- [ ] 0.1 Download the `m21-integrity-Linux` and `m21-integrity-Windows` artifacts of runs `37925178678` and
      `37925186306` (`gh run download <id> -n m21-integrity-<OS>`) and keep the two coverage summaries of each. Proof:
      eight files, each beginning with its `coverageScope=` line. `ci.yml` keeps an artifact 14 days: check the run's
      date before trusting this deadline.

## 1. Measure before changing

- [ ] 1.1 Read the four caps and the four ratchets from their sources, and the retention of the artifact from
      `ci.yml`. Proof: the values with file and line. Do not copy them from this change or from a rule file
      (`live-numbers`).
- [ ] 1.2 Compare each cap with the lowest ratio of the summaries of 0.1. Proof: the four comparisons. A difference is a
      finding: stop and report it before writing any test.
- [ ] 1.3 Check that the eight summaries name one commit, and that the four aggregate ones name one population. Proof:
      the values. If the summaries do not carry the commit, the lot says how it is recorded beside each file.
- [ ] 1.4 If 0.1 was too late: requalify on the current head by the procedure of `coverage-ratchet`, expecting no
      change of value, and use that evidence.

## 2. Reproduce before fixing

- [ ] 2.1 Write the comparison of the caps with kept evidence, with no evidence in the tree. Proof: red, saying that
      the caps rest on nothing a build can read.
- [ ] 2.2 Write the completeness rule against fabricated sets: one platform missing, two commits, two populations, a
      summary of the other scale. Proof: four named failures.

## 3. The change

- [ ] 3.1 Add the evidence at the location the maintainer chose (open question 1; recommended `config/`). Proof: 2.1
      passes with the caps unchanged.
- [ ] 3.2 Enforcement: textual and numeric assertions over files outside the classpath — text is the only hold
      (ADR-0103). Intent: "a cap that differs from the lowest kept measurement of its scale must not pass". Keep
      `CoverageScaleSeparationTest` green: no gate reads a key, a report or evidence of the other scale.
- [ ] 3.3 Break each rule once, then revert: raise one cap by one unit of its last digit; point a cap at the highest
      ratio; delete the Windows summaries; swap in a summary of the other scale. Proof: the named failures.

## 4. Verification and destinations

- [ ] 4.1 `./mvnw clean verify` on both platforms. Proof: green, the six ratchet values and the four caps unchanged.
- [ ] 4.2 Amend ADR-0104 (`adr-author`): the requirements of a cap raise include the kept evidence and the equality.
- [ ] 4.3 `docs/developer/BUILD_AND_TEST.md`: the two kinds of raise and where the three values are read, with no
      headroom figure. `RepositoryDocumentationCoherenceTest` must stay green.
- [ ] 4.4 `.claude/rules/testing.md`: replace the statement that the proof stays consultable in the artifacts by where
      it is kept. `.claude/skills/coverage-ratchet/SKILL.md`: the step that files the evidence of a requalification.
- [ ] 4.5 Pull request description: the finding, the comparisons of 1.2, the location chosen, the justification of each
      governance file touched.

# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 The per-module table, with no build: for the head of `develop`, download `m21-integrity-Linux` and
      `m21-integrity-Windows` of the `push` run and of the promotion pull request's run
      (`gh run list --workflow ci.yml --commit <sha>`, procedure of the `coverage-ratchet` skill; the artifacts are
      kept fourteen days). For each module of the population: lines, branches, covered by its own report, covered in
      its group of the aggregate report, the difference. Proof: the table, four runs, the SHA and the run numbers.
      This specification measured none of it.
- [ ] 1.2 From the same reports, the wholly borrowed classes of every module, on each platform. Proof: the list, and
      the classes whose status differs between the two platforms, with the test that explains each. State whether the
      seven classes the audit names are in it.
- [ ] 1.3 Check the two premises of the design on the real reports: the two reports count the same lines and branches
      for every module, and no module's own report covers more than the aggregate. Proof: the comparison; a module
      that breaks either is reported before anything is built on it.
- [ ] 1.4 The spread of the total between the two runs of a platform and between platforms, in lines and in branches.
      Proof: the four totals. Compare with the reference spread the coverage ratchets are sized against
      (`.claude/rules/testing.md`), re-read with the `live-numbers` skill.
- [ ] 1.5 Rank the classes of `morpheus-application` by borrowed lines. Proof: the ranking - it replaces the audit's
      name search as the work order. Re-count on the day: 31 classes named by no test, 343 source files.

## 2. Reproduce before fixing

- [ ] 2.1 The third gate in `morpheus-coverage-report`, reading both reports and writing its evidence, with the list of
      1.2 empty and no maximum. Proof: red on `HEAD`, naming the classes of 1.2.
- [ ] 2.2 Its refusals, each on a reactor assembled on purpose with explicit file times, as the two coverage gates
      test theirs: a module missing on one scale, a stale report, two reports that count different lines, an own
      report that covers more than the aggregate. Proof: one named failure per cause.

## 3. The change

- [ ] 3.1 Replace ADR-0104 by a consolidated ADR: the third quantity, its definition in counts, the direction of its maximum, its
      qualification on the highest measurement of both platforms, and the interdiction to derive a coverage threshold
      from it. Check the highest number attributed before assigning the next one (`live-numbers`).
- [ ] 3.2 The list of wholly borrowed classes, exact, with the platform-dependent group only if 1.2 found a member.
      Mechanism: an assertion on the reports, since the property is about measured execution and no rule on bytecode
      can state it. Proof: 2.1 passes.
- [ ] 3.3 The two keys of the maximum in `config/m21-quality-ratchets.properties`, the qualified measurement of 1.1 in
      the gate with its SHA and runs, the margin of 1.4 computed in lines and in branches. Proof: the gate passes on
      the four runs' reports dropped in a worktree.
- [ ] 3.4 Extend `CoverageScaleSeparationTest` to the third gate, textual on the three gate sources - intent: *these
      key and file names must not appear in that gate*. Add the borrowed-credit evidence to the scope guard test of
      the M21 validators, both platforms. Proof: both tests pass; the validators are unchanged.
- [ ] 3.5 Add the evidence file to the `m21-integrity-<OS>` artifact in `ci.yml`. Proof: the artifact of the pull
      request's own runs, both platforms.
- [ ] 3.6 Tests in `morpheus-application/src/test`, in the order of 1.5, against hand-written doubles of the ports and
      with no change to `morpheus-application/pom.xml`. Each failure case by `assertThrows` and an assertion on the
      message. Leave `WorkspacePathBoundary` and `ObservedProjectSnapshotPublisher` where sprint 11's decision on
      AUD-ARC-01 puts them. Proof: each class tested leaves the list of 3.2 in the same commit.
- [ ] 3.7 Break once, then revert: (a) delete one of the new tests - the build names the class as unlisted; (b) set the
      maximum under the qualified measurement - refused; (c) make the third gate read `perModuleLineCoverageMinimum`
      - the separation test names it. Proof: the three failures.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` on the whole reactor; changed-line coverage gate. Proof: green, and the three evidence
      files each with its own first line.
- [ ] 4.2 The four coverage keys and the four `*_QUALIFIED_*_RATIO` constants are byte-identical to `develop`. Proof:
      the diff.
- [ ] 4.3 Run `RepositoryDocumentationCoherenceTest` and `ProductionIntegrityContractTest` and let their failures
      enumerate the destinations of the new keys. Then `docs/developer/BUILD_AND_TEST.md`,
      `.claude/rules/testing.md` (the third quantity; the memory-store rule does not apply inside
      `morpheus-application`) and `.claude/skills/coverage-ratchet/SKILL.md` (a maximum is lowered, not raised).
      No figure in any of them.
- [ ] 4.4 Linux and Windows: the evidence of the pull request's two runs. Proof: the borrowed credit of
      `morpheus-application` before (1.1) and after, per platform - the measurement a later lowering of the maximum
      cites. The maximum is not lowered in this pull request.
- [ ] 4.5 Pull request description: the finding, the two corrections (the reactor cycle, the ceiling), the table of
      1.1, and the justification the governance rules require for the ratchet file and the architecture tests.

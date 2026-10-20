# Tasks

## 1. Measure before changing

- [x] 1.1 List, for every tag, the last migration it ships (`git ls-tree --name-only <tag>
      morpheus-store-sqlite/src/main/resources/db/migration/`). Proof: the table; on 2026-10-13, `v1.0.0` 12, `v1.1.0`
      15, `v1.2.0` 15. Do not trust this line: re-read the tags.
- [x] 1.2 List, for every migration after the lowest released level, the tables it reads or rewrites and whether it acts
      on existing rows. Proof: the table; on 2026-10-13, V016 and V017 `specification_versions`, V018 `policy_packs` and
      `policy_pack_versions`, V019 `sync_state`, V020 `composition_conflict`.
- [x] 1.3 For each of those tables, read the insert statement of the store at the tag of the baseline
      (`git show v1.2.0:<store file>`). Proof: the statements, cited in the test next to the rows they justify.
- [x] 1.4 List the release-notes files and `ProductMetadata.version()`. Proof: the two sets the rule of 3.3 compares.

## 2. Reproduce before fixing

- [x] 2.1 Write the level-15 baseline with rows in the tables of 1.2 and open it with the current store. Proof: the
      result on `HEAD`, whichever it is. A failure is a defect of the upgrade path: stop, report it, and fix it in its
      own change with this test as the reproduction.
- [x] 2.2 Write the rule of 3.3 against a fabricated tree in which a released version has no level. Proof: red, naming
      the version.

## 3. The change

- [x] 3.1 Run the upgrade test once per declared level (12 and 15 today), sharing the assertions and restricting the
      rows to the tables the level has. `R2UpgradeCompatibilityTest` becomes that test or is replaced by it; its
      assertions on identity, history, checksums and the second opening are kept.
- [x] 3.2 Add the read-back through the stores and one write per constrained family (decisions 2 and 3 of the design).
      Proof: each assertion names the operation of the store it goes through.
- [x] 3.3 Declare the released levels once and add the completeness rule. Mechanism: a comparison of two sets, one of
      them read from file names under `docs/user/release-notes/` — textual by nature (ADR-0103), intent: "a released
      version without a declared level must not pass". Suggested home: beside the test that ties the upgrade guide to
      the schema constant.
- [x] 3.4 Break each rule once, then revert: make V019 fail on a non-empty `sync_state`; drop a row in V017; remove the
      declaration of one released version; declare a level above the constant. Proof: the four named failures.

## 4. Verification and destinations

- [ ] 4.1 `./mvnw clean verify` on Windows and on Linux (the pull request's required checks). Proof: green on both.
- [x] 4.2 If the presence ratchets are raised, raise them from the executions measured on both platforms and follow
      `coverage-ratchet` and `live-numbers`; run `RepositoryDocumentationCoherenceTest` and
      `ProductionIntegrityContractTest` and let their failures name the destinations.
- [x] 4.3 One sentence in the developer documentation of the store and one step in
      `docs/project/validation/RELEASE_QUALIFICATION.md`: a release declares its level in the version that follows it.
- [ ] 4.4 Pull request description: the finding, the table of 1.1, what the level-15 run found, and the limit of the
      proof (rows written by the test, not by the released program).

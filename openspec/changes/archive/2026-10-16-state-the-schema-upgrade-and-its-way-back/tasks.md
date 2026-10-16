# Tasks

Specified by pull request 437; implemented on `feat/state-the-schema-upgrade-and-its-way-back`.

## 1. Questions only the maintainer can answer

- [x] 1.1 Is a direct upgrade from 1.1.0 to 1.2.1 supported, or must 1.2.0 be installed in between? Proof: the answer,
      recorded in the guide's "from which version" paragraph.
  - 2026-10-11: not answered - the maintainer's call. The guide says it was written for a start from 1.2.0 and claims nothing about a direct upgrade from 1.1.0.
  - 2026-10-11 (follow-up): the guide now states the fact rather than a support promise - a 1.1.0 database is at the
    same schema V015 as a 1.2.0 one (`git show v1.1.0` lists fifteen migrations), so the migrations, the backup and the
    way back are the same; the path was not qualified separately. Whether it is *supported* remains the maintainer's word.
  - 2026-10-16: the maintainer decided: a direct upgrade from 1.1.0 is supported, without installing 1.2.0 in between. The guide says so.
- [x] 1.2 Confirm the location operators should keep the pre-upgrade backup in (the `backups` root of the layout, or
      anywhere outside the installation directory). Proof: the answer, in the guide.
  - 2026-10-11: not answered. The guide names the layout's `backups` directory, where `server backup create` writes by default, and advises a second copy elsewhere.
  - 2026-10-16: the maintainer decided: the layout's `backups` directory, plus a second copy outside the installation directory. The guide says so.

## 2. Reproduce before fixing

- [x] 2.1 Add the coherence test (suggested home: `RepositoryDocumentationCoherenceTest`, or a sibling in the same
      package) with four methods, one per requirement. Proof: all four fail on `HEAD` — the latest guide is
      `UPGRADE_1_2.md`, which states V015, makes the backup optional (`:24`), quotes no refusal, and
      `UPGRADE_1_1.md` carries no notice.
  - 2026-10-11: `UpgradeGuideCoherenceTest`, a sibling of `RepositoryDocumentationCoherenceTest` (already over a thousand lines). Three methods red on `HEAD` (`b8661dd4`): V015 against 20, no mandatory-backup section, no refusal quoted. The fourth (earlier guides) was green on `HEAD` because `UPGRADE_1_1.md` already linked `UPGRADE_1_2.md`; it turned red by itself once the new guide existed.
- [x] 2.2 Prove the selection is not vacuous: assert the set of `UPGRADE_*.md` is non-empty and that the version ordering
      is numeric (`1_2_1` after `1_2`, `1_10` after `1_9`). Proof: a unit test of the ordering inside the test class.
  - 2026-10-11: `guideVersionsAreOrderedNumericallyNotAlphabetically`; `latest` throws on an empty set.
- [x] 2.3 Reproduce the trap the guide warns about, once, by hand or in a store test: open a frozen V012 fixture
      (`R2UpgradeCompatibilityTest` holds one) through `createBackup`, and read the backup's
      `MAX(version)`. Proof: it equals `SUPPORTED_SCHEMA_VERSION`, not 12. Keep the test if it is cheap; it documents
      the behaviour `back-up-before-a-schema-migration` builds on.
  - 2026-10-11: not done here. The behaviour is read in `SqliteServerMaintenance.createBackup` and is the subject of `back-up-before-a-schema-migration`, whose store tests are the right place for it.
  - 2026-10-13: `SqlitePreMigrationCopyTest#aServerBackupOfAnOutdatedDatabaseIsAlreadyMigratedAndOnlyTheKeptCopyIsNot` - `createBackup` on a schema-12 database returns a backup at `SUPPORTED_SCHEMA_VERSION`, and the copy kept before the migration is the only file still at 12. Kept: it is cheap and it is what the guide says.

## 3. The change

- [x] 3.1 Write `docs/user/UPGRADE_1_2_1.md` in French, like its siblings: scope and source versions; schema reached;
      **mandatory backup with the installed version** (stop, `server backup create`, `server backup verify`, where to
      keep it, why a later backup does not help); installation per platform (Windows setup, Windows ZIP, Linux
      tar.gz); first start and what to check; **way back** (the refusal quoted, the offline restore, what is lost);
      diagnostics (history mismatch: do not edit `schema_migrations`). Proof: 2.1 passes for the three content
      requirements.
  - 2026-10-11: written. **Corrected while writing**: the published 1.2.0 has no refusal of a future schema on opening (added by `fc2a3c07` on 2026-08-12). The guide says 1.2.0 does not refuse a migrated database and forbids starting it on one; a sixth test method pins that on `UPGRADE_1_2_1.md` by name, and the proposal, design and spec of this change record it.
- [x] 3.2 Add the opening notice to `UPGRADE_1_2.md` and `UPGRADE_1_1.md`; bound `UPGRADE_1_2.md:108` to 1.2.0. Leave
      every other dated sentence as it is. Proof: 2.1's fourth method passes; `git diff` shows only the notice and that
      one sentence.
  - 2026-10-11: notices added to both guides; the rollback sentence of `UPGRADE_1_2.md` is bounded to the step between 1.1.0 and 1.2.0.
- [x] 3.3 Link the guide from `docs/user/README.md` and from `docs/user/release-notes/RELEASE_NOTES_1.2.1.md` (one line each).
      Proof: `RepositoryDocumentationCoherenceTest` and the link checks stay green.
  - 2026-10-11: `docs/user/README.md`, `docs/user/release-notes/RELEASE_NOTES_1.2.1.md` (the notes live there, not under `docs/release/` as this task said) and one pointer in `INSTALLATION.md`, whose update section still described the schema as unchanged.
- [x] 3.4 Break each rule once, then revert: raise the number in the guide by one; remove the refusal quote; remove a
      notice. Proof: three named failures, in the pull request.
  - 2026-10-11: three named failures: `states V021 ... SUPPORTED_SCHEMA_VERSION is 20`; `must quote the refusal`; `UPGRADE_1_1.md ... must link UPGRADE_1_2_1.md`. Each reverted.

## 4. Verification

- [x] 4.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green; the
      new methods are counted (`architectureTestsMinimum` only rises).
  - 2026-10-11: 615 tests, 0 failure, on Windows, offline, against the modules installed in the local Maven repository. `architectureTestsMinimum` is not raised by this change.
- [x] 4.2 Every command the new guide names resolves (`gate-reachability`, "Documentation that is current SHALL name
      only commands that resolve"). Proof: the existing gate, green.
  - 2026-10-11: `GateReachabilityArchitectureTest` green in the same run.
- [x] 4.3 Pull request description justifies the test added under `morpheus-architecture-tests/` (governance file).
  - 2026-10-11: in the description of the pull request that carries this lot.

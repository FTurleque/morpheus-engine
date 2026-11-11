# Design

## Context

- The store migrates on open, inside one transaction (`SqliteSchemaManager.migrate`), and records one ledger row per
  migration with a checksum. `SqliteMigrationChecksumGoldenTest.CANONICAL_CHECKSUMS` pins the checksum of every
  migration as a literal, and `R2UpgradeCompatibilityTest` already writes those pinned values into the ledger of its
  baseline rather than recomputing them (`:134-150`).
- Released levels, read from the tags on 2026-10-13: 12 (`v1.0.0`), 15 (`v1.1.0`, `v1.2.0`). The level of the version
  being released is whatever `SUPPORTED_SCHEMA_VERSION` says on the tagged commit.
- The repository's evidence of a released version, readable without Git history, is
  `docs/user/release-notes/RELEASE_NOTES_<version>.md` (1.1.0, 1.2.0 and the version in progress today; 1.0.0 has none).
  CI checks out without tags, so a test cannot ask Git which versions exist.
- What a database at level 15 can hold and a later migration touches: `specification_versions` (V016, V017),
  `policy_packs` and `policy_pack_versions` (V018), `sync_state` (V019), `composition_conflict` (V020).

## Goals / Non-Goals

**Goals**: the upgrade from each released level is executed on rows, and what the rows meant is read back through the
store's own API; a release cannot be followed by a version that forgot to declare the level it shipped.

**Non-Goals**: the levels no release shipped (13, 14, 16 to 19) — a database at one of them exists only on a
developer's machine, and the single-migration tests cover each step; a downgrade; rewriting the single-migration tests;
the copy taken before migrating (`back-up-before-a-schema-migration`).

## Decisions

1. **One baseline per released level, built from the migrations that release shipped.** Migrations 1 to N are applied
   from the resources, and the ledger receives the pinned checksums, as today. *Rejected*: a binary database file
   produced by each released program and committed — it is the most faithful fixture, but it is an opaque blob nobody
   reviews, and it needs the old binaries to be rebuilt or downloaded to regenerate. Kept as an open question below.
   *Rejected*: one test per level from 13 to 19, as the audit proposes — six of those seven levels never left the
   repository.

2. **The rows are written in the shape of the baseline, and read back through the store.** The baseline holds at least
   one row in every table a later migration reads or rewrites, written with SQL against the baseline's own columns.
   After the upgrade the assertions go through `SqliteSpecificationKnowledgeStore` and the stores of the portfolio,
   saved-view and policy families, not only through `SELECT`: a column that kept its value but that the current code no
   longer reads the same way is a loss the SQL assertion would miss. What stays unproven is said in the test: the rows
   are written by the test, not by the released program.

3. **The upgrade must leave a database that can still be written.** V018 adds triggers that reject some updates of
   `policy_packs`; V016 adds a unique index. A read-only assertion would pass on a database whose next write fails. One
   write per family the later migrations constrain is part of the proof.

4. **The released levels are declared once, and a missing one fails the build.** The declaration maps a released
   version to its last migration. A rule compares it with the release notes present in `docs/user/release-notes/`: every
   version that has a release-notes file and is lower than `ProductMetadata.version()` must be declared. The version in
   progress is exempt until it is released; the first build of the next version then fails until the level just shipped
   is declared. 1.0.0, which has no release-notes file, is declared by hand and the rule requires the declaration to
   keep it. Textual by nature on the documentation side (a `.md` file name); the mechanism is one comparison of two
   sets. *Rejected*: reading the tags — absent from a CI checkout. *Rejected*: trusting the release checklist alone —
   it is the kind of step that was forgotten for the upgrade guide.

5. **No ADR.**

## Risks / Trade-offs

- A baseline row written by the test can be a shape the released program never produced. The mitigation is to write
  each row from the insert statement of the released store, read with `git show <tag>:<file>`, and to cite the tag in
  the test.
- The rule of decision 4 reads `docs/` from a build. It belongs with the documentation coherence tests in
  `morpheus-architecture-tests`, which already read `docs/user/UPGRADE_*.md` against the schema constant.
- If the upgrade from level 15 fails on real-shaped rows, this change has found a defect that blocks the release. It is
  then fixed in its own change, with this test as its reproduction.

## Open Questions

1. **Should a database produced by the released program itself be kept as a fixture?** Recommendation: not now. Decide
   after the first run of the level-15 baseline: if it finds nothing, the scripted baseline is enough; if it finds a
   shape the test author had not thought of, a recorded database earns its place.
2. **Is a direct upgrade from 1.0.0 (level 12) still supported?** Task 1.1 of
   `state-the-schema-upgrade-and-its-way-back` asks the same question for 1.1.0. Recommendation: keep the level-12
   baseline as long as the program accepts such a database; the test is what makes the answer true.

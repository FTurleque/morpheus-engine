# Tasks

Implementation is a separate lot, started after this specification is reviewed and after the sprint 6 changes that
rewrite reads of the same tables are merged. Nothing below is done by the pull request that introduces this change.

## 1. Measure before changing

- [x] 1.1 On a database migrated by the product, list every index a migration created whose column list (names,
      collations, directions) is the leading part of another non-partial index of its table. Proof: the list, with the
      index it repeats and whether the match is exact or a prefix. On 2026-10-13: eleven, the ten of the audit plus
      `idx_policy_pack_versions_pack`. Trust the list measured, not either number.
- [x] 1.2 List every statement of `morpheus-store-sqlite/src/main/java` that filters or orders, and run
      `EXPLAIN QUERY PLAN` on each **through `sqlite-jdbc`**, on a migrated database. Proof: a table statement / plan,
      committed with the pull request description; the lines that carry `SCAN` or `USE TEMP B-TREE` are marked. The
      observations of the proposal were made with another SQLite build and are not this proof.
- [x] 1.3 For every table that carries an index of 1.1, and for `requirement_versions` and `saved_views`, record the
      plan of each of its statements before and after the candidate migration. Proof: the two columns; the statements
      whose chosen index changes name are identified, none gains a scan or a sort; no statement compares or orders
      `saved_views.name` in the binary collation, or decision 5 falls back to its alternative.
- [x] 1.4 Confirm that the module still runs neither `ANALYZE` nor `PRAGMA optimize`. Proof: the search and its empty
      result; otherwise the plan guard needs a populated fixture and the design is revised first.
- [x] 1.5 Re-read the sprint 6 and sprint 13 changes as merged: which statements of these tables they rewrote, whether
      one of them added a migration, and whether `page-requirement-reads-in-the-store` asks this migration for an index
      on the key its pages order by. Proof: the list, the migration version this change takes, and the index added or
      the measurement that says none is needed.

## 2. Reproduce before fixing

- [x] 2.1 The redundancy test on the migrated schema. Proof: red on `HEAD`, naming each index of 1.1.
- [x] 2.2 The plan guard for the bounded and unbounded CURRENT requirement reads and the saved-view listing, explaining
      the store's own statement text. Proof: red on `HEAD` on the three, each with its `USE TEMP B-TREE` line.
- [x] 2.3 The order tests: `n` lowest entity version ids among mixed temporal states; `beta` / `Alpha` / `alpha`.
      Proof: green on `HEAD` and unchanged afterwards - they pin what must not move.
- [x] 2.4 The upgrade test: a database at the released schema with rows in every table of 1.1. Proof: written against
      the released baselines of `prove-the-upgrade-from-every-released-schema` if that change is merged, otherwise
      against a fixture built as `R2UpgradeCompatibilityTest` builds its own; red on `HEAD` for the absence of
      the indexes, green for the rows.

## 3. The change

- [x] 3.1 The migration: the drops of 1.1, the partial index of decision 4, the replacement of decision 5. Register it
      in `SqliteSchemaManager` and raise `SUPPORTED_SCHEMA_VERSION` by one. Read the version from the constant
      everywhere (`.claude/skills/live-numbers`); `SqliteStoreTestsDeriveTheSchemaVersionTest` refuses a literal.
- [x] 3.2 Pin its canonical checksum in `SqliteMigrationChecksumGoldenTest`; turn the assertions of existence of the dropped
      indexes in `SqliteSchemaMigrationTest` into assertions of absence. Proof: 2.1, 2.2 and 2.4 pass.
- [x] 3.3 Time one full publication on the M19 large fixture before and after. Proof: the two figures; the bulk inserts
      into the three projection tables that lost an index do not cost more.
- [x] 3.4 Extend the plan guard to the statements of 1.3 whose index changed name, then, as a second lot, to the list
      of 1.2, each remaining sort listed with its reason. Proof: the guard is green and its list has no entry without
      a reason.
- [x] 3.5 Break each rule once, then revert: re-create one dropped index in a scratch migration (2.1 fails and names
      it); remove the new partial index (2.2 fails); add an excuse for a statement that does not sort (the guard asks
      for its removal).

## 4. Documentation and verification

- [x] 4.1 `docs/user/UPGRADE_1_2_1.md` states the schema version reached; `UpgradeGuideCoherenceTest` passes. No SHA,
      test name or `./mvnw` command enters `docs/user/` (`rules/documentation.md`).
- [x] 4.2 `docs/user/release-notes/RELEASE_NOTES_1.2.1.md` and `docs/developer/REMOTE_SERVER_PLATFORM.md` (which names
      "the latest migration" and is stale): one sentence each, without restating the version number where the constant
      can be named.
- [x] 4.3 `./mvnw clean verify` on Windows and on Linux; the changed-line coverage gate. Proof: green; no ratchet of
      `config/m21-quality-ratchets.properties` is touched.
- [x] 4.4 Run the filter of `.claude/skills/adr-author`. Proof: its answer in the pull request; recommendation of the
      design is that no ADR is needed.
- [x] 4.5 M19: `scripts\validate.cmd m19` and its `.sh` twin. Proof: the publication and query budgets hold; the
      figures before and after are in the pull request.
- [x] 4.6 Pull request description: the three findings, the list of 1.1 with the audit's count beside it, the plan
      table of 1.2 and 1.3, and the justification required for a change under `morpheus-architecture-tests/` if 3.2
      touches one.

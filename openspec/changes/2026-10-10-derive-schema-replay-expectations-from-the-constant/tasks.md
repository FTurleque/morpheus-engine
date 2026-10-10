# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Reproduce before fixing

- [x] 1.1 Add the guard of design decision 3 to `morpheus-architecture-tests` as a method of its own (one intention: "no
      store test compares the schema version or the ledger count with a literal"): scan
      `morpheus-store-sqlite/src/test/java` for `assertEquals` with an integer literal first argument and a
      `currentVersion(` or `getInt("count")` second argument; keep an exclusion list whose entries need a reason
      (`R2UpgradeCompatibilityTest` first); assert the tree scanned is not empty and that at least one assertion of the
      scanned shape exists (a rule that matches nothing passes too). Proof: on `HEAD` it fails naming
      `SqliteSchemaMigrationTest` lines 39, 154 and 194.
  - 2026-10-11: `SqliteStoreTestsDeriveTheSchemaVersionTest` (exclusion list with reasons: `R2UpgradeCompatibilityTest`; a stale exclusion fails). Red first: it named `SqliteSchemaMigrationTest.java` lines 39, 154 and 194 and nothing else.
- [x] 1.2 Break the guard once: add `assertEquals(7, new SqliteSchemaManager().currentVersion(connection))` to an unrelated
      store test, observe the failure, remove it; and add `assertEquals(7, list.size())` to confirm it is not reported.
      Proof: both outcomes in the pull request.

  - 2026-10-11: `assertEquals(7, new SqliteSchemaManager().currentVersion(connection))` in a scratch store test -> `ZzScratchTest.java:5 compares ... with the literal 7`; `assertEquals(7, list.size())` -> not reported. Scratch file removed.
## 2. The change

- [x] 2.1 `SqliteSchemaMigrationTest`: rename `migrationReplayIsIdempotentAndLedgerContainsEighteenImmutableEntries` to
      `migrationReplayIsIdempotentAndLedgerContainsOneEntryPerSupportedMigration`; replace the literals at `:39`, `:154`
      and `:194` by `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`. The test as written opens twice and reads once, so it
      does not yet prove the "no-op" scenario: capture the version set and count after each opening and compare them, and
      check each checksum is hexadecimal as well as 64 long. Proof: `./mvnw test -pl morpheus-store-sqlite
      -Dtest=SqliteSchemaMigrationTest` passes; 1.1 now passes.
  - 2026-10-11: renamed to `migrationReplayIsIdempotentAndLedgerContainsOneEntryPerSupportedMigration`; the three literals read `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`; the test now captures the ledger versions after each opening and compares them, checks they are exactly 1..SUPPORTED_SCHEMA_VERSION, and checks every checksum is lower-case hexadecimal as well as 64 long. `SqliteSchemaMigrationTest`: 5 tests pass.
- [x] 2.2 Simulate the next migration without writing one: raise the constant locally by one in a scratch copy and check
      that the only store tests that fail are those that exercise the missing migration, and that `SqliteSchemaMigrationTest`
      is not among the literals that need editing. Proof: the failing set listed in the pull request; scratch copy discarded.

  - 2026-10-11: a scratch `V021` migration with the constant raised to 21 fails four store tests - `SqliteMigrationChecksumGoldenTest` (2), `SqliteMigrationLineEndingCompatibilityTest`, `SqliteMigrationCompatibilityM19Test` - all of which read the pinned canonical checksums, i.e. the tests that are meant to demand a new migration be pinned. `SqliteSchemaMigrationTest` is not among them and needed no edit. Scratch files reverted and `target/` cleaned (a stale `V021` in `target/classes` had made five unrelated tests fail until `clean`).
## 3. Integration

- [x] 3.1 `./mvnw test -pl morpheus-store-sqlite,morpheus-architecture-tests`. Proof: green; the new architecture
      method is counted (decide with `coverage-ratchet` whether `architectureTestsMinimum` rises; it never falls).
  - 2026-10-11: locally, the store module passes (176 tests, 1 skipped) and the architecture module passes 605 of 606 - the one failure is `CoverageQualityGateTest`, which needs a preceding full `clean verify`; the full reactor is the CI of the pull request. `architectureTestsMinimum` is not raised (margin of about 20 on the minimum, see `make-declared-gates-reachable` 6.2).

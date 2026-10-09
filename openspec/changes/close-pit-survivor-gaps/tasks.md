# Tasks

Every test asserts the message, value or order that kills its mutations (`rules/testing.md`); the row it kills is in
`qualification.md`. A test of a defect is seen failing before its fix.

## 1. Reproduce the two defects

- [x] 1.1 In `morpheus-store-sqlite`, register a membership with one provider `"openspec\nmarkdown"` and read it back; verify it fails today (two providers) while the memory store returns one

  Written as `m23/PortfolioPersistenceParityTest` (architecture tests, where both stores are on the classpath), so it
  holds whichever option 2.1 takes: it compares the two stores' outcomes, a refusal included, and forbids only a
  provider set other than the one registered. Red on 2026-10-09 for three of four cases, SQLite only; the fourth
  case shows the defect also substitutes providers: `{"openspec\nmarkdown", "openspec"}` reads back as
  `{openspec, markdown}`, a registered provider lost and an unregistered one invented, at the same set size.
- [x] 1.2 Repeat 1.1 through the HTTP and MCP `providers` argument; verify both reach the same store state

  Written after 3.1, so proved red by running them against the pre-fix `ProviderId`: all three accepted the
  registration (HTTP 201, MCP success, CLI exit 0). With the fix, HTTP answers 400, MCP an error result and the CLI
  the usage exit, each with the named reason, and no membership is stored. The CLI was added: it splits `--providers`
  the same way. HTTP also covers `providerId` of a reference. The HTTP test found that the first refusal text,
  written `\u000A`, never reached the caller: the boundary's location filter drops any message holding a backslash
  and answers the exception name. The notation became `[U+000A]` (ADR-0023 amendment).
- [x] 1.3 In `morpheus-store-sqlite`, write audits at `…:00Z`, `…:00.500Z`, `…:00.500100Z` (and three at one instant); verify `listAudit` returns them out of order today and the memory store in order

  Written as `m25/PolicyAuditOrderTest` (both stores; eight writes in one instant rather than three, so ordering
  by identity passes by chance once in 8!). Red on 2026-10-09. Store port: memory in order, SQLite
  `[write-2, write-1, write-3 … write-10, write-0]`. Public audit (`PolicyPackService.audit`): chronological in both
  stores, but the memory store's eight tied records came back shuffled (`write-6, write-7, write-5, write-3, …`).
  SQLite's ties happened to keep write order only because each SQLite write takes over a millisecond, so their
  UUIDv7 prefixes differ. This corrects the proposal: the public audit was never out of chronological order.

## 2. Decisions (design, "Open decisions")

- [x] 2.1 Decide where a provider identifier is constrained (decision 1) and record it in an ADR or an amendment of the ADR that owns `ProviderId`

  Option (a), in the amendment of ADR-0023 dated 2026-10-09.
- [x] 2.2 Decide the audit-order strategy (decision 2), including what an existing database migrates to

  Option (c) with stable ties, decided on 2026-10-09 (design, decision 2): no migration.
- [ ] 2.3 Decide `MultiProviderCompositionResult.diagnostics()` (decision 3), the parser's double whitespace skip (decision 4) and requirement-delta observation (decision 5)
- [ ] 2.4 Decide whether the store parity of decision 6 is fixed here or spun off; if spun off, open the issue and link it here

## 3. Fix the defects

- [x] 3.1 Apply 2.1; verify 1.1 and 1.2 pass and an existing SQLite membership still reads back

  `ProviderId` refuses ISO control characters left after trimming, naming the identifier with them written as
  `[U+XXXX]` (`ProviderIdTest`, red first). 1.1 passes; `SqlitePortfolioStorePersistenceTest` keeps the existing
  round trips and adds a stored empty segment refused by name, the filter that recomposed it being removed. 1.2 is
  still open: the transport tests are not written yet. `PROVIDER_SDK.md` states the constraint for `provider.id`.
  Run in WSL on 2026-10-09: the Windows home directory refuses listing to its own user, which breaks `javac`'s
  `toRealPath` on every JAR of `~/.m2` (an environment fault, not a code one).
- [x] 3.2 Apply 2.2; verify 1.3 passes, a database written before the change reads back in chronological order, and `SqliteMigrationAtomicityTest` and the migration checksum tests still pass if a migration is added

  `PolicyPackStore.listAudit` documents the order; `SqlitePolicyPackStore` drops `ORDER BY at, id` and sorts the
  parsed records, `MemoryPolicyPackStore` sorts instead of returning insertion order. `PolicyAuditOrderTest` now
  asserts the decided requirement (chronological, ties by identity, same order on every read); against the pre-fix
  stores its store-port case fails (memory ties in insertion order, SQLite as in 1.3), its public case passes
  because the service already sorted — it stays as a regression guard. No migration: the stored text is unchanged,
  so an older database is read by the same code.

## 4. Missing tests, class by class

- [ ] 4.1 `MultiProviderCompositionService`: the exact field set per entity type (with `CHANGE`), different projects with their message, secondary diagnostics (dedupe, append, an `ERROR` one), a specification/requirement key collision, the conflict order, the `rootLocator` conflict asserted before its loop; apply 2.3
- [ ] 4.2 `QueryExecutionService`: each budget at its exact boundary (references, portfolio sum, rows), the row mapping, and the diagnostic paths of lines 161 and 194 with the path they report
- [ ] 4.3 `NormalizedProjectContent`: one rejection test per reference check with its message, and the message added to the existing `rejects*` tests
- [ ] 4.4 `SyntheticJsonParser`: truncated documents refused as `IllegalArgumentException` with their message, empty containers, every escape and hex digit case, raw non-BMP characters, exact-limit documents; apply 2.3
- [ ] 4.5 `SqlitePolicyPackStore`: a closed store for every public method, re-activation on a new version (UPDATE branch), audit mismatch per write method with no state change, unknown pack, version and inactive pack, full-record audit equality and `listDefinitions` content
- [ ] 4.6 `SqlitePortfolioStore`: the closed-store test asserts `SQLite portfolio store is closed` and covers `findMembership`, `findReference`, `outgoing`, `incoming`, `findFreshness`; the repository and source locators, the source-membership and freshness-membership guards

## 5. Replay and record

- [ ] 5.1 Replay the PIT command of PIT-AUD-5 on the six classes; verify every survivor left is a row `qualification.md` marks `EQUIVALENT` or `UNREACHABLE`, and record the before/after counts in the audit document
- [ ] 5.2 Run `./mvnw clean verify` and the persistence parity tests; verify the test ratchets of `config/m21-quality-ratchets.properties` still hold and record whether a ratchet can rise (`coverage-ratchet` skill)

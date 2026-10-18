# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [x] 1.1 List the production callers of the three journal reads and the surfaces that reach each. Proof: the table;
      on 2026-10-13, three surfaces for the policy audit and no production caller for the two others. Re-measure
      rather than trust it.
      *2026-10-17: the policy audit: HTTP (`MorpheusPolicyApiService`), MCP (`get_policy_audit`), CLI (`policy audit`); the lifecycle audit list and the archive list: no production caller (checked by `AppendOnlyJournalReadsArchitectureTest`).*
- [ ] 1.2 Measure the answer of the audit route on journals of increasing size: bytes, heap, time, and the size at
      which the remote proxy refuses it. Proof: the figures; the audit and ADR-0107 both say this was never measured.
      *2026-10-17: not measured on the uncorrected code: the new budget gate calls the paged port. `policy_audit_page_p95_ms=29` after (100,001 records, deepest page). The size at which the remote proxy refuses the whole answer was not measured.*
- [x] 1.3 Show that the existing index does not give the order: `EXPLAIN QUERY PLAN` and the result of
      `ORDER BY at, id` on the three timestamps of `policy-audit-order`. Proof: the wrong order, on `HEAD`.
      *2026-10-17: shown by `SqlitePolicyAuditPageTest#theTextOfTheInstantDoesNotGiveTheOrderTheKeyDoes`: `ORDER BY at, id` returns `cba` for `:00Z`, `.500Z`, `.500100Z`.*
- [x] 1.4 List the statements that delete from the three tables. Proof: none on 2026-10-13.
      *2026-10-17: no statement deletes from the three tables; now held by `noStatementOfTheProductDeletesFromAJournal`.*
## 2. Decide

- [x] 2.1 The maintainer chooses the order key (design, decision 2) and whether the two other collections of MCP-2 are
      decided with this one. Proof: the choice, written in the amendment of ADR-0107.
      *2026-10-17: the maintainer chose (b), a stored sortable key and a migration, on 2026-10-17, and left the two other collections of MCP-2 out.*
- [x] 2.2 The amendment of ADR-0107 and the decision record for the journals (check the highest number in
      `docs/architecture/adr/` first; skill `live-numbers`). Proof: `AdrIndexCoherenceTest` green.
      *2026-10-17: ADR-0107 amended; ADR-0116 (highest number was 0115).*
## 3. Reproduce before fixing

- [x] 3.1 A store test: a long journal, one page asked, and the count of records the SQLite store built. Proof: red on
      `HEAD`, which builds them all.
      *2026-10-17: `SqlitePolicyAuditPageTest#aPageHoldsOnlyItsRecordsAndSaysHowManyFollow` and the query-plan test; red on `HEAD` for want of the method.*
- [x] 3.2 The paging scenarios against both stores, timestamps of `PolicyAuditOrderTest` placed across a page
      boundary. Proof: red on `HEAD` for want of the method; they are the parity reference afterwards.
      *2026-10-17: `PolicyAuditOrderTest`, rewritten to read the journal by pages of four across the three timestamps whose text sorts wrongly, through the store and the service, on both stores.*
- [x] 3.3 An HTTP test and a CLI test: default page, next page, limit out of range, unknown parameter, unknown pack.
      Proof: red on `HEAD`, which answers a bare array.
      *2026-10-17: `MorpheusPolicyAuditPagingHttpTest` and `MorpheusPolicyAuditPagingCliTest`: default page, next page, last page, limit out of range, unknown parameter, unknown pack. Unknown pack answers 400 over HTTP as the rest of the pack tree does, not 404.*
- [x] 3.4 The ArchUnit rule on the two unbounded lists and the textual assertion on `DELETE`. Proof: both green on
      `HEAD`; each is broken once by adding the call and the statement, then reverted.
      *2026-10-17: the call rule and the DELETE assertion, each broken once (a scratch class calling `listArchives`, a scratch string with `DELETE FROM policy_audit`), then reverted.*
## 4. The change

- [x] 4.1 The port and the two stores; the migration, if the choice of 2.1 needs one (skill and rules of
      `sqlite-schema-history`; the pre-migration copy of ADR-0110; the upgrade page). Proof: 3.1 and 3.2 pass;
      `PolicyAuditOrderTest` and `PolicyPersistenceParityTest` pass, changed only where they read the whole journal.
      *2026-10-17: V021, `at_key` computed by SQL for existing rows and by `SqlitePolicyPackStore.orderKey` for new ones, `idx_policy_audit_pack_order`; `SqlitePolicyAuditOrderKeyMigrationTest` compares the two. The old index is kept (AUD-PRF-07 drops indexes). `docs/user/UPGRADE_1_2_2.md` written for the version that carries V021 (assumed 1.2.2).*
- [x] 4.2 The service returns the store's page without sorting it again. Proof: 3.2 through the service.
      *2026-10-17: `PolicyPackService.audit` returns the store page; the second sort is gone.*
- [x] 4.3 HTTP, CLI and MCP (skill `public-capability`): the route and the command take the page; the MCP tool takes
      it at the store instead of slicing. Proof: 3.3 passes; `GrowingCollectionToolsPagingTest` and
      `PagedResponseVocabularyArchitectureTest` green.
      *2026-10-17: HTTP, CLI and MCP take the page; `PageArguments.request`; `PagedResponseVocabularyArchitectureTest` green (the CLI view is a record, `PolicyPublicViews.AuditPageView`).*
- [x] 4.4 The OpenAPI document (both parameters with `minimum` and `maximum`, the answer's shape), the notes of
      `policy.audit` in the manifest, the release notes, `docs/user/POLICY_PACKS.md`, `docs/user/CLI.md`,
      `docs/developer/POLICY_PLATFORM.md`, the Javadoc of `PageArguments` (its sentence on the unbounded read).
      Proof: `PublicHttpRouteConvergenceTest`, the M25 contract tests and `RepositoryDocumentationCoherenceTest` green.
      *2026-10-17: OpenAPI m25, manifest notes, `POLICY_PACKS.md`, `MCP.md`, `POLICY_PLATFORM.md`, `PageArguments` Javadoc, `UPGRADE_1_2_2.md`. **No release note**: 1.2.1 is published and frozen, the next version has none yet.*
- [x] 4.5 The two unbounded lists: excused by name for `dead-code-refusal`, or removed from the port (decision 4).
      *2026-10-17: neither list is removed from its port nor excused: the name scan counts the tests that call them, so `UnreferencedPublicMethodArchitectureTest` does not ask for an excuse; the call rule keeps production away.*
## 5. Verification

- [x] 5.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
      *2026-10-17: `./mvnw clean verify` green locally and on both CI lanes of PR 508.*
- [ ] 5.2 A page read through a real remote server on a journal above the size of 1.2. Proof: answered.
      *2026-10-17: not done: no real remote server was driven over a journal above the size of 1.2, because 1.2 was not measured.*
- [x] 5.3 M19 on both platforms, if the journal-page budget was kept: `scripts\validate.cmd m19` and
      `bash ./scripts/validate-m19.sh`. Proof: the budget is met; the others are unchanged.
      *2026-10-17: `policy_audit_page_p95_ms=29` against 1,000 ms; Windows, the class alone, not `validate-m19`.*
- [x] 5.4 Pull request description: the finding and what it got wrong about the index, the figures of 1.2, the break of
      form for HTTP and CLI clients, the choice of 2.1 and its residual if it is (c).
      *2026-10-17: PR 508 states the finding, what it got wrong about the index, the break of form, the maintainer choice (b); no figure for 1.2 (not measured).*
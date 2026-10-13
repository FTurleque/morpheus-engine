# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List the production callers of the three journal reads and the surfaces that reach each. Proof: the table;
      on 2026-10-13, three surfaces for the policy audit and no production caller for the two others. Re-measure
      rather than trust it.
- [ ] 1.2 Measure the answer of the audit route on journals of increasing size: bytes, heap, time, and the size at
      which the remote proxy refuses it. Proof: the figures; the audit and ADR-0107 both say this was never measured.
- [ ] 1.3 Show that the existing index does not give the order: `EXPLAIN QUERY PLAN` and the result of
      `ORDER BY at, id` on the three timestamps of `policy-audit-order`. Proof: the wrong order, on `HEAD`.
- [ ] 1.4 List the statements that delete from the three tables. Proof: none on 2026-10-13.

## 2. Decide

- [ ] 2.1 The maintainer chooses the order key (design, decision 2) and whether the two other collections of MCP-2 are
      decided with this one. Proof: the choice, written in the amendment of ADR-0107.
- [ ] 2.2 The amendment of ADR-0107 and the decision record for the journals (check the highest number in
      `docs/architecture/adr/` first; skill `live-numbers`). Proof: `AdrIndexCoherenceTest` green.

## 3. Reproduce before fixing

- [ ] 3.1 A store test: a long journal, one page asked, and the count of records the SQLite store built. Proof: red on
      `HEAD`, which builds them all.
- [ ] 3.2 The paging scenarios against both stores, timestamps of `PolicyAuditOrderTest` placed across a page
      boundary. Proof: red on `HEAD` for want of the method; they are the parity reference afterwards.
- [ ] 3.3 An HTTP test and a CLI test: default page, next page, limit out of range, unknown parameter, unknown pack.
      Proof: red on `HEAD`, which answers a bare array.
- [ ] 3.4 The ArchUnit rule on the two unbounded lists and the textual assertion on `DELETE`. Proof: both green on
      `HEAD`; each is broken once by adding the call and the statement, then reverted.

## 4. The change

- [ ] 4.1 The port and the two stores; the migration, if the choice of 2.1 needs one (skill and rules of
      `sqlite-schema-history`; the pre-migration copy of ADR-0110; the upgrade page). Proof: 3.1 and 3.2 pass;
      `PolicyAuditOrderTest` and `PolicyPersistenceParityTest` pass, changed only where they read the whole journal.
- [ ] 4.2 The service returns the store's page without sorting it again. Proof: 3.2 through the service.
- [ ] 4.3 HTTP, CLI and MCP (skill `public-capability`): the route and the command take the page; the MCP tool takes
      it at the store instead of slicing. Proof: 3.3 passes; `GrowingCollectionToolsPagingTest` and
      `PagedResponseVocabularyArchitectureTest` green.
- [ ] 4.4 The OpenAPI document (both parameters with `minimum` and `maximum`, the answer's shape), the notes of
      `policy.audit` in the manifest, the release notes, `docs/user/POLICY_PACKS.md`, `docs/user/CLI.md`,
      `docs/developer/POLICY_PLATFORM.md`, the Javadoc of `PageArguments` (its sentence on the unbounded read).
      Proof: `PublicHttpRouteConvergenceTest`, the M25 contract tests and `RepositoryDocumentationCoherenceTest` green.
- [ ] 4.5 The two unbounded lists: excused by name for `dead-code-refusal`, or removed from the port (decision 4).

## 5. Verification

- [ ] 5.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 5.2 A page read through a real remote server on a journal above the size of 1.2. Proof: answered.
- [ ] 5.3 M19 on both platforms, if the journal-page budget was kept: `scripts\validate.cmd m19` and
      `bash ./scripts/validate-m19.sh`. Proof: the budget is met; the others are unchanged.
- [ ] 5.4 Pull request description: the finding and what it got wrong about the index, the figures of 1.2, the break of
      form for HTTP and CLI clients, the choice of 2.1 and its residual if it is (c).

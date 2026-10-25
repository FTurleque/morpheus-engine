# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

**Where the MCP caller is.** `give-each-cli-command-and-mcp-tool-a-capability-class` retires `MorpheusMcpToolService`:
the requirement reads its tools made are in `MorpheusSpecificationMcpTools` (`get_current_specification`) and
`MorpheusRequirementMcpTools`, and its test is `CatalogueToolsAnswerTest`.

## 1. Measure before changing

- [ ] 1.1 List every caller of both overloads of `listCurrentRequirementVersions` under `src/main`, and for each
      whether it needs every requirement, a page, or a number. Proof: the table; on 2026-10-13, nine classes call the
      unbounded overload (eight in `morpheus-application`, `MorpheusMcpToolService`) and one the bounded one.
- [ ] 1.2 Run `scripts\validate.cmd m19` (no `-Version`) on `HEAD` and keep `requirement_query_p95_ms`; add, locally
      and unbudgeted, the last page and a query that matches nothing. Proof: the three figures per platform, with the
      processor and the storage stated. The audit measured none; neither did this specification.
- [ ] 1.3 Verify the unverified premise of the design: that the pinned `sqlite-jdbc` lets the store register a
      function on its connection, under the connection policy in force on the day
      (`own-the-sqlite-connection-policy-in-one-place`). Proof: a throwaway test that registers one and calls it from
      a `WHERE`. If it fails, decision 4 falls to its next option and the pull request says so.
- [ ] 1.4 Read whether `requirement_id` is unique among the CURRENT rows of a snapshot, in the schema and in the write
      path. Proof: the constraint or its absence; it decides whether the tie-break is ever exercised.
- [ ] 1.5 Read the changes of sprints 6, 7 and 11 that are active on the day and state, per change, which file this
      lot shares with it. Proof: the list.

## 2. Reproduce before fixing

- [ ] 2.1 The characterisation suite of decision 2, through the three services, on both stores. Proof: green on
      `HEAD` - it pins today's result, the accented, wildcard, keyless, cross-field and beyond-the-total cases
      included. One assertion per case, with the query and the page in the message.
- [ ] 2.2 The second named list in `CurrentRequirementReaderArchitectureTest`, holding only the readers of 1.1 that
      need every requirement. Proof: red on `HEAD`, naming `RequirementQueryService`,
      `SpecificationContextQueryService`, `HistoricalRequirementQueryService` and `MorpheusMcpToolService`.
- [ ] 2.3 The parity suite of decision 3, written against the port operation. Proof: it does not compile, then fails,
      until 3.2 and 3.3.

## 3. The change

- [ ] 3.1 The port operation and its value types, with a default that answers from the whole population. Proof: every
      existing implementation of the port compiles unchanged; a test of the default on a hand-written double.
- [ ] 3.2 The memory store: the operation with the application's normalisation. Proof: 2.1 on the memory store.
- [ ] 3.3 The SQLite store, by the option 1.2 and 1.3 select. An explicit `ORDER BY` with the tie-break; the page
      and the total read in one transaction through `SqliteConnectionScope`. Proof: 2.1 and 2.3 pass.
- [ ] 3.4 The three services ask the port; `MorpheusHistoryApiService` passes its page instead of slicing; the MCP
      header asks for a count. Proof: 2.1 unchanged and green; 2.2 green; `RequirementQueryContractTest` and
      `MorpheusMcpToolServiceTest` unchanged and green.
- [ ] 3.5 The rule that both shipped stores declare the operation (ArchUnit - intent: *these classes declare this
      method*). Check both classes are in the imported set of `morpheus-architecture-tests`. Proof: green.
- [ ] 3.6 `M19QueryPerformanceGate`: the last page and the empty match, same budget. Proof: `M19_METRIC` lines for
      the three, both platforms, in the nightly lane or by `workflow_dispatch`.
- [ ] 3.7 Break once each, then revert: (a) fold with SQLite's `lower()` - the accented case of 2.1 fails on SQLite
      only; (b) drop the tie-break - the order scenario of 2.3 fails; (c) remove the operation from the SQLite store -
      3.5 names it; (d) call the unbounded read from a paged service - 2.2 names it. Proof: the four failures.
- [ ] 3.8 Only if the option chosen adds a column or an index: the migration, its checksum, the schema constant read
      and never copied, the upgrade statement and the backup the sprint 5 changes require, and the ADR - highest
      number checked with a glob first. Otherwise state in the pull request that the schema is untouched.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; `./mvnw dependency:analyze`; changed-line coverage gate. Proof: green.
- [ ] 4.2 `PublicHttpRouteConvergenceTest` and `PublicSurfaceManifestCoversEveryServedToolTest` with no diff on
      `contracts/public-surfaces.tsv` or `docs/developer/reference/openapi/`. Proof: green, and the empty diff.
- [ ] 4.3 The three existing `*PersistenceParityTest` suites and the new one. Proof: green on Linux and Windows.
- [ ] 4.4 `scripts\validate.cmd m19` and `validate-m19.sh`. Proof: the figures of 1.2 beside the figures after, per
      platform; the budget constant unchanged or lower.
- [ ] 4.5 Documentation: the port's contract in `docs/developer/reference/contracts/` if a page describes
      `VersionedRequirementStore`; nothing under `docs/user/`. No figure of 4.4 in a current page.
- [ ] 4.6 Pull request description: the finding, the nine callers, the constraint on the match, the option chosen and
      why, the measurement before and after, and that the scan for the total remains.

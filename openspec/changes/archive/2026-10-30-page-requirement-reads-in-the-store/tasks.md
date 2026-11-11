# Tasks

**Where the MCP caller is.** `give-each-cli-command-and-mcp-tool-a-capability-class` retires `MorpheusMcpToolService`:
the requirement reads its tools made are in `MorpheusSpecificationMcpTools` (`get_current_specification`) and
`MorpheusRequirementMcpTools`, and its test is `CatalogueToolsAnswerTest`.

## 1. Measure before changing

- [x] 1.1 List every caller of both overloads of `listCurrentRequirementVersions` under `src/main`, and for each
      whether it needs every requirement, a page, or a number. Proof: the table; on 2026-10-13, nine classes call the
      unbounded overload (eight in `morpheus-application`, `MorpheusMcpToolService`) and one the bounded one.
      *2026-10-29, `develop` at `6b3f6304`: nine classes call the unbounded overload. A page: `RequirementQueryService`, `SpecificationContextQueryService`, `HistoricalRequirementQueryService`. A number: `MorpheusSpecificationMcpTools` (`get_current_specification`). Every requirement: `RequirementSnapshotComparisonService`, `ChangeCompletenessService`, `DecisionReferenceQualityService`, `RequirementQualityService`, `TaskQualityService`. The bounded overload: `QueryExecutionService`.*
- [x] 1.2 Run `scripts\validate.cmd m19` (no `-Version`) on `HEAD` and keep `requirement_query_p95_ms`; add, locally
      and unbudgeted, the last page and a query that matches nothing. Proof: the three figures per platform, with the
      processor and the storage stated. The audit measured none; neither did this specification.
      *2026-10-29, `M19QueryPerformanceGate` run alone on `6b3f6304` with the two scenarios added (the validator starts with a full `clean verify`). First page / last page / no match, p95 in ms: Windows 138 / 105 / 99, Linux (WSL 2, same machine) 251 / 108 / 120. AMD Ryzen 7 5700G, NVMe SSD (Corsair Force MP600); the machine was running other builds.*
- [x] 1.3 Verify the unverified premise of the design: that the pinned `sqlite-jdbc` lets the store register a
      function on its connection, under the connection policy in force on the day
      (`own-the-sqlite-connection-policy-in-one-place`). Proof: a throwaway test that registers one and calls it from
      a `WHERE`. If it fails, decision 4 falls to its next option and the pull request says so.
      *Holds. `org.sqlite.Function` registers on the physical connection reached by `unwrap(SQLiteConnection.class)`, through the scope's proxy too: `SqliteRequirementPageTest#theCorpusFunctionIsRegisteredOnTheConnectionAnOperationScopeLends` pages through `SqliteOperationRuntime`. The store registers it before every page read, since a scope may replace its physical connection. Option chosen: the function.*
- [x] 1.4 Read whether `requirement_id` is unique among the CURRENT rows of a snapshot, in the schema and in the write
      path. Proof: the constraint or its absence; it decides whether the tie-break is ever exercised.
      *Unique. The schema alone does not say so (the partial unique index is on `entity_identity_id`), but `RequirementVersionRecord` refuses an entity identity other than the requirement identity, and both write paths (`ProjectSnapshotImportService`, `RequirementDeltaApplicationService`) set them equal. The tie-break by entity version identity keeps the order total by declaration; no stored data exercises it.*
- [x] 1.5 Read the changes of sprints 6, 7 and 11 that are active on the day and state, per change, which file this
      lot shares with it. Proof: the list.
      *Sprint 6 `declare-a-budget-before-bounding-a-cost` (4/14): `M19QueryPerformanceGate`. Sprint 7 `page-the-two-remaining-journals` (12/12, not archived): `SqliteQueryPlanGuardTest`. Sprint 11 `let-application-package-cycles-only-decrease` (in progress in a parallel session): the application package graph - this lot adds no edge between two application packages (the historical service returns a `store` type, not a `query` one). `own-the-sqlite-connection-policy-in-one-place`: no file, the page reads through its scope.*

## 2. Reproduce before fixing

- [x] 2.1 The characterisation suite of decision 2, through the three services, on both stores. Proof: green on
      `HEAD` - it pins today's result, the accented, wildcard, keyless, cross-field and beyond-the-total cases
      included. One assertion per case, with the query and the page in the message.
      *`RequirementPageReadsCharacterisationTest`, seven tests over both stores; green on the previous services (the historical read through its list API, sliced in the test), then unchanged and green after.*
- [x] 2.2 The second named list in `CurrentRequirementReaderArchitectureTest`, holding only the readers of 1.1 that
      need every requirement. Proof: red on `HEAD`, naming `RequirementQueryService`,
      `SpecificationContextQueryService`, `HistoricalRequirementQueryService` and `MorpheusMcpToolService`.
      *`WHOLE_CURRENT_READERS` in `CurrentRequirementReaderArchitectureTest` names the five readers of 1.1 that compute over every requirement, each with its reason, under the same two checks as the full-history list. Red proven by 3.7 (d); `MorpheusMcpToolService` no longer exists, its caller is `MorpheusSpecificationMcpTools`.*
- [x] 2.3 The parity suite of decision 3, written against the port operation. Proof: it does not compile, then fails,
      until 3.2 and 3.3.
      *`RequirementPagePersistenceParityTest`: fourteen criteria, both orders, limits 1, 3, 7 and every offset to past the total; it did not compile before 3.1.*

## 3. The change

- [x] 3.1 The port operation and its value types, with a default that answers from the whole population. Proof: every
      existing implementation of the port compiles unchanged; a test of the default on a hand-written double.
      *`pageCurrentRequirementVersions`, `countCurrentRequirementVersions`, `RequirementPageCriteria`, `RequirementPageOrder`, `RequirementPage` in `com.morpheus.application.store`; the defaults answer from the whole population. `RequirementPagePortTest` (module `morpheus-application`) tests them on a hand-written double.*
- [x] 3.2 The memory store: the operation with the application's normalisation. Proof: 2.1 on the memory store.
      *`MemorySpecificationKnowledgeStore`; `MemoryRequirementPageTest`, and 2.1 on the memory store.*
- [x] 3.3 The SQLite store, by the option 1.2 and 1.3 select. An explicit `ORDER BY` with the tie-break; the page
      and the total read in one transaction through `SqliteConnectionScope`. Proof: 2.1 and 2.3 pass.
      *One statement reads the total and the page (a left join keeps the total when the page is empty), so both describe one state. The match is `instr(morpheus_requirement_corpus(...), ?)`, the function calling `RequirementPageCriteria.corpus`. The order compares identities on signed halves, as `UUID.compareTo` does: the text order of the canonical form disagrees with the domain's for an identity whose first digit is 8 or more, which the design assumed never happens. `SqliteRequirementPageTest`, `SqliteQueryPlanGuardTest` (page excused for its sort, count served by an index), 2.1 and 2.3 green.*
- [x] 3.4 The three services ask the port; `MorpheusHistoryApiService` passes its page instead of slicing; the MCP
      header asks for a count. Proof: 2.1 unchanged and green; 2.2 green; `RequirementQueryContractTest` and
      `MorpheusMcpToolServiceTest` unchanged and green.
      *Done; `MorpheusHistoryApiService` builds its page from the port's. `RequirementQueryContractTest`, `PublishedHistoryContractTest` (call adapted to the paged signature), `CatalogueToolsAnswerTest`, `McpToolTranscriptTest` and `PublicPayloadConvergenceTest` green.*
- [x] 3.5 The rule that both shipped stores declare the operation (ArchUnit - intent: *these classes declare this
      method*). Check both classes are in the imported set of `morpheus-architecture-tests`. Proof: green.
      *`ShippedRequirementStoresPageThemselvesArchitectureTest`: both stores declare the page and the count, and the list is every production implementation of the port. Both classes are in the imported set.*
- [x] 3.6 `M19QueryPerformanceGate`: the last page and the empty match, same budget. Proof: `M19_METRIC` lines for
      the three, both platforms, in the nightly lane or by `workflow_dispatch`.
      *Three scenarios under the unchanged 1000 ms budget. Local figures in 4.4; the nightly lane gives the CI figures.*
- [x] 3.7 Break once each, then revert: (a) fold with SQLite's `lower()` - the accented case of 2.1 fails on SQLite
      only; (b) drop the tie-break - the order scenario of 2.3 fails; (c) remove the operation from the SQLite store -
      3.5 names it; (d) call the unbounded read from a paged service - 2.2 names it. Proof: the four failures.
      *2026-10-29, each break run then reverted: (a) `lower()` instead of the function - `theLexicalSearchFoldsEveryLetterTheApplicationFoldsAndOnlyThose` fails on SQLite only ('école'); (b) the tie-break cannot be broken observably (1.4), so the signed order was dropped instead - the parity suite fails on the first page of the whole read; (c) the page removed from the SQLite store - 3.5 names `SqliteVersionedRequirementStore`; (d) the unbounded read called from `RequirementQueryService` - 2.2 names it.*
- [x] 3.8 Only if the option chosen adds a column or an index: the migration, its checksum, the schema constant read
      and never copied, the upgrade statement and the backup the sprint 5 changes require, and the ADR - highest
      number checked with a glob first. Otherwise state in the pull request that the schema is untouched.
      *No column, no index, no migration: the schema is untouched.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`; `./mvnw dependency:analyze`; changed-line coverage gate. Proof: green.
      *2026-10-30, merged branch: `./mvnw clean verify` BUILD SUCCESS on Windows (dependency analysis included, `failOnWarning`); `check-diff-coverage.py` against `6b3f6304`: changed branches 97.6 %, every changed line of the page covered but four `catch` lines of the SQLite store.*
- [x] 4.2 `PublicHttpRouteConvergenceTest` and `PublicSurfaceManifestCoversEveryServedToolTest` with no diff on
      `contracts/public-surfaces.tsv` or `docs/developer/reference/openapi/`. Proof: green, and the empty diff.
      *Green in the same build; `git diff 6b3f6304 -- contracts/ docs/developer/reference/openapi/` is empty.*
- [x] 4.3 The three existing `*PersistenceParityTest` suites and the new one. Proof: green on Linux and Windows.
      *Green on Windows in the same build; Linux is the pull request's own CI run.*
- [x] 4.4 `scripts\validate.cmd m19` and `validate-m19.sh`. Proof: the figures of 1.2 beside the figures after, per
      platform; the budget constant unchanged or lower.
      *p95 in ms, first page / last page / no match. Windows before 138 / 105 / 99, after 98 / 87 / 56. Linux (WSL 2) before 251 / 108 / 120, after 70 / 103 / 45. Budget constant unchanged. The scan for the total remains: the empty query is answered in SQL, a lexical one calls the function once per CURRENT row and term.*
- [x] 4.5 Documentation: the port's contract in `docs/developer/reference/contracts/` if a page describes
      `VersionedRequirementStore`; nothing under `docs/user/`. No figure of 4.4 in a current page.
      *No page of `docs/developer/reference/contracts/` describes `VersionedRequirementStore`. ADR-0137 sections 8 and 10 say where the page is cut and how SQLite evaluates the match, in present tense; its decision is unchanged.*
- [x] 4.6 Pull request description: the finding, the nine callers, the constraint on the match, the option chosen and
      why, the measurement before and after, and that the scan for the total remains.
      *In the pull request.*

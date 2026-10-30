# Proposal

## Why

AUD-PRF-05 (sprint 13, Medium) reports that the requirement search filters, sorts and cuts its page in memory after
loading every CURRENT requirement of the snapshot. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding
**holds**, it is wider than its evidence, and its remedy meets a constraint the audit did not name.

What was re-read:

- `RequirementQueryService.java:63-73`: `listCurrentRequirementVersions(snapshot.id())`, then `filter`, `sorted`,
  `subList`. Unchanged.
- `VersionedRequirementStore` has one bounded read, `listCurrentRequirementVersions(snapshotId, limit)`, used only by
  the query DSL to enforce its source-row budget. It takes no offset, no predicate and no order of the caller's: it
  cannot serve a page. No other port of `morpheus-application` takes a limit, an offset or a cursor.
- The unbounded overload is called from **nine** classes, not the eight the finding counts: the seven it lists,
  `RequirementQueryService`, and `MorpheusMcpToolService.java:85`, which loads every CURRENT requirement of the
  snapshot to read `.size()`.
- The same shape serves two more public reads the finding does not cite. `SpecificationContextQueryService.java:74-84`
  loads the snapshot's CURRENT requirements, keeps those of one specification, sorts and cuts a page
  (`specification.context`). `HistoricalRequirementQueryService.java:29` returns the whole sorted list, and the HTTP
  adapter cuts the page itself with `PagedEnvelope.slice` (`MorpheusHistoryApiService.java:61`, `version.requirements`).
- The prerequisite the finding sets is met: the M19 budgets run in the nightly lane on both platforms
  (`nightly.yml`, archived change `make-declared-gates-reachable`). `M19QueryPerformanceGate` measures the first page
  of one query over the large fixture; a deep page and a query that matches nothing are not measured.

**The constraint.** The match is defined in Java: `RequirementSearchQuery.normalize` is
`strip().toLowerCase(Locale.ROOT)`, applied to the key, the title and the statement, and every term must be contained
(`RequirementQueryContractTest#lexicalSearchIsCaseInsensitiveUsesAllTermsAndDoesNotFuzzyMatch`). SQLite's own
`lower()` and `LIKE` fold ASCII letters only, and `LIKE` reads `%` and `_` as wildcards. "`WHERE` + `ORDER BY` +
`LIMIT/OFFSET`", written the obvious way, changes what a search for an accented or a non-Latin term returns, and makes
the two stores disagree - which `RequirementQueryContractTest#memoryAndSqliteProduceExactlyTheSameLexicalResult`
exists to refuse. The remedy is specified here as a behaviour that must not change, not as a query.

**The limit of the gain.** The page carries `totalMatches`, a required field of the published response. Whatever the
store does, it evaluates the predicate over every CURRENT row of the snapshot to count. What moving the page into the
port removes is the materialisation of every row as a domain record, the sort in the application, and the adapter's
knowledge of how a page is cut - not the scan. The cost was not measured by the audit and is not measured here; the
first tasks measure it.

## What Changes

- The store port answers a page of CURRENT requirements itself: criteria, order, offset, limit, and the total.
- The three public reads that page requirements (`requirement.find`, `specification.context`,
  `version.requirements`) ask the port for their page; none loads the whole population to return part of it, and the
  HTTP adapter stops cutting a page of requirements.
- A reader that only needs the number of CURRENT requirements asks for a count.
- The classes that still load the whole CURRENT population are listed by name with their reason, as the readers of
  the whole history already are.
- Both stores answer identically, the accented and the wildcard cases included, and the M19 query gate measures a
  deep page.
- **No public contract changes**: same routes, tools and commands, same parameters and bounds, same response.

## Capabilities

### New Capabilities

- `requirement-page-reads`: how a page of the requirements of a published snapshot is read - by whom the page is cut,
  in what order, with what total, and identically on every store.

### Modified Capabilities

(none)

## Impact

- `morpheus-application` (`VersionedRequirementStore`, `RequirementQueryService`, `SpecificationContextQueryService`,
  `HistoricalRequirementQueryService`), `morpheus-store-sqlite`, `morpheus-store-memory`, `morpheus-api`
  (`MorpheusHistoryApiService`), `morpheus-mcp` (`MorpheusMcpToolService`).
- `contracts/public-surfaces.tsv` and `docs/developer/reference/openapi/*.yaml`: **unchanged**. The convergence gates
  must pass without an edit to either.
- No ADR for the port method. An ADR, and the schema-upgrade statement of
  `state-the-schema-upgrade-and-its-way-back`, only if the measurement leads to a persisted search column or an index
  (design, decision 4).
- Gates: `CurrentRequirementReaderArchitectureTest` (a second named list), the M19 query gate (a scenario added, no
  budget raised), a parity suite. Ratchets: none moved.
- Same layer as sprint 6 (unbounded loads), sprint 7 (indexes) and sprint 11 (the architecture of the ports); the
  boundaries and the order are in the design.

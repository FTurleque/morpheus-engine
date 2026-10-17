# Proposal

## Why

AUD-PRF-04 (sprint 6, High) reports three tables that only grow, read without `LIMIT` or pagination, one of them over
HTTP and MCP. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds in part**, and the part that holds
is not the one its remedy fits:

- **The three reads are as described.** `SqlitePolicyPackStore.listAudit` (`:560-578`) reads every audit record of a
  pack and sorts them in Java; `SqliteChangeLifecycleMutationStore.listAudit` (`:74-98`) and
  `SqliteSyncStateStore.listArchives` (`:90-120`) read every row of a change's audit and of a project's archives.
  None of the three tables is ever deleted from, and one statement of the module carries a `LIMIT`
  (`SqliteVersionedRequirementStore.java:313`).
- **Only one of them has a reader.** The policy audit is reached by `PolicyPackService.audit` (`:234`) from HTTP
  (`MorpheusPolicyApiService.java:130`), MCP (`MorpheusPolicyMcpTools.java:121`) and the CLI
  (`MorpheusPolicyCli.java:153`). The lifecycle audit list and the archive list have **no caller in production code**:
  only tests read them. They load nothing on any public path; they are a trap for the first surface that will.
- **MCP already pages the answer, and says it does not page the read.** Since the amendment of 2026-09-29 to ADR-0107
  (MCP-2), `get_policy_audit` takes `offset` and `limit` and answers with the five-key envelope; `PageArguments` states
  that "reading the collection stays unbounded", and the ADR calls it an accepted residual "to treat if the read
  becomes the bottleneck". HTTP and the CLI still answer with the whole journal as a bare array; the ADR left paging
  them "to be decided separately". This change is that decision.
- **The remote server turns the size into a failure.** The proxy refuses an upstream answer above
  `MAX_PROXY_RESPONSE_BYTES` with `502 UPSTREAM_RESPONSE_TOO_LARGE`, and the audit route offers no `limit` to get under
  it: a journal that grows past the bound becomes unreadable remotely, for good.

**The audit's remedy does not work as written.** It proposes a cursor on `idx_policy_audit_pack(pack_id, at, id)`.
The column `at` holds `Instant.toString()`, whose text does not sort by time when the fraction varies in length; the
capability `policy-audit-order` exists because of it, the store's own comment says so (`:555-558`), and ADR-0107
rejected `LIMIT`/`OFFSET` in the port for that reason. A page taken in the order of that index is a page of the wrong
records. Bounding the read needs an order the database can follow first.

**Retention.** The audit also asks for a retention policy. The two audits are evidence, and the manifest describes the
policy one as append-only. Deleting from them is a decision about what the product keeps as proof, not a performance
fix; it is recorded here as a decision to take, and no purge is specified.

Sizes were not measured, by the audit or here: one policy audit record per configuration write, one lifecycle record
per transition, one archive row per source file that disappears or moves at a synchronisation.

## What Changes

- The policy audit is read from the store one page at a time, in the order `policy-audit-order` defines, on both
  stores.
- HTTP and the CLI page it with the vocabulary and the bounds MCP already uses; the OpenAPI declares them.
- No production code can read a whole journal: the two lists without a reader stay unreachable until they are bounded.
- No product code deletes from a journal, and a decision record says what is kept and why.

## Capabilities

### New Capabilities

- `append-only-journal-reads`: a journal that only grows is read by pages bounded at the store, is never read whole by
  a public surface, and is not deleted from without a recorded decision.

### Modified Capabilities

(none - `policy-audit-order` keeps its text; a page is a slice of the order it defines)

## Impact

- `morpheus-application` (`PolicyPackStore`, `PolicyPackService`), `morpheus-store-sqlite`, `morpheus-store-memory`,
  `morpheus-api`, `morpheus-cli`, `morpheus-mcp` (the page is taken at the store instead of in memory).
- **Contract change**: `GET /api/v1/policy-packs/{id}/audit` and `policy audit` answer with a page instead of a bare
  array. `docs/developer/reference/openapi/morpheus-v1-policy-m25.yaml`, the notes of `policy.audit` in
  `contracts/public-surfaces.tsv`, the release notes, `docs/user/POLICY_PACKS.md`, `docs/user/CLI.md`,
  `docs/developer/POLICY_PLATFORM.md`.
- ADR-0107 amended (the page reaches HTTP and the CLI; the bound applies at the source). A new ADR for what the
  journals keep. Possibly a schema migration (design, decision 2), which then follows `sqlite-schema-history`,
  ADR-0110 and `state-the-schema-upgrade-and-its-way-back`.
- Measured against the journal-page budget of `declare-a-budget-before-bounding-a-cost`, if that budget is kept.
- After the three database changes of sprint 5. Before sprint 7's AUD-PRF-13 (the same two reads sorted twice) and
  beside sprint 13's AUD-PRF-05 (a page carried by the requirement port): whichever lands first fixes how a store port
  spells a page, and the other reuses it.

# Proposal

## Why

The tool audit of 2026-10-08 (`docs/audits/AUDIT_OUTILLE_2026-10-08.md`, finding PIT-AUD-5) found surviving mutations
concentrated in six classes and asked for them to be examined one mutation at a time before becoming tasks. That
examination is done: the 169 surviving or uncovered mutations of `MultiProviderCompositionService`,
`QueryExecutionService`, `NormalizedProjectContent`, `SqlitePolicyPackStore`, `SqlitePortfolioStore` and
`SyntheticJsonParser` were each read against the mutated source (`origin/develop` at `3ec3ea46`) and qualified
(`qualification.md`).

| Verdict | Count |
|---|---|
| missing test | 146 |
| equivalent | 13 |
| redundant code | 6 |
| unreachable with valid input | 3 |
| **defect** | **1** |

Two defects were confirmed by reading the code, one revealed by a surviving mutation and one found beside them:

- **Provider identifiers do not survive a round trip through the SQLite portfolio store.** `ProviderId` only trims
  and refuses a blank value, so it accepts an embedded line break; `SqlitePortfolioStore` joins a membership's
  providers with `"\n"` and splits on it when reading back. The HTTP and MCP `providers` argument is split on commas
  only, so `"openspec\nmarkdown"` is registered as one provider and read back from SQLite as two. The memory store
  keeps it as one: the two stores disagree.
- **The policy audit is not returned in chronological order.** `SqlitePolicyPackStore.listAudit` orders by `at`,
  stored as `Instant.toString()` text whose fractional part has a variable length: `…:00.500100Z` sorts before
  `…:00.500Z` though it is later. Ties fall back to a UUIDv7 whose low bits are random. The memory store returns
  insertion order.

## What Changes

- The two defects are fixed, each after a test reproduces it.
- The decisions the examination leaves open are taken (design, "Open decisions").
- The missing tests are written, class by class, each asserting the message or the value that kills its mutations.
- The same PIT command is replayed on the six classes, and every survivor left is an equivalent mutation the
  qualification names.

## Capabilities

### New Capabilities

- `portfolio-provider-identity`: a portfolio membership reads back the providers it was registered with.
- `policy-audit-order`: a policy audit is returned in the order it was written.

### Modified Capabilities

(none — `openspec/specs/` holds no capability yet)

## Impact

- `morpheus-domain` or the portfolio entry points (provider identifiers), `morpheus-store-sqlite`
  (`SqlitePortfolioStore`, `SqlitePolicyPackStore`, possibly a migration for the audit order).
- Tests in `morpheus-application`, `morpheus-provider-synthetic`, `morpheus-store-sqlite`.
- Possibly `MultiProviderCompositionService` and `SyntheticJsonParser` (redundant code), depending on the decisions.

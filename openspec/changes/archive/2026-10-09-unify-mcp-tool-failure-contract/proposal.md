# Proposal

## Why

ADR-0102 states that an MCP handler maps every exception it can reach. Two paths break that rule at `3ec3ea46`
(audit `docs/audits/AUDIT_OUTILLE_2026-10-08.md`, findings MCP-AUD-1 and MCP-AUD-2, confirmed by reading):

- Opening a SQLite operation scope throws `IllegalStateException` (driver failure wrapped by
  `SqliteConnectionScope.open`, or "SQLite database is reserved for exclusive maintenance" from the lease). Handlers
  that touch a store but map only `IllegalArgumentException | KnowledgeStoreException` — `MorpheusMcpServer` among
  them — let it escape, and the SDK answers with a JSON-RPC protocol error instead of a tool result with `isError`.
  **Refuted by execution on 8 October 2026** (tasks 1.1 and 1.2): no MCP handler opens a `SqliteConnectionScope`;
  every store it opens goes through `SqliteStoreConnection.open`, which already wraps any `RuntimeException` —
  the lease refusal and a driver failure included — into `KnowledgeStoreException`. Every store-touching tool
  already answers `isError` with `Cannot initialize SQLite … store`. The audit's reading missed that wrapping.
- The handler deadline (4 min) is held by a gate at twice the longest peer timeout (2 × 120 s), but one MINOS or
  NEXUS operation sends three to four sequential requests, each bounded by that timeout. A slow peer within its
  configured bound can therefore hit the handler deadline and close the MCP session, which the deadline's own
  Javadoc says cannot happen.

## What Changes

- The contract that holds today is pinned: a test calls every published tool, with arguments its own schema
  accepts, against a store the driver cannot read and against a store reserved for maintenance, and requires a
  refusal from every handler that reaches the store. Narrowing one handler's `catch` fails it. No production change
  to the store boundary.
- One gateway operation towards a peer gets a single overall deadline, and the handler deadline is derived from the
  number of sequential peer requests per operation; the gate checks that composition instead of "twice one request".

## Capabilities

### New Capabilities

- `mcp-tool-failure-contract`: what an MCP client receives when a MORPHEUS tool cannot complete, and how long a tool
  call can last before MORPHEUS answers.

### Modified Capabilities

(none — `openspec/specs/` holds no capability yet)

## Impact

- `morpheus-mcp`: tests only (`McpStoreFailureInjectionTest`, `McpSchemaArguments`).
- `morpheus-mcp-transport`: `BoundedStdioServerTransportProvider.DEFAULT_HANDLER_DEADLINE` and its Javadoc.
- `morpheus-integration-minos`, `morpheus-integration-nexus`: gateway operation deadline.
- `morpheus-architecture-tests`: `ProductionIntegrityContractTest#theHandlerSafetyBoundKeepsTwiceTheLongestConfigurablePeerTimeout`,
  replaced by `#theHandlerSafetyBoundOutlastsTheLongestPeerOperation`.
- `morpheus-mcp-transport`: new `PeerOperationDeadline` (the envelope every peer integration fits in).
- `docs/user/CLI.md`: the handler bound is nine minutes.
- ADR-0102 and ADR-0106 amendments.

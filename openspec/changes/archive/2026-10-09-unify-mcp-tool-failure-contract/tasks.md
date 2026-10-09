# Tasks

## 1. Reproduce before fixing

- [x] 1.1 Add a `morpheus-mcp` test that calls `get_current_specification` (through `MorpheusMcpServer`) while the database lease is held exclusively; verify today the call ends in a protocol error instead of `isError`
  - 2026-10-08: **not reproduced** — the call answers `isError` with `Cannot initialize SQLite knowledge store` (no path, no chain)
- [x] 1.2 Add a test where opening the SQLite scope fails (driver failure injected or unreadable file); verify today the error escapes the handler
  - 2026-10-08: **not reproduced** — same refusal; MCP-AUD-1 refuted, see design decision 1
- [x] 1.3 Add a gate test computing the worst-case MINOS and NEXUS operation duration from the gateway request sequence and the maximum timeout; verify it fails against the current 4-minute bound

## 2. Store boundary

- [x] 2.1 ~~Surface store-opening failures as `KnowledgeStoreException` subtypes~~ — dropped: the failures already surface as `KnowledgeStoreException` on the MCP path (design decision 1)
- [x] 2.2 Add the per-handler failure-injection test of design decision 2 for every tool class of ADR-0102 axis 2; verify it fails if one handler's catch set is narrowed
  - 2026-10-08: `McpStoreFailureInjectionTest` green; narrowing `MorpheusMcpServer.call` or `MorpheusPortfolioMcpTools` to `IllegalArgumentException` fails it, naming the tool

## 3. Peer operation deadline

- [x] 3.1 Add an overall operation deadline in the MINOS and NEXUS gateways; verify with a fixture peer answering each request just under the timeout that the result arrives and the session stays open
- [x] 3.2 Derive `DEFAULT_HANDLER_DEADLINE` from the gateway constants, fix its Javadoc, and replace the "twice one timeout" gate with the composition of 1.3; verify the gate passes

## 4. Documentation

- [x] 4.1 Amend ADR-0102 (store-opening failures) and ADR-0106 (deadline composition); verify `AdrIndexCoherenceTest` passes

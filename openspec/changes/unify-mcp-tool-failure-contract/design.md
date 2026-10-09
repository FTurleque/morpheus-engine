# Design

## Context

ADR-0102 §2 derives the mapped exception set from what a handler can reach ("store → + KnowledgeStoreException",
"service refusing a state → + IllegalStateException"). It did not account for `SqliteConnectionScope.open`, which
wraps every `SQLException` into `IllegalStateException`, nor for `SqliteDatabaseLease.acquireShared`, which throws
`IllegalStateException` outside any `try` in `SqliteDatabaseSecurity.openPhysical`. ADR-0102 §3 keeps
`MorpheusProviderPluginMcpTools` on `catch (RuntimeException)` as a redaction boundary. The SDK maps an exception
escaping the handler to error `-32603`. `BoundedStdioServerTransportProvider.DEFAULT_HANDLER_DEADLINE` is 4 min; the
MINOS and NEXUS gateways each send `initialize`, `tools/list` and one or two `tools/call`, each bounded by a timeout
of at most 120 s, with no overall operation deadline.

## Goals / Non-Goals

**Goals:** prove — and pin — that every store-touching handler maps every exception the store can raise; make the
handler bound a derived quantity a test can recompute.

**Non-Goals:** changing the HTTP failure mapping (already a generic 409/500); redesigning the peer protocol.

## Decisions

1. **No change at the store boundary — the defect does not exist on the MCP path** (decision of the maintainer,
   8 October 2026, after tasks 1.1 and 1.2 failed to reproduce it). The `IllegalStateException` thrown by
   `SqliteConnectionScope.open` and by the lease is real, but no MCP handler opens a scope: each store is opened by
   `SqliteStoreConnection.open`, which converts any `RuntimeException` into `KnowledgeStoreException`. HTTP and CLI,
   which do open scopes, map `IllegalStateException` and `KnowledgeStoreException` identically. Converting the type
   anyway was considered and rejected: it would change a type three store tests pin, for no observable defect.
2. **One test injects store failures into every published tool** (driver cannot read the file; database reserved
   for exclusive maintenance), with arguments derived from each tool's own schema and checked against the SDK
   validator, and asserts `isError`, a store refusal, and a message free of `Caused by` and absolute paths. The tool
   classes are listed per name and the list is compared with what the server serves.
3. **Overall operation deadline in the gateways** equal to the configured timeout times the number of sequential
   requests, plus a fixed start-up allowance; the handler bound becomes `max operation deadline + margin`, and the
   architecture gate recomputes it from the gateway constants.

## Risks / Trade-offs

- [A longer handler bound delays detection of a truly stuck handler] → it stays bounded and is now derived, not
  guessed.

# Proposal

## Why

AUD-TRV-05 (sprint 5, High) reports that the SQLite connection policy is a property of each composition site, not of the
module. Re-read on `develop` at `bd2294b6` on 2026-10-13, the finding **holds**:

- 16 files of the three adapters instantiate a `Sqlite*` store: 7 in `morpheus-api`, 5 in `morpheus-mcp`, 4 in
  `morpheus-cli`.
- Only three places open a connection scope: `ApiRuntime.java:42`, the inner runtime of
  `MorpheusQueryApiService.java:261`, and `CliRuntime.java:38`. Every other site opens stores with no scope.
- `MorpheusMcpRuntime`'s own Javadoc says "Each store opens its own SQLite connection, and the constructor opens seven
  of them", and it is built at six places. `ApiRuntime` says "Nine logical stores share exactly one physical,
  thread-confined SQLite connection and one schema check".
- The scope is ambient and cannot nest: `SqliteConnectionScope` holds it in a `ThreadLocal` and throws
  "nested SQLite connection scopes are not supported".
- `.claude/rules/security.md` requires connections to be opened through `SqliteConnectionScope.open(databasePath)`, and
  no test, structural or textual, verifies it. ADR-0109 names 32 composition roots.

An MCP tool call opens seven physical connections and replays seven schema checks where the same operation through HTTP
or the CLI opens one: two surfaces the convergence manifest declares equivalent put different pressure on the database
and have different transactional semantics. And two runtimes that each open a scope cannot run on one thread, which
nothing tests.

## What Changes

- Each adapter has one runtime type, parameterised by the stores an operation needs, that always opens the scope -
  starting with the MCP runtime.
- A rule holds it: a class of an adapter that builds a SQLite store does so inside a scope, or receives its stores from
  the runtime.
- The refusal to nest becomes a tested property.

## Capabilities

### New Capabilities

- `sqlite-connection-policy`: one physical connection and one schema check per operation, on every transport, enforced.

### Modified Capabilities

(none)

## Impact

- `morpheus-mcp` (the runtime and four tool classes), `morpheus-api` (five classes), `morpheus-cli` (three classes),
  `morpheus-store-sqlite` (the scope).
- ADR-0109 is clarified: its list of composition roots shrinks. `.claude/rules/security.md` and `architecture.md`.
- With `keep-the-scheduler-free-while-sqlite-waits`, before `admit-local-requests-before-they-meet-the-database`.

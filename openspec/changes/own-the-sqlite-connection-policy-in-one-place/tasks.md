# Tasks

Started on 2026-10-13 with the MCP runtime, which the design names as its own lot. The other adapters, the rule and
the amendment of ADR-0109 are not done; each task says where it stands.

## 1. Measure before changing

- [x] 1.1 List every class of the three adapters that builds a SQLite store, whether a scope is open there, and how many
      connections one operation opens. Proof: the table; on 2026-10-13, 16 files and three scope openings.
      *2026-10-13: fourteen classes of the three adapters contain a `new Sqlite...Store(`, not sixteen. Five open a scope: `ApiRuntime`, the inner runtime of `MorpheusQueryApiService`, `CliRuntime`, and since this change `MorpheusMcpRuntime` and `MorpheusCompositionMcpTools`. Nine build one store per operation and open none: `MorpheusLocalHttpServerBootstrap`, `MorpheusOperabilityApiService`, `MorpheusPolicyManagementHttpRoutes` (two routes, one store each), `MorpheusPortfolioApiService`, `MorpheusMcpServer`, `MorpheusPolicyMcpManagementTools`, `MorpheusPortfolioMcpTools`, `CliProjectWriteCapabilityResolver`, `MorpheusPortfolioCli`. After the MCP runtime, exactly one site still opened two connections for one operation: the composition tools.*
- [ ] 1.2 Count the physical connections and schema checks of one MCP tool call, one HTTP request and one CLI command
      for the same operation. Proof: the three numbers, read from the contention metrics or a counting driver.
      *2026-10-13: only the MCP number, and only one side of it. The scope's diagnostics count the connections a scope opens: zero for the old runtime, which opened none through a scope, one for the new. The seven of the old runtime are its own Javadoc's count, not a measurement of mine.*
- [x] 1.3 Classify the sites that act on the database file itself (backup, restore, lease). Proof: the list of
      decision 3 with its reasons.
      *2026-10-13: the two start-up recoveries (`MorpheusLocalHttpServerBootstrap`, `MorpheusMcpServer`) are in the list of 1.1 as single-store sites. The maintenance commands of `MorpheusServerCli` build no store: they act on the file through `SqliteServerMaintenance`.*

## 2. Reproduce before fixing

- [x] 2.1 A test that an MCP tool call opens one connection. Proof: red on `HEAD` (seven).
      *2026-10-13: `MorpheusMcpRuntimeConnectionTest`. Red on `HEAD`, though not with seven: the test reads the scope's diagnostics, and the old runtime opened no scope, so it read zero where one is required.*
- [x] 2.2 The structural rule and its textual twin. Proof: red on `HEAD`, naming the sites of 1.1 without a scope.
      *2026-10-13: `SqliteConnectionScopeUseArchitectureTest`, textual only. Red before the composition tools were changed, naming them. **Departure**: no structural twin. Depending on the scope class says nothing about whether two constructions happen under one scope, which is the property; the textual rule reads each try-with-resources instead.*
- [x] 2.3 The two nesting tests. Proof: the first passes on `HEAD` with an unnamed message; the second is new.
      *2026-10-13: both, on the MCP runtime - a second runtime while the first is open is refused by name and the first still works; two runtimes in sequence on one thread both work.*

## 3. The change

- [x] 3.1 The MCP runtime inside a scope. Proof: 2.1 passes; the MCP tool tests pass unmodified.
      *2026-10-13: `MorpheusMcpRuntime` opens a scope, then its seven stores inside it. **One thing the MCP tool tests caught**: opened through a scope, a database that cannot be opened raised `IllegalStateException`, which no tool handler answers, where the first store used to raise `KnowledgeStoreException`. The runtime translates it, and lets the nesting refusal through. All 147 MCP tests pass; the two of `McpStoreFailureInjectionTest` failed before the translation.*
- [ ] 3.2 One runtime per adapter; the inner runtime of the query service absorbed; the remaining sites. Proof: 2.2
      passes; `AdapterCompositionRootArchitectureTest` green with a shorter list.
      *2026-10-13: **not done as specified, and deliberately.** One runtime type per adapter would move every named composition root to correct the one site that 1.1 found. The composition tools open a scope; the rule of 2.2 holds the invariant for every class. `AdapterCompositionRootArchitectureTest` is green with the same list: no class stopped reaching a concrete store. The inner runtime of the query service is not absorbed.*
- [x] 3.3 The named refusal of nesting. Proof: 2.3 passes.
      *2026-10-13: `SqliteConnectionScope.NESTED_REFUSAL`, in both places that refuse.*
- [x] 3.4 The amendment of ADR-0109; `.claude/rules/security.md` and `architecture.md`.
      *2026-10-13: ADR-0109 amended with the rule and with why its list does not shrink here; `.claude/rules/architecture.md` and `security.md`.*
- [x] 3.5 Break each rule once, then revert.
      *2026-10-13: the rule was red on the composition tools before they were changed. The nesting refusal is asserted by a test that opens a second runtime inside the first.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The persistence parity tests and the store tests of the pre-migration copy. Proof: green unmodified.
- [ ] 4.3 M19: `scripts\validate.cmd m19`. Proof: budgets unchanged - fewer connections should not cost.
- [ ] 4.4 Pull request description: the finding, the table of 1.1, the numbers of 1.2 before and after.

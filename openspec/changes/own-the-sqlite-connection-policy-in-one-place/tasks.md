# Tasks

Started on 2026-10-13 with the MCP runtime, which the design names as its own lot. The other adapters, the rule and
the amendment of ADR-0109 are not done; each task says where it stands.

## 1. Measure before changing

- [ ] 1.1 List every class of the three adapters that builds a SQLite store, whether a scope is open there, and how many
      connections one operation opens. Proof: the table; on 2026-10-13, 16 files and three scope openings.
- [ ] 1.2 Count the physical connections and schema checks of one MCP tool call, one HTTP request and one CLI command
      for the same operation. Proof: the three numbers, read from the contention metrics or a counting driver.
      *2026-10-13: only the MCP number, and only one side of it. The scope's diagnostics count the connections a scope opens: zero for the old runtime, which opened none through a scope, one for the new. The seven of the old runtime are its own Javadoc's count, not a measurement of mine.*
- [ ] 1.3 Classify the sites that act on the database file itself (backup, restore, lease). Proof: the list of
      decision 3 with its reasons.

## 2. Reproduce before fixing

- [x] 2.1 A test that an MCP tool call opens one connection. Proof: red on `HEAD` (seven).
      *2026-10-13: `MorpheusMcpRuntimeConnectionTest`. Red on `HEAD`, though not with seven: the test reads the scope's diagnostics, and the old runtime opened no scope, so it read zero where one is required.*
- [ ] 2.2 The structural rule and its textual twin. Proof: red on `HEAD`, naming the sites of 1.1 without a scope.
- [x] 2.3 The two nesting tests. Proof: the first passes on `HEAD` with an unnamed message; the second is new.
      *2026-10-13: both, on the MCP runtime - a second runtime while the first is open is refused by name and the first still works; two runtimes in sequence on one thread both work.*

## 3. The change

- [x] 3.1 The MCP runtime inside a scope. Proof: 2.1 passes; the MCP tool tests pass unmodified.
      *2026-10-13: `MorpheusMcpRuntime` opens a scope, then its seven stores inside it. **One thing the MCP tool tests caught**: opened through a scope, a database that cannot be opened raised `IllegalStateException`, which no tool handler answers, where the first store used to raise `KnowledgeStoreException`. The runtime translates it, and lets the nesting refusal through. All 147 MCP tests pass; the two of `McpStoreFailureInjectionTest` failed before the translation.*
- [ ] 3.2 One runtime per adapter; the inner runtime of the query service absorbed; the remaining sites. Proof: 2.2
      passes; `AdapterCompositionRootArchitectureTest` green with a shorter list.
- [x] 3.3 The named refusal of nesting. Proof: 2.3 passes.
      *2026-10-13: `SqliteConnectionScope.NESTED_REFUSAL`, in both places that refuse.*
- [ ] 3.4 The amendment of ADR-0109; `.claude/rules/security.md` and `architecture.md`.
- [ ] 3.5 Break each rule once, then revert.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The persistence parity tests and the store tests of the pre-migration copy. Proof: green unmodified.
- [ ] 4.3 M19: `scripts\validate.cmd m19`. Proof: budgets unchanged - fewer connections should not cost.
- [ ] 4.4 Pull request description: the finding, the table of 1.1, the numbers of 1.2 before and after.

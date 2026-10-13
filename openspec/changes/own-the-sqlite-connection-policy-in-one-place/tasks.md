# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List every class of the three adapters that builds a SQLite store, whether a scope is open there, and how many
      connections one operation opens. Proof: the table; on 2026-10-13, 16 files and three scope openings.
- [ ] 1.2 Count the physical connections and schema checks of one MCP tool call, one HTTP request and one CLI command
      for the same operation. Proof: the three numbers, read from the contention metrics or a counting driver.
- [ ] 1.3 Classify the sites that act on the database file itself (backup, restore, lease). Proof: the list of
      decision 3 with its reasons.

## 2. Reproduce before fixing

- [ ] 2.1 A test that an MCP tool call opens one connection. Proof: red on `HEAD` (seven).
- [ ] 2.2 The structural rule and its textual twin. Proof: red on `HEAD`, naming the sites of 1.1 without a scope.
- [ ] 2.3 The two nesting tests. Proof: the first passes on `HEAD` with an unnamed message; the second is new.

## 3. The change

- [ ] 3.1 The MCP runtime inside a scope. Proof: 2.1 passes; the MCP tool tests pass unmodified.
- [ ] 3.2 One runtime per adapter; the inner runtime of the query service absorbed; the remaining sites. Proof: 2.2
      passes; `AdapterCompositionRootArchitectureTest` green with a shorter list.
- [ ] 3.3 The named refusal of nesting. Proof: 2.3 passes.
- [ ] 3.4 The amendment of ADR-0109; `.claude/rules/security.md` and `architecture.md`.
- [ ] 3.5 Break each rule once, then revert.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The persistence parity tests and the store tests of the pre-migration copy. Proof: green unmodified.
- [ ] 4.3 M19: `scripts\validate.cmd m19`. Proof: budgets unchanged - fewer connections should not cost.
- [ ] 4.4 Pull request description: the finding, the table of 1.1, the numbers of 1.2 before and after.

# Tasks

## 1. Measure before changing

- [x] 1.1 List every command path the CLI routes today, by reading `MorpheusMain.run`, `MorpheusCli.dispatch` and each
      sub-command's `handles` and `switch`, and by invoking each path. Proof: the list with file and line. Do not count
      `case` literals: on 2026-10-13 there are 88 distinct ones and they mix commands, actions and options.
- [x] 1.2 Extract the `cli` column: rows, sentinels, named commands. Proof: the three counts; on 2026-10-13 at
      `8b26d3d7`, 86 rows, 15 sentinels, 71 commands (the audit: 89 rows, 74 commands). Re-measure.
- [x] 1.3 Compare 1.1 and 1.2 in both directions. Proof: the three lists — cells naming no served command, served
      commands without a row, sentinels on a served command. On 2026-10-13 the last holds `portfolio.list`,
      `portfolio.members`, `portfolio.conflicts`; the second holds at least `paths`, `analyze-change`, `quality`,
      `product-info`, `composition sync`, `constraints evaluate`, `api`, `mcp`.
- [x] 1.4 Compare 1.1 with the usage lines of the embedded help. Proof: the served paths the help does not document.
- [x] 1.5 Read the tests that pin exact lines of the manifest (`ProductionIntegrityContractTest` and the
      `publicManifestAndOpenApiExposeSame*IntentFamilies` tests). Proof: the lines that must not be reworded.

## 2. Reproduce before fixing

- [x] 2.1 Write the convergence gate against the list of 1.1 held in the test. Proof: red on `HEAD`, naming the entries
      of 1.3.
- [x] 2.2 Write the help comparison. Proof: red on `HEAD`, naming the entries of 1.4.
- [x] 2.3 Write the routing proof — every listed path is not refused as unknown, an unlisted one is. Proof: green for
      the list of 1.1, which shows the list equals what the CLI routes before anything consults it.

## 3. The change

- [x] 3.1 Move the list into `morpheus-cli` as the declaration and make the dispatch refuse an undeclared path first.
      Proof: 2.3 passes against the declaration; an action added to a `switch` alone is refused as unknown.
- [x] 3.2 Correct the manifest: the three portfolio cells and their notes; a row for each served command that is a
      capability, with a real surface or a sentinel in the `mcp` and `http` columns after reading what the MCP server
      and the route table serve; the exclusions and their reasons for the others. Follow the skill
      `public-capability`. Proof: 2.1 passes; `PublicHttpRouteConvergenceTest` and
      `PublicSurfaceManifestCoversEveryServedToolTest` still pass.
- [x] 3.3 Complete the help. Proof: 2.2 passes; `MorpheusHelpInvocationsParseTest` still passes with the new lines.
- [x] 3.4 Enforcement: the manifest side is a textual assertion and the CLI side an execution, kept together. Intent in
      one sentence: "the commands the manifest names and the commands the CLI serves are the same set" (ADR-0103; skill
      `enforcement-choice`). Suggested home: `morpheus-architecture-tests`, beside the HTTP and MCP gates.
- [x] 3.5 Break each rule once, then revert: rename a command in a cell; add a declared path with no row; put a sentinel
      back on `portfolio.list`; name one command in two rows; add an exclusion for a path with a row; add a `case`
      without declaring it; remove a help line. Proof: the seven named failures.

## 4. Verification and destinations

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning. Build
      `morpheus-provider-reference` before the architecture tests.
- [ ] 4.2 Amend ADR-0089 through the skill `adr-author`; check the highest ADR number before any new file.
- [x] 4.3 `.claude/rules/governance.md`: replace the sentence saying neither gate checks the `cli` column by the
      description of the gate, its exclusions and what it does not prove. `.claude/skills/public-capability/SKILL.md`:
      the step that declares a command.
- [x] 4.4 User documentation of each command that becomes documented, in `docs/user/` only, without a gate name or a
      path under `src/` (`rules/documentation.md`).
- [x] 4.5 If the presence ratchets are raised, follow `live-numbers` and `coverage-ratchet`.
- [x] 4.6 Pull request description: the finding, the lists of 1.3 and 1.4, every manifest line added or changed with its
      reason, the exclusions, the justification of the gate added under `morpheus-architecture-tests/`.

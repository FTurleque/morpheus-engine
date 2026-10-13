# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Re-count on the lot's base: lines, imports, dispatched commands and private records of `MorpheusCli`;
      lines, dispatched tools and projection methods of `MorpheusMcpToolService`; capability classes of each
      adapter. Proof: the table; on 2026-10-13 at `8b26d3d7`, 908 lines / 67 imports / 13 commands besides `help` and
      `version` / 14 records; 412 lines / 14 tools; 14 and 12 capability classes.
- [ ] 1.2 For each command and each tool, list the tests that pin its text output, its JSON output and one refusal.
      Proof: the table, with the gaps. `MorpheusCliTest` held 17 tests on 2026-10-13.
- [ ] 1.3 List the application types each dispatch class uses for dispatch itself (failure types, product metadata)
      and those it uses to serve a command. Proof: the two lists, measured with ArchUnit on the compiled classes,
      not by reading imports.
- [ ] 1.4 Say, for each field of `MorpheusMcpRuntime` and `CliRuntime`, the concrete type it exposes and the
      application port that type implements. Proof: the table. State whether
      `own-the-sqlite-connection-policy-in-one-place` has already changed it.
- [ ] 1.5 Measure the precedence of the `handles(args)` chain of `MorpheusMain`: is any argument vector claimed by
      two classes today? Proof: a test that enumerates the first words of the help and counts the claimants.
- [ ] 1.6 Propose the grouping of the thirteen commands and fourteen tools into capability classes from the families
      of `contracts/public-surfaces.tsv`. Proof: the table, in the pull request of 2.1.

## 2. Characterise before extracting

- [ ] 2.1 The missing tests of 1.2, on `experiments/m0/fixtures/`, with `morpheus-store-memory` or
      `morpheus-provider-synthetic` wherever the command allows it. Their own pull request, green on the base, no
      production line changed. A refusal is asserted by `assertThrows` or exit code **and** by its message.
- [ ] 2.2 The exhaustiveness test of the dispatch against the help and against the tool catalogue. Proof: green on
      the base; red when a help line is added by hand without a claimant; revert.

## 3. The change

- [ ] 3.1 The runtime hands out application ports where 1.4 found concrete types read by a future capability class.
      Proof: the tests of 2.1 pass unmodified; if a root no longer reaches a concrete store, its name leaves
      `COMPOSITION_ROOTS` in the same pull request, with the dated amendment of ADR-0109.
- [ ] 3.2 The capability package of each adapter, and the classes of 1.6, one family per pull request, read-only
      families first. Proof per pull request: 2.1 and 2.2 untouched and green,
      `CliOptionParsingRefusesUnknownOptionsTest` and `MorpheusHelpInvocationsParseTest` untouched and green.
- [ ] 3.3 `sync` and `projects`, after `ingest-a-project-through-the-provider-port`.
- [ ] 3.4 The dispatcher rule, as an ArchUnit rule with the exact allowance of 1.3. Intent: *a class that dispatches
      depends on no use case of the application* - a dependency. Check the three obligations of `enforcement-choice`:
      the classes are in the imported set; none of the banned types is reached through a compile-time constant (or
      the text scan is added beside the rule); the rule is broken once.
- [ ] 3.5 Break once, then revert: a command implemented in the dispatch class of `cli`; a tool implemented in the
      server class of `mcp`; an allowance left unused. Proof: the three named failures.

## 4. Documentation and decision records

- [ ] 4.1 The convention in the ADR of `express-adapter-internal-roles-as-packages`, or its own ADR if that change
      was not taken (re-read `docs/architecture/adr/0*.md` for the next free number; line in the `README.md`).
- [ ] 4.2 `.claude/rules/architecture.md` (the adapters' shape), `.claude/CLAUDE.md` ("Points d'entree cles"),
      `.claude/skills/public-capability/SKILL.md` (where a new command or tool goes), the `docs/developer/` pages of
      the CLI and MCP adapters. A file of `docs/` that moves gets its line in `docs/project/README.md`.
- [ ] 4.3 The amendments CLI-6 and CLI-8 of ADR-0103 are dated records: not rewritten. If a parser family they name
      changes owner, a new dated note says so.

## 5. Verification

- [ ] 5.1 `./mvnw clean verify` and `./mvnw dependency:analyze` on Linux and Windows; changed-line coverage gate.
- [ ] 5.2 `scripts\validate.cmd m28 -Version <current>` and `validate-m28.sh`: the MCP client integration starts the
      real server.
- [ ] 5.3 `contracts/public-surfaces.tsv`, the OpenAPI files and the embedded help byte-identical to the base.
      Proof: `git diff --stat`.
- [ ] 5.4 The executed-test count of `morpheus-architecture-tests` before and after, read in a validation summary:
      higher. `architectureTestsMinimum` is raised by the `coverage-ratchet` procedure, in its own commit.
- [ ] 5.5 Pull request description: the finding, the tables of 1.1 to 1.6, the composition roots before and after.

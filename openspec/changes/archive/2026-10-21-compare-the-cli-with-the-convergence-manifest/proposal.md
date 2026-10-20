# Proposal

## Why

AUD-TST-06 (sprint 8) reports that the `cli` column of the convergence manifest is checked only for being non-empty,
where the `http` and `mcp` columns each have a gate that compares in both directions. Re-read on `develop` at
`8b26d3d7` on 2026-10-13, the finding **holds, and the column is already false**:

- `ProductionIntegrityContractTest.java:79` asserts `columns[2]` is not blank, then pins three literal lines. No other
  test reads the column: `PublicHttpRouteConvergenceTest` compares the `http` column,
  `PublicSurfaceManifestCoversEveryServedToolTest` the `mcp` column, and `.claude/rules/governance.md` says so.
- `contracts/public-surfaces.tsv` holds 86 capability rows today (the audit: 89); 15 carry a sentinel in the `cli`
  column and 71 name a command.
- **Three sentinels are contradicted by the code.** Rows `portfolio.list`, `portfolio.members` and
  `portfolio.conflicts` say `EXPLICITLY_NOT_EXPOSED` for the CLI, with the note "HTTP-only today".
  `MorpheusPortfolioCli.java:110`, `:112` and `:121` serve `portfolio list`, `portfolio members` and
  `portfolio conflicts`.
- **Served commands have no row.** `paths`, `analyze-change` and `quality` (`MorpheusCli.java:151`, `:162`, `:163`),
  `product-info` (`MorpheusProductCli.java:40`), `composition sync` (`MorpheusCompositionCli.java:60`),
  `constraints evaluate` (`MorpheusConstraintSemanticsCli.java:25-29`), and the launchers `api` and `mcp`.
- **There is no list of commands to compare with.** `MorpheusMain.main` recognises three launch forms (`:35-39`), `MorpheusMain.run` then asks thirteen sub-command
  classes in turn whether they handle the arguments (`:71-102`), and each of them, like `MorpheusCli.dispatch`
  (`:143-164`), routes with its own `switch`. The audit's count of 88 `case "…"` literals mixes commands, actions and option names. HTTP has
  `MorpheusHttpRouteTable`; the CLI has nothing of the kind.
- **The embedded help is a third list, also incomplete.** `MorpheusMain.java:160-164` documents five `portfolio`
  actions; the command serves eleven. `MorpheusHelpInvocationsParseTest` proves that what the help documents is
  accepted, not that what is accepted is documented.

The manifest is presented as the law of convergence. On the transport the user types into, it describes a product that
is not the one shipped, and nothing would have said so.

## What Changes

- The CLI declares its command paths once; the dispatch refuses a path that is not declared, and every declared path is
  routed.
- A gate compares that declaration with the `cli` column in both directions: a served command without a row, a row
  naming a command that is not served, and a sentinel on a capability whose command is served each fail the build.
- The manifest is corrected: the three false sentinels name their command, and each served command gets a row or a
  written exclusion, under the rule the maintainer set for HTTP on 2026-10-08 (the manifest lists capabilities;
  operability entry points are reasoned exclusions).
- The embedded help documents every declared command path.
- `.claude/rules/governance.md` and the skill `public-capability` describe the comparison that exists.

## Capabilities

### New Capabilities

(none)

### Modified Capabilities

- `public-surface-convergence`: requirements added for the CLI transport — the declared command table, the comparison
  with the `cli` column in both directions, and the help. The existing HTTP requirements are unchanged.

## Impact

- `morpheus-cli`: one declaration of the command paths, consulted by the dispatch; the help text.
- `contracts/public-surfaces.tsv`: three cells corrected, rows added — a governance file, justified in the pull
  request. No OpenAPI change is expected; a new row whose capability has an HTTP route names the route that exists.
- `morpheus-architecture-tests`: one convergence gate for the CLI, beside the HTTP and MCP ones.
- ADR-0089 (production integrity and public surface convergence) is amended: the CLI joins the transports whose served
  set is declared once and compared. The skill `adr-author` decides between an amendment and a reference.
- `.claude/rules/governance.md`, `.claude/skills/public-capability/SKILL.md`, `docs/user/CLI.md` or
  `docs/user/PORTFOLIOS.md` where a command becomes documented.
- Presence ratchets may rise; raised from a measurement on both platforms only.
- Lands before `share-one-argument-base-across-cli-subcommands` (AUD-QUA-06, sprint 9) and
  `give-each-cli-command-and-mcp-tool-a-capability-class` (AUD-ARC-09, sprint 12), which move the code that routes the
  commands: the latter notes that no gate would notice a command that changed while it was being moved. This change
  adds the table and the gate; it does not unify the parsers and does not move a command.

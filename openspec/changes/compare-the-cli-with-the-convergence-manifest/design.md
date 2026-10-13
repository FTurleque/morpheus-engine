# Design

## Context

- A command path is the sequence of words that selects a behaviour before any option: `version`, `portfolio list`,
  `policy pack create`, `server identity migrate-legacy`. Cells of the `cli` column are written that way, sometimes
  followed by an option the command requires (`server restore --confirm`, `update-check --manifest`).
- Routing is spread: `MorpheusMain.run` tests `handles(args)` class by class, then each class switches on its action.
  Some paths are rewritten on the way (`policy pack create` is routed as `pack-create`).
- `MorpheusHttpRouteTable` is the precedent: one list of routes and methods, from which the `Allow` header is computed
  and against which `MorpheusRemoteRoutePolicy` refuses to load. `PublicHttpRouteConvergenceTest` compares that table,
  the manifest and the OpenAPI documents, and also starts a server to prove it routes every entry.
- `PublicSurfaceManifestCoversEveryServedToolTest` is the precedent for a column: a cell names one tool, a tool is named
  once, a sentinel names nothing and so cannot hide a served tool, an empty read is a failure.
- The manifest's own rule: an absence is declared by a sentinel, never implicit; the maintainer decided on 2026-10-08
  that the manifest lists capabilities and that operability probes are reasoned exclusions of the gate.

## Goals / Non-Goals

**Goals**: the commands the CLI serves are known from one declaration; the `cli` column can no longer name a command
that does not exist, omit one that does, or deny one with a sentinel; a command a user can run is in the help.

**Non-Goals**: the options of a command (a cell may keep the required option it shows; the gate compares the path);
unifying the argument parsers of the sub-commands (AUD-QUA-06, sprint 9); a generated help; comparing payloads between
transports (AUD-ARC-10, sprint 9); changing what any command does.

## Decisions

1. **A declared table of command paths, and the dispatch consults it.** The table lives in `morpheus-cli` and lists
   every path. The dispatch refuses, as an unknown command, a path the table does not hold, before any sub-command
   class is asked. A `case` that the table does not reach is then dead code, and a path of the table that no class
   routes is caught by decision 2. *Rejected*: deriving the served set from the `case` literals by reading the sources
   — it is what the audit counted, and it cannot tell a command from an option. *Rejected*: taking the help as the
   source — measured incomplete today. *Rejected*: a table used by the test only — a second list that can lie like the
   manifest does.

2. **The gate proves the table by running the CLI.** For every path of the table, the CLI is invoked with the path and
   no option against an empty store, and the answer must not be the unknown-command or unknown-action refusal; for a
   path absent from the table, it must be. This is what `PublicHttpRouteConvergenceTest` does with a started server,
   and what `MorpheusHelpInvocationsParseTest` already does for the help. Server launch paths are handed to their
   option parser instead of being started, as that test does.

3. **The comparison is in both directions, with named exclusions.** Every cell that is not a sentinel names a path of
   the table; every path of the table is named by exactly one row or by one exclusion with a written reason
   (`help`, `paths`, the `api` and `mcp` launchers are the expected candidates — operability entry points, not
   capabilities). An exclusion for a path that is not in the table fails, so the list cannot rot. Textual on the
   manifest side — a `.tsv` — and executed on the CLI side: the two holds ADR-0103 describes, kept together.

4. **Which served commands get a row is decided by the rule already set for HTTP.** A command that reads or writes
   product knowledge is a capability and gets a row, with a real surface or a sentinel in each of the two other
   columns. `analyze-change`, `quality`, `product-info`, `composition sync` and `constraints evaluate` are in that case
   and their rows are written by the lot after reading what each does and whether an existing row already is that
   capability. The three portfolio rows have their cell corrected and their note rewritten.

5. **The help documents every path of the table.** The same table makes the reverse of
   `MorpheusHelpInvocationsParseTest` a comparison of two sets. It is in this change because the defect was found here
   and costs one assertion once the table exists.

6. **ADR-0089 is amended, not replaced.** The decision — convergence is declared in the manifest and enforced — stands;
   the amendment records that the CLI's served set is now declared once, and why the `case` literals were not used.

## Risks / Trade-offs

- A table consulted by the dispatch is a behaviour change if it is wrong: a command missing from it stops working. The
  mitigation is the order of the tasks — the table is first written as a test-side list and proven equal to what the
  CLI routes today, path by path, before the dispatch consults it.
- Adding rows changes a governance contract. Each row is justified in the pull request; the tests that pin exact lines
  of the manifest (M21, M24, M25, M27) are read before a line they pin is touched.
- "Not an unknown-command refusal" accepts a command that fails for any other reason. That is the limit
  `MorpheusHelpInvocationsParseTest` states for itself; it is restated in the gate.
- A cell that carries an option (`server restore --confirm`) is compared on its path only: an option renamed in the
  parser is not seen here. It is seen by the help test when the help documents it.

## Open Questions

1. **Row or exclusion for `paths`, `api` and `mcp`?** Recommendation: exclusions with a reason — they start or describe
   the process and expose no capability, as the health and readiness probes do for HTTP.
2. **`product-info` and `version`: one capability or two?** The row `product.version` names `version` for the CLI and
   `get_product_info` for MCP. Recommendation: the lot reads both outputs; if `product-info` is the CLI form of what
   `get_product_info` answers, it gets its own row rather than a second command in one cell — a cell names one surface.
3. **Should the gate also compare the command paths with `docs/user/CLI.md`?** Recommendation: no. The user guide is
   prose; the help is the product's own statement and is the one compared.

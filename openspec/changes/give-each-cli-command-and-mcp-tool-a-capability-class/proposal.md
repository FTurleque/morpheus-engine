# Proposal

## Why

AUD-ARC-09 (sprint 12) reports that `morpheus-api` is cut by capability while `morpheus-cli` and `morpheus-mcp` each
keep a historical monolith beside their capability classes. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the
finding **holds; several of its counts differ**:

- `morpheus-cli/.../MorpheusCli.java`: 908 lines and 67 imports, as the audit says. Its `dispatch` (line 136) routes
  13 commands besides `help` and `version` (`paths`, `projects`, `sync`, `sync-status`, `requirements`, `changes`,
  `constraints`, `decisions`, `tasks`, `trace-requirement`, `change-context`, `analyze-change`, `quality`; the audit
  writes 14) and the class declares 14 private view records (the audit writes 15).
- Beside it, 14 capability classes named `Morpheus*Cli` (the audit counted 12). `MorpheusMain` (lines 71-106) tries
  each through a chain of `handles(args)` and falls through to `new MorpheusCli().run(...)`: the monolith is the
  default branch of the dispatch, which is why a new command lands there without anybody deciding it.
- `morpheus-mcp/.../MorpheusMcpToolService.java`: 412 lines, a `switch` over 14 tool names, and the projection of
  ten domain types to payload maps. Beside it, 12 capability classes (11 named `*McpTools`, which is what the audit
  counted, and `MorpheusPolicyMcpManagementTools`). `MorpheusMcpServer` (lines 137-154) builds the service and then
  adds the `specifications()` of each capability class.
- No gate refuses a fourteenth command in `MorpheusCli` or a fifteenth tool in `MorpheusMcpToolService`.

Three facts constrain the remedy the audit proposes ("one class per capability, the monolith reduced to dispatch"),
and none is in the finding:

- **Every class that reads a field of `MorpheusMcpRuntime` is a composition root.** Its fields are typed with the
  concrete stores (`SqliteSnapshotBusinessContentStore content`, ...). `MorpheusMcpToolService` is a named root of
  ADR-0109 for that reason. Cutting it into several classes as it stands would add as many names to a list that only
  shrinks.
- **`MorpheusCli` instantiates the OpenSpec reader itself** (import at line 49). Its `sync` and `projects` commands
  are what `ingest-a-project-through-the-provider-port` (AUD-ARC-02, sprint 11) rewires.
- **The projections of `MorpheusMcpToolService` are copies** of those in `morpheus-api` (AUD-ARC-10, sprint 9,
  `project-shared-domain-types-to-one-public-shape`). Moving them into capability classes before they are shared
  moves the duplication.

The `cli` column of `contracts/public-surfaces.tsv` is compared with the code by no gate
(`.claude/rules/governance.md`), so nothing would notice a command that changed while it was being moved.

## What Changes

- Every command word of the CLI and every tool of the MCP server is served by a capability class; the class that
  dispatches holds no command or tool behaviour.
- A gate holds it, by structure: the dispatch classes do not reach the application's use cases, so a command added
  there cannot do anything.
- `morpheus-cli` and `morpheus-mcp` get the packages that rule needs.
- The extraction changes no output, no exit code, no tool name and no payload, and adds no composition root; both
  are proved before a line moves.

## Capabilities

### New Capabilities

- `adapter-capability-decomposition`: where the behaviour of a command or of a tool lives, and what a dispatcher may
  hold.

### Modified Capabilities

(none)

## Impact

- `morpheus-cli`: `MorpheusCli`, `MorpheusMain`, new capability classes, their tests. `morpheus-mcp`:
  `MorpheusMcpToolService`, `MorpheusMcpServer`, `MorpheusMcpRuntime`, new capability classes, their tests.
- `morpheus-architecture-tests`: one new rule class; the gates that read `MorpheusCli.java` or
  `MorpheusMcpToolService.java` as text (two files each on 2026-10-13); `CliOptionParsingRefusesUnknownOptionsTest`
  and `MorpheusHelpInvocationsParseTest` must pass unmodified - they discover parsers and help lines by shape.
  `architectureTestsMinimum` rises with the new rule (`live-numbers`).
- ADR-0109: its list changes only by names leaving it, or by one name replaced by another under an amendment.
  The convention itself is recorded in the ADR of `express-adapter-internal-roles-as-packages`, or in one of its own
  if that change is not taken.
- No public surface moves: `contracts/public-surfaces.tsv`, the OpenAPI files, the tool catalogue and the help text
  stay byte-identical.
- **Order**: after `express-adapter-internal-roles-as-packages` lot A (the source locator and the entry rule), after
  `project-shared-domain-types-to-one-public-shape`, `share-one-argument-base-across-cli-subcommands` and
  `own-the-sqlite-connection-policy-in-one-place`. The `sync` and `projects` commands wait for
  `ingest-a-project-through-the-provider-port`; the eleven other commands and the MCP tools do not wait for sprint 11.

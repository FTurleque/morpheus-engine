# Design

## Context

- The CLI has two dispatch layers. `MorpheusMain` asks each capability class whether it `handles(args)`; whatever
  none claims goes to `MorpheusCli`, whose own `switch` ends in `unknown command`. The capability classes each parse
  their arguments, build their runtime and print; `MorpheusCli` does the same for thirteen commands in one class,
  with a shared error mapping to `CliExitCode`.
- The MCP server has one aggregation: `MorpheusMcpServer` concatenates the `specifications()` of the capability
  classes, and wraps the fourteen historical tools around `MorpheusMcpToolService.execute(toolName, arguments)`.
- `MorpheusMcpRuntime` exposes its stores as fields of concrete SQLite types. ArchUnit counts a field access as a
  dependency on the field's type.
- ADR-0130 (decisions 2 and 8) guards the help text and the option parsers by discovery; it names
  `MorpheusCli.CommandOptions` as one of four permissive parser families, found by its shape.
- `MorpheusCliTest` holds 17 tests for the thirteen commands. Whether each command's text and JSON output is pinned
  by a test is not known and is the first measure.

## Goals / Non-Goals

**Goals**: one place per command and per tool; a dispatcher that cannot grow a behaviour; no observable change.

**Non-Goals**: a common base for argument parsing (`share-one-argument-base-across-cli-subcommands`); sharing the
projections between `api` and `mcp` (`project-shared-domain-types-to-one-public-shape`); changing which class opens
the database (`own-the-sqlite-connection-policy-in-one-place`); renaming a command, an option or a tool; a limit on
the size of a class.

## Decisions

1. **The convention is a dependency rule, not a count.** Intent, in one sentence: *a class that dispatches depends on
   no use case of the application* - a dependency, so an ArchUnit rule (`enforcement-choice`). The dispatch classes
   live in the adapter's root package, the capability classes in a package of their own, and the rule forbids the
   first to depend on `com.morpheus.application..` beyond the types the lot measures as needed for dispatch itself
   (the failure types mapped to exit codes or to tool failures, `ProductMetadata` for `version`). A command added to
   the dispatcher then has nothing to call. *Rejected*: "no class of `cli` has more than N command methods" - N is a
   number nobody can justify, and it is satisfied by splitting a method. *Rejected*: "`MorpheusCli` depends only on
   classes named `Morpheus*Cli`" - a name standing in for a role, which `express-adapter-internal-roles-as-packages`
   removes elsewhere.

2. **Every word is claimed.** The dispatch is exhaustive by construction: each command word and each tool name maps
   to exactly one capability class, and a word claimed twice or by nobody fails a test that reads the dispatch and
   the published surface - the help text for the CLI, the tool catalogue for MCP. `unknown command` stays the
   dispatcher's own answer. This removes the default branch that made the monolith the place where things land.

3. **Characterise before extracting.** For each of the thirteen commands and fourteen tools: one test of the text
   output, one of the JSON output, one of a refusal with its exit code or failure code, on the fixtures of
   `experiments/m0/fixtures/` with the memory store where the command allows it. Written and green on the base, then
   left untouched by the extraction. Where such tests already exist they are listed, not duplicated.

4. **The runtime hands out ports.** A capability class of `mcp` receives what it needs typed as the application's
   ports, so that it is not a composition root. If `own-the-sqlite-connection-policy-in-one-place` has already made
   the runtime the only root of the module, this is free; if not, it is the first step of this change and
   `MorpheusMcpToolService` leaves ADR-0109's list in the same pull request. *Rejected*: adding the new classes to
   `COMPOSITION_ROOTS` - the list only shrinks, and nothing here needs a concrete store.

5. **Grouping follows the manifest, not the old class.** A capability class serves the commands or tools of one
   family of `contracts/public-surfaces.tsv` (`requirement.*`, `change.*`, `sync.*`, ...), as the classes delivered
   since M22 do. The lot proposes the grouping from the manifest and records it; it is not thirteen classes for
   thirteen commands.

6. **Shared pieces of the monolith move once, by what they are.** The error mapping and the printing helpers of
   `MorpheusCli` stay with the dispatcher or become package-level collaborators of the capability package; the
   private view records go with the command that prints them; `CommandOptions` stays one family, wherever it lives,
   and the parser guard must still discover it and its call sites.

7. **One family per pull request, read-only families first.** `sync` and `projects` last, after sprint 11's
   ingestion port.

## What can start before sprint 11 decides, and what cannot

- **Does not depend on it**: decisions 1 to 6 for the eleven commands other than `sync` and `projects` and for all
  fourteen MCP tools; the rule; the packages of `cli` and `mcp`.
- **Depends on `ingest-a-project-through-the-provider-port`**: `sync` and `projects`, the only commands that build a
  provider reader. Extracting them first would move the direct instantiation into a new class that sprint 11 then
  rewrites.
- **Depends on the ADR of `decide-what-technology-the-application-core-may-touch` only indirectly**: if it moves the
  update discovery's transport to `morpheus-cli`, that adapter gains a class which is neither a dispatcher nor a
  command; the package layout of decision 1 leaves room for it (a third package), and the rule does not concern it.

## Risks / Trade-offs

- Twenty-seven behaviours move. The characterisation tests are the only proof, and they are written by the same lot
  that moves the code: they are reviewed first, in their own pull request.
- The dispatcher rule has an allowance (the failure types). An allowance written too wide lets a behaviour back in;
  it is a named list of types, exact, and the lot breaks the rule once with a use case to prove it bites.
- The count of composition roots can go down by this change and must not go up; a pull request that needs a new name
  is wrong by construction.
- Smaller classes do not by themselves make a transverse change single-sited: that is what the two sprint 9 changes
  are for. This change only ends the two-shapes-per-adapter state.

## Open Questions

- **Does `MorpheusCli` survive as the dispatcher, or does `MorpheusMain` absorb it?** Recommendation: it survives,
  reduced: it owns the error mapping that the thirteen commands share and that the capability classes of M22+
  duplicate; whether those converge on it is `share-one-argument-base-across-cli-subcommands`' question.
- **Should the `handles(args)` chain of `MorpheusMain` become a table?** Yes. `MorpheusMain.run` holds one ordered
  table of capability classes, each a `handles` method reference and the run that serves it, in the order the chain
  had; the first that claims the arguments serves them, `MorpheusCli` serves what none claims. No declared path has
  two claimants (task 1.5), so the order decides nothing today. MINOS and NEXUS are resolved inside the two entries
  that use them. `CliCommandDispatchIsExhaustiveTest` reads the classes of the table from the method references of
  the compiled `run`.
- **Is `MorpheusMcpToolService`, a public class, used outside the module?** No import of it exists outside
  `morpheus-mcp` on 2026-10-13. Recommendation: it disappears rather than staying as a facade.

# Design

## Context

- ArchUnit patterns select by package prefix (`com.morpheus.integration..`) or by segment (`..mcp..`). A module is a
  Maven fact that no rule sees.
- `ImportedClasspathCompletenessTest` already reads the `<module>` list of the root POM and the source packages of
  each module; it checks that they are imported, not what they are called.
- ADR-0109, decision 2: the bounded STDIO transport depends on no other MORPHEUS package. That decision is about the
  transport's dependencies; it never decided its name.
- The transport's test fixtures are separate processes started by class; no string in the repository names one of
  them by its fully qualified name, and no configuration, script or workflow names the package (searched on
  2026-10-13).
- The SDK's package is published: a plugin registers
  `META-INF/services/com.morpheus.sdk.provider.MorpheusProviderPlugin`. Renaming it would break every plugin.

## Goals / Non-Goals

**Goals**: the transport is named for what it is; no rule reaches it by accident; a module's package is a declared,
checked fact.

**Non-Goals**: renaming a module; renaming the SDK's or the testkit's package; changing what any module may depend on
(the prohibitions that hold today hold after); the internal structure of `api`, `mcp` and `cli` (sprint 12).

## Decisions

1. **Rename the package to `com.morpheus.transport.mcp`; keep the module name.** *Rejected*: renaming the module to
   `morpheus-integration-mcp` - it would make the false reading official (the MCP server is not an integration), and a
   module name is cited by POMs, documentation, scripts and coverage evidence, where a package name is cited by Java
   files only. *Rejected*: `com.morpheus.mcp.transport`, the name the module would give mechanically - it sits under
   `com.morpheus.mcp..`, which `LayerDependencyTest` forbids to MINOS and NEXUS, the transport's two clients.
   *Rejected*: leaving the package and documenting the exception (see Open Questions) - it keeps the trap and only
   labels it.

2. **Nothing is relaxed by the move.** Each rule whose targets include `com.morpheus.integration..` gains
   `com.morpheus.transport..` as a target: domain, application, api, providers, stores. For `api` this states in the
   open what was true by accident; if the HTTP adapter ever needs an MCP transport, that will be an amendment someone
   writes, not a prohibition someone trips over. The three `"..mcp.."` patterns keep matching the transport by its
   last segment; the lot verifies it rather than assumes it (task 1.3) and adds the explicit target if it does not.

3. **The correspondence is a declared table, compared to the sources both ways.** One entry per module that carries
   production sources: its root package. The test reads the module list from the root POM - a module without an entry
   fails, an entry without a module fails - and requires every production package of a module to sit under its root.
   It also refuses a root that is another module's root or lies inside one, unless that nesting is declared with its
   reason (the testkit inside the SDK). Intent in one sentence: *a package prefix belongs to the modules that declare
   it*. Textual by nature - it compares directories to a POM, which no rule on bytecode sees. *Rejected*: deriving the
   package from the module name with an exception list - three exceptions out of sixteen, and the derived name is
   wrong for the very module this change is about (decision 1).

4. **Entries whose package does not follow from the module name carry a reason.** After this change:
   `morpheus-mcp-transport` (the mechanical name falls inside the MCP adapter's package), `morpheus-provider-sdk`
   (published package, cannot move), `morpheus-provider-testkit` (extends the SDK's package by design). The table says
   which, so a reader who looks for `provider.sdk` finds out why it is not there.

5. **The transport rule keeps its name and changes its subject.** `theMcpTransportDependsOnNoOtherMorpheusPackage`
   judges `com.morpheus.transport.mcp..`; `failOnEmptyShould` guarantees the subject is not empty after the move.

6. **ADR-0109 is amended, not superseded.** A dated amendment records the new package name and the reason; the
   decision and its measures stay as written. The rule page then titles the rule by the package it judges and gives
   the module in passing.

## Risks / Trade-offs

- A package rename touches every file of a module and every importer. It is mechanical, and the compiler finds every
  miss - except the nine file paths that architecture tests read as text, which fail at run time instead; they are
  listed in task 1.1 and changed in the same commit.
- Open branches that touch `morpheus-mcp-transport` will conflict. The lot is scheduled when none is open, or lands
  first.
- `git blame` continuity depends on `git mv` and a rename-only commit, separate from any content change.

## Open Questions

- **Rename, or declare the exception?** The cheaper alternative keeps `com.morpheus.integration.mcp`, adds the
  correspondence test with that module declared as an exception, and corrects the rule page. It closes the audit's
  "nothing compares module and package" and leaves "a prohibition on integrations hits the transport".
  **Recommendation**: rename. The cost is one mechanical commit; the alternative leaves the rules of
  `LayerDependencyTest` meaning something other than what they say.
- **Should `api` stay forbidden from the transport?** **Recommendation**: yes for now (decision 2) - no code needs
  it, and lifting it is a decision for the day a need exists.

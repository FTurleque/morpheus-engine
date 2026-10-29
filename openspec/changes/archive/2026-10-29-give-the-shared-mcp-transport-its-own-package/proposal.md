# Proposal

## Why

AUD-ARC-07 (sprint 10) reports that the module `morpheus-mcp-transport` ships the package
`com.morpheus.integration.mcp`, so the shared transport is governed as an integration. Re-read on `develop` at
`8b26d3d7` on 2026-10-13, the finding **holds exactly**:

- The module's sources are twelve production classes and twenty-one test classes, all under
  `com/morpheus/integration/mcp`.
- The transport is used by three modules: `morpheus-mcp` (the server - `MorpheusMcpServer`),
  `morpheus-integration-minos` (`MinosMcpCodeGateway`) and `morpheus-integration-nexus` (`NexusMcpContextGateway`). It
  is the base the two integrations and the MCP adapter share, not a third integration.
- Rules that forbid `com.morpheus.integration..` therefore forbid the transport without saying so:
  `LayerDependencyTest.java:23` (domain), `:41` (application), `:56` (api), and
  `AdapterSiblingArchitectureTest.java:76` (providers) and `:93` (stores), through the constant `INTEGRATIONS`.
- The one rule about the transport names the package, not the module:
  `AdapterCompositionRootArchitectureTest.java:97-99`. The rule page titles it by the module:
  `.claude/rules/architecture.md:170`, "`morpheus-mcp-transport` ne dépend d'aucun autre paquet MORPHEUS". ADR-0109
  does the same (decision 2, and the measure at line 26).

Measured on the way, and not in the audit:

- Three rules forbid the pattern `"..mcp.."` (`QueryPlatformArchitectureTest.java:27`,
  `PolicyPlatformArchitectureTest.java:22`, `ReasoningPlatformArchitectureTest.java:22`). It matches any package with a
  segment `mcp`, so it covers the transport too, by its last segment.
- Of the eighteen modules the root POM declares, sixteen carry production sources. For thirteen the root package is
  the module name read mechanically (`morpheus-store-sqlite` is `com.morpheus.store.sqlite`). Three differ:
  `morpheus-mcp-transport` (`integration.mcp`), `morpheus-provider-sdk` (`sdk.provider`) and
  `morpheus-provider-testkit` (`sdk.provider.testkit`, inside the SDK's own package). The second already cost a vacant
  pattern once: the Javadoc of `AdapterSiblingArchitectureTest` warns that `..provider.sdk..` matches nothing, and the
  rule pages still spell it that way (carried by `forbid-adapter-dependencies-from-the-provider-sdk`).

Rules select classes by package; the reactor, the POMs and the documentation speak of modules. Nothing holds the two
together, so a rule can be written for one set of modules and apply to another. The next occurrence is foreseeable: a
prohibition added on `com.morpheus.integration..` to keep MINOS and NEXUS apart from something also keeps the shared
transport apart from it.

## What Changes

- The transport's package becomes `com.morpheus.transport.mcp`: outside `com.morpheus.integration..`, and outside
  `com.morpheus.mcp..`, the MCP adapter's package.
- Every prohibition that reached the transport through `com.morpheus.integration..` now names it: nothing is relaxed
  by the move.
- A test holds the correspondence between each module of the reactor and its root package, read from the root POM,
  with the modules whose package does not follow from their name declared and reasoned.
- ADR-0109 is clarified, and the rule page names the package the rule judges.

## Capabilities

### New Capabilities

- `module-package-correspondence`: every module of the reactor owns one declared root package, the build compares the
  declaration to the sources, and the shared MCP transport is named apart from the integrations and from the MCP
  adapter.

### Modified Capabilities

(none)

## Impact

- `morpheus-mcp-transport`: thirty-three files move (`git mv`), package declarations and imports follow.
- Importers: `morpheus-mcp` (one production class, one test), `morpheus-integration-minos` and
  `morpheus-integration-nexus` (one production class each), `morpheus-architecture-tests` (seven files: two package
  patterns, two imports, nine file paths read as text).
- `LayerDependencyTest`, `AdapterSiblingArchitectureTest`, `AdapterCompositionRootArchitectureTest`: targets and
  subject updated; one new test class for the correspondence.
- ADR-0109: a clarification (the decision is unchanged, its wording names a package that moves). No new ADR.
- `.claude/rules/architecture.md`; `docs/developer/` and `docs/architecture/arc42/` name the module only and are
  unaffected by a package move.
- No POM coordinates, no module name, no script, no workflow, no configuration file: none of them names the package
  (searched on 2026-10-13). No public surface: the package is not part of the CLI, MCP or HTTP contracts, and
  `contracts/public-surfaces.tsv` and the OpenAPI are untouched.
- Coverage: the same classes under another name; both scales unchanged. No ratchet beyond the count of architecture
  tests.
- Order: independent of the other changes of sprint 10. `forbid-adapter-dependencies-from-the-provider-sdk` adds the
  transport to the SDK's forbidden targets once this has landed (its task 2.3). Sprint 12 (restructuring the adapters)
  should start from the correspondence table this change introduces.

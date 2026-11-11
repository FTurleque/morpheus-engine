# Proposal

## Why

AUD-ARC-14 (sprint 10) reports eight compatibility constructors while the repository rules forbid them. Re-read on
`develop` at `8b26d3d7` on 2026-10-13, the finding **holds, with a different count and a wider shape**.

The rule, `.claude/rules/code-style.md:61`: "Jamais de feature flag ni de shim de compatibilité rétroactive - modifier
le code directement". And `:13`: "Préférer des fabriques nommées (`generate()`, `parse()`) à plusieurs constructeurs
publics surchargés".

**What the Javadoc calls a compatibility constructor** - nine today, not eight: `Constraint.java:24`,
`ProviderProbeResult.java:33`, `ChangeOrchestrationState.java:86` (`ConstraintView`), `ChangeTransitionEvaluation.java:22`,
`RequirementSearchPage.java:35`, `SnapshotBusinessContent.java:35`, `NormalizedProjectContent.java:93`,
`ProjectDiscoveryResult.java:31`, and `UpdateCheckResult.java:19-24` ("Source-compatible constructor"), which the audit
did not list. The audit also cites `SourceScanPolicy.java:66`; that constructor is at `:56` since
`remove-unreachable-and-unreferenced-code` and carries no such Javadoc.

**What the code does, whatever the Javadoc says** - a scan of `src/main` finds seventeen public constructors of a
record that are not its canonical one, in fifteen records. Matching `new X(` call sites by argument count (a source
scan, to be confirmed by the compiler in the lot):

| Callers | Constructors |
|---|---|
| none at all | `ProjectDiscoveryResult` (4 args), `ConstraintView` (2), `ChangeTransitionEvaluation` (8), `UpdateCheckResult` (7), `RequirementSearchPage` (5) |
| tests only | `SnapshotBusinessContent` (9), `ProviderProbeResult` (8), `SourceScanPolicy` (5), `UpdateManifest` (4), `MorpheusRemoteIdentityFile.Identity` (3), `.GeneratedCredential` (3), `MinosIntegrationSettings` (5), `NexusIntegrationSettings` (5) |
| production | `Constraint` (4 - two providers), `NormalizedProjectContent` (6, 10 and 11 - the OpenSpec readers and the reference plugin) |

Five are dead code that `UnreferencedPublicMethodArchitectureTest` cannot see: it judges methods, not constructors.
Eight exist for the tests alone. And each of the seventeen hands the caller a value it did not choose: `UNKNOWN`
semantics for a `Constraint`, an empty list of acceptance criteria, an identity that never expires
(`Identity`, `Optional.empty()` for `expiresAt`), integration settings without a pin. The same page forbids "dégradation
silencieuse", and the last milestone delivered is M28 while several of these announce callers older than M15 or M16.

A gate on the word "compatibility" would be satisfied by deleting a comment. The property worth enforcing is the
structural one: a record is built one way, and any other entry has a name that says what it assumes.

Two more places use the vocabulary the rule forbids and are not constructors:

- `VersionedRequirementStore.java:54` and `:69`: two `default` methods "preserve source compatibility for
  third-party/test adapters". Both production stores override both.
- `MorpheusProductMcpTools.java:26`: the MCP tool `check_product_update` is a "Compatibility stub", declared as such in
  `contracts/public-surfaces.tsv` (`product.update-discovery`).

## What Changes

- A record of the product has one public constructor, the canonical one. The five constructors nobody calls and the
  eight only tests call are removed; the tests build their values through the canonical constructor or a fixture of
  their own module.
- The four with production callers become named factories that state the assumption they make.
- A structural test refuses a second public constructor on a record, with a list of exceptions that can only shrink.
- The rule page states the interdiction as enforced, and says what is not a shim: a default method of a port, and a
  public name retained by a decision recorded in the convergence manifest.

## Capabilities

### New Capabilities

- `record-construction`: a record of the product is constructed through its canonical constructor or through a named
  factory, never through an overloaded constructor that fills in a default.

### Modified Capabilities

(none)

## Impact

- `morpheus-domain` (`Constraint`, `ProviderProbeResult`), `morpheus-application` (nine records), `morpheus-api`
  (`MorpheusRemoteIdentityFile`), `morpheus-integration-minos`, `morpheus-integration-nexus`; the OpenSpec and Markdown
  providers and the reference plugin as callers; test sources in the same modules and in `morpheus-architecture-tests`.
- `morpheus-provider-reference` calls a 6-argument `NormalizedProjectContent` constructor: `application` types are
  what a third-party plugin compiles against, so their removal is a source-incompatible change for plugin authors.
  Stated in the release notes; whether it requires an SDK API version change is an open question of the design.
- `.claude/rules/code-style.md` (two lines and one new paragraph).
- Coverage: removed constructors leave the denominator; ratios can only rise. No ratchet or ceiling is touched.
- No CLI command, MCP tool or HTTP route moves. No ADR.
- Related: `remove-the-unpinned-plugin-entry-points` (AUD-QUA-11) removes the two `@Deprecated(forRemoval = true)`
  methods the same rule forbids.

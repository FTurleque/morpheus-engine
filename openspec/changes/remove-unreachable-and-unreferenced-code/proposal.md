# Proposal

## Why

AUD-TRV-06 and AUD-QUA-05 (sprint 3) report code that nothing can execute. Re-read on `develop` at `0f3b0ad0` on
2026-10-12, both **hold exactly**:

**Three unreachable branches on security paths (AUD-TRV-06)**

- `LocalSourceInventoryScanner.java:118-120` builds `FileVisitOption.FOLLOW_LINKS` when
  `policy.followSymbolicLinks()` is true, and tests the flag again at `:152`, `:175` and `:207`.
  `SourceScanPolicy.java:41-43` refuses to construct a policy with that flag set. The branch that follows links cannot
  run.
- `MinosMcpCodeGateway.java:206` and `NexusMcpContextGateway.java:234` launch `staged.orElse(jar)`: the verified copy
  if the settings carry a pin, the original JAR otherwise. `MinosIntegrationSettings.java:132-133` and
  `NexusIntegrationSettings.java:126-127` define `enabled()` as "configured **and** pinned", and both gateways refuse to
  launch when it is false. The "otherwise" cannot run.

The integration is fail-closed today, and `MinosUnpinnedExecutionTest#existingUnpinnedJarIsNeverExecutable` covers it.
But the guarantee is a consequence of a guard several lines away: relaxing `enabled()` would make the launch of an
unverified JAR reachable, and relaxing the policy constructor would silently enable link traversal in a user-supplied
workspace. Neither branch is tested, because neither can be reached.

**Sixteen public methods named nowhere else (AUD-QUA-05)**

A scan of the 1 202 Java files of the repository for `public` methods without `@Override` whose name occurs once finds
sixteen: six in `BusinessContentQueryService` (`snapshotChange`, `listSnapshotChanges`, `snapshotConstraints`,
`snapshotDesignDecisions`, `snapshotImplementationTasks`, `snapshotAcceptanceCriteria`), and one each in
`ConstraintEvaluationQueryService`, `ChangeLifecycleQualityService`, `LiveExternalReferenceResolutionService`,
`SpecificationProviderRegistry`, `QualityFactValue`, `QueryExport`, `MultiProviderCompositionResult`,
`ChangeLifecycleMutationPolicy`, `MorpheusQueryApiService` and `MinosIntegrationSettings`. No test calls them either.
`MinosIntegrationSettings.processEnvironment()` is misleading on a security path: it builds a `MINOS_HOME` for the peer,
while the real launch passes the home as a JVM property and an explicitly empty environment; NEXUS has no equivalent.

The repository's own rule is "no dead code". These methods also sit in the coverage denominator and pull the
per-module ratchet down while protecting nothing.

## What Changes

- The three unreachable branches are removed. The symbolic-link flag leaves `SourceScanPolicy`; the two gateways launch
  the verified copy or fail to compile.
- The sixteen methods are removed.
- A gate refuses a public method of the product's own modules that nothing names, so the list cannot grow back.

## Capabilities

### New Capabilities

- `dead-code-refusal`: the product carries no public method that nothing references and no branch a constructor makes
  unreachable on a security path, and the build says so.

### Modified Capabilities

(none)

## Impact

- `morpheus-application` (`sync`, `query`, `quality`, `reference`, `provider`, `composition`, `lifecycle.mutation`),
  `morpheus-api`, `morpheus-integration-minos`, `morpheus-integration-nexus`, and the tests that construct a
  `SourceScanPolicy`.
- `morpheus-architecture-tests`: one gate.
- Coverage: sixteen uncovered methods leave the denominator; the per-module and aggregate ratios can only rise. No
  ratchet or ceiling is touched by this change.
- No CLI command, MCP tool or HTTP route moves: none of the sixteen methods is reached from a public surface, which is
  what "named nowhere" means.

# Proposal

## Why

AUD-ARC-02 (sprint 11) reports that the main ingestion path has no port: `api` and `cli` instantiate the concrete
OpenSpec reader, and the scanned directory is a literal in the HTTP service. Re-read on `develop` at `8b26d3d7` on
2026-10-13, the finding **holds, and its remedy does not**:

- `OpenSpecProjectContentReader` is a `public final class` with no `implements`
  (`morpheus-provider-openspec/.../OpenSpecProjectContentReader.java:15`), constructed at
  `MorpheusProjectSyncApiService.java:67`, `MorpheusCli.java:272` and `:548` - the three sites the audit cites.
- `List.of(Path.of("openspec"))` is written at `MorpheusProjectSyncApiService.java:55` **and** at
  `MorpheusCli.java:239`; the audit names the first only.
- `ProviderAntiLockInTest` has three `@Test` methods and none reaches the publication path.
- A gate pins the lock-in: `ProjectSyncApiServiceArchitectureTest` line 37 asserts that the sync service
  **contains** `new OpenSpecProjectContentReader().read(`.

The audit proposes to declare a port `ProjectContentReader` in `application.read`. **That port exists.**
`SpecificationContentReader.read` returns a `ProviderReadResult` whose content is an
`Optional<NormalizedProjectContent>` - the type the sync publishes - and it is implemented by the OpenSpec, Markdown,
synthetic and reference providers and by every SDK plugin. The provider module carries two aggregators over the same
three readers: `OpenSpecSpecificationContentReader`, behind the port, which contains a failure and reports it per
category; and `OpenSpecProjectContentReader`, outside the port, which throws. The publication path uses the second.
A second port would give each provider two ways to produce the same content.

So the defect is that the path that writes the published truth goes around the port, and that nothing says what a
publication does with the read result the port can return and the strict reader cannot: a partial one.

## What Changes

- Sync and change analysis obtain the project content through `SpecificationContentReader`. No transport class
  constructs a provider's reader outside its composition site, and the non-port OpenSpec aggregator has no caller left.
- **A read that is not complete is not published**: the sync fails by name, says which categories were not read, and
  the active snapshot is unchanged.
- An unchanged workspace synced before and after the change publishes the same business content.
- The directories scanned for a project are stated once, by the provider module that reads them; `api` and `cli` name
  none.
- `ProviderAntiLockInTest` covers the publication path with the three providers.

## Capabilities

### New Capabilities

- `project-ingestion-port`: how the content of a project reaches a published snapshot, and what is refused on the way.

### Modified Capabilities

(none)

## Impact

- `morpheus-api` (`MorpheusProjectSyncApiService`), `morpheus-cli` (`MorpheusCli`: `sync`, `analyze change`),
  `morpheus-provider-openspec` (the aggregator without a caller), an application use case that turns a read result
  into publishable content or a refusal.
- `ProjectSyncApiServiceArchitectureTest` asserts today the construction this change removes: its assertion is
  rewritten in the same change, to the stricter form - a governance file, to be justified in the pull request.
  `ProjectSnapshotImportContractTest` and `FailedPublishSequenceContractTest` use the strict reader as a fixture.
- `AdapterCompositionRootArchitectureTest`: `MorpheusProjectSyncApiService` stays the only transport class that
  reaches a provider; the list does not grow, ADR-0109 is not amended.
- `contracts/public-surfaces.tsv` and the OpenAPI are **expected unchanged**: `project.sync` keeps its route and its
  conflict status. To be confirmed by the convergence gates, not assumed.
- No ADR of its own. Independent of the decision of `decide-what-technology-the-application-core-may-touch`: the
  audit orders it after AUD-ARC-01, but nothing here depends on which option is taken.

# Tasks

**Precondition**: the ADR of `decide-what-technology-the-application-core-may-touch` is accepted and marks at least
one class as leaving. Otherwise withdraw this change.

## 1. Measure before changing

- [x] 1.1 Read the record and list the classes marked as leaving, per family. Proof: the list; one lot per family.
      *2026-10-28: ADR-0150 (Acceptée, option C) marks one class as leaving: `product.UpdateDiscoveryService`, families outbound network, threads and file access, all three through its manifest transport. One lot. Every other named class stays.*
- [x] 1.2 For each: its callers, the adapter that owns them, and every architecture assertion that reads the file by
      path, with the string it looks for. Proof: the table; on 2026-10-13 four test classes read
      `UpdateDiscoveryService.java`.
      *2026-10-28: one production caller, `MorpheusProductCli.updateCheck` (`morpheus-cli`); the MCP tool `check_product_update` is a no-I/O stub (`MorpheusProductMcpTools`) and HTTP does not expose the capability (`contracts/public-surfaces.tsv`, `EXPLICITLY_NOT_EXPOSED`). Assertions reading the file or its name: `UpdateTrustBoundaryContractTest` (`manifest.attestationUri()`, `UpdateTrustLevel.DISCOVERY_ONLY`: stay in the service), `AuditHardeningWorkflowContractTest#remoteDiscoveryCannotRegressToChecksumOnlyOrClaimVerifiedTrust` (`optionalUri(properties, "attestationUri")`, `manifest.requireRemoteDiscoveryContract(manifestUri)`: stay), `ProductionIntegrityContractTest#updateDiscoveryHasNoImplicitStartupEntryPoint` (`new UpdateDiscoveryService()` counted under `src/main/java`: the no-argument constructor disappears, repointed), `SilentCatchArchitectureTest` (`UpdateDiscoveryService.java#IOException`: the catch moves with the transport, repointed), `ApplicationTechnologyBoundaryArchitectureTest` (the name in three families: removed). `distribution/build-portable.{ps1,sh}` list `UpdateDiscoveryService.class` as a packaged entry: the class stays, unchanged.*
- [x] 1.3 For each: the behaviours a caller can observe - refusals, bounds, results - and the test that holds each.
      Proof: the list; a behaviour without a test gets one before the move.
      *2026-10-28: `http:` and other schemes refused before I/O, relative address refused (`ProductIntegrityTest`); oversized manifest refused, file and HTTPS (`ProductIntegrityTest`, `UpdateDiscoveryServiceStalledBodyTest`); stalled body, interrupted read, reader released (`UpdateDiscoveryServiceStalledBodyTest`); `file:` manifest read (both). Without a test: a redirect not followed, a missing file refused by name, the deadline refused by its own message.*

## 2. Characterise before moving

- [x] 2.1 The missing tests of 1.3, green on `HEAD`. Proof: for update discovery - `http:` refused before I/O, a
      redirect not followed, an oversized manifest and an expired deadline refused by name, a `file:` manifest read.
      Each asserts the message or the code, not only the exception type.
      *2026-10-28: commit `4fb33278`, green on `HEAD` (`ProductIntegrityTest` 15, `UpdateDiscoveryServiceStalledBodyTest` 7, `UpdateManifestTrustTest` 3): a 302 answered `update manifest request failed with HTTP 302: <uri>` and its target never requested; a missing file `cannot read update manifest: <uri>`; a relative address `manifestUri must be absolute`; deadline, oversized body and interruption asserted by their full message.*

## 3. The move, one family at a time

- [x] 3.1 The port, in the package of the capability; the implementation, in the adapter that owns the caller; the
      wiring, explicit. Proof: 2.1 still green, unchanged.
      *2026-10-28: port `product.UpdateManifestSource` (`read(URI, int byteLimit, Duration deadline)`); `UpdateDiscoveryService(UpdateManifestSource, Duration)` keeps the scheme refusals, the size refusal and the parsing; `UpdateManifestFetcher` (`morpheus-cli`, package `command`, package-private) reads `file:` and `https:` and owns the client, its `Redirect.NEVER` and the timed body read; `MorpheusProductCli` wires it by `new`. The stalled-body test moves with it as `UpdateManifestFetcherTest`, its client asking to follow redirects so that the redirect test proves the fetcher's own policy; the application tests run on a file source of the test tree, and the scheme refusals on a source that fails if called. Same messages; `morpheus-application` 503 tests and `morpheus-cli` 1991 green.*
- [x] 3.2 Repoint the assertions of 1.2 whose string moved; leave the others. Proof: each repointed assertion fails
      once when its string is removed at the new place, then is restored.
      *2026-10-28: `ProductionIntegrityContractTest` counts `new UpdateDiscoveryService(`; `SilentCatchArchitectureTest` names `UpdateManifestFetcher.java#IOException`. Broken once each (a space before the parenthesis in `MorpheusProductCli`: `expected: <1> but was: <0>`; the catch turned into a rethrow: the map loses its entry), restored. The two assertions whose strings stayed are untouched.*
- [x] 3.3 Remove the class from the named list of the technology gate. Proof: the gate fails with the name still
      present, passes without it.
      *2026-10-28: outbound network and threads leave `CONFINED_FAMILIES` for `UNUSED_FAMILIES`, refused in all of `com.morpheus.application..`; `UpdateDiscoveryService` leaves the file-access list. The HEAD version of the gate against the moved code fails `everyNamedClassStillReachesItsFamily`; a `java.net.http` constant reintroduced in the service fails `aFamilyTheApplicationDoesNotUseIsRefusedInEveryPackage`; both restored. Four `@Test` methods before and after.*
- [x] 3.4 `./mvnw dependency:analyze`. Proof: 0 warning; no dependency added to `morpheus-application`'s POM.
      *2026-10-28: `./mvnw -o dependency:analyze -pl morpheus-application,morpheus-cli -am`: BUILD SUCCESS, no warning; no POM touched.*

## 4. Verification and record

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green on both platforms through the required
      checks.
- [x] 4.2 Clarify the ADR: where each class lives (what moved and when goes to the pull request). Update `.claude/rules/architecture.md` if it named the
      class. Proof: the diff.
      *2026-10-28: ADR-0150 decision 1 refuses network and threads and names the port and the fetcher; the table of decision 2 loses its row marked "sort"; compliance names the tests that follow the transport. `.claude/rules/architecture.md`, `.claude/CLAUDE.md`, DT-18 closed in `docs/architecture/risks/register.md` and `arc42/11-risques-dette.md`. `archgate check`: 10/10, no briefing warning.*
- [ ] 4.3 Pull request description: the finding, the record's decision, the table of 1.2, and that no command changed
      its output.

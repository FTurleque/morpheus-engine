# Tasks

Implementation is a separate lot, started after this specification is reviewed and after the sprint 11 decision
(`decide-what-technology-the-application-core-may-touch`) is accepted naming the resolver, or replaced by a consolidated ADR that names it. Nothing below is
done by the pull request that introduces this change.

## 1. Measure before changing

- [x] 1.1 Diff the two settings files after replacing the peer's name, and the two pairs of test classes the same way.
      Proof: the diffs; on 2026-10-13 at `8b26d3d7`, 233 and 221 lines, eleven hunks, one of them a message. Re-measure:
      the files grew by a third between the audit and this reading.
      *2026-10-27 at `2fcf7415`: 224 and 212 lines, eleven hunks after the name swap (one message, `requireNonBlank`/`requireText`, a redundant timeout assignment, a comment, layout). Tests: settings suites differ by names, a timeout value and the gateway class; launcher suites by the message only.*
- [x] 1.2 List every reader of the two settings types and of their public constants, in product code, tests,
      architecture tests, validators and documentation. Proof: the list; it bounds what may not change.
      *Product: the runtime and gateway of each integration, `NexusMcpTechnicalContextProvider`. Tests: the four twins, `MinosUnpinnedExecutionTest`, `NexusMcpTechnicalContextProviderTest` (canonical constructor). Architecture: `ProductionIntegrityContractTest` (`MAX_TIMEOUT_SECONDS`), `PeerLauncherArchitectureTest`. Docs: ADR-0128, `.claude/rules/security.md`, dated validation and audit records.*
- [x] 1.3 List the textual assertions that name either settings file (`PeerLauncherArchitectureTest`, any D2, M21 or
      M28 suite). Proof: the list, each with the sentence it enforces - these are retargeted in 3.4, none dropped.
      *`PeerLauncherArchitectureTest` (five sentences: absolute launcher in the constructor, `java.home`, `.toRealPath()`, no `orElse("java")`, no bare literal). `LaunchEnvironmentDisclosureTest` reads every quoted `MORPHEUS_*` literal of the main sources: a quoted environment prefix in a settings type is refused, so the prefixes derive from the peer's name (`Peer.named`).*
- [x] 1.4 Read the state of the sprint 11 decision: is file-system access in `morpheus-application` listed by class,
      and what does it say of `ExternalJarIntegrity`? Proof: the paragraph; it decides the home (design, decision 1).
      *ADR-0148 (accepted 2026-10-25) lists file access by class; `ExternalJarIntegrity` stays. Maintainer decision 2026-10-27: the resolver joins the lists through a consolidated ADR that replaces ADR-0148.*
- [ ] 1.5 Read the presence ratchets, the execution counts of the four twin test classes and both coverage scales from
      the last validation summary. Proof: the figures and their source (`.claude/skills/live-numbers`).

## 2. Reproduce before fixing

- [x] 2.1 The parity test in `morpheus-architecture-tests`, over a table of configurations. Proof: its result on
      `HEAD` - one difference expected, the NEXUS "runner JAR" message; anything else is a drift found before the
      merge.
      *Red at `HEAD`: one difference, input 'jar not a file', `<PEER> JAR is not a file` against `<PEER> runner JAR is not a file`. Green after 3.3.*
- [x] 2.2 The ArchUnit dependency rule. Intent: *the settings types of the integrations do not depend on the file
      system API nor on the pin verification.* Proof: red on `HEAD`, naming both types; the rule retains classes.
      *Red at `HEAD`: 20 violations, both constructors through `ExternalJarIntegrity.normalizeSha256`, both `resolve` through `Files`. Green after 3.2.*
- [x] 2.3 Prove the parity test bites: change one bound on one side on a scratch branch. Proof: the failure naming the
      input and the peer.
      *`MAX_TIMEOUT_SECONDS = 119` and a clamped timeout on the NEXUS side: `bothPeersDeclareTheSameSettings` and `bothPeersResolveTheSameInputsAlike` fail, the latter naming input 'timeout at the maximum' with PT2M against PT1M59S.*

## 3. The change

- [x] 3.1 The resolver, in the home decided by 1.4, with the peer as a three-part parameter. Proof: a parameterised
      suite over the two peers covering every row of 2.1, with an assertion on each refusal's wording.
      *`PeerLaunchSettingsResolver` and `PeerLaunchSettings` in `com.morpheus.application.security`; `PeerLaunchSettingsResolverTest`, 16 tests, each over both peers, each refusal asserted word for word. No module carries `junit-jupiter-params`, so the cases loop over the peers instead of adding a dependency.*
- [x] 3.2 The two settings types become thin records over the shared result. Proof: 2.2 passes; the four existing
      suites and `MinosUnpinnedExecutionTest` pass unmodified.
      *2.2 green; the four suites, `MinosUnpinnedExecutionTest` and `NexusMcpTechnicalContextProviderTest` pass unmodified (6/11/6/11, one Linux-only skip per launcher suite on Windows).*
- [x] 3.3 The message of decision 4, if accepted. Proof: 2.1 green with no listed exception; the status tests updated
      in the same commit.
      *`NEXUS JAR is not a file`; `NexusPeerLauncherResolutionTest` updated in the same change; 2.1 green with no exception.*
- [x] 3.4 Retarget `PeerLauncherArchitectureTest`: its assertions on the resolver, and the absence of a bare launcher
      literal in the two settings files. Proof: the test before and after, side by side in the pull request - same
      sentences, one more.
      *Same five sentences, now on `PeerLaunchSettings` and `PeerLaunchSettingsResolver`, the absence checks extended to the two settings files, plus one: each settings type builds its components through `new PeerLaunchSettings(` in its compact constructor.*
- [x] 3.5 Break each rule once, then revert: a `Files` check in one settings type; a bare `java` fallback in the
      resolver; a bound changed on one side. Proof: the three named failures.
      *`Files.isReadable` in `MinosIntegrationSettings.resolve`: `theSettingsTypesOfTheIntegrationsResolveNothingThemselves` fails naming the method. `Optional.of("java")` in the resolver: `theOnlyBareLauncherNamesAreTheTwoFilesLookedForUnderTheRuntime` fails naming the resolver. A bound on one side: see 2.3.*
- [x] 3.6 Clarify ADR-0128 (the settings resolution is shared, and where it lives); add the resolver to the sprint 11 list if 1.4 requires it. Proof: the diffs;
      `AdrIndexCoherenceTest` green.
      *ADR-0128 decisions 6 and 8 name the resolver and the shared constructor; ADR-0148 replaced by a consolidated ADR (ADR-0150) that names `PeerLaunchSettingsResolver` for file access and process environment; `AdrIndexCoherenceTest` green.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning; the execution counts of 1.5
      not lower.
- [ ] 4.2 Both coverage scales and the changed-line gate on Linux and Windows - the launcher resolution has
      platform-specific branches (`java.exe`, symbolic links). Proof: the figures next to 1.5; tests are written if a
      ratio falls, no ratchet is lowered.
- [ ] 4.3 The real-peer compatibility scripts under `distribution/`, on both platforms. Proof: MINOS and NEXUS start
      with the default launcher and with a configured one, as before.
- [x] 4.4 `.claude/rules/security.md`, section on external MCP processes: the launcher invariant named on the resolver
      instead of on the two constructors. `SECURITY.md` unchanged unless 3.3 changed a message it quotes. Proof: the
      diff.
      *The invariant is named on `PeerLaunchSettings` and the resolver. `SECURITY.md` quotes no changed message.*
- [ ] 4.5 Pull request description: the finding, the diffs of 1.1, the result of 2.1 on `HEAD`, the home chosen and
      why, the retargeted test, the justification of the architecture tests.

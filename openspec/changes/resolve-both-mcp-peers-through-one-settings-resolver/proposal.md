# Proposal

## Why

AUD-QUA-09 (sprint 9) reports that `MinosIntegrationSettings` and `NexusIntegrationSettings` are the same file at
ninety per cent, with a drift already installed. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding
**holds, and the copy has grown since the audit**:

- The two files are now 233 and 221 lines (the audit measured 171 and 154). After replacing the peer's name, the
  difference is eleven hunks: one message (`MINOS JAR is not a file` against `NEXUS runner JAR is not a file`), one helper
  name (`requireNonBlank` against `requireText`, same body), one redundant assignment present on one side only, a
  comment, and layout.
- What grew is security code. `resolve-the-peer-java-command-without-the-path` (pull request 456, implemented on
  2026-10-12) added the launcher resolution - the runtime's own `java`, a configured launcher made absolute and real,
  the refusal of a bare command - **twice, identically**, because its design states that the two modules "may not
  depend on each other" and that this finding "will merge them". `PeerLauncherArchitectureTest` holds the two copies
  by text, and says why in its comment: the two settings classes "may not share code".
- One item of the audit's evidence is closed: `processEnvironment()`, dead and present on the MINOS side only, was
  removed by `remove-unreachable-and-unreferenced-code` (AUD-QUA-05). It is not re-specified here.
- The tests are twins too: `MinosIntegrationSettingsTest` and `NexusIntegrationSettingsTest`,
  `MinosPeerLauncherResolutionTest` and `NexusPeerLauncherResolutionTest`.
- The other pairs the audit names stand as it says: the exception and the gateway factory are trivial twins; the
  runtime and the gateway really differ and are out of scope.

These two classes decide whether MORPHEUS starts third-party code: the JAR, its SHA-256 pin, the launcher, the home
directory, the time bound. Every hardening of that path now has to be written twice and kept equal by a textual test.
It was, once, four days ago; the next one may not be.

## What Changes

- The resolution of a peer's launch settings - JAR, pin, launcher, home, time bound, and the errors that make a
  configuration invalid - exists once, parameterised by the peer.
- Each integration keeps what is its own: the names of its properties and environment variables, its peer's name, its
  public settings type.
- A test compares the two peers on the same inputs, and a rule refuses an integration that resolves something itself.
- The launcher invariants held today by text on two files are held on the one place that implements them.

## Capabilities

### New Capabilities

- `mcp-peer-settings-resolution`: how the launch settings of an MCP peer are resolved, for every peer alike.

### Modified Capabilities

(none - `mcp-peer-launch-command`, introduced by `resolve-the-peer-java-command-without-the-path` and not yet archived,
keeps its three requirements; this change moves where they are implemented, not what they say)

## Impact

- **Production code**: the two settings records, and one new resolver. `morpheus-integration-minos` and
  `morpheus-integration-nexus` are siblings (`AdapterSiblingArchitectureTest`) and both depend on
  `morpheus-application` and `morpheus-mcp-transport`.
- **Where the resolver lives is the decision of this change** (design, decision 1), and it meets two others:
  - `morpheus-mcp-transport` may depend on no other MORPHEUS package
    (`AdapterCompositionRootArchitectureTest#theMcpTransportDependsOnNoOtherMorpheusPackage`), and the resolution
    verifies the pin through `ExternalJarIntegrity`, which is in `morpheus-application`;
  - `morpheus-application` is where `ExternalJarIntegrity` already is, but the resolver reads the file system, and
    `decide-what-technology-the-application-core-may-touch` (sprint 11, AUD-ARC-01) confines file access in the
    application to a named list of classes that may shrink and may not grow in silence.
- **Public behaviour**: the setting names, the status details and the refusals are unchanged, except - if the
  maintainer accepts it - the one message that differs today between the peers.
- `PeerLauncherArchitectureTest` is **retargeted, not removed**: its two assertions move to the single
  implementation and are joined by a dependency rule; nothing is weakened. One parity test is added in
  `morpheus-architecture-tests`, the only module that sees both integrations.
- **ADR amendments**, not a new ADR: ADR-0069 (MINOS over MCP STDIO) and ADR-0073 (NEXUS over MCP STDIO) each gain
  the statement that the settings resolution is shared and where it lives. The sprint 11 ADR names the resolver in its
  list of application classes that read the file system.
- **Ratchets.** `testsMinimum` counts test executions across the reactor: folding four twin test classes into two
  would lower the sum. The behaviour tests are kept per peer (parameterised over the two peers, each invocation
  counting) so that the count does not fall; the margin is read from a validation summary, not computed from
  annotations. Coverage: both scales read before and after; no ratchet is lowered.
- The audit orders this after AUD-QUA-07; no code link was found (neither integration declares a varargs map
  builder). It is independent of the other changes of sprint 9.

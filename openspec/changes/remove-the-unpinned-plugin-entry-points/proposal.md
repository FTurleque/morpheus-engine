# Proposal

## Why

AUD-QUA-11 (sprint 10) reports two `@Deprecated(forRemoval = true)` compatibility shims in the provider SDK with no
removal plan. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds exactly**, and re-reading it shows
that a security invariant of the repository is asserted on the shim rather than on the real path.

**The two shims**

- `ProviderPluginActivator.java:22-27`: `activate(ProviderPluginCandidate)`, "retained only for compatibility", always
  throws "provider plugin activation requires a trusted SHA-256 pin".
- `ProviderPluginService.java:40-49`: `probe(Path, String, Path)`, always throws "provider plugin probe requires a
  trusted SHA-256 pin".
- Both date from 2026-08-13 (`6163788c`, `cbd2ffc9`). No file of `docs/` or `.claude/` gives a removal date.
- Callers: the first is called by `ProviderPluginActivatorTest.java:73` only (under `@SuppressWarnings("removal")`);
  the second by nothing, tests included. `UnreferencedPublicMethodArchitectureTest` excludes the SDK, so the build
  does not report it.
- `rules/code-style.md:61` forbids "tout shim de compatibilité rétroactive". Neither class is part of what a plugin
  implements (`MorpheusProviderPlugin`): they are the host side, and the only host is MORPHEUS.

**The invariant that rests on the shim**

- `.claude/rules/security.md:82-83` states that activation requires a pin and cites `ProviderPluginActivator` with the
  message above. `.claude/commands/security-audit.md:39` repeats it.
- Three architecture tests assert that the source of `ProviderPluginActivator` contains that message:
  `ExternalCodeTrustBoundaryArchitectureTest.java:141` ("unpinned executable activation must keep failing closed"),
  `ProviderPluginTrustBoundaryContractTest.java:54`, `ProviderPluginPlatformContractTest.java:88`.
- That string occurs once in the class, at `:26` - inside the deprecated method. The path that really activates a
  plugin, `activate(candidate, expectedSha256)` at `:34`, never produces it: a missing pin is a
  `NullPointerException("expectedSha256")` and a malformed one says "expected SHA-256 must contain exactly 64
  hexadecimal characters" (`ExternalJarIntegrity.java:93-100`).

So the fail-closed property is real - no public signature activates without a pin - but what the gates read is a
sentence in a method that exists to be removed. Deleting the shim, as the rule requires, turns three gates red; keeping
it keeps them green whatever the real path does.

**One more unreachable unpinned branch.** The private `activate(candidate, Optional<String>)` computes
`stagedJar.orElse(candidate.jarPath())` (`:47-49`). Its only caller passes `Optional.of(...)`, so the fallback to the
unverified JAR cannot run. It is the shape `remove-unreachable-and-unreferenced-code` removed from the MINOS and NEXUS
gateways for AUD-TRV-06, left here.

## What Changes

- The two unpinned overloads are removed, and the test that exists only to call one of them.
- The one activation entry refuses a missing pin by name, before touching the JAR, and loads the verified copy or
  nothing: no expression names the candidate's own path as what the class loader reads.
- The gates assert the property - one public way to activate, one to probe, each taking the pin - and the behaviour of
  the real path, instead of a sentence in the source.
- The rule page and the audit command cite what is now enforced.

## Capabilities

### New Capabilities

- `provider-plugin-pinned-entry`: third-party provider code is activated and probed through one entry each, which
  cannot be called without a pin and cannot load an unverified file.

### Modified Capabilities

(none)

## Impact

- `morpheus-provider-sdk`: `ProviderPluginActivator`, `ProviderPluginService`, `ProviderPluginActivatorTest`.
- `morpheus-architecture-tests`: the three assertions above replaced by stricter ones (justified in the pull request,
  as every change under that module is).
- `.claude/rules/security.md`, `.claude/commands/security-audit.md`, `docs/developer/PROVIDER_SDK.md` if it names the
  removed overloads.
- No CLI command, MCP tool or HTTP route moves: the CLI requires `--sha256`, the remote route answers
  `PLUGIN_SHA256_REQUIRED`, and the MCP column stays `EXPLICITLY_NOT_EXPOSED`. `contracts/public-surfaces.tsv` and the
  OpenAPI are untouched.
- Coverage: two always-throwing methods and one unreachable branch leave the denominator. No ratchet is touched.
- No ADR: ADR-0090 and ADR-0101 already decide pinned, explicit activation.

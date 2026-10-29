# Tasks

## 1. Measure before changing

- [x] 1.1 List every caller of `ProviderPluginActivator.activate(candidate)` and of the three-argument
      `ProviderPluginService.probe`, with the compiler: remove each, compile the reactor with tests, record what fails.
      Proof: the list; on 2026-10-13, one test for the first, nothing for the second.
      Done 2026-10-28: both overloads removed, `./mvnw -o test-compile` of the whole reactor green; the only caller was `ProviderPluginActivatorTest#unpinnedActivationFailsClosed` (removed with it), the three-argument `probe` had none.
- [x] 1.2 List every place that quotes "requires a trusted SHA-256 pin". Proof: the list, sorted into gates (three
      architecture tests), rule pages (two), sample text (two tests), product messages (`MorpheusRemoteProxyTargetResolver`,
      the manifest note); only the first two groups are in scope.
      Done: gates `ExternalCodeTrustBoundaryArchitectureTest:141`, `ProviderPluginTrustBoundaryContractTest:55`, `ProviderPluginPlatformContractTest:92`; rule pages `.claude/rules/security.md:104`, `.claude/commands/security-audit.md:39`; sample text `ServerLocationDisclosureTest:56`, `RemoteTextPolicyTest:50`; product messages `MorpheusRemoteProxyTargetResolver:63`, `MorpheusProviderPluginApiService:10` (Javadoc), the manifest note of `provider.plugins.probe`; also ADR SEC-0129:82, which stays true (the pinned entry now produces the sentence).
- [x] 1.3 Confirm that the string asserted by the three gates occurs only inside the deprecated method. Proof: the
      line; on 2026-10-13, `ProviderPluginActivator.java:26`.
      Done: `ProviderPluginActivator.java:26`, inside the deprecated method, at `65a97b06`.
- [x] 1.4 Confirm that every caller of the private `activate(candidate, Optional<String>)` passes a present value.
      Proof: the single call site.
      Done: one call site, `ProviderPluginActivator.java:35`, passing `Optional.of(normalizeSha256(...))`.

## 2. Reproduce before fixing

- [x] 2.1 SDK tests on the pinned entry: a `null` pin, a blank pin, a 63-character pin. Proof: red on `HEAD` for the
      first two - a `NullPointerException` and a shape message where a named pin refusal is specified.
      Done: `ProviderPluginActivatorTest` `aMissingPinIsRefusedByNameBeforeAnyCopyIsStaged`, `aBlankPinIsRefusedByNameBeforeAnyCopyIsStaged`, `aPinOfTheWrongShapeIsRefusedWithTheExpectedShapeBeforeAnyCopyIsStaged`; red on `HEAD` for the first two (`NullPointerException: expectedSha256`, then the shape message), the third green as specified. Each also asserts that no staging copy appeared.
- [x] 2.2 The shape test: exactly one public `activate` and one public `probe`, each with the pin. Proof: red on
      `HEAD`, naming the two overloads.
      Done: `ProviderPluginTrustBoundaryContractTest#theOnlyPublicEntriesToPluginCodeTakeThePin`, on the classes imported by ArchUnit; red on `HEAD`, naming `activate(ProviderPluginCandidate)` (the assertion on the activator comes first; the probe overload is named once the activator holds, see 4.1).

## 3. The change

- [x] 3.1 Remove the two overloads and `ProviderPluginActivatorTest#unpinnedActivationFailsClosed`. Proof: 2.2 passes.
      Done: 2.2 green.
- [x] 3.2 The named refusal of a missing pin, before any I/O; the MINOS and NEXUS refusals unchanged. Proof: 2.1
      passes; the integration tests of the two peers pass unmodified.
      Done: the check lives in the activator, so `ExternalJarIntegrity` and the MINOS/NEXUS settings are untouched; `ExternalJarIntegrityTest` green unmodified.
- [x] 3.3 The private method takes the normalised pin and loads the staged copy; the `orElse` is gone. Proof: the
      activation, cleanup and process-isolation tests of the SDK pass unmodified.
      Done: `activatePinned(candidate, trustedSha256)` loads `stagedJar`. `ProviderPluginActivation` follows: its staging copy is a `Path`, and its unused five-argument constructor, which built an activation with no staging copy, is removed. Activation, cleanup, isolation and descendant tests of the SDK green unmodified (92 tests).
- [x] 3.4 Replace the three textual assertions by the mapping of the design, decision 4, and add the two negative
      textual assertions (`orElse(candidate.jarPath())`, `@Deprecated`). Mechanism: a structural rule for the shape -
      intent "no way to reach plugin code without a pin"; text for the two expressions - intent "this expression must
      not appear here". Proof: green.
      Done. Mapping: `ExternalCodeTrustBoundaryArchitectureTest:141` -> text `assertFalse(orElse(candidate.jarPath()))` and `assertFalse(@Deprecated)`; `ProviderPluginTrustBoundaryContractTest:55` -> text `Path stagedJar = ExternalJarIntegrity.stageVerifiedCopy(candidate.jarPath(), trustedSha256);` (the load target is the staging result) plus the structural method of 2.2, which also refuses `@Deprecated` on the bytecode; `ProviderPluginPlatformContractTest:92` -> behaviour: the real reference JAR, activated with a `null`, empty and blank pin, is refused with the named message.
- [x] 3.5 Design, decision 5: the activator and its handle are package-private; the probe is the only public entry to
      plugin code and refuses a missing pin by name. Proof: the shape test reads both types as non-public.
      Done: `ProviderPluginTrustBoundaryContractTest#theOnlyPublicEntryToPluginCodeIsThePinnedProbe` (2.2 renamed for what
      it asserts); `ProviderPluginPlatformContractTest` refuses the reference JAR through `ProviderPluginService.probe`;
      `ProviderPluginServicePinTest` red then green on the probe refusal.

## 4. Break each gate once, then revert (`.claude/skills/enforcement-choice`)

- [x] 4.1 Restore the unpinned `activate` overload. Proof: the shape test names it.
      Done: an unpinned `activate(ProviderPluginCandidate)` restored -> the shape test fails and names it; same for an unpinned `probe(Path, String, Path)`; `@Deprecated` put on the pinned entry -> both the structural and the textual assertion fail. Reverted.
- [x] 4.2 Restore the `orElse` fallback. Proof: the textual assertion fails.
      Done: `Optional.of(stagedJar).orElse(candidate.jarPath())` as load target -> `ExternalCodeTrustBoundaryArchitectureTest` and `ProviderPluginTrustBoundaryContractTest` fail. Reverted.
- [x] 4.3 Make the pinned entry accept a blank pin. Proof: the SDK test of 2.1 fails.
      Done: blank pin accepted -> `aBlankPinIsRefusedByName...` and `ProviderPluginPlatformContractTest` fail. Reverted.

## 5. Documentation

- [x] 5.1 `.claude/rules/security.md`, section "Plugins providers (M22)": the pin requirement cited by its tests and by
      the refusal the pinned entry produces. `.claude/commands/security-audit.md`: the same.
      Done.
- [x] 5.2 `docs/developer/PROVIDER_SDK.md`: remove any mention of the overloads; `SECURITY.md` only if it names them.
      Done: the paragraph announcing an optional `sha256` and an unpinned local mode is replaced; `SECURITY.md` names neither overload.
- [x] 5.3 The two tests that quote the message as sample text: keep the sample a sentence the product can say.
      Done: nothing to change - the refusal keeps the wording, and the pinned entry now produces it.
- [x] 5.4 Dated records are left as they are.
      Done.

## 6. Verification

- [x] 6.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, zero warning, both platforms through the
      required checks; the M22 and trust-boundary gates named individually in the pull request.
      Open: not run locally (the lot ran the SDK suite, the three gates, `UnreferencedPublicMethodArchitectureTest`,
      `RepositoryDocumentationCoherenceTest`, `RefusalTestEvidenceArchitectureTest`, `SilentCatchArchitectureTest`,
      `D2RepositoryHardeningArchitectureTest` and `dependency:analyze` on the touched modules, all green); left to CI.
      **2026-10-29**: PR #583, merged as `b0368076`: `MORPHEUS CI / exact-head` green on `ubuntu-latest` and `windows-latest` at the merged head `e0e40750`; changed-line coverage 94.12 % lines / 90 % branches.
- [x] 6.2 Presence ratchets: one test removed, several added; `testsMinimum` and `architectureTestsMinimum` are read
      from `config/m21-quality-ratchets.properties` and never lowered.
      Done: one SDK test removed, three added; one architecture test method added, none removed; no ratchet touched.
- [x] 6.3 Pull request description: the finding, the mapping from each former assertion to its replacement, the three
      breaks, the justification for a change under `morpheus-architecture-tests/`.
      Open: no pull request opened by the lot; the mapping and the breaks are written in 3.4 and 4.x.
      **2026-10-29**: written in the description of PR #583, which carries the four sprint 10 lots.

# Tasks

## 1. Measure before changing

- [ ] 1.1 List every caller of `ProviderPluginActivator.activate(candidate)` and of the three-argument
      `ProviderPluginService.probe`, with the compiler: remove each, compile the reactor with tests, record what fails.
      Proof: the list; on 2026-10-13, one test for the first, nothing for the second.
- [ ] 1.2 List every place that quotes "requires a trusted SHA-256 pin". Proof: the list, sorted into gates (three
      architecture tests), rule pages (two), sample text (two tests), product messages (`MorpheusRemoteProxyTargetResolver`,
      the manifest note); only the first two groups are in scope.
- [ ] 1.3 Confirm that the string asserted by the three gates occurs only inside the deprecated method. Proof: the
      line; on 2026-10-13, `ProviderPluginActivator.java:26`.
- [ ] 1.4 Confirm that every caller of the private `activate(candidate, Optional<String>)` passes a present value.
      Proof: the single call site.

## 2. Reproduce before fixing

- [ ] 2.1 SDK tests on the pinned entry: a `null` pin, a blank pin, a 63-character pin. Proof: red on `HEAD` for the
      first two - a `NullPointerException` and a shape message where a named pin refusal is specified.
- [ ] 2.2 The shape test: exactly one public `activate` and one public `probe`, each with the pin. Proof: red on
      `HEAD`, naming the two overloads.

## 3. The change

- [ ] 3.1 Remove the two overloads and `ProviderPluginActivatorTest#unpinnedActivationFailsClosed`. Proof: 2.2 passes.
- [ ] 3.2 The named refusal of a missing pin, before any I/O; the MINOS and NEXUS refusals unchanged. Proof: 2.1
      passes; the integration tests of the two peers pass unmodified.
- [ ] 3.3 The private method takes the normalised pin and loads the staged copy; the `orElse` is gone. Proof: the
      activation, cleanup and process-isolation tests of the SDK pass unmodified.
- [ ] 3.4 Replace the three textual assertions by the mapping of the design, decision 4, and add the two negative
      textual assertions (`orElse(candidate.jarPath())`, `@Deprecated`). Mechanism: a structural rule for the shape -
      intent "no way to reach plugin code without a pin"; text for the two expressions - intent "this expression must
      not appear here". Proof: green.

## 4. Break each gate once, then revert (`.claude/skills/enforcement-choice`)

- [ ] 4.1 Restore the unpinned `activate` overload. Proof: the shape test names it.
- [ ] 4.2 Restore the `orElse` fallback. Proof: the textual assertion fails.
- [ ] 4.3 Make the pinned entry accept a blank pin. Proof: the SDK test of 2.1 fails.

## 5. Documentation

- [ ] 5.1 `.claude/rules/security.md`, section "Plugins providers (M22)": the pin requirement cited by its tests and by
      the refusal the pinned entry produces. `.claude/commands/security-audit.md`: the same.
- [ ] 5.2 `docs/developer/PROVIDER_SDK.md`: remove any mention of the overloads; `SECURITY.md` only if it names them.
- [ ] 5.3 The two tests that quote the message as sample text: keep the sample a sentence the product can say.
- [ ] 5.4 Dated records are left as they are.

## 6. Verification

- [ ] 6.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, zero warning, both platforms through the
      required checks; the M22 and trust-boundary gates named individually in the pull request.
- [ ] 6.2 Presence ratchets: one test removed, several added; `testsMinimum` and `architectureTestsMinimum` are read
      from `config/m21-quality-ratchets.properties` and never lowered.
- [ ] 6.3 Pull request description: the finding, the mapping from each former assertion to its replacement, the three
      breaks, the justification for a change under `morpheus-architecture-tests/`.

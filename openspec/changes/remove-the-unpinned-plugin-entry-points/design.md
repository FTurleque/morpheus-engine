# Design

## Context

- A plugin author implements `MorpheusProviderPlugin`. `ProviderPluginActivator` and `ProviderPluginService` are called
  by MORPHEUS itself: `ProviderPluginProbeWorker` (in the isolated child process), `MorpheusProviderPluginApiService`
  and `MorpheusProviderPluginCli`. Binary compatibility of these two classes is owed to no one outside the reactor.
- The pinned path normalises the pin (`ExternalJarIntegrity.normalizeSha256`), stages a verified copy
  (`stageVerifiedCopy`) and gives that copy to the class loader. The child process verifies the pin again.
- `ServerLocationDisclosureTest` and `RemoteTextPolicyTest` quote the shim's message as a sample of text that is safe to
  disclose. They test a text filter, not the activator.
- ADR-0103: a textual assertion forbids or requires a *mention*. Here the required mention was satisfied by the code
  the rule page forbids.

## Goals / Non-Goals

**Goals**: no shim; the fail-closed property held by the shape of the API and by the behaviour of the real path; the
rule page citing something that executes.

**Non-Goals**: the pin model itself, the staging residue after a crash (`SECURITY.md`), the remote-only policy of the
probe, the `PLUGIN_SHA256_REQUIRED` refusal of the remote route.

## Decisions

1. **Remove both overloads now; no deprecation window.** `forRemoval` has been set for two months, one overload has no
   caller at all, and the other is called by the test that proves it throws. *Rejected*: an ADR with a removal date
   and a gate asserting it (the audit's alternative) - it is the right tool when someone outside depends on the
   signature, and nobody does.

2. **The activation entry refuses a missing pin by name.** A `null` or blank pin is refused with a message that says a
   trusted SHA-256 pin is required, before the JAR is opened; a pin of the wrong shape keeps the message of
   `normalizeSha256`. This keeps the sentence the rule page cites true of the path that runs, and makes "missing" and
   "malformed" two named refusals instead of a `NullPointerException` and a format error. Whether the check lives in the
   activator or in `ExternalJarIntegrity` is left to the lot; MINOS and NEXUS settings use the same helper, and their
   own refusals must not change.

3. **The load target is the staged copy, with no fallback.** The private method takes the normalised pin, not an
   `Optional`; `Path loadJar` is the result of `stageVerifiedCopy`. Same decision as for the two gateways in
   `remove-unreachable-and-unreferenced-code`, same reason: the guarantee should not depend on what a caller several
   lines away happens to pass.

4. **What replaces the three textual assertions - stricter, in two forms.**
   - *Shape*, structural: `ProviderPluginActivator` declares exactly one public `activate` method and
     `ProviderPluginService` exactly one public `probe` method, and each takes the pin. Intent: "there is no way to
     reach third-party code without a pin" - a property of the declared API, which a rule on the imported classes sees
     and a `contains` does not.
   - *Text*, kept where text is the only grip: the activator's source contains no `orElse(candidate.jarPath())` and no
     `@Deprecated`; the existing assertions on `stageVerifiedCopy(` and `releaseLoaderAndStaging(` stay.
   - *Behaviour*, in the SDK's own tests: a missing pin and a malformed pin are refused by name and no class loader is
     created; the existing mismatch test (`rejectsDigestMismatchBeforeCreatingPluginClassLoader`) stays.
   Each of the three former assertions maps to at least one of these, and the mapping is written in the pull request.

5. **No ADR.**

## Risks / Trade-offs

- Three gates are edited in a change whose subject is a security path. The governance rule is met by replacing each
  assertion with a stricter one, and by breaking each new one once (tasks 4.x).
- The two disclosure tests keep quoting a sentence as sample text; if the message of decision 2 differs in wording, the
  samples are updated so that they still quote something the product can say.

## Open Questions

- **Should `ProviderPluginActivator` be public at all?** Its only production caller is in its own package.
  **Recommendation**: leave visibility alone here; `UnreferencedPublicMethodArchitectureTest` excludes the SDK on
  purpose, and narrowing the SDK's host-side surface is a separate decision about what the SDK publishes.

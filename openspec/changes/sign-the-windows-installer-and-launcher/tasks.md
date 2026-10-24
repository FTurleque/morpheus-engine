# Tasks

Implementation is a separate lot, started after this specification is reviewed and after
`refuse-an-unsigned-or-wrongly-signed-windows-binary` is merged (this change uses its verification and its
declaration). Nothing below is done by the pull request that introduces this change.

**Sections 2 to 5 need no certificate and can be built and rehearsed now. Section 6 waits for the certificate.**
Section 1 is the maintainer's and should start first: identity validation has a lead time outside the repository.

## 1. Actions and decisions only the maintainer can take

- [ ] 1.1 Decide the legal identity that will appear on the certificate (individual or organisation, exact name,
      country). Proof: the decision. Everything below depends on it; see `design.md`, external facts, for what was read
      about eligibility — re-read the providers' pages on the day, they are perishable.
- [ ] 1.2 Choose the provider and where the key lives: managed signing service, an authority's cloud HSM, or a hardware
      token. Recommendation and consequences in `design.md` (a token moves the release build off hosted runners and
      reopens decision 5). Proof: the decision, in the ADR draft of 5.4.
- [ ] 1.3 Choose the validation level (recommendation: OV-level) and the timestamp authority (recommendation: the
      provider's). Proof: the decision.
- [ ] 1.4 Decide whether 1.2.1 waits for the certificate (recommendation: no) and what the documentation says of
      releases published before the baseline (recommendation: nothing is re-signed; the first signed version is
      named). Proof: the decision, in the pull request.
- [ ] 1.5 Read the repository's visibility and plan, and from them whether a protected environment with a required
      reviewer and a tag restriction is available. Proof: what GitHub's settings page offers, recorded in the pull
      request. If environments are unavailable, decision 5 of `design.md` is reopened before section 6.
- [ ] 1.6 Start the purchase and the identity validation. Outside this change; recorded so that it is not mistaken for
      something the rehearsal replaces.
- [ ] 1.7 Decide whether the installer's `AppPublisher` follows the name on the certificate. Proof: the decision.

## 2. Measure before changing

- [ ] 2.1 Reuse the table of the other change's task 2.1 (every PE file of the image with its signature status). Proof:
      the list of executables outside the runtime — the population this change signs. If it holds more than the
      launcher, say so in the pull request.
- [ ] 2.2 On the pinned compiler, confirm how a signing tool is defined from the command line and that the installer
      **and** the uninstaller come out signed when the directive is set. **Unverified premise**: the option was read
      in the documentation of the current release, not run on the pinned version. Proof: a compile with a throwaway
      self-signed certificate, the two files' signature status, and the exact option form.
- [ ] 2.3 Confirm that a self-signed code-signing certificate can be created in, used from and removed from the
      personal certificate store of a hosted Windows runner without elevation prompts and without touching a trust
      store. Proof: a dispatched run on a branch; the store listed before and after.
- [ ] 2.4 Measure what the compiler and the build print when they sign: does any line show the tool's full command?
      Proof: the log excerpt. It decides how strict the "no echo" proof of 3.3 must be.
- [ ] 2.5 Read what `AuditHardeningWorkflowContractTest`, `D2RepositoryHardeningArchitectureTest`,
      `ProductReleaseContractTest`, `InnoSetupToolchainTrustArchitectureTest` and `GateReachabilityArchitectureTest`
      pin in `release.yml`, `nightly.yml`, `MORPHEUS.iss` and the build scripts. Proof: the assertions that must pass
      unmodified, in particular `aReleaseRehearsalCannotPublishAttestOrPassForAQualification`.

## 3. Reproduce before fixing

- [ ] 3.1 Extend the rehearsal's proofs in `validate-m20.ps1` behind the test-signing request: the four files of the
      sixth requirement must be signed by the run's identity, and the two launchers byte-identical. Proof: red on
      `HEAD` — every file is unsigned.
- [ ] 3.2 Workflow contract rules in `AuditHardeningWorkflowContractTest`, textual by nature (`.claude/rules/architecture.md`; intent: "these
      names must not appear in a workflow a pull request or a schedule can reach"): no workflow with a `pull_request`
      trigger and no job of `nightly.yml` names the signing environment, the signing credential or the release state;
      no `run:` block of any workflow contains a secret expression. Proof: the last rule is measured on `HEAD` first —
      on 2026-10-13 the five secret expressions of the workflows were all `env:` mappings (re-measure); if a step
      violates it by then, that is a finding to report, not a rule to relax silently.
- [ ] 3.3 A script test under `scripts/tests/`: the signing entry point, run with a marker in place of the credential,
      prints the marker nowhere; given an unknown state it refuses; asked for the release state without a credential
      it fails with `WINDOWS_SIGNING_FAILED`. Proof: red before the entry point exists.
- [ ] 3.4 A contract test on the installer definition and the build scripts: the tool handed to the compiler holds no
      credential expression; the smoke branch cannot be combined with the release state; the launcher is signed before
      `Compress-PortableArchiveWithRetry` and before the payload ZIP. Proof: red, then green after 4.x.

## 4. The signing path, with the test identity

- [ ] 4.1 The signing entry point under `distribution/windows/` (one file path in, a named failure out; states `test`
      and `release`; the provider part isolated behind it). ASCII only. Proof: 3.3 green for the test state.
- [ ] 4.2 `build-portable.ps1`: sign the executables outside the runtime after `jpackage` and before the packaging
      proofs; add a proof line that fails when a signing build leaves one unsigned. Proof: 3.4; a local test-signed
      build.
- [ ] 4.3 `MORPHEUS.iss` and `build-installer.ps1`: the signing directive compiled in only for a signing build, the
      tool defined from the script with no secret, the smoke refusal. Proof: 2.2's measurement repeated through the
      real scripts; `InnoSetupToolchainTrustArchitectureTest` green unmodified.
- [ ] 4.4 `build-release.ps1`: the signing state as a parameter, printed; `WINDOWS_SIGNING_REQUIRED` when the version
      is at or above the declared baseline and the state is not `release`; the manifest statement of the other change
      now reports the observed signer. Proof: a rehearsal manifest stating the test signer; a forced baseline at the
      current version makes an unsigned release build fail by name, then reverted.
- [ ] 4.5 `validate-m20.ps1`: the test-signing request — create the identity, build, run 3.1's proofs, run the other
      change's verification on the result and require a named refusal, remove the identity in `finally`. Without the
      request: no certificate, and the summary line "signing path not exercised". `validate-m20.sh`: the named
      non-applicability line. `verify-windows-setup-lifecycle.ps1`: report the installed uninstaller's signature when
      given a signed installer. Proof: both summaries; the certificate store listed after a forced failure.
- [ ] 4.6 `nightly.yml`: the Windows rehearsal step passes the test-signing request. Permissions unchanged. Proof:
      `GateReachabilityArchitectureTest` green; a dispatched run of the lane on the branch.
- [ ] 4.7 Break each rule once, then revert: move the launcher signing after the archive; remove the directive from
      the installer definition; make the entry point return success without signing; leave the certificate in the
      store. Proof: four named failures.

## 5. Documentation, rules and the decision record

- [ ] 5.1 `distribution/README.md`: the three states, what is signed and in what order, that releases are unsigned
      until the baseline. `docs/project/validation/RELEASE_QUALIFICATION.md`: the pipeline guarantees block and the
      rehearsal's "not exercised" list. Proof: `RepositoryDocumentationCoherenceTest` and
      `ReleaseQualificationContractTest` green.
- [ ] 5.2 `.claude/rules/security.md`: the signing credential beside `NVD_API_KEY` and the TLS password (environment
      or federated identity, never an argument, never a pull request); the entry point as the only caller of a
      signing tool. `SECURITY.md`: what a signature does and does not guarantee.
- [ ] 5.3 `docs/architecture/risks/register.md`: the unsigned-distribution entry of the other change gains this
      mitigation; a second entry for certificate renewal (a lapse fails the next release at the signing step).
- [ ] 5.4 Draft the ADR (signing policy) with the `adr-author` procedure: next free number re-measured with a `glob`
      on `docs/architecture/adr/*-[0-9][0-9][0-9][0-9]-*.md` and `archive/` (`live-numbers`), status *proposed* until the maintainer's decisions of
      section 1 are in it; index line in `docs/architecture/adr/README.md` (`AdrIndexCoherenceTest`). The dated
      pointer in ADR-0089 is added when the ADR is accepted.

## 6. After the certificate exists — the cutover

- [ ] 6.1 Maintainer: create the protected environment (version tags only, reviewer if available) and place the
      credential or the federated trust in it. An agent does not do this.
- [ ] 6.2 Fill the provider part of the entry point for the chosen provider. Every action added to `release.yml` is
      pinned by 40-character SHA with its version in a comment. Proof: `D2RepositoryHardeningArchitectureTest` green;
      3.2 green with the real names.
- [ ] 6.3 One dry signature outside any release, by the maintainer, of a scratch executable with the release identity,
      verified with `Get-AuthenticodeSignature`. Proof: the subject string exactly as Windows reports it — the value
      the declaration needs. Not a release artifact; nothing is published.
- [ ] 6.4 The cutover change: publisher subject and baseline in the declaration, timestamp authority declared,
      `release.yml` wired (environment on the Windows job, identity made available after the gates, the build step
      asking for the release state), `AppPublisher` per 1.7, the ADR accepted, ADR-0089's pointer. Proof: the contract
      test of the seventh requirement red before the declaration is filled, green after.
- [ ] 6.5 User pages for the first signed version: `docs/user/INSTALLATION.md` (publisher name, how to read the
      signature, that a reputation prompt may still appear), the release notes of that version,
      `docs/user/PRODUCT_INTEGRITY.md`. No SHA, gate name or build command. Proof: the documentation coherence test.
- [ ] 6.6 Maintainer: cut the tag. Then run `scripts\verify-release-provenance.ps1 -Tag <tag>` on Windows and the
      shell variant on Linux, and record both outputs as the procedure asks. Proof: the signature check's `PASS` line
      naming the installer and the launcher — the sprint's exit criterion.
- [ ] 6.7 Close the risk-register entry for the unsigned distribution, citing the validation record.

## 7. Verification of the lot that needs no certificate

- [ ] 7.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green.
- [ ] 7.2 `scripts\validate.cmd m20 -Version <current>` with and without the test-signing request on Windows;
      `bash scripts/validate-m20.sh <current>` on Linux; `scripts\validate-m28.ps1 -Version <current>` to confirm the
      pull-request lane still builds unsigned. Proof: the summaries read from `validation-output/`.
- [ ] 7.3 Two consecutive dispatched runs of the `release-rehearsal` lane. Proof: the run links, the duration against
      the lane's timeout.
- [ ] 7.4 Parse every edited `.ps1` under Windows PowerShell 5.1 and check it is ASCII (`rules/tooling.md`).
- [ ] 7.5 Pull request description: the finding, the measurements, where the audit's remedy was not followed and why,
      the decisions still open, and the justification for each governance file touched.

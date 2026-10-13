# Tasks

Implementation is a separate lot, started after this specification is reviewed, and after
`rehearse-the-release-chain-before-the-tag` and `disclose-third-party-components-with-the-release` are merged (this
change extends the lane of the first and adds an eighth check to the seven of the second). Nothing below is done by the
pull request that introduces this change. **No task here needs a certificate.**

## 1. Questions only the maintainer can answer

- [ ] 1.1 Does a release below the signing baseline end the qualification with exit code zero and the named line
      (recommended), or is it refused? Proof: the decision, in the pull request.
- [ ] 1.2 Is the shell variant expected to verify on Linux when its tool allows it, or is Windows the only place the
      signature check counts (see 2.3)? Proof: the decision after the measurement.

## 2. Measure before changing

- [ ] 2.1 Build the Windows image and installer on a clean checkout and list every PE file (`.exe`, `.dll`) of the
      image with its `Get-AuthenticodeSignature` status and signer: at the image root, under `app`, under `runtime`.
      Proof: the table in the pull request. It settles two premises this specification does **not** assume: which
      executables exist outside the runtime (expected: the launcher only), and whether the runtime's binaries arrive
      signed by the JDK distribution.
- [ ] 2.2 Run `Get-AuthenticodeSignature` on the installer built today and on the pinned Inno Setup compiler, and on a
      copy of the compiler with one byte changed. Proof: the three statuses and the properties used to tell them apart
      (`Status`, `SignerCertificate.Subject`, `TimeStamperCertificate`), recorded as the mapping to the named reasons.
- [ ] 2.3 On Linux, run `osslsigncode verify` on the same three files. Proof: the tool's version, the options needed to
      supply trust anchors, its exit status on each file, and whether it can tell an untrusted chain from a trusted
      one with the system store. **Unverified premise**: this specification could not confirm the options or the exit
      status from the tool's documentation. If the tool cannot establish trust, the shell variant keeps only the named
      not-verified path, and 1.2 is answered.
- [ ] 2.4 Read what `ReleaseQualificationContractTest`, `ProductReleaseContractTest` and
      `GateReachabilityArchitectureTest` pin in the files this change edits. Proof: the assertions that must still pass
      unmodified, and those extended in the same commit (`bothVariantsPerformTheSameVerifications`).
- [ ] 2.5 Re-measure the finding's count before the pull request is written: occurrences of `signtool`,
      `osslsigncode`, `SignTool` and `Authenticode` under `distribution/`, `scripts/` and `.github/`. On 2026-10-13:
      `Authenticode` in `ensure-inno-setup.ps1` only, the others nowhere. Do not reuse this figure.

## 3. Reproduce before fixing

- [ ] 3.1 Script tests under `scripts/tests/` for the verification, on the files of 2.2: unsigned with signing
      required, unsigned below a baseline, unsigned with no baseline, another publisher, altered, no declaration, and
      the positive control. Each asserts the named reason, not only a failure (`rules/testing.md`). Proof: red before
      the verification exists. These tests need Windows; on another platform they are skipped by a platform condition
      with a comment saying why.
- [ ] 3.2 Extend `bothVariantsPerformTheSameVerifications`: both variants hold the named reasons — or, for the shell
      variant, the named not-verified statement — and read the declaration. Proof: red on `HEAD`.
- [ ] 3.3 A contract test for the declaration: the file exists, the baseline is empty or a version not greater than the
      root POM's, and no qualification or rehearsal script holds a MORPHEUS publisher literal. Proof: red before the
      file exists; then broken once by setting the baseline above the POM version.
- [ ] 3.4 Extend the rehearsal's textual rule in `GateReachabilityArchitectureTest`: the rehearsal jobs and validators
      contain no step that adds a certificate to a trust store (`Import-Certificate`, `certutil -addstore`,
      `Cert:\LocalMachine\Root`, `Cert:\CurrentUser\Root`). Proof: green on `HEAD` (it pins the absence), red when one
      is added.

## 4. The verification

- [ ] 4.1 The declaration file under `distribution/windows/`: publisher subject pattern (undeclared for now), signing
      baseline (empty for now), and one comment line per field saying who moves it and when. Keep it ASCII.
- [ ] 4.2 The verification, in one place, called by the qualification and by the rehearsal (design, decision 9):
      inputs are a file, the declared publisher, and whether a signature is required; outputs are a pass or one named
      reason. Keep every `.ps1` ASCII (`rules/tooling.md`). Proof: 3.1 green.
- [ ] 4.3 `verify-release-provenance.ps1`: an eighth check — the installer and every executable of the Windows archive
      outside the runtime, the runtime's count printed, the manifest statement compared. Proof: 3.2 green for this
      variant; `theQualificationToolingCannotManufactureTheEvidenceItChecks` green unmodified.
- [ ] 4.4 `verify-release-provenance.sh`: the same check through the tool of 2.3, or the named not-verified statement
      and a final verdict that is not `RELEASE QUALIFICATION PASS`. Proof: 3.2 green; a run without the tool shows the
      named line and the distinct verdict.
- [ ] 4.5 `distribution/build-release.ps1`: the signature statement per asset in the Windows manifest, read from the
      binary just built — never written from an intention. Proof: a rehearsal manifest states `unsigned` for both
      assets today. Check the consumers of the manifest (`check-release-rehearsal-assets.py`, both qualification
      variants, `validate-m20.*`) still read it.
- [ ] 4.6 `validate-m20.ps1`: the four proofs of the fifth requirement, each recorded in the summary as expected, plus
      the line "Not exercised by a rehearsal: the Authenticode signature of the release binaries".
      `validate-m20.sh`: the named line saying the Linux build produces no signable binary. Proof: both summaries.
- [ ] 4.7 Break each proof once, then revert: make the verification accept an unsigned file; make it ignore the signer;
      make it accept a hash mismatch; make it refuse everything. Proof: four named rehearsal failures, the last one
      from the positive control.

## 5. Documentation and rules

- [ ] 5.1 `docs/project/validation/RELEASE_QUALIFICATION.md`: the eighth row of the control table, the count in "Les
      deux exécutent les mêmes … contrôles", the statement of which variant's output counts for the signature, and the
      rehearsal section's list of what is not exercised. Proof:
      `theOperatorProcedureKeepsIssue185OpenUntilARealReleaseIsQualified` green unmodified.
- [ ] 5.2 `docs/user/INSTALLATION.md`: a section on the publisher signature, written for the unsigned state the
      documented release is in (sixth requirement). `docs/user/PRODUCT_INTEGRITY.md`: one paragraph placing the
      signature beside the checksum and the provenance — `checksum != provenance != publisher signature`. Proof:
      `RepositoryDocumentationCoherenceTest` green; the added text read against `rules/documentation.md` (no SHA, no
      gate name, no build command).
- [ ] 5.3 `distribution/README.md` and `SECURITY.md`: what is verified, and that releases are unsigned until a
      baseline is declared. `docs/architecture/risks/register.md`: an entry for the unsigned Windows distribution,
      mitigated by this verification and closed only by the first signed release.
- [ ] 5.4 `.claude/rules/security.md`: one line under the supply-chain section naming the declaration file as the
      single source of the expected publisher. No number is copied.

## 6. Verification

- [ ] 6.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green.
- [ ] 6.2 `scripts\validate.cmd m20 -Version <current>` on Windows and `bash scripts/validate-m20.sh <current>` on
      Linux; then one dispatched run of the `release-rehearsal` lane on the branch. Proof: both summaries read from
      `validation-output/`, the run link.
- [ ] 6.3 Test every edited hook-adjacent `.ps1` for parse errors under Windows PowerShell 5.1 (`rules/tooling.md`).
- [ ] 6.4 Pull request description: the finding, the measurements of section 2, the decisions of `design.md`, what is
      still not covered (the release's own signature), and the justification for each governance file touched.

# Proposal

## Why

AUD-DEP-05 (sprint 14, after AUD-DEP-12) reports that the Windows installer and launcher carry no Authenticode
signature, while the repository refuses to run an Inno Setup compiler whose signature is not valid. Re-read on `develop`
at `8b26d3d7` on 2026-10-13, the finding **holds**, and the reading corrects it on four points that shape the remedy:

- **Nothing signs and nothing verifies.** `signtool`, `osslsigncode`, `codesign`, `SignTool` and `SignedUninstaller`
  occur nowhere under `distribution/`, `scripts/`, `.github/` or the documentation. `Authenticode` occurs in
  `distribution/ensure-inno-setup.ps1:34-40` and `:120-126` (the compiler and its installer must be `Valid` **and**
  signed by `Pyrsys B.V.`) and in two tests that pin those lines. `distribution/windows/MORPHEUS.iss:19-49` (`[Setup]`)
  has no signing directive. The audit's count (zero outside the toolchain check) is confirmed.
- **The audit's "archives" cannot be signed.** A `zip` or a `tar.gz` has no Authenticode form. What can carry a
  signature is the installer, the uninstaller it deploys, and the executable files inside the application image. The
  launcher reaches the user twice: in `morpheus-<version>-windows-x64.zip`, and in `morpheus-payload.zip` embedded in
  the installer (`build-installer.ps1:79-81`), both built from the same image.
- **The verification the audit asks for has no check to extend.** `scripts/verify-release-provenance.ps1` and `.sh` run
  seven checks (tag, asset set, checksums, manifests, attestation, bundles, inventory); none reads a signature. The
  audit says "as it is already done for the compiler": the compiler check is in the *build*, not in the qualification,
  and it tests two things — validity and the signer's name. A verification that tested validity alone would accept any
  binary signed by anybody.
- **The provenance command is now in a user page.** The audit found `gh attestation verify` only in the qualification
  procedure; `docs/user/PRODUCT_INTEGRITY.md:122` names it since `disclose-third-party-components-with-the-release`.
  `docs/user/INSTALLATION.md` still says nothing of what Windows shows when the installer is launched.

The certificate is an external purchase with a lead time nobody in the repository controls. The verification is not: it
can be written, and proven to refuse, today — the repository already holds a validly signed third-party binary (the
pinned Inno Setup compiler) and builds an unsigned one every night. This change is the half that does not wait.
`sign-the-windows-installer-and-launcher` is the half that does.

## What Changes

- The release qualification gains a signature check on the Windows binaries MORPHEUS produces: the installer, and every
  executable of the Windows archive outside the embedded runtime. It refuses, by name, a missing signature, an invalid
  one, an untrusted chain, an unexpected signer and a missing timestamp.
- The expected publisher is declared once, in a committed file both variants read. So is the first version from which
  releases are signed: below it, a release is reported *unsigned by declared baseline*; from it, an unsigned binary
  fails the qualification. Until that version is declared, every qualification says that signing is not required yet —
  it never passes in silence and never claims a signature.
- The Windows release manifest states, per asset, whether it is signed and by whom; the qualification compares the
  statement with what it observes.
- The shell variant either verifies with a named tool or says by name that it did not, and in that case does not print
  the unqualified pass sentence.
- The nightly release rehearsal proves the refusals on Windows without any certificate: an unsigned installer, a
  validly signed binary of another publisher, a binary altered after signing, and a positive control.
- The installation page tells the user whether the release they hold is signed, what Windows displays, and how to check.

## Capabilities

### New Capabilities

- `windows-signature-verification`: what the release qualification and the release rehearsal verify about the
  Authenticode signature of the Windows binaries, how the expected publisher and the signing baseline are declared, and
  what an unsigned release says about itself.

### Modified Capabilities

(none)

## Impact

- `scripts/verify-release-provenance.ps1` and `.sh` (an eighth check), `scripts/validate-m20.ps1` and `.sh` (the
  refusal proofs and one more "not exercised" line), `distribution/build-release.ps1` (the manifest statement), a new
  committed declaration under `distribution/windows/`, `docs/project/validation/RELEASE_QUALIFICATION.md` (control
  table), `docs/user/INSTALLATION.md`, `docs/user/PRODUCT_INTEGRITY.md`, `distribution/README.md`, `SECURITY.md`,
  `docs/architecture/risks/register.md`.
- Tests: `ReleaseQualificationContractTest` (`bothVariantsPerformTheSameVerifications`), `ProductReleaseContractTest`
  and `GateReachabilityArchitectureTest` pin these files textually and are extended, not loosened. New script tests
  under `scripts/tests/`.
- ADR: none by itself. The structural decision (who signs, where the key lives) belongs to
  `sign-the-windows-installer-and-launcher`, which writes the ADR that ADR-0089 announces (`:71`).
- No asset is added to a release: the signature is inside the binaries, so the three expected-asset lists held equal by
  `release-component-disclosure` do not move. No CLI command, MCP tool or HTTP route moves. No ratchet moves.
- `release.yml` is not modified by this change.
- Order: after `rehearse-the-release-chain-before-the-tag` (it extends the lane that change adds) and after
  `disclose-third-party-components-with-the-release` (it adds an eighth check to the seven that change leaves).

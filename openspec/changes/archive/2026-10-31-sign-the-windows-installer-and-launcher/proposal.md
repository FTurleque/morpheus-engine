# Proposal

## Why

AUD-DEP-05 (sprint 14, after AUD-DEP-12): the Windows installer and launcher are published without an Authenticode
signature, so Windows presents them as coming from an unknown publisher and the operator has no native way to tell the
official installer from a substitute. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds**
(evidence in `refuse-an-unsigned-or-wrongly-signed-windows-binary`, which specifies the verification). This change
specifies the signing, and reading the build corrects the audit's remedy on three points:

- **"Sign Setup.exe and the launcher through Inno Setup's `SignTool`" signs only one of the two.** The installer does
  not copy the application image: `MORPHEUS.iss:55-58` embeds `morpheus-payload.zip`, which the transactional engine
  stages at install time (`build-installer.ps1:75-81`). The compiler's signing directive signs the installer, the
  uninstaller, and source files flagged for it — never a file inside a ZIP. The launcher also ships in
  `morpheus-<version>-windows-x64.zip`, which `build-portable.ps1:328` builds before the installer exists. The launcher
  therefore has to be signed in `build-portable.ps1`, after `jpackage` (`:201-206`) and before anything is archived,
  checksummed, listed in a manifest or attested.
- **A certificate "acquired" can no longer be a file.** Since June 2023 the private key of a publicly trusted
  code-signing certificate must live on certified hardware — a token or a cloud HSM — for OV as for EV (source and
  caveats in `design.md`). A USB token cannot be plugged into a GitHub-hosted runner; the release is built there, from
  the exact tagged commit, and attested there. The purchase decision is therefore also a decision about where the
  release is built, and the audit's "OV or EV" is the smaller half of it.
- **Nothing today separates a signing identity from the rest of the build.** `release.yml` holds no secret and no
  `environment:`; `validate-m28.ps1` builds the installer on every pull request (`ci.yml:86`). Wiring a credential into
  `build-installer.ps1` without a boundary would hand it to that lane.

The certificate is an external dependency owned by the maintainer, with a lead time (identity validation) nobody in the
repository controls. Everything else is not: where signing happens in the build, how the credential is kept off command
lines and out of pull requests, and the proof that each expected file comes out signed can be built and rehearsed now
with a throwaway identity. This change separates the two.

## What Changes

- The Windows build signs, when asked, the launcher (before the archive and the installer payload are made), the
  installer and the uninstaller it deploys. A build asked to sign that cannot sign fails; it never falls back to an
  unsigned artifact.
- Signing is explicit and has three declared states: not signing, signing with an ephemeral test identity, signing
  with the release identity. Every build prints which one it used.
- The signing credential reaches the build only through the environment or a federated identity, never through a
  command line, a compiler directive or a log; it is reachable only from the release workflow, on a tag, behind a
  protected environment — never from a `pull_request` event and never from the nightly workflow.
- Every signature carries a trusted timestamp, or the build fails.
- The nightly Windows rehearsal builds a test-signed release with an identity generated in the runner and destroyed
  with it, proves that each expected file is signed by that identity, and proves that the qualification's verification
  refuses those binaries as not issued to the declared publisher.
- When the certificate exists: the release workflow signs with it, the publisher and the signing baseline are declared,
  and the first signed release is qualified. An ADR records the signing policy.

## Capabilities

### New Capabilities

- `windows-release-signing`: which Windows binaries a MORPHEUS release signs and in what order, how the signing
  identity is selected and protected, what a rehearsal may sign with, and how the first signed release is declared.

### Modified Capabilities

(none)

## Impact

- `distribution/build-portable.ps1`, `distribution/build-installer.ps1`, `distribution/build-release.ps1`,
  `distribution/windows/MORPHEUS.iss`, one new signing entry point under `distribution/windows/`,
  `scripts/validate-m20.ps1` (and `.sh` for the named non-applicability), `scripts/verify-windows-setup-lifecycle.ps1`
  (the installed tree), `.github/workflows/nightly.yml` (the Windows rehearsal leg), and — at the cutover only —
  `.github/workflows/release.yml` and the declaration file introduced by the other change.
- Tests: `AuditHardeningWorkflowContractTest`, `ProductReleaseContractTest`, `GateReachabilityArchitectureTest`,
  `D2RepositoryHardeningArchitectureTest` (action pinning) pin these files textually and are extended, not loosened.
- ADR: **one new ADR** — the signing policy ADR-0089 announces (`:71`, "une future politique de signature … pourra
  nécessiter une ADR complémentaire"), with a dated pointer added to ADR-0089. Its number is the next free one at the
  time it is written (the highest attributed on 2026-10-13 is 0110; re-measure, other changes in flight write ADRs).
- GitHub state outside the repository: a protected environment and its credential; the purchase and the identity
  validation. Maintainer's actions.
- No CLI command, MCP tool or HTTP route moves; `contracts/public-surfaces.tsv` and the OpenAPI are untouched. No
  release asset is added. No ratchet moves.
- `docs/user/INSTALLATION.md`, `docs/user/release-notes/` (the first signed version), `distribution/README.md`,
  `SECURITY.md`, `docs/project/validation/RELEASE_QUALIFICATION.md`, `docs/architecture/risks/register.md`,
  `.claude/rules/security.md`.
- Order: after `refuse-an-unsigned-or-wrongly-signed-windows-binary` (it uses that verification and that declaration)
  and after `rehearse-the-release-chain-before-the-tag`. Sections 1 to 5 of `tasks.md` need no certificate; section 6
  waits for it.

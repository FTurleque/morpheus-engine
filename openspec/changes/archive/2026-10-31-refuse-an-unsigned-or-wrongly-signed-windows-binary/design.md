# Design

## Context

- `scripts/verify-release-provenance.{ps1,sh}` read a **published** release and fail closed on seven checks. They are
  read-only by contract (`theQualificationToolingCannotManufactureTheEvidenceItChecks`) and deliberately restate their
  expectations instead of reading the pipeline's (`release-component-disclosure`, decision 6).
- `scripts/validate-m20.{ps1,sh}` rehearse the build half of a release on a local tag; the `release-rehearsal` lane of
  `nightly.yml` runs them on both platforms with `contents: read` and nothing else, and
  `GateReachabilityArchitectureTest#aReleaseRehearsalCannotPublishAttestOrPassForAQualification` refuses the three
  publishing permissions there.
- `distribution/ensure-inno-setup.ps1` is the repository's only signature verification: `Get-AuthenticodeSignature`,
  status `Valid`, subject matching a pinned pattern. After it runs, a validly signed third-party executable is on disk
  on every Windows build.
- `distribution/build-release.ps1` writes `morpheus-<version>-windows-x64-release-manifest.json` with `assets[]`
  (name, bytes, sha256, checksum). The last published release, `v1.2.0`, predates the attested workflow: the present
  qualification already cannot pass on it (no SBOM, no attestation bundle).
- Facts read on 2026-10-13, each with its source, none taken from memory:
  - A signed file still meets SmartScreen until it has reputation, whatever the certificate type: "EV-signed files now
    go through the same reputation-building process as OV certificates", behaviour "removed in 2024"; for the managed
    service, "initial warnings expected". Source: Microsoft Learn, *Code signing options for Windows app developers*
    (`learn.microsoft.com/en-us/windows/apps/package-and-deploy/code-signing-options`, page dated 2026-08-29).
  - `osslsigncode` documents signing, timestamping and "signature verification" of PE files on Linux. Source: the
    project README (`github.com/mtrojnar/osslsigncode`). Its README does **not** describe the options of `verify`, how
    the trust anchors are supplied, or its exit status: **not verified**, hence task 2.3.

## Goals / Non-Goals

**Goals**: a published Windows binary that is unsigned, altered, signed by somebody else or signed without a timestamp
cannot be qualified once signing is declared; before that, no qualification can be read as covering a signature; the
refusals are proven every night without owning a certificate; the user knows what they hold.

**Non-Goals**: producing a signature (that is `sign-the-windows-installer-and-launcher`); choosing a certificate or a
provider; verifying a signature at install time or inside `update-check` (its `trustLevel` stays `DISCOVERY_ONLY`);
signing or verifying the Linux archive (it is covered by its checksum and its attestation); re-signing the binaries of
the embedded Java runtime; verifying the uninstaller, which exists only after an installation and is covered by the
other change's installed-tree proof.

## Decisions

1. **Verify identity, not only validity.** The check requires a valid signature **and** a signer matching the declared
   publisher, as `ensure-inno-setup.ps1` does for its supplier. *Rejected*: validity alone — it accepts a substitute
   signed with any trusted certificate, which is the attack the finding describes.

2. **The publisher is matched on the certificate subject, declared once.** A committed declaration under
   `distribution/windows/` holds the expected subject pattern and the signing baseline; both variants of the
   qualification, the rehearsal and (later) the build read it. *Rejected*: pinning a thumbprint — a public code-signing
   certificate is now short-lived (see the other change's design), so a thumbprint would have to be edited at every
   renewal and would fail a release for an administrative reason. *Rejected*: one copy in each script — the two
   qualification variants restate the *asset list* on purpose, because that list is the pipeline's own expectation; the
   publisher's name is not something the pipeline decides, and two copies of it can only disagree. The declaration is
   read, not defaulted: a missing or empty declaration is a refusal, never "any signer".

3. **A declared baseline answers both "not yet" and "what about older releases".** The declaration holds the first
   version whose release is signed, empty until the first signed release is cut. A tag below the baseline is reported
   `unsigned by declared baseline`; a tag at or above it must be signed; with no baseline, every tag is reported
   `signing not required yet`. In all three cases a binary that *does* carry a signature is verified in full — a wrong
   signature is never excused by the baseline. *Rejected*: requiring a signature from the day this change merges — it
   would make 1.2.1 unqualifiable until a purchase completes, tying a corrective release to an external lead time.
   *Rejected*: a boolean `signingRequired` — it says nothing of older releases and can be flipped back; a version can
   only be compared. The baseline is not a feature flag in product code: it is a dated fact about releases, in a
   governance file, and moving it is reviewed like a ratchet.

4. **The release says what it is.** Each Windows manifest asset gains a signature statement: `signed` with the
   signer's subject, or `unsigned`. The qualification fails when the statement and the observation differ, in either
   direction. A reader of the release page then knows without running anything. *Rejected*: a separate asset — it would
   move the three expected-asset lists for one field.

5. **What is verified: the installer, and every executable of the Windows archive outside the runtime.** The population
   is found by reading the archive, not by naming `morpheus.exe`: a second launcher added later is verified the day it
   ships. The runtime's own binaries are third-party: their signature status is counted and printed, not judged
   (task 2.1 measures it; nothing here assumes they are signed). The launcher inside the installer's payload is not
   re-extracted: it is the same file as the archive's by construction, and the other change's build proof holds that.

6. **A timestamp is required.** Without one, a signature stops verifying when its certificate expires; the binary a user
   downloaded last year would turn "unknown publisher" by itself. The check refuses a signature with no timestamp
   countersignature.

7. **Windows is the authority; the shell variant must not pretend.** The decision a user's machine takes is Windows'
   trust decision, and `Get-AuthenticodeSignature` is that decision. On Linux the shell variant uses a stated tool
   (`osslsigncode`, to be confirmed by task 2.3) for what that tool can establish, or — when the tool is absent or
   cannot establish trust — prints `AUTHENTICODE_NOT_VERIFIED_ON_THIS_PLATFORM` and ends with a verdict that is not the
   unqualified pass sentence. The procedure then requires the Windows variant's full pass before a release is recorded.
   *Rejected*: skipping the check on Linux with a `PASS` line — a silent pass. *Rejected*: failing the shell variant
   whenever the tool is absent — it would turn an environment detail into "the release is bad".

8. **The refusals are proven with what the repository already has.** No test certificate is needed to prove the
   verification: the nightly Windows rehearsal runs the same verification on (a) the installer it just built, unsigned —
   `AUTHENTICODE_SIGNATURE_MISSING`; (b) the Inno Setup compiler, validly signed by its publisher, checked against
   MORPHEUS's declared publisher — `AUTHENTICODE_SIGNER_UNEXPECTED`; (c) a copy of a signed file with one byte changed —
   `AUTHENTICODE_SIGNATURE_INVALID`; (d) the compiler against its own pinned publisher — pass. (d) is the control that
   keeps (a)–(c) honest: a verification that refuses everything would pass the first three. *Rejected*: installing a
   throwaway root certificate in the runner's trust store to obtain a "valid" test signature — it changes a machine's
   trust settings to make a test green, and is not needed. The rehearsal's summary gains one line: the release
   signature itself was not exercised.

9. **One verification, two callers.** The qualification and the rehearsal call the same verification code, so the
   rehearsal proves the code the qualification runs. Where it lives (a function file sourced by both, or a small script
   beside them) is the implementation's choice; a second copy is not.

10. **User page: state, not promise.** `docs/user/INSTALLATION.md` says whether the release is signed, the publisher
    name Windows displays, how to read the signature from the file's properties, and that the checksum and the
    provenance remain available. It does not promise that a signed installer raises no SmartScreen prompt — Microsoft
    states the opposite for new files. It carries no SHA, gate name or build command (`rules/documentation.md`).

11. **No ADR here.** A check added to an existing qualification and a lane extended, inside ADR-0088 and ADR-0089. The
    signing policy has its ADR in the other change.

## Risks / Trade-offs

- **Until a baseline is declared, this check refuses only wrong signatures, not absent ones.** That is the honest state
  of an unsigned product; the output says so on every run, and the risk register carries it.
- **Subject matching trusts the certificate authority's identity validation.** Anyone able to obtain a certificate with
  the same subject from a trusted authority passes. That is the trust model of Authenticode itself; the attestation
  check (control 5) remains the repository-bound proof and is not weakened.
- **The positive control depends on a third party's signature staying valid** (the pinned compiler). If it expired
  without a timestamp, the control would fail — loudly, and the build already depends on that same validity.
- **Linux coverage may be partial.** If task 2.3 finds the tool cannot establish trust, the shell variant only ever
  reports "not verified here". The procedure already asks for both outputs; it will say which one counts for this check.
- **The manifest statement is written by the build that signs.** It is a claim, which is why the qualification compares
  it with the bytes instead of trusting it.

## Open Questions

- The exact subject pattern of the publisher: unknown until the certificate exists (it is the legal name the authority
  validates, which may differ from the installer's `AppPublisher`, `FTurleque` today). The declaration ships with the
  baseline empty and the subject marked undeclared; a qualification on a signed binary with no declared subject is
  refused. Recommendation: fill both in the cutover commit of the other change.
- Should the qualification of a tag **below** the baseline exit zero? Recommendation: yes, with the named line — the
  release is what it was declared to be. Moot for `v1.2.0`, which fails earlier checks.
- Should the shell variant require `osslsigncode` when run in CI? Recommendation: decide after task 2.3; if the tool
  verifies trust reliably, require it where the procedure runs it.

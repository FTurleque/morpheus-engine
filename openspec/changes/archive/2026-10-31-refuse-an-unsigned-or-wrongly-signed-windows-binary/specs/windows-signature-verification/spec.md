# Spec Delta

## Purpose

Define what the release qualification and the release rehearsal verify about the Authenticode signature of the Windows
binaries MORPHEUS produces, how the expected publisher and the first signed version are declared, and what a release
that is not signed says about itself.

## ADDED Requirements

### Requirement: The qualification SHALL verify the signature and the signer of every Windows binary MORPHEUS produces

The release qualification MUST check the Windows installer and every executable file of the Windows archive outside the
embedded runtime, and MUST refuse, with a named reason, a binary whose signature is missing when one is required,
invalid, not trusted, issued to another signer than the declared publisher, or not timestamped.

#### Scenario: A correctly signed release passes the check

- **WHEN** the qualification reads a release at or above the signing baseline whose installer and launcher are signed by
  the declared publisher, with a trusted chain and a timestamp
- **THEN** the signature check prints a `PASS` line naming each verified binary and its signer

#### Scenario: An unsigned binary is refused once signing is required

- **WHEN** the release is at or above the signing baseline and the installer, or an executable of the archive, carries
  no signature
- **THEN** the qualification fails with `AUTHENTICODE_SIGNATURE_MISSING` and names the binary

#### Scenario: A binary altered after signing is refused

- **WHEN** a signed binary's content no longer matches its signature
- **THEN** the qualification fails with `AUTHENTICODE_SIGNATURE_INVALID` and names the binary, whatever the baseline

#### Scenario: A binary signed by another publisher is refused

- **WHEN** a binary carries a valid, trusted signature whose signer does not match the declared publisher
- **THEN** the qualification fails with `AUTHENTICODE_SIGNER_UNEXPECTED` and prints the signer it found, whatever the
  baseline

#### Scenario: A signature that chains to no trusted root is refused

- **WHEN** a binary is signed with a certificate the verifying platform does not trust
- **THEN** the qualification fails with `AUTHENTICODE_SIGNATURE_NOT_TRUSTED`

#### Scenario: A signature without a timestamp is refused

- **WHEN** a binary carries a valid signature by the declared publisher and no timestamp countersignature
- **THEN** the qualification fails with `AUTHENTICODE_TIMESTAMP_MISSING`

#### Scenario: A new executable is verified without editing the check

- **WHEN** the Windows archive gains a second executable outside the embedded runtime
- **THEN** the check verifies it too, because it reads the archive instead of a list of names

#### Scenario: The runtime's binaries are reported, not judged

- **WHEN** the check reads the executables under the embedded runtime
- **THEN** it prints how many are signed and how many are not, and their status does not change the verdict

### Requirement: The expected publisher and the signing baseline SHALL be declared once and read, never defaulted

One committed declaration MUST hold the expected publisher and the first product version whose release is signed; both
variants of the qualification and the rehearsal MUST read it, and none MUST fall back to accepting any signer when it is
missing or incomplete.

#### Scenario: Both variants read the same declaration

- **WHEN** the contract test reads the two qualification scripts and the rehearsal validators
- **THEN** each reads the declaration file, and none contains a publisher name of its own for MORPHEUS

#### Scenario: A missing declaration is a refusal

- **WHEN** the declaration file is absent, or a signed binary is checked while no publisher is declared
- **THEN** the check fails with `AUTHENTICODE_PUBLISHER_NOT_DECLARED` instead of accepting the signer it found

#### Scenario: A release below the baseline is reported as such

- **WHEN** the qualification reads a release whose version is lower than the declared baseline and whose binaries are
  unsigned
- **THEN** it prints `unsigned by declared baseline` with both versions, and does not print a signature `PASS` line

#### Scenario: Before any baseline, no qualification covers a signature

- **WHEN** no baseline is declared and the release's binaries are unsigned
- **THEN** the check prints `AUTHENTICODE_NOT_REQUIRED_YET`, and the output nowhere states that the release is signed

#### Scenario: The baseline never excuses a wrong signature

- **WHEN** a release below the baseline, or checked before any baseline, carries a signature that is invalid or issued
  to another signer
- **THEN** the qualification fails with the corresponding named reason

#### Scenario: The baseline cannot be ahead of the product

- **WHEN** the declared baseline is greater than the version the root POM declares
- **THEN** the build fails and names both values

### Requirement: A Windows release SHALL state its signing status and the qualification SHALL compare the statement with the binaries

The Windows release manifest MUST state for each executable asset whether it is signed and, when it is, the signer's
subject; the qualification MUST fail when that statement differs from what it observes on the published binary.

#### Scenario: An unsigned release says so

- **WHEN** the Windows release build produces an installer that carries no signature
- **THEN** the manifest states `unsigned` for it, and the build does not write a signer

#### Scenario: A manifest that claims a signature the binary lacks is refused

- **WHEN** the manifest states an asset is signed and the published binary carries no signature
- **THEN** the qualification fails and names the asset and both statements

#### Scenario: A signed binary the manifest calls unsigned is refused

- **WHEN** the published installer is signed and its manifest states `unsigned`
- **THEN** the qualification fails and names the asset

### Requirement: A variant that cannot verify a signature MUST say so by name and MUST NOT report an unqualified pass

Each variant of the qualification MUST either perform the signature check with a tool it names, or print a named
statement that the check was not performed on this platform; in the latter case its final verdict MUST differ from the
sentence a complete qualification prints.

#### Scenario: The Windows variant always verifies

- **WHEN** `scripts/verify-release-provenance.ps1` runs
- **THEN** it performs the signature check with the platform's own verification and never reports it as not performed

#### Scenario: The shell variant names what it did not verify

- **WHEN** the shell variant runs where its stated tool is absent or cannot establish trust
- **THEN** it prints `AUTHENTICODE_NOT_VERIFIED_ON_THIS_PLATFORM`, completes its other checks, and ends with a verdict
  that states the signature was not verified here instead of `RELEASE QUALIFICATION PASS`

#### Scenario: A partial verdict is not recorded as a qualification

- **WHEN** the qualification procedure is read
- **THEN** it states that the signature check counts only from a variant that performed it, and that a release at or
  above the baseline is recorded only with that output

#### Scenario: Both variants name the same refusals

- **WHEN** the contract test reads both variants
- **THEN** each contains every named reason of the first requirement, or the named not-verified statement in its place

### Requirement: The release rehearsal SHALL prove on a schedule that the verification refuses and accepts

The Windows release rehearsal MUST run the verification the qualification uses on an unsigned binary, on a binary
validly signed by another publisher, on a binary altered after signing and on a positive control, MUST fail when any of
the four gives another result than expected, and MUST state that the release's own signature was not exercised.

#### Scenario: The unsigned installer is refused

- **WHEN** the rehearsal verifies the installer it has just built without a signature, as if signing were required
- **THEN** the verification answers `AUTHENTICODE_SIGNATURE_MISSING`, and the rehearsal records the refusal as expected

#### Scenario: Another publisher's valid signature is refused

- **WHEN** the rehearsal verifies the pinned Inno Setup compiler against MORPHEUS's declared publisher, or against a
  fixed stand-in publisher while none is declared
- **THEN** the verification answers `AUTHENTICODE_SIGNER_UNEXPECTED`

#### Scenario: The rehearsal proves the undeclared publisher is refused

- **WHEN** no publisher is declared and the rehearsal verifies a signed binary without supplying a stand-in
- **THEN** the verification answers `AUTHENTICODE_PUBLISHER_NOT_DECLARED`

#### Scenario: An altered signed binary is refused

- **WHEN** the rehearsal verifies a copy of a signed binary with one byte changed
- **THEN** the verification answers `AUTHENTICODE_SIGNATURE_INVALID`

#### Scenario: The positive control passes

- **WHEN** the rehearsal verifies the pinned Inno Setup compiler against its own pinned publisher
- **THEN** the verification passes, and a verification that refuses it fails the rehearsal

#### Scenario: A verification that stops refusing fails the night it does

- **WHEN** the verification is changed so that it accepts an unsigned binary
- **THEN** the next Windows rehearsal fails and names the proof that no longer holds

#### Scenario: The rehearsal still holds no publishing or signing permission

- **WHEN** the rehearsal jobs' permissions and steps are read by the reachability gate
- **THEN** they are unchanged: `contents: read` only, no secret, no step that installs a certificate in a trust store

#### Scenario: The Linux rehearsal says the check does not apply

- **WHEN** the Linux rehearsal prints its summary
- **THEN** it states by name that the Linux build produces no Authenticode-signable binary, instead of omitting the line

### Requirement: The installation page SHALL tell the user whether the release is signed and how to check it

The user installation documentation MUST state whether the Windows binaries of the documented release are signed, the
publisher name Windows displays when they are, what Windows displays when they are not, and how to verify the file
without the repository; it MUST NOT promise that a signed installer raises no reputation prompt.

#### Scenario: An unsigned release is described as it behaves

- **WHEN** the documented release is below the signing baseline, or no baseline is declared
- **THEN** the page says Windows will present the installer as coming from an unknown publisher, and gives the checksum
  verification as the available check

#### Scenario: A signed release names its publisher

- **WHEN** the documented release is at or above the baseline
- **THEN** the page names the publisher shown by Windows and how to read the signature from the file, and says that a
  reputation prompt may still appear for a new release

#### Scenario: The page stays a user page

- **WHEN** the section this change adds to the page is read
- **THEN** it holds no commit SHA, no gate name, no build command and no source path

# Spec Delta

## Purpose

Define which Windows binaries a MORPHEUS release signs and in what order, how the signing identity is selected and kept
away from untrusted events, command lines and logs, what a rehearsal may sign with, and how the first signed release is
declared.

## ADDED Requirements

### Requirement: A signing build SHALL sign the launcher, the installer and the uninstaller, and the launcher before anything is derived from the image

When a Windows build is asked to sign, it MUST sign every executable of the application image outside the embedded
runtime before the portable archive, the installer payload, a checksum, the release manifest or an attestation is
produced, and MUST produce an installer and an uninstaller that are signed by the same identity.

#### Scenario: The archive carries the signed launcher

- **WHEN** a signing build completes
- **THEN** the launcher inside the Windows archive carries a signature by the build's signing identity, and the
  archive's published checksum is the checksum of the archive that contains it

#### Scenario: The installer deploys the same signed launcher

- **WHEN** the installer produced by a signing build is installed
- **THEN** the installed launcher is byte-identical to the launcher of the Windows archive, and carries the same
  signature

#### Scenario: The installer and the uninstaller are signed

- **WHEN** a signing build completes and its installer is installed
- **THEN** the installer and the uninstaller found in the installation directory each carry a signature by the build's
  signing identity

#### Scenario: Signing after archiving is refused

- **WHEN** the step that signs the launcher is moved after the creation of the portable archive
- **THEN** the build's own proof fails, naming the archive whose launcher is unsigned

#### Scenario: The runtime's binaries are left as they came

- **WHEN** a signing build completes
- **THEN** no file under the embedded runtime has been modified by the signing step

### Requirement: Signing SHALL be requested explicitly, and a build asked to sign MUST NOT produce an unsigned artifact

A Windows build MUST take its signing state from its caller — not signing, signing with a test identity, or signing
with the release identity — MUST print the state it used, and MUST fail when it was asked to sign and any expected file
could not be signed.

#### Scenario: A build does not sign because a credential is present

- **WHEN** a build is run without a signing request in an environment where a signing credential is available
- **THEN** nothing is signed, and the build prints that it did not sign

#### Scenario: A build does not skip signing because a credential is absent

- **WHEN** a build is asked to sign with the release identity and the credential is missing or refused
- **THEN** the build fails with `WINDOWS_SIGNING_FAILED`, names the file it could not sign, and leaves no installer or
  archive presented as a release artifact

#### Scenario: The release of a version at or above the baseline cannot be unsigned

- **WHEN** the release build runs for a version equal to or greater than the declared signing baseline and is not asked
  to sign with the release identity
- **THEN** it fails with `WINDOWS_SIGNING_REQUIRED` before packaging

#### Scenario: Below the baseline, an unsigned release build says so

- **WHEN** the release build runs for a version below the baseline, or while no baseline is declared, without a signing
  request
- **THEN** it completes, prints that the artifacts are unsigned, and the manifest states `unsigned`

#### Scenario: The smoke installer is never signed with the release identity

- **WHEN** the installer is built in its smoke mode
- **THEN** a request to sign it with the release identity is refused

### Requirement: The signing credential MUST NOT appear on a command line, in a compiler directive or in a log

The signing credential MUST reach the build only through the process environment or a federated identity session; no
script, workflow or installer definition MUST pass it as an argument, and no build output MUST contain it.

#### Scenario: The compiler is given a command without a secret

- **WHEN** the installer definition and the script that invokes the compiler are read
- **THEN** the signing tool handed to the compiler is a script path and a file placeholder, with no credential, password,
  PIN or token expression

#### Scenario: A workflow never expands a secret inside a command

- **WHEN** the workflows are read by the workflow contract test
- **THEN** no `run:` block contains a secret expression; the signing credential, if one is stored, is mapped under
  `env:` of the one step that signs

#### Scenario: Passing the credential as an argument fails the build

- **WHEN** a change adds the credential to the argument list of the signing entry point or of the compiler
- **THEN** the contract test fails and names the file

#### Scenario: The entry point does not echo what it was given

- **WHEN** the signing entry point runs with a marker value in place of the credential
- **THEN** the marker appears nowhere in the build's output or in the validator's logs

### Requirement: The release signing identity SHALL be reachable only from the release workflow, on a version tag, after the release gates

The release signing identity MUST be available only to the Windows job of the release workflow, through a protected
environment restricted to version tags, and only to the step that builds the release, after the tag has been found
reachable from `main` and the replayed gate has passed; no workflow a `pull_request` event can trigger and no scheduled
workflow MUST name that environment or that credential.

#### Scenario: A pull request cannot reach the identity

- **WHEN** the workflow contract test reads every workflow that declares a `pull_request` trigger
- **THEN** none names the signing environment, the signing credential, or the release signing state

#### Scenario: The nightly workflow cannot reach the identity

- **WHEN** the reachability gate reads the rehearsal jobs
- **THEN** they hold `contents: read` only, reference no environment and no secret, and request the test state at most

#### Scenario: The identity arrives after the gates

- **WHEN** the Windows release job is read
- **THEN** the step that makes the identity available comes after the tag-reachability check and after the replayed
  milestone gate, and the gate's own installer builds do not sign with it

#### Scenario: Every action the signing path uses is pinned

- **WHEN** an action is added to the release workflow for the signing identity
- **THEN** it is referenced by a 40-character commit SHA, and the repository hardening gate refuses a tag reference

### Requirement: Every signature SHALL carry a trusted timestamp or the build SHALL fail

A build signing with the release identity MUST timestamp every signature through the declared timestamp authority and
MUST fail when a timestamp cannot be obtained.

#### Scenario: A release signature is timestamped

- **WHEN** a build signs with the release identity
- **THEN** each signed file carries a timestamp countersignature, and the build prints the authority it used

#### Scenario: An unreachable authority stops the release

- **WHEN** the timestamp authority cannot be reached during a release build
- **THEN** the build fails with `WINDOWS_SIGNING_TIMESTAMP_FAILED` and no artifact is produced without a timestamp

#### Scenario: The authority is declared, not chosen at run time

- **WHEN** the signing entry point is read
- **THEN** it takes the timestamp authority from the committed signing declaration and holds no fallback authority

### Requirement: The release rehearsal SHALL exercise the signing path with an identity that exists only for the run

The Windows release rehearsal MUST build a test-signed release through the same entry point and in the same order as a
release, with a signing identity generated during the run and removed on every exit path, MUST prove that each expected
file is signed by that identity, MUST prove that the qualification's verification refuses those files, and MUST state
what it did not exercise.

#### Scenario: Each expected file is test-signed

- **WHEN** the scheduled Windows rehearsal completes
- **THEN** its summary lists the launcher of the archive, the launcher of the installer payload, the installer and the
  installed uninstaller, each with the test identity as signer

#### Scenario: A file that comes out unsigned fails the rehearsal

- **WHEN** a change stops the installer definition from signing the uninstaller
- **THEN** the next Windows rehearsal fails and names the uninstaller

#### Scenario: A test-signed release cannot be qualified

- **WHEN** the rehearsal runs the qualification's verification on its test-signed installer as if signing were required
- **THEN** the verification refuses it with a named reason, and a verification that accepts it fails the rehearsal

#### Scenario: The test identity does not outlive the run

- **WHEN** the rehearsal ends, successfully or not
- **THEN** the test certificate and its key are no longer in any certificate store of the machine, and no trust store
  was modified at any point

#### Scenario: A hand-run validator does not create certificates uninvited

- **WHEN** the release validator is run without the test-signing request
- **THEN** it creates no certificate, builds unsigned, and its summary says the signing path was not exercised

#### Scenario: The rehearsal names what it did not exercise

- **WHEN** the rehearsal prints its summary
- **THEN** it lists as not exercised the release signing identity and the signing service, and states whether a
  timestamp authority was contacted

### Requirement: The first signed release SHALL be declared by the change that makes signing obligatory

The change that wires the release signing identity MUST, in the same change, declare the publisher's subject and the
signing baseline, record the signing policy in an architecture decision record, and state in the user documentation
from which version releases are signed; published releases below the baseline MUST NOT be re-signed or re-published.

#### Scenario: The cutover declares publisher and baseline together

- **WHEN** the release workflow gains the signing identity
- **THEN** the signing declaration holds a publisher subject and a baseline equal to the version about to be released,
  and a contract test refuses the workflow wiring while either is empty

#### Scenario: The first signed release is qualified by the verification

- **WHEN** the first release at the baseline is published
- **THEN** the Windows variant of the qualification passes its signature check on the installer and the launcher, and
  that output is what the validation record of the release holds

#### Scenario: Earlier releases are left as published

- **WHEN** the cutover is complete
- **THEN** no asset of a release below the baseline has been replaced, and the user documentation states the first
  signed version without claiming anything for earlier ones

#### Scenario: The policy is recorded

- **WHEN** the ADR index is read after the cutover
- **THEN** it lists a decision record for the Windows signing policy, and ADR-0089 points to it

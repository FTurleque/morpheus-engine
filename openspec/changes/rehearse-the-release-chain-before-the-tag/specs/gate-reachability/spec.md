# Spec Delta

## Purpose

Add the release build to the gates that a workflow must reach on both platforms, and bound what a rehearsal of the
release may claim: it exercises the build, it publishes nothing, and it is not evidence that a release was qualified.

## ADDED Requirements

### Requirement: The release build SHALL be rehearsed on a schedule on both platforms and a failed rehearsal SHALL fail the run

A workflow MUST run the M20 release validator on Linux and on Windows on a schedule and on demand, through the same
`distribution/build-release` scripts the release workflow calls, and a validator that fails MUST fail its job.

#### Scenario: Both platforms rehearse the release build

- **WHEN** the nightly workflow runs on a schedule
- **THEN** a Linux job runs `scripts/validate-m20.sh` and a Windows job runs `scripts/validate-m20.ps1`, each with the
  current product version, each producing the platform's archive, checksum and release manifest for the checked-out
  commit

#### Scenario: A broken Linux packaging is found without a tag

- **WHEN** a change makes `distribution/build-portable.sh` fail and no tag is pushed
- **THEN** the next rehearsal fails on Linux and names the failing stage

#### Scenario: A rehearsal can be run on a branch before a merge

- **WHEN** the workflow is dispatched manually on a branch
- **THEN** it rehearses that branch's commit, not `develop`'s

#### Scenario: The validator is given the current version

- **WHEN** the lane invokes the validator
- **THEN** the version argument is the one the root POM declares, and a validator left on its dated default fails on the
  version check instead of passing on an old value

### Requirement: The rehearsal SHALL compare what the builds produce with what the release workflow requires

The rehearsal MUST read the list of assets the release workflow requires before publishing, and MUST fail when a name on
that list — other than those declared as produced only by a real release — is missing or empty among the files the two
builds produced.

#### Scenario: The produced files match the expected names

- **WHEN** both platform legs have uploaded their release files
- **THEN** a comparison job finds every expected name except the attestation bundles, each non-empty, and passes

#### Scenario: An expected asset nobody builds fails the rehearsal

- **WHEN** a name is added to the `expected` list of `release.yml` and no build script produces it
- **THEN** the comparison job fails and names the missing asset

#### Scenario: The list is read, not restated

- **WHEN** the comparison job's definition is read
- **THEN** it holds no asset name of its own other than the declared exceptions; the names come from `release.yml`

### Requirement: A rehearsal MUST NOT publish, attest or produce qualification evidence

A rehearsal MUST NOT push a tag, create or edit a release, or create an attestation; it MUST state which steps of the
release it did not exercise; and its output MUST NOT contain the sentence a real qualification prints.

#### Scenario: The lane has no step that could publish

- **WHEN** the rehearsal jobs and the validators they run are read by the reachability gate
- **THEN** their executable text contains no `git push`, no `gh release` mutation and no `actions/attest`, and the
  validator deletes its local validation tag on every exit path

#### Scenario: The rehearsal names what it did not exercise

- **WHEN** the validator prints its summary
- **THEN** it lists as not exercised: the tag's reachability from `main`, the provenance attestation, the publication,
  and the public read-back of the published release

#### Scenario: A rehearsal cannot be recorded as a qualification

- **WHEN** the rehearsal's output is read
- **THEN** it never contains `RELEASE QUALIFICATION PASS`, and the qualification procedure states that a rehearsal does
  not close the release tracking issue

#### Scenario: The lane holds no publishing permission

- **WHEN** the rehearsal jobs' permissions are read
- **THEN** they are limited to `contents: read`, with neither `id-token: write`, `attestations: write` nor
  `contents: write`

### Requirement: The M20 release validator SHALL be launched by a workflow on each platform it exists for

`scripts/validate-m20.sh` and `scripts/validate-m20.ps1` MUST each be named in the executable text of a `run:` step of a
workflow of `.github/workflows/`, and the build MUST fail when either stops being named. Comments do not count.

#### Scenario: Both variants are launched

- **WHEN** the reachability gate reads the workflows after this change
- **THEN** it finds `validate-m20.sh` in a step that runs on Linux and `validate-m20.ps1` in a step that runs on Windows

#### Scenario: Removing the lane fails the build

- **WHEN** the rehearsal lane is removed from the nightly workflow
- **THEN** the reachability gate fails and names the variant that is no longer launched

#### Scenario: One platform is not enough

- **WHEN** a workflow launches `validate-m20.sh` and none launches `validate-m20.ps1`
- **THEN** the gate fails and names `validate-m20.ps1`

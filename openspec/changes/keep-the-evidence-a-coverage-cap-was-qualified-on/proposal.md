# Proposal

## Why

AUD-TST-09 (sprint 8) reports that the four coverage ratchets sit within 0.41 point of their qualified caps, so that no
session can raise one without a new qualification on both platforms, and asks that this be documented and that the
evidence be archived at each promotion. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds, and its
second half is more pressing than the first**:

- The four values are those the audit read: `config/m21-quality-ratchets.properties` holds 0.691 / 0.612 per module and
  0.900 / 0.757 aggregate; the caps are `CoverageQualityGateTest.java:95-96` (0.694341 / 0.615727) and
  `AggregateCoverageGateTest.java:77-78` (0.903026 / 0.761089).
- **The margin is spent by convention, not by a gate.** The gates assert only that a ratchet does not exceed its cap
  (`CoverageQualityGateTest.java:312`). Three thousandths remain under each cap, four under the aggregate branch one. What stops a session from taking them
  is the rule written in the comments and in `rules/testing.md` — a ratchet keeps three times the reference spread
  between platforms — and nothing executes that rule. The audit's "no margin left" describes the convention.
- **The evidence a cap rests on expires.** Each cap's comment cites two workflow runs by number and says the values
  were read from each run's `m21-integrity-<OS>` artifact (`AggregateCoverageGateTest.java:44-50`,
  `CoverageQualityGateTest.java:40-41`). `ci.yml:94` keeps that artifact 14 days. The evidence of the qualifications of
  15 and 22 September is already gone; that of 9 October, on which all four current caps rest, disappears on 23
  October 2026. `rules/testing.md` says the proof "reste consultable dans les artefacts".
- **Nothing compares a cap with a measurement.** The constants are typed from the artifacts into the source. A cap
  typed one digit too high passes every gate, and after the artifacts expire nobody can tell.
- `docs/developer/BUILD_AND_TEST.md` states the rule that a raise needs a qualification on both platforms (rule 3 of
  its "Règle d'évolution"), but not the difference between raising a ratchet inside a cap and raising the cap, nor
  where to read what remains.

A cap is the one number of the coverage device that is neither derived nor checked, and in ten days it will also be
unverifiable.

## What Changes

- The evidence a cap was qualified on — the coverage summaries of both platforms for the qualifying commit — is kept in
  the repository, and each cap is compared with it: a cap equals the lowest measurement of its scale.
- A qualification that lacks a platform, mixes commits, or mixes populations is refused.
- The developer guide says, without stating a number, how to tell a decision (a ratchet inside its cap) from a
  requalification (the cap), and where the remaining distance is read.

## Capabilities

### New Capabilities

- `coverage-qualification-evidence`: the guarantee that a qualified coverage cap is backed by evidence kept with the
  code, measured on both platforms at one commit, and that the cap equals what that evidence shows.

### Modified Capabilities

(none — `gate-verdict-currency` concerns the freshness of the report a gate reads; the scales and their separation are
ADR-0104's and are unchanged)

## Impact

- A small set of text files kept in the repository (location: open question 1 of the design), one set per
  qualification still in force.
- `CoverageQualityGateTest` and `AggregateCoverageGateTest`, or a test beside them, for the comparison — governance
  files, justified in the pull request. No ratchet and no cap changes value.
- ADR-0104 is amended: its "exigences d'une hausse de plafond" gain the kept evidence.
- `docs/developer/BUILD_AND_TEST.md`, `.claude/rules/testing.md` (the sentence about the artifacts),
  `.claude/skills/coverage-ratchet/SKILL.md` (the step that files the evidence).
- **Time-bound**: the artifacts of runs `37925178678` and `37925186306` must be downloaded before they expire, whatever
  becomes of the rest of this change.
- Independent of the other changes of sprint 8.

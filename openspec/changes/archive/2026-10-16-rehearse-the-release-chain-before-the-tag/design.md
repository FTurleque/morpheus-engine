# Design

## Context

- `release.yml`: two build jobs (`linux-release`, `windows-release`) check out the tag's SHA, verify it is reachable from
  `main`, build through `distribution/build-release.{sh,ps1}`, attest with `actions/attest`, keep the attestation bundle
  and upload; `publish` downloads, checks ten expected names, refuses an existing release and runs `gh release create`.
  The Windows job replays `validate-m28.ps1` first.
- `validate-m20.{sh,ps1}`: clean workspace, POM version equal to the argument, `./mvnw clean verify`, a local tag
  `m20-validation-<sha>`, `build-release.*` on that tag, checks on archive, checksum and manifest, then the launcher
  proofs; the tag is deleted on exit. Their default version is `1.0.0`, so the current version must be passed
  (`rules/tooling.md`).
- `nightly.yml` already holds the pattern: the `m19-budgets` lane, a two-platform matrix, checking out "the dispatched
  ref, or develop on a schedule", uploading evidence, added by `make-declared-gates-reachable`.
- A `schedule` trigger runs the workflow file of the default branch (`main`). `origin/develop` is 61 commits ahead of
  `origin/main` today; a lane added on `develop` runs nightly only after the next promotion, and by `workflow_dispatch`
  before that.
- `openspec/specs/gate-reachability` already requires that a validator exist on both platforms or on neither, and that
  the M19 gates run on a schedule. It says nothing of a validator that exists on both platforms and is launched by no
  workflow — which is M20's situation.

## Goals / Non-Goals

**Goals**: the release build is exercised every night on both platforms without a tag; a divergence between what the
build scripts produce and what the release workflow expects is found before the tag; nobody can read a rehearsal as a
qualification.

**Non-Goals**: cutting `v1.2.1` (the maintainer's act, after the sprint's exit criterion is met); rehearsing the
attestation or the publication (see decision 3); changing `release.yml`; a gate on "the POM version has no tag" (it
needs the network and would fail every build between a version bump and its release).

## Decisions

1. **Reach the validator that exists, do not write a second rehearsal.** `validate-m20` already does what the audit asks
   of a "disposable tag", with a tag that never leaves the runner. *Rejected*: a `workflow_dispatch` or `workflow_call`
   entry on `release.yml` with publication disabled — it puts a second mode inside the one workflow that must stay
   simple, gives the rehearsal the `id-token: write` and `attestations: write` permissions of the release jobs, and
   `AuditHardeningWorkflowContractTest` pins that file's trigger. *Rejected*: copying the release jobs into
   `nightly.yml` — a copy of a pipeline drifts, which is the defect being fixed.

2. **A throwaway pre-release tag is refused, and the reason is written down.** It would be a public, immutable tag and a
   public attestation for an artifact nobody is meant to install; `release.yml` refuses its name; the qualification
   procedure forbids it and a test holds that sentence. This design does not weaken any of the three.

3. **The rehearsal says what it did not exercise.** Four things are proven only by a real tag: the reachability of the
   tag from `main`, `actions/attest` through OIDC, the cross-job download with digest check followed by
   `gh release create`, and the public read-back by `verify-release-provenance`. The validator prints them as *not
   exercised* in its summary; the lane's name contains "rehearsal". *Rejected*: attesting in the rehearsal to cover
   more — an attestation is a public Sigstore record that cannot be withdrawn.

4. **A third job compares names, as `publish` does.** After the two platform legs upload their `dist` files, a job
   reads the `expected=(…)` list from `release.yml`, removes the attestation bundles (declared by name as produced only
   by a real release), and requires every remaining name to be present and non-empty. The list is read from the
   workflow, not restated: a rehearsal with its own list would test itself. With
   `disclose-third-party-components-with-the-release`, the SBOM and its checksum enter that list and are therefore
   rehearsed from the day they are added.

5. **Nightly, both platforms, failure fails the run — like M19.** The release build includes `clean verify` and the
   packaging; on Windows the comparable M28 lane costs about 17 minutes. That is too much for every pull request and
   affordable once a night. `workflow_dispatch` allows a run on a branch before a merge, as for M19.

6. **The rule names M20; the generic rule is rejected on measurement.** The first draft required every
   `scripts/validate-<target>` to be launched by a workflow or excused by name. Counted on 2026-10-11 by the reviewing
   agent: 13 targets, 26 files, of which the workflows launch four; after this change 18 to 20 files would sit in the
   excuse list. A rule that is mostly exceptions enforces little and costs a list to maintain. *Rejected in that form.*
   The retained rule is the M20 one, textual by nature (`.yml`, `.sh`, `.ps1`; ADR-0103), in
   `GateReachabilityArchitectureTest`. That test compares validator names without their extension today
   (`:145-149`); "on each platform" needs the extension, which is the one new mechanism. The rule is broken once before
   it is accepted (task 3.4). The existing scenario "A validator that no workflow runs is refused" concerns test classes
   outside Surefire's selection and is not modified: `validate-m20` runs `clean verify`, not such a class. Which of the
   other validators deserve a lane is a question for AUD-TRV-07 (sprint 8), where the scripts themselves get tested.

7. **The tracking issue is reopened by the maintainer, not by an agent.** It is GitHub state, outward-facing, and the
   repository's rule is that #185 closes on a real release. The pages that name #185 are correct as written once it is
   open again; nothing in them is edited to match an accidental closure. A sentence is added to
   `RELEASE_QUALIFICATION.md` warning that a commit message placing "close", "fix" or "resolve" before an issue number
   closes it on promotion to `main`.

8. **No ADR.** A lane in an existing workflow and a rule in an existing gate, inside the decision already taken by
   `make-declared-gates-reachable`.

## Risks / Trade-offs

- **A green rehearsal is not a release.** The four unexercised steps are the ones hardened since `v1.2.0` and never run.
  DT-11 stays open and keeps saying so; this change lowers the risk of the build half, not of the publication half.
- **Schedule runs `main`'s file.** Until the promotion the lane exists only on demand. Same situation as the M19 lane and
  the Dependency-Check alert when they were added; the pull request must say it.
- **Runner time**: two more full reactors per night. The M19 lane already pays one per platform; if the cost matters,
  the two lanes can share a build later — not in this change.
- **`validate-m20` requires a clean workspace and creates a tag in the runner's clone.** On a hosted runner the clone is
  discarded; on a developer machine the script already deletes the tag on exit, including on failure (`trap` /
  `finally`). Task 2.2 confirms the `.ps1` does so on every exit path.
- **Local tag name vs a real tag**: `m20-validation-<sha>` cannot collide with `vX.Y.Z` and is refused by
  `release.yml`'s own pattern if it were ever pushed.

## Open Questions

- Should the rehearsal run only when files under `distribution/`, `scripts/` or the workflows changed, rather than every
  night? Simpler to run it always; revisit if the minutes matter.
- Does the maintainer want the nightly failure notified the same way as the Dependency-Check alert (issue #435 tracks
  whether scheduled-workflow failures reach them at all)?

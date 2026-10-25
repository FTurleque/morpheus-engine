# Design

## Context

- `SpecificationContentReader.read(ProviderReadRequest, EntityIdentityResolver)` returns `ProviderReadResult`:
  provider id, optional content, one `ReadCategoryReport` per requested category with a status among `READ`, `ABSENT`,
  `UNSUPPORTED`, `FAILED`, `PARTIAL`, and diagnostics.
- The two OpenSpec aggregators call the same `OpenSpecCurrentSpecificationReader`, `OpenSpecChangeMetadataReader` and
  `OpenSpecRequirementDeltaReader`. They differ in what surrounds them: the port one probes first, catches a failing
  group and marks its categories, builds the `ProjectSpecification` from the root, and adds diagnostics for
  unsupported and partial categories; the strict one takes the project from the current-specification reader and
  lets any failure propagate.
- **Not verified**: that the two produce the same `NormalizedProjectContent` for a workspace both read completely.
  Read side by side, the project descriptor and the diagnostics list are built differently. Measuring it is the first
  task.
- The sync sequence - scan, prepare, read, publish, complete or fail - is written twice, in
  `MorpheusProjectSyncApiService` and in `MorpheusCli`, and the two already differ (the HTTP route always forces a
  full plan).
- A registered project does not record its provider: `ProjectStoreEntry` is an id and a root locator. Every project
  is synced as OpenSpec because the transports say so.

## Goals / Non-Goals

**Goals**: the publication path goes through the port; an incomplete read is refused by name; the provider module
says what it scans; the anti-lock-in test sees the path.

**Non-Goals**: letting a user choose the provider of a project (a new capability, with a manifest line - not this
change); merging the two sync sequences into one use case (AUD-ARC-09, sprint 12); changing what the port returns.

## Decisions

1. **Use the port that exists; do not declare a second.** *Rejected*: the audit's `ProjectContentReader` - every
   provider and every SDK plugin would implement two interfaces for one content, and `ProviderAntiLockInTest` would
   have two contracts to keep equal.

2. **Publishable is decided once, in the application.** A use case takes a `ProviderReadResult` and returns the content
   or a named refusal. Content is publishable when it is present, no requested category is `FAILED`, and no category
   is `PARTIAL` because a source was rejected (`INVALID_SOURCE` among the codes of its report). A category read
   partially for another reason - a requirement the provider could not normalize, a fence never closed, a requirement
   without scenario - is published with the diagnostics that name what was left out, as the strict reader published
   it; an `UNSUPPORTED` category is published and named by a warning. A missing report counts as unread. This is the
   behaviour of the strict reader on the situations task 1.2 measured, said by the application instead of by an
   exception escaping a provider.

3. **The refusal is the one a caller already knows.** Same shape as "source scan is incomplete": the sync plan is
   marked failed, the HTTP route answers its conflict, the CLI its state failure, and the message names the categories
   and relays the first errors of the provider, which name the file at fault relative to the workspace - through the
   public projection on the HTTP side, never a server path.

4. **Equivalence is measured before the switch.** A test reads the fixtures of `experiments/m0/fixtures/` with both
   aggregators and compares what a publication stores. A difference is either removed in the port aggregator or
   declared in the pull request with what a re-sync of an existing project will show; it is never discovered by a user
   whose unchanged workspace reports changes.

5. **The scanned directories are a constant of the provider module.** The composition site passes them with the
   reader. *Rejected for now*: a method on `SpecificationProvider` or `SpecificationContentReader` - both are
   implemented by third-party plugins, so an added method is a change of the SDK contract, and a default method is the
   compatibility shim the repository refuses. Textual assertion, intent "this directory name does not appear in a
   transport": ArchUnit sees no string literal.

6. **The reader is constructed at one site per adapter.** Composition roots are named by ADR-0109 and `api` already
   reaches the OpenSpec provider through one class; the change keeps it at one. The rewritten assertion of
   `ProjectSyncApiServiceArchitectureTest` says the sync service does not construct the non-port aggregator, and an
   ArchUnit rule says no class outside `com.morpheus.provider.openspec` depends on it until it is removed.

## Risks / Trade-offs

- The port aggregator contains the failures of a file that the strict one let through as exceptions; the error a
  caller reads for a broken workspace changes from the provider's exception to the named refusal, which still relays
  the provider's errors. A failure that is not a file's - a defect, the identity store - leaves the port read as it
  left the strict one. Tests of both transports state what is read.
- The probe runs before the read: a workspace the probe does not support is now refused before any reader runs.

## Open Questions

- **Is content with an `UNSUPPORTED` category publishable?** Yes, with the category named by a warning (ADR-0124);
  decided by the maintainer on 2026-10-25. No shipped provider reports one for the published categories on the
  OpenSpec fixtures; Markdown and synthetic do for the categories they do not carry.
- **Is the strict aggregator removed or kept for tests?** Removed; its test users take the port (2026-10-25).
- **Which `PARTIAL` refuses?** Only one caused by a rejected source (2026-10-25), so that a workspace the strict reader
  published still publishes.

# Proposal

## Why

AUD-ARC-03 and AUD-ARC-13 (sprint 11 of the audit of 2026-10-09) stay partial after the first wave: the application
keeps 21 slice dependencies on a cycle, tolerated by name, in two components; the domain has no polymorphic type and
two decisions on a single domain value still live in application services.

Measured on `11bb1ccf` (2026-10-30) on the bytecode: each of the 21 dependencies comes from a type placed above the
packages that use it - `NormalizedProjectContent` in `ingestion` (read by `read` and `traceability`),
`IntegrationStatusDisclosure` in `security` (projects `context` and `reference` types),
`LocalWritePermissionHardener` in `security` (used by `operability`, touches only the file system), and the lifecycle
state machine in `application.lifecycle`, next to `lifecycle.mutation`, which depends on `orchestration`.

## What Changes

- Move `NormalizedProjectContent` to `application.read`, `IntegrationStatusDisclosure` to `application.context`,
  `LocalWritePermissionHardener` to `application.files`, and the lifecycle transition types to
  `com.morpheus.domain.change.lifecycle`. No persisted format and no public surface names these packages.
- Decide a lifecycle transition by `ChangeLifecycleTransitionRequest.decide()`; its outcome becomes the sealed family
  `ChangeLifecycleTransitionDecision` (`Allowed`, `Blocked`).
- Move the resolution state of an external reference through `ExternalReference` (`resolvedAs`, `withoutResolver`,
  `notFound`) and its traceability resolution to `ExternalReferenceResolutionState.traceabilityResolution()`.
- Replace the named list of the application cycle rule by `beFreeOfCycles()` without exception.
- ADR-0156 consolidates ADR-0152 and adds decision 6.

## Impact

`morpheus-domain`, `morpheus-application`, imports in every module that names a moved type,
`morpheus-architecture-tests`, ADR-0152 archived.

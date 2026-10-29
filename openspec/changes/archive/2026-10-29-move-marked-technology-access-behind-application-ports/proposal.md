# Proposal

## Why

**This change is conditional.** It applies only to the classes that the decision record of
`decide-what-technology-the-application-core-may-touch` marks as *leaving* the application (AUD-ARC-01, sprint 11). If
the maintainer keeps every named class - option B of that change - it is withdrawn, not implemented.

It is written now because the sprint's exit criterion has two branches - "the infrastructure ports exist, or the ban
is extended" - and the first one needs requirements that do not depend on how many classes leave. Under the
recommended option, one does: the outbound transport of `UpdateDiscoveryService`. Under the pure-core option, the ten
file-access classes and the four that read the process environment do too.

Re-read on `develop` at `8b26d3d7` on 2026-10-13, what a move must carry with it:

- `UpdateDiscoveryService` (313 lines) refuses `http:` before any network I/O, never follows a redirect, bounds the
  manifest and its deadline, and reads `file:` manifests itself. It is constructed by `MorpheusProductCli` only, and
  the capability is CLI-only in `contracts/public-surfaces.tsv`.
- Four architecture tests read that file by path (`UpdateTrustBoundaryContractTest`,
  `AuditHardeningWorkflowContractTest`, `ProductionIntegrityContractTest`, `SilentCatchArchitectureTest`), and the
  file-access classes are read by path too - `LocalSourceInventoryScanner` by ten test files.

A move that carried the code and left the assertions pointing at an emptied file would keep every gate green while
the invariant went unwatched.

## What Changes

- For each class the record marks as leaving: the application declares a port for what the use case needs, an adapter
  implements it with the technology, and the process wires it explicitly.
- The decisions stay in the application - which scheme is accepted, what is refused before any I/O, what a result
  claims. Only the access leaves.
- Every assertion that read the moved code follows it, and the named list of the technology gate shrinks by the same
  change.

## Capabilities

### New Capabilities

- `application-infrastructure-ports`: how the application reaches a technology the decision record took out of it.

### Modified Capabilities

(none)

## Impact

- **Depends on** the ADR of `decide-what-technology-the-application-core-may-touch`; cannot start before it.
- Under the recommended option: `application.product` (a port, `UpdateDiscoveryService` without its client),
  `morpheus-cli` (the implementation and the wiring in `MorpheusProductCli`), four architecture tests, the named list
  of the technology gate. No new module.
- Under the pure-core option, additionally: a home for ten classes, which is a structural decision of its own
  (coverage population of ADR-0104, `ImportedClasspathCompletenessTest`, release layout) and is split into one lot per
  family.
- No public surface changes: same commands, same outputs, same refusals. The ADR is clarified only so that its list says where each class lives.

# Proposal

## Why

AUD-ARC-01 (sprint 11) reports that `morpheus-application`, the layer that defines the ports, does file I/O and
outbound HTTP itself, while the repository's diagram presents it as "ports + use cases" and only two of its packages
carry a ban on infrastructure. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds, and the
inventory is wider than the two families it names**:

- `java.net.http`: 1 file, `product/UpdateDiscoveryService.java` (lines 8-10, and `HttpClient.newBuilder()` at 44).
- `java.nio.file.Files`: 10 files, as the audit counted - `sync` x4, `security` x2, `discovery` x2, `product`, `files`.
  `java.nio.file.Path` alone is imported by 23 files and sits in port signatures (`SpecificationProvider.probe(Path)`,
  `ProviderReadRequest`).
- Process environment: `System.getenv` in 1 file (`operability/LocalOperationalRuntime`), `System.getProperty` in 3
  (`ProductMetadata`, `SensitiveValueRedactor`, `LocalWritePermissionHardener`).
- Standard streams: `System.err` in 1 file (`LocalOperationalRuntime`). Threads: one virtual-thread executor, in
  `UpdateDiscoveryService`.
- `java.sql`, Jackson (`tools.jackson`, `com.fasterxml`), `ProcessBuilder`, `Runtime.getRuntime`, class loading: **0
  file**. The module's POM declares `morpheus-domain` and nothing else in compile scope.
- The textual ban exists for `application.reasoning` and `application.policy` only
  (`ReasoningPlatformArchitectureTest`, `PolicyPlatformArchitectureTest`). The audit writes "2 of the 36 packages", then "38
  packages can acquire": 36 packages are declared today, so 34 are ungated.

Two statements of the audit are softer on HEAD than written. "No clock port" - `java.time.Clock` is injected by
constructor in six services; only `LocalOperabilityService` and `OperationalRecorder` call `Instant.now()` directly.
"Testing the update discovery needs a real HTTP client" - `UpdateDiscoveryService(HttpClient, Duration)` exists,
package-private, and is what its tests use.

What is missing is the decision. Nothing says whether this I/O is a debt or the design, so nothing can refuse the
next one: a package of the application can acquire `java.sql` or a second HTTP client today and no gate reacts.

## What Changes

- An ADR decides what `morpheus-application` may touch, family by family, from the measured inventory. The maintainer
  chooses between three options laid out in `design.md`; this change is written so that its requirements hold under
  each of them.
- Whatever the option: a technology family the application does not use is refused for **all** its packages, and a
  family it does use is confined to the classes measured today, named, in a list that can shrink and cannot grow in
  silence. Each named class carries what the ADR decided for it - stays, or leaves.
- The repository's descriptions of the application layer say what the ADR says and what the gate enforces.
- Moving the classes marked as leaving is **not** in this change: it is
  `move-marked-technology-access-behind-application-ports`, which applies only to what the ADR marks.

## Capabilities

### New Capabilities

- `application-technology-boundary`: which technology the application layer may reach, and through which classes.

### Modified Capabilities

(none)

## Impact

- **A new ADR** in `docs/architecture/adr/`, under the next free number (the highest attributed on 2026-10-13 is
  `0110`; re-read the directory before assigning), indexed in `docs/architecture/adr/README.md`.
- One architecture test class in `morpheus-architecture-tests`; `architectureTestsMinimum` rises with it, by the
  `coverage-ratchet` and `live-numbers` procedures. No product code changes.
- `.claude/CLAUDE.md` (diagram caption and "Architecture" section), `.claude/rules/architecture.md` (section "Coeurs
  purs"), `docs/architecture/arc42/05-vue-blocs.md` and `11-risques-dette.md`, `docs/architecture/risks/register.md`.
- No public surface, no OpenAPI, no manifest line.
- **Sprints 12 and 13 wait for the ADR**, not for this change's gate: AUD-ARC-06, AUD-TRV-03 and AUD-PRF-05 need to
  know whether packages are about to move out of the application.

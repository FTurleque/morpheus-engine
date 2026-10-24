# Design

## Context

Measured on `8b26d3d7`, 2026-10-13, by search of `morpheus-application/src/main/java` (343 files, 36 packages). The
lot re-measures with ArchUnit before the ADR is written: a search sees imports, not dependencies.

| Family | Files | Where |
|---|---|---|
| Outbound HTTP (`java.net.http`) | 1 | `product/UpdateDiscoveryService` |
| File access (`java.nio.file.Files`, `FileSystems`, watch service, ACL views) | 10 | `sync/LocalSourceInventoryScanner`, `LocalSourceWatcher`, `SourceFingerprint`, `WorkspacePathBoundary`; `security/ExternalJarIntegrity`, `LocalWritePermissionHardener`; `discovery/ProjectDiscoveryService`, `WorkspaceRootResolver`; `files/SafeWorkspaceFileResolver`; `product/UpdateDiscoveryService` |
| Process environment (`System.getenv`, `System.getProperty`) | 4 | `operability/LocalOperationalRuntime`, `SensitiveValueRedactor`; `product/ProductMetadata`; `security/LocalWritePermissionHardener` |
| Standard streams | 1 | `operability/LocalOperationalRuntime` |
| Threads and executors | 1 | `product/UpdateDiscoveryService` |
| Wall clock read directly (`Instant.now()`) | 2 | `operability/LocalOperabilityService`, `OperationalRecorder` |
| SQL, JSON library, process execution, class loading | 0 | - |

What the ten file-access classes are, which the audit does not say:

- Five are **security or integrity boundaries** asserted textually by path: `ExternalJarIntegrity` (pinned JAR copy),
  `LocalWritePermissionHardener`, `SafeWorkspaceFileResolver`, `LocalSourceInventoryScanner` (named by ten
  architecture test files), `SourceFingerprint`. Moving one moves its assertions with it.
- Three have **no caller under any `src/main`**: `LocalSourceWatcher`, `ProjectDiscoveryService`, and
  `WorkspaceRootResolver` (used by `ProjectDiscoveryService` only). Whether they stay at all comes before where they
  live.
- The product is local-first: a workspace on disk is its input, and `Path` is already the vocabulary of its ports,
  including the plugin SDK contract.

## Goals / Non-Goals

**Goals**: a written decision; a gate that makes the decision hold from the day it is taken; descriptions that say
what the gate enforces.

**Non-Goals**: moving a class (the conditional change); the package cycles (AUD-ARC-03,
`let-application-package-cycles-only-decrease`); the ingestion path (AUD-ARC-02,
`ingest-a-project-through-the-provider-port`); banning `java.nio.file.Path`.

## The decision put to the maintainer

**Option A - a pure core.** Every family of the table leaves the application; ports are declared there and
implemented in an adapter. Truthful diagram, rules testable without a disk. Cost: about 2 170 lines in ten classes to
move, five of them pinned by textual security assertions; a home to find for them - a new reactor module changes the
coverage population (ADR-0104), `ImportedClasspathCompletenessTest` and the release layout, an existing adapter
(`store-*`, `provider-*`) is the wrong owner for a workspace boundary; and `Path` stays in the port signatures anyway,
so "pure" would still mean "no access", not "no `java.nio.file`".

**Option B - a service layer that may do local I/O.** The application is declared a use-case layer allowed to read
the local workspace and the environment; the ban is extended to everything else. Cheapest, and honest. Cost: outbound
HTTP stays in the layer that defines ports, and the diagram stops promising a technology-free core.

**Option C - named exceptions, each with a fate (recommended).** The same gate as B, and the ADR marks each named
class *stays* or *leaves*:

- *leaves*: outbound HTTP (`UpdateDiscoveryService`'s transport) - the only network egress of the core, one class, a
  port with one obvious adapter (`cli`, which owns the process), and the family where a second use would matter most;
- *stays*: the workspace and integrity classes - local file access is what a local-first use case is made of, they are
  already the security boundary, and nothing observed is fixed by moving them;
- *decided separately*: the three classes without a production caller - remove or keep is a question for
  `remove-unreachable-and-unreferenced-code`'s rule, not for this ADR.

C costs what B costs plus one small extraction, and leaves A reachable: the named list is its counter, as
`COMPOSITION_ROOTS` is for ADR-0109.

## Decisions

1. **The requirements are the same under A, B and C.** A family unused today is refused everywhere; a family in use is
   confined to a named, exact, shrink-only list; each entry carries its fate. A is "every entry leaves", B is "every
   entry stays", C is mixed. The gate is written once, before the maintainer has to choose, and the choice changes a
   column of the ADR, not the test.

2. **ArchUnit rules, with the existing textual bans kept.** Intent, in one sentence: *no class of the application
   depends on this technology, except the named ones* - a dependency, so a rule on the bytecode
   (`enforcement-choice`, `.claude/rules/architecture.md`). `morpheus-application` is in the imported set and `failOnEmptyShould` is active.
   The textual assertions on `application.reasoning` and `application.policy` are not migrated: they also refuse a
   mention, and they ban more (`com.morpheus.application.store`, `SELECT * FROM`). *Rejected*: extending the textual
   scan to 36 packages - a mention of `java.sql` in a Javadoc would fail, and an unspelled dependency would pass.

3. **A family is a list of types or calls, written in the test.** File access is `Files`, `FileSystems`, channels,
   watch and attribute views, and the `java.io` file streams; `Path`, `LinkOption`, `IOException` and the in-memory
   streams are vocabulary. The environment is the calls `System.getenv` and `System.getProperty`. The ADR states the
   same lists; the test is the living source.

4. **The named list is exact, by the pattern of `AdapterCompositionRootArchitectureTest`.** A class that reaches a
   confined family without being named fails; a named class that no longer reaches it fails too. Adding a name takes
   a consolidated ADR that replaces this one (`.claude/rules/adr.md`).

5. **`java.time.Clock` is the clock port.** It is a JDK type already injected by six services; a port of the project's
   own would be a second name for it. The two direct `Instant.now()` of `operability` are listed in the ADR; gating
   them belongs to `inject-the-operational-runtime`, which rebuilds those classes.

## Risks / Trade-offs

- A named list is a list of names, which sprint 12 wants fewer of (AUD-ARC-06). It is accepted here because the names
  are the debt counter, and because under A or C it shrinks to nothing or to a package.
- The gate freezes today's state before the decision is taken. That is its purpose: the decision is then about what
  exists, not about what was added while it was pending.

## Open Questions

- **A, B or C?** Recommendation: C.
- **Under A or C, where does an implementation live?** Recommendation: outbound HTTP in `morpheus-cli`, behind a port
  of `application.product`; no new module for one class.
- **Do the three classes without a production caller stay?** Recommendation: decide it first; a class that is removed
  needs no fate.

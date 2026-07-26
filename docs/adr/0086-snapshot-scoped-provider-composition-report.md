# ADR-0086 — Rapport de composition provider snapshot-scoped

Statut : **Proposée — M18**

Date : 26 juillet 2026

## Contexte

La provenance de chaque entité normalisée est déjà portée par `Provenance`, mais cela ne suffit pas à expliquer après coup :

- quels providers ont participé à un import ;
- leur precedence ;
- lesquels étaient optionnels/required ;
- quels conflits ont été détectés ;
- comment ils ont été résolus.

Ces informations doivent survivre à un reopen SQLite sans contaminer `SnapshotBusinessContent` avec des détails d'orchestration provider.

## Décision

Créer un port applicatif dédié :

```text
ProviderCompositionReportStore
  put(snapshotId, report)
  find(snapshotId)
```

Le rapport est immutable, provider-neutral et snapshot-scoped.

Persistance :

```text
MemoryProviderCompositionReportStore
SqliteProviderCompositionReportStore
SQLite V012
```

Le modèle persiste :

```text
snapshot composition summary
provider contributions
  providerId
  precedence
  required
  outcome
  itemCount
conflicts
  entityType
  logicalKey
  resolution
  winner provider/entity
  reason
conflict contenders
  provider/entity
```

## Atomicité de publication

`ProjectSnapshotImportService` reçoit un overload `publishFull(ComposedProjectContent, ...)`.

Ordre :

```text
create BUILDING snapshot
persist business content
persist traceability
persist provider composition report
validate diagnostics
activate snapshot
```

Ainsi un snapshot ACTIVE issu d'une composition M18 possède toujours son rapport. L'import historique single-provider reste compatible via l'overload existant et un rapport vide/single-provider n'est pas inventé rétroactivement.

## Query

Un service applicatif lit le rapport du snapshot ACTIVE ou d'un snapshot explicitement demandé. CLI/MCP/HTTP exposent une projection JSON-safe, jamais les objets store bruts.

## Invariants

```text
published business content != provider composition governance
provider provenance survives reopen
conflict history != transient log only
ACTIVE composed snapshot -> composition report persisted
historical snapshots immutable
```

## Validation attendue

```text
Memory == SQLite report PASS
SQLite close/reopen PASS
resolved/unresolved conflict round-trip PASS
ACTIVE composed snapshot has report PASS
single-provider legacy import remains compatible PASS
query ACTIVE/snapshot PASS
```
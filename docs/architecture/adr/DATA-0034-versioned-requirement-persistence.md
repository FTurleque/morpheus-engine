---
id: DATA-0034
title: "Introduire une persistance métier versionnée par occurrence"
domain: data
rules: false
---

# Introduire une persistance métier versionnée par occurrence

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

Le domaine porte une occurrence versionnée d'une entité (`EntityVersion<T>` : `EntityVersionId`, `DomainIdentity`,
`SpecificationVersionId`, `TemporalState`, contenu) et le cycle de vie technique de `KnowledgeSnapshot` avec son
activation atomique (ADR-0031, ADR-0120). Le contenu métier lui-même doit être stocké durablement, et chaque
occurrence persistée doit répondre explicitement à :

```text
quelle identité logique ?
quelle occurrence ?
quelle SpecificationVersion ?
quel KnowledgeSnapshot ?
quel TemporalState ?
```

Plutôt que de créer d'emblée toutes les tables métier, un premier vertical slice complet porte sur `Requirement`, qui
a une identité MORPHEUS stable, un rattachement structurel à `Specification`, un contenu métier, une provenance, et
sert d'oracle à la séparation `CURRENT` / `PROPOSED`.

## Decision

**Pattern de référence.** La persistance métier versionnée est portée par `Requirement` ; les autres familles métier
réutilisent le même pattern (ADR-0044).

**Version logique et snapshot technique séparés.** Une même `SpecificationVersion` peut être représentée par
plusieurs snapshots techniques, par exemple après une reconstruction ou un changement de provider
(`SpecificationVersion 1 <--- N KnowledgeSnapshot`). La relation est matérialisée séparément et ne fait pas de
l'identité du snapshot une identité métier.

**Ports applicatifs** (`morpheus-application`) : `VersionedRequirementStore`, `SnapshotSpecificationVersionBinding`,
`RequirementVersionRecord`, `CurrentRequirementQueryService`. `RequirementVersionRecord` associe un
`KnowledgeSnapshotId` à une `EntityVersion<Requirement>` et impose que l'identité de l'occurrence soit celle de
l'exigence (`entityVersion.entityIdentity == requirement.id.value`). Le store exige en plus que la
`SpecificationVersion` liée au snapshot soit celle de l'occurrence.

**Schéma SQLite** (migration `V004__versioned_requirement_persistence.sql`), en colonnes typées, sans payload JSON
générique :

- `specification_versions` persiste la version logique indépendamment des snapshots techniques ;
- `snapshot_specification_versions` lie explicitement `snapshot_id -> specification_version_id` ; plusieurs snapshots
  peuvent référencer la même version logique ;
- `requirement_versions` persiste de quoi reconstruire exactement une `EntityVersion<Requirement>`, provenance
  inline : identifiants d'occurrence, d'identité, d'exigence, de spécification, de version et de snapshot, état
  temporel, clé, titre, énoncé, provider, source, preuve.

**Unicité `CURRENT`.** Dans un snapshot, une identité logique a au plus une occurrence `CURRENT`
(`(snapshot_id, entity_identity_id)` unique quand `temporal_state = 'CURRENT'`). Plusieurs occurrences `PROPOSED` de
la même identité restent permises. Le store mémoire applique la même règle que SQLite.

**Vue `CURRENT`.** `CurrentRequirementQueryService` ne lit jamais toutes les occurrences d'un projet pour en choisir
une arbitrairement : il résout le snapshot actif du projet, puis l'occurrence `CURRENT` de l'identité dans ce
snapshot. Un `PROPOSED` stocké dans le snapshot actif reste invisible dans la vue `CURRENT`, y compris après
réouverture de la base SQLite.

**Idempotence et collisions.** `putSpecificationVersion`, `bindSnapshotVersion` et `putRequirementVersion` sont
idempotents quand la définition est identique. Une même identité technique avec une définition différente lève
`KnowledgeStoreException`. Un binding snapshot/version incompatible, ou une seconde occurrence `CURRENT` pour la même
identité dans un snapshot, est rejeté explicitement.

**Frontières.**

```text
DomainIdentity          != EntityVersionId
SpecificationVersion    != KnowledgeSnapshot
TemporalState           != KnowledgeSnapshotState
Requirement normalisé   != occurrence persistée
```

La persistance ne modifie pas les providers et ne rend pas SQLite visible depuis le domaine. Elle n'introduit ni
payload JSON métier, ni ORM, ni framework serveur.

## Do's and Don'ts

### Do

- Persister une occurrence métier avec son identité logique, son identifiant d'occurrence, sa `SpecificationVersion`,
  son snapshot et son `TemporalState`.
- Lier un snapshot à sa `SpecificationVersion` par un binding explicite.
- Résoudre la vue `CURRENT` par le snapshot actif, puis par l'identité.
- Appliquer les mêmes règles d'unicité et de collision dans `store-memory` et `store-sqlite`.

### Don't

- Ne pas stocker le contenu métier en payload JSON générique.
- Ne pas accepter deux occurrences `CURRENT` de la même identité dans un snapshot.
- Ne pas accepter une occurrence dont la `SpecificationVersion` diffère de celle liée à son snapshot.
- Ne pas remplacer silencieusement une définition existante par une définition différente.

## Consequences

### Positives

- Chaque occurrence persistée dit à quelle identité, version et snapshot elle appartient.
- Plusieurs snapshots techniques peuvent représenter une même version logique sans dupliquer l'identité métier.
- Le pattern est réutilisable par les autres familles métier.

### Négatives

- Une table typée par famille métier, et non un stockage générique.

### Risques

- Une divergence entre `store-memory` et `store-sqlite` sur les règles d'unicité ou de collision fausserait les tests
  qui reposent sur le store mémoire.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/VersionedRequirementPersistenceTest.java` :
  aller-retour mémoire et SQLite, `PROPOSED` concurrents permis et rattachements invalides rejetés sur les deux
  stores, vue `CURRENT` limitée au snapshot actif et à l'état `CURRENT`, séparation conservée après réouverture
  SQLite.
- `RequirementVersionRecord` refuse dans son constructeur une identité d'occurrence différente de celle de
  l'exigence.
- L'index unique partiel `uq_requirement_versions_current_snapshot_identity` de
  `morpheus-store-sqlite/src/main/resources/db/migration/V004__versioned_requirement_persistence.sql` tient l'unicité
  `CURRENT` en SQLite.
- Revue : absence de payload JSON générique dans les migrations.

## References

- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — migrations du schéma SQLite.
- [ADR-0031](ARCH-0031-explicit-temporal-projection-and-entity-version.md) — projection temporelle et
  `EntityVersion`.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas.
- [ADR-0036](DATA-0036-published-history-comparison-and-logical-rollback.md) — comparaison de l'historique publié.
- [ADR-0044](DATA-0044-snapshot-business-content-projection.md) — projection du contenu métier par snapshot.
- [ADR-0120](DATA-0120-snapshots-versionnes-et-activation-atomique.md) — snapshots versionnés et activation atomique.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé neutre.
- [Validation M3](../../project/validation/VALIDATION_M3.md)

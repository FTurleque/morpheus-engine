---
id: DATA-0141
title: "Contenu métier persisté par snapshot en tables typées"
domain: data
rules: false
---

# Contenu métier persisté par snapshot en tables typées

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0034, ADR-0044

## Context

Le domaine porte une occurrence versionnée d'une entité (`EntityVersion<T>` : `EntityVersionId`, `DomainIdentity`,
`SpecificationVersionId`, `TemporalState`, contenu) et le cycle de vie technique de `KnowledgeSnapshot` avec son
activation atomique (ADR-0136, ADR-0143). Le contenu métier lui-même doit être stocké durablement et rester
requêtable, et chaque occurrence persistée doit répondre explicitement à :

```text
quelle identité logique ?
quelle occurrence ?
quelle SpecificationVersion ?
quel KnowledgeSnapshot ?
quel TemporalState ?
```

`Requirement` a une identité MORPHEUS stable, un rattachement structurel à `Specification`, un contenu métier, une
provenance, et sert d'oracle à la séparation `CURRENT` / `PROPOSED` : il porte le pattern de persistance versionnée.
Les autres familles du contenu normalisé (`Specification`, `Scenario`, `ChangeProposal`, `Constraint`,
`DesignDecision`, `ImplementationTask`, `AcceptanceCriterion`, `Evidence`) ont des identités métier stables et une
provenance, mais pas de `TemporalState` ni d'`EntityVersionId` de production : leur persistance ne doit pas leur
inventer une temporalité artificielle.

## Decision

### 1. Version logique et snapshot technique séparés

Une même `SpecificationVersion` peut être représentée par plusieurs snapshots techniques, par exemple après une
reconstruction ou un changement de provider (`SpecificationVersion 1 <--- N KnowledgeSnapshot`). La relation est
matérialisée séparément et ne fait pas de l'identité du snapshot une identité métier.

```text
DomainIdentity          != EntityVersionId
SpecificationVersion    != KnowledgeSnapshot
TemporalState           != KnowledgeSnapshotState
Requirement normalisé   != occurrence persistée
```

### 2. `Requirement` : persistance versionnée par occurrence

**Ports applicatifs** (`morpheus-application`) : `VersionedRequirementStore`, `SnapshotSpecificationVersionBinding`,
`RequirementVersionRecord`, `CurrentRequirementQueryService`. `RequirementVersionRecord` associe un
`KnowledgeSnapshotId` à une `EntityVersion<Requirement>` et impose que l'identité de l'occurrence soit celle de
l'exigence (`entityVersion.entityIdentity == requirement.id.value`). Le store exige en plus que la
`SpecificationVersion` liée au snapshot soit celle de l'occurrence.

**Schéma SQLite** (migration `V004__versioned_requirement_persistence.sql`) :

- `specification_versions` persiste la version logique indépendamment des snapshots techniques ;
- `snapshot_specification_versions` lie explicitement `snapshot_id -> specification_version_id` ; plusieurs snapshots
  peuvent référencer la même version logique ;
- `requirement_versions` persiste de quoi reconstruire exactement une `EntityVersion<Requirement>`, provenance
  inline : identifiants d'occurrence, d'identité, d'exigence, de spécification, de version et de snapshot, état
  temporel, clé, titre, énoncé, provider, source, preuve.

**Unicité `CURRENT`.** Dans un snapshot, une identité logique a au plus une occurrence `CURRENT`
(`(snapshot_id, entity_identity_id)` unique quand `temporal_state = 'CURRENT'`). Plusieurs occurrences `PROPOSED` de
la même identité restent permises.

**Vue `CURRENT`.** `CurrentRequirementQueryService` ne lit jamais toutes les occurrences d'un projet pour en choisir
une arbitrairement : il résout le snapshot actif du projet, puis l'occurrence `CURRENT` de l'identité dans ce
snapshot. Un `PROPOSED` stocké dans le snapshot actif reste invisible dans la vue `CURRENT`, y compris après
réouverture de la base SQLite.

**Idempotence et collisions.** `putSpecificationVersion`, `bindSnapshotVersion` et `putRequirementVersion` sont
idempotents quand la définition est identique. Une même identité technique avec une définition différente lève
`KnowledgeStoreException`. Un binding snapshot/version incompatible, ou une seconde occurrence `CURRENT` pour la même
identité dans un snapshot, est rejeté explicitement.

### 3. Les autres familles : projection complète et immuable par snapshot

**Projection.** Le port `SnapshotBusinessContentStore` persiste un `SnapshotBusinessContent` : une occurrence immuable
du contenu normalisé, possédée explicitement par un `KnowledgeSnapshotId` et un `SpecificationVersionId`. Elle contient
les spécifications, scénarios, changements, contraintes, décisions de conception, tâches d'implémentation, critères
d'acceptation (ADR-0081) et preuves. `Requirement` reste exclusivement dans `VersionedRequirementStore`.

**Identité et temporalité.** Les identités métier existantes (`SpecificationId`, `ScenarioId`, `ChangeId`,
`ConstraintId`, `DesignDecisionId`, `TaskId`, `EvidenceId`…) restent canoniques. Une occurrence dans un snapshot n'est
ni une nouvelle `DomainIdentity` ni une `EntityVersion` inventée. La cohérence temporelle de lecture est obtenue par
le choix du snapshot publié ; elle n'est pas simulée par un faux `TemporalState`.

**Ownership.** Avant tout `putSnapshotContent`, le store exige que le snapshot existe, qu'il soit lié à une
`SpecificationVersion`, et que cette version soit celle du contenu. Les `Specification` et `ChangeProposal`
appartiennent au même projet que le snapshot.

**Intégrité.** Une projection est complète et immuable : au plus une par `KnowledgeSnapshotId`. Même snapshot et même
contenu : idempotent ; même snapshot et contenu différent : `KnowledgeStoreException`. Les collections de premier
niveau sont ordonnées canoniquement par identité, et les identités sont uniques dans chaque famille. Les relations
internes sont validées : une contrainte, une décision de conception, une tâche, et un critère d'acceptation qui en
nomme un, référencent un `ChangeProposal` présent dans la projection. `Scenario.requirementId` reste une référence
typée vers `Requirement`, persisté séparément ; aucun lien alternatif n'est fabriqué.

**Preuve et provenance.** Toute entité qui porte une `Provenance` référence un `EvidenceId` présent dans la
projection. La reconstruction conserve exactement la provenance (`providerId`, `providerVersion` optionnel,
`SourceLocator`, `externalId` et `sourceRevision` optionnels, `evidenceId`) et, pour une `Evidence`, son
`SourceLocator`, sa `SourceRange` et son `excerptHash` optionnels.

**Schéma SQLite.** Migration `V007__snapshot_business_content_projection.sql`, complétée par les migrations des
familles ajoutées ensuite. Les listes sont persistées dans des tables enfants avec un `ordinal` qui reconstruit leur
ordre exact.

### 4. Tables typées, mêmes règles sur les deux backends

Le contenu métier est persisté en colonnes typées dans des tables normalisées, une par famille : ni payload JSON
métier générique, ni ORM, ni framework serveur, ni index plein texte (FTS). La persistance ne modifie pas les
providers et ne rend pas SQLite visible depuis le domaine.

Le store mémoire applique les mêmes règles d'unicité, d'ownership et de collision que SQLite.
`MemorySnapshotBusinessContentStore` et `SqliteSnapshotBusinessContentStore` exposent `putSnapshotContent` et
`findSnapshotContent` avec la même sémantique observable ; l'adaptateur mémoire compose `SpecificationKnowledgeStore`
et `VersionedRequirementStore` pour réutiliser les mêmes règles d'ownership. SQLite reconstruit exactement la
projection après fermeture et réouverture.

## Do's and Don'ts

### Do

- Persister une occurrence de `Requirement` avec son identité logique, son identifiant d'occurrence, sa
  `SpecificationVersion`, son snapshot et son `TemporalState`.
- Lier un snapshot à sa `SpecificationVersion` par un binding explicite.
- Résoudre la vue `CURRENT` par le snapshot actif, puis par l'identité.
- Persister le contenu métier hors `Requirement` comme une projection complète et immuable d'un snapshot.
- Vérifier l'existence du snapshot et la concordance de sa `SpecificationVersion` avant d'écrire une occurrence ou une
  projection.
- Ordonner canoniquement les collections par identité et persister l'ordre des listes par un `ordinal`.
- Rattacher toute entité provenancée à une `Evidence` présente dans la projection.
- Appliquer les mêmes règles d'unicité, d'ownership et de collision dans `store-memory` et `store-sqlite`.

### Don't

- Ne pas stocker de payload JSON métier générique.
- Ne pas accepter deux occurrences `CURRENT` de la même identité dans un snapshot.
- Ne pas accepter une occurrence dont la `SpecificationVersion` diffère de celle liée à son snapshot.
- Ne pas remplacer silencieusement une définition ou une projection existante par une définition différente.
- Ne pas attribuer de `TemporalState` ni d'`EntityVersionId` aux familles de la projection.
- Ne pas persister `Requirement` dans la projection.

## Consequences

### Positives

- Chaque occurrence persistée dit à quelle identité, version et snapshot elle appartient.
- Plusieurs snapshots techniques peuvent représenter une même version logique sans dupliquer l'identité métier.
- Toutes les familles métier sont requêtables par snapshot publié, avec leur provenance.
- Mémoire et SQLite partagent les mêmes règles d'ownership et de collision.

### Négatives

- Une table typée par famille métier, et non un stockage générique ; une famille ajoutée exige ses propres tables.
- Une projection est réécrite en entier pour chaque snapshot ; elle ne se met pas à jour par morceaux.

### Risques

- Une divergence entre `store-memory` et `store-sqlite` sur les règles d'unicité, d'ownership ou de collision
  fausserait les tests qui reposent sur le store mémoire.

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
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SnapshotBusinessContentPersistenceTest.java` :
  même projection canonique en mémoire et en SQLite, règles d'ownership et de binding équivalentes, refus des
  identités dupliquées, des liens de changement rompus et des preuves inconnues, idempotence et collision, ordre des
  listes, réouverture SQLite.
- Le constructeur compact de `SnapshotBusinessContent`
  (`morpheus-application/src/main/java/com/morpheus/application/store/SnapshotBusinessContent.java`) ordonne les
  collections et refuse une preuve ou un changement inconnu.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteSnapshotBusinessContentProjectionTest.java`
  exerce la projection SQLite.
- Revue : absence de payload JSON métier dans les migrations.

## References

- [ADR-0021](DATA-0021-sqlite-schema-migrations-foundation.md) — migrations du schéma SQLite.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle et
  `EntityVersion`.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas.
- [ADR-0036](DATA-0036-published-history-comparison-and-logical-rollback.md) — historique publié.
- [ADR-0081](DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation dans la projection.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé neutre.
- [ADR-0137](ARCH-0137-lectures-et-recherche-sur-le-snapshot-publie.md) — lectures métier et requête lexicale sur le snapshot publié.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [Validation M3](../../project/validation/VALIDATION_M3.md), [Validation M5](../../project/validation/VALIDATION_M5.md)

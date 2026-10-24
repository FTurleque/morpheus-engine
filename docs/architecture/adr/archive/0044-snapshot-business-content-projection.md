# ADR-0044 — Projection métier requêtable snapshot-scoped

> **Archivée.** Cette décision est consolidée dans [ADR-0141](../DATA-0141-contenu-metier-par-snapshot.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0141**
- Date : 2026-07-23

## Context

`Requirement` a une persistance versionnée complète (ADR-0034). Les autres familles métier du contenu normalisé
(`Specification`, `Scenario`, `ChangeProposal`, `Constraint`, `DesignDecision`, `ImplementationTask`,
`AcceptanceCriterion`, `Evidence`) doivent être persistées et requêtables. Elles ont des identités métier stables et
une provenance, mais pas de `TemporalState` ni d'`EntityVersionId` de production : leur persistance ne doit pas leur
inventer une temporalité artificielle.

## Decision

**Projection par snapshot.** Le port `SnapshotBusinessContentStore` persiste un `SnapshotBusinessContent` : une
occurrence immuable du contenu normalisé, possédée explicitement par un `KnowledgeSnapshotId` et un
`SpecificationVersionId`. Elle contient les spécifications, scénarios, changements, contraintes, décisions de
conception, tâches d'implémentation, critères d'acceptation (ADR-0081) et preuves. `Requirement` reste exclusivement
dans `VersionedRequirementStore`.

**Identité et temporalité.** Les identités métier existantes (`SpecificationId`, `ScenarioId`, `ChangeId`,
`ConstraintId`, `DesignDecisionId`, `TaskId`, `EvidenceId`…) restent canoniques. Une occurrence dans un snapshot n'est
ni une nouvelle `DomainIdentity` ni une `EntityVersion` inventée. La cohérence temporelle de lecture est obtenue par
le choix du snapshot publié ; elle n'est pas simulée par un faux `TemporalState`.

**Ownership.** Avant tout `putSnapshotContent`, le store exige que le snapshot existe, qu'il soit lié à une
`SpecificationVersion`, et que cette version soit celle du contenu. Les `Specification` et `ChangeProposal` appartiennent
au même projet que le snapshot.

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

**Schéma SQLite.** Les tables sont normalisées, sans payload JSON métier (migration
`V007__snapshot_business_content_projection.sql`, complétée par les migrations des familles ajoutées ensuite). Les
listes sont persistées dans des tables enfants avec un `ordinal` qui reconstruit leur ordre exact.

**Backends.** `MemorySnapshotBusinessContentStore` et `SqliteSnapshotBusinessContentStore` exposent
`putSnapshotContent` et `findSnapshotContent` avec la même sémantique observable. L'adaptateur mémoire compose
`SpecificationKnowledgeStore` et `VersionedRequirementStore` pour réutiliser les mêmes règles d'ownership. SQLite
reconstruit exactement la projection après fermeture et réouverture.

**Hors de cette décision.** Ni nouvelle temporalité métier, ni `EntityVersionId` artificiel, ni JSON de persistance,
ni ORM, ni index plein texte (FTS).

## Do's and Don'ts

### Do

- Persister le contenu métier hors `Requirement` comme une projection complète et immuable d'un snapshot.
- Vérifier l'existence du snapshot et la concordance de sa `SpecificationVersion` avant d'écrire une projection.
- Ordonner canoniquement les collections par identité et persister l'ordre des listes par un `ordinal`.
- Rattacher toute entité provenancée à une `Evidence` présente dans la projection.

### Don't

- Ne pas attribuer de `TemporalState` ni d'`EntityVersionId` aux familles de la projection.
- Ne pas persister `Requirement` dans la projection.
- Ne pas remplacer une projection existante par un contenu différent.
- Ne pas stocker de payload JSON métier.

## Consequences

### Positives

- Toutes les familles métier sont requêtables par snapshot publié, avec leur provenance.
- Mémoire et SQLite partagent les mêmes règles d'ownership et de collision.

### Négatives

- Une projection est réécrite en entier pour chaque snapshot ; elle ne se met pas à jour par morceaux.
- Une famille ajoutée exige ses propres tables typées.

### Risques

- Une divergence entre les deux backends fausserait les tests qui reposent sur le store mémoire.

## Compliance and Enforcement

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

- [ADR-0034](0034-versioned-requirement-persistence.md) — persistance versionnée de `Requirement`.
- [ADR-0036](../DATA-0036-published-history-comparison-and-logical-rollback.md) — historique publié.
- [ADR-0043](0043-deterministic-requirement-lexical-query.md) — requête lexicale déterministe.
- [ADR-0081](../DATA-0081-first-class-acceptance-verification-evidence.md) — critères d'acceptation dans la projection.
- [ADR-0121](../ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé neutre.
- [ADR-0122](0122-lectures-metier-sur-le-snapshot-publie.md) — lectures métier sur le snapshot publié.
- [Validation M5](../../../project/validation/VALIDATION_M5.md)

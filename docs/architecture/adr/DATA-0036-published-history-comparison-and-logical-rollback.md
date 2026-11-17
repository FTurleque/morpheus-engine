---
id: DATA-0036
title: "Historique publié, comparaison de snapshots et rollback logique"
domain: data
rules: false
---

# Historique publié, comparaison de snapshots et rollback logique

- Statut : **Acceptée**
- Date : 2026-07-23

## Context

La publication d'une baseline repose sur des invariants qui empêchent toute fuite de candidat :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
ACTIVE observable atomiquement
APPLY != PROMOTE != ACTIVATE
```

L'état publié doit être consultable dans le temps et comparable, et un retour à un état antérieur doit être possible
sans contourner le lifecycle des snapshots (ADR-0143).

Le store possède déjà les informations structurelles nécessaires :

```text
KnowledgeSnapshotMetadata.predecessorId
KnowledgeSnapshotState.ACTIVE / RETIRED
snapshot -> SpecificationVersion binding
snapshot -> RequirementVersionRecord[]
```

Les cinq notions que la décision sépare :

```text
published history
historical query
snapshot comparison
logical rollback plan
retention policy
```

L'historique publié compte exactement un `ACTIVE`, la tête, et 0..N `RETIRED`, les prédécesseurs publiés. Une
occurrence `CURRENT` d'un snapshot `RETIRED` reste `CURRENT` relativement à cette projection historique : la dimension
historique est portée par le snapshot publié adressé, pas par une mutation rétroactive des occurrences persistées.

```text
snapshot state RETIRED != occurrence TemporalState.HISTORICAL
```

La comparaison `from -> to` utilise `DomainIdentity` comme clé de continuité logique :

```text
absent from, présent to             => ADDED
présent from, absent to             => REMOVED
présent des deux, contenu différent => MODIFIED
présent des deux, contenu identique => UNCHANGED
```

La provenance et l'evidence font partie du `Requirement` normalisé et restent donc observables dans une modification.
`MOVED` et `RENAMED` attendent une taxonomie dédiée.

Le rollback logique transforme la baseline `ACTIVE` actuelle vers le contenu métier de la cible historique. La
comparaison et l'applicabilité du rollback sont deux contrats distincts : ADR-0035 impose qu'un
`RequirementDelta.MODIFIED` se résolve vers la même `SpecificationId` que la baseline courante, et une reconstruction
cross-specification attend une politique explicite `MOVED`/reparenting au lieu d'affaiblir ADR-0035.

```text
RETIRED -X-> ACTIVE
cross-specification MODIFIED => comparable
cross-specification MODIFIED -X-> rollback
```

Le plan suit le pipeline d'application et de promotion des deltas :

```text
rollback plan
    -> APPLY explicite
    -> nouvelle SpecificationVersion
    -> nouveau snapshot BUILDING
    -> PROMOTE explicite
    -> READY
    -> ACTIVATE explicite

logical rollback != reactivate RETIRED
logical rollback != APPLY
logical rollback != PROMOTE
logical rollback != ACTIVATE
```

Le `ChangeId` représente l'intention de rollback ; la résolution `SpecificationId -> specificationKey` sert le contrat
`RequirementDelta`, et la résolution inverse `specificationKey -> SpecificationId` est réutilisable par
`RequirementDeltaApplicationPlan`. Le rollback ne prétend pas reconstruire d'autres familles métier que `Requirement`.

La rétention couvre tous les snapshots publiés de la lignée `ACTIVE/RETIRED`, leurs bindings de
`SpecificationVersion` et leurs `RequirementVersionRecord`. Une autre politique devra préserver explicitement
rollback, provenance, audit et traçabilité avant d'être autorisée.

L'historique, la comparaison et le rollback lisent `knowledge_snapshots`, `specification_versions`,
`snapshot_specification_versions` et `requirement_versions`. Après fermeture et réouverture SQLite, l'`ACTIVE` reste
observable, le `RETIRED` reste requêtable et la comparaison reste identique. Les frontières tenues :

```text
com.morpheus.domain      -X-> provider
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> provider implementation
com.morpheus.application -X-> SQLite
```

## Decision

1. **Notions séparées** : historique, requête historique, comparaison, rollback logique et rétention, distincts de
   `TemporalState`, `ChangeLifecycleState`, `RequirementDeltaKind` et de l'activation.
2. **Historique** : l'unique `ACTIVE`, ses prédécesseurs `RETIRED` par `predecessorId` ; jamais `BUILDING`,
   `VALIDATING`, `READY`, `FAILED`. Rejetés : predecessor absent, d'un autre projet ou non `RETIRED`, cycle, tête non
   `ACTIVE`.
3. **Requête historique** : sur un `KnowledgeSnapshotId` explicite `ACTIVE` ou `RETIRED`, ne rend que ses occurrences
   `TemporalState.CURRENT`, sans en muter aucune.
4. **Comparaison** : `ADDED`, `MODIFIED`, `REMOVED`, `UNCHANGED` par `DomainIdentity` sur le `Requirement` normalisé,
   hors `EntityVersionId`, `SpecificationVersionId`, `KnowledgeSnapshotId`, `TemporalState`, triée par
   `DomainIdentity` ; ni `MOVED` ni `RENAMED`.
5. **Rollback** : jamais la réactivation d'un `RETIRED` (seul un `READY` s'active). Il cible un `RETIRED` de la lignée
   et traduit `ACTIVE -> cible` en `RequirementDelta` (`ADDED`, `MODIFIED` au contenu cible, `REMOVED`, rien pour
   `UNCHANGED`) ; un `MODIFIED` qui change de `SpecificationId` est refusé (ADR-0035). APPLY, PROMOTE, ACTIVATE
   explicites ; nouvelles `EntityVersionId`, historique ni réutilisé ni modifié.
6. **Identités** : l'appelant fournit le `ChangeId`, un `RequirementDeltaId` par identité modifiée et
   `SpecificationId -> specificationKey` ; aucun identifiant généré, aucun fuzzy matching.
7. **Périmètre** : `Requirement` de `VersionedRequirementStore`, deltas sans scénario.
8. **Rétention** : `PublishedHistoryRetentionPolicy.KEEP_ALL_PUBLISHED` ; ni TTL, ni limite, ni purge, ni compactage,
   ni suppression physique.
9. **Frontières** : aucune table propre ; `com.morpheus.application.history` ne dépend que du domaine, de
   `SpecificationKnowledgeStore` et `VersionedRequirementStore`.

## Do's and Don'ts

### Do

- Construire l'historique publié depuis l'unique `ACTIVE` en suivant `predecessorId`, et rejeter predecessor absent,
  d'un autre projet, cycle, predecessor non `RETIRED` et tête non `ACTIVE`.
- Adresser une requête historique à un `KnowledgeSnapshotId` explicite `ACTIVE` ou `RETIRED`, et n'en retourner que les
  occurrences `CURRENT`.
- Classer une comparaison par `DomainIdentity` et contenu du `Requirement` normalisé, en ordre de `DomainIdentity`.
- Cibler un rollback sur un `RETIRED` de la lignée courante et le matérialiser en deltas explicites appliqués par le
  pipeline APPLY / PROMOTE / ACTIVATE.
- Recevoir de l'appelant le `ChangeId` et un `RequirementDeltaId` par identité modifiée.
- Conserver tous les snapshots publiés (`KEEP_ALL_PUBLISHED`).

### Don't

- Ne pas exposer un snapshot `BUILDING`, `VALIDATING`, `READY` ou `FAILED` comme historique.
- Ne pas réactiver un snapshot `RETIRED`.
- Ne pas classer `MODIFIED` sur la seule différence d'`EntityVersionId`, de `SpecificationVersionId`, de
  `KnowledgeSnapshotId` ou de `TemporalState`.
- Ne pas introduire implicitement `MOVED` ou `RENAMED`, ni rollbacker un `MODIFIED` cross-specification.
- Ne pas réutiliser une occurrence historique lors d'une reconstruction, ni modifier l'historique source.
- Ne pas générer d'identifiant implicitement ni faire de fuzzy matching.
- Ne pas purger, compacter ni supprimer physiquement un snapshot publié.

## Consequences

### Positives

- Tout état publié antérieur reste requêtable et comparable, de façon identique en mémoire et en SQLite.
- Un retour arrière passe par le même pipeline contrôlé qu'un changement ordinaire : il est tracé, promu et activé
  explicitement.

### Négatives

- La rétention intégrale fait croître la base sans borne.
- Un déplacement de requirement entre spécifications ne peut pas être annulé par rollback tant qu'une politique
  `MOVED` n'existe pas.
- Le rollback ne reconstruit pas les scénarios.

### Risques

- Une lignée corrompue (predecessor manquant, cycle) rend l'historique entier inconsultable : l'échec est explicite
  mais bloquant.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/PublishedHistoryContractTest.java` :
  `memoryPublishesQueriesComparesAndRollsBackWithoutReactivatingHistory`,
  `sqliteKeepsPublishedHistoryQueryableAndComparableAfterReopen`,
  `historicalQueriesAndComparisonsRejectNonPublishedCandidates`,
  `logicalRollbackRequiresExactlyOneExplicitDeltaIdPerChangedIdentity`,
  `logicalRollbackRejectsCrossSpecificationMoveUntilMovedPolicyExists`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : `domain` et
  `application` ne dépendent d'aucun adaptateur.

## References

- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — état courant et proposé.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité stable.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle et version
  d'entité.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des requirements.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé, deltas de requirement.
- [VALIDATION_M3](../../project/validation/VALIDATION_M3.md)

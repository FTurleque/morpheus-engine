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

## Decision

Cinq notions sont séparées :

```text
published history
historical query
snapshot comparison
logical rollback plan
retention policy
```

Elles restent distinctes de :

```text
TemporalState
ChangeLifecycleState
RequirementDeltaKind
KnowledgeSnapshot activation
```

### Historique publié

L'historique publié d'un projet est la lignée obtenue en partant du snapshot `ACTIVE` et en suivant `predecessorId`
jusqu'à la racine.

```text
exactement un ACTIVE : la tête
0..N RETIRED          : les prédécesseurs publiés
```

Les snapshots `BUILDING`, `VALIDATING`, `READY` et `FAILED` ne font jamais partie de l'historique publié.

La reconstruction de lignée rejette explicitement :

- un predecessor absent ;
- un predecessor appartenant à un autre projet ;
- un cycle ;
- un predecessor publié dont l'état n'est pas `RETIRED` ;
- une tête qui n'est pas `ACTIVE`.

### Sémantique de requête historique

Une requête historique est toujours adressée à un `KnowledgeSnapshotId` explicite et n'est autorisée que pour :

```text
ACTIVE
RETIRED
```

La vue métier d'un snapshot publié ne retourne que ses occurrences `TemporalState.CURRENT`.

Une occurrence `CURRENT` d'un snapshot `RETIRED` reste `CURRENT` relativement à cette projection historique :

```text
snapshot state RETIRED != occurrence TemporalState.HISTORICAL
```

La dimension historique est portée par le snapshot publié adressé, pas par une mutation rétroactive des occurrences
persistées.

### Comparaison de snapshots

La taxonomie est strictement :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

Elle utilise `DomainIdentity` comme clé de continuité logique. Pour une comparaison `from -> to` :

```text
absent from, présent to             => ADDED
présent from, absent to             => REMOVED
présent des deux, contenu différent => MODIFIED
présent des deux, contenu identique => UNCHANGED
```

Le contenu comparé est le `Requirement` normalisé complet. Les métadonnées d'occurrence suivantes n'entrent pas dans
la classification :

```text
EntityVersionId
SpecificationVersionId
KnowledgeSnapshotId
TemporalState
```

La provenance et l'evidence font partie du `Requirement` normalisé et restent donc observables dans une modification.

`MOVED` et `RENAMED` ne sont pas introduits implicitement. Un changement de clé, de titre ou de rattachement de
spécification reste `MODIFIED` tant qu'une taxonomie dédiée n'est pas décidée.

L'ordre de sortie est déterministe par `DomainIdentity`.

### Rollback logique

Un rollback n'est jamais la réactivation d'un snapshot `RETIRED` :

```text
RETIRED -X-> ACTIVE
```

Le lifecycle des snapshots reste inchangé : seuls les snapshots `READY` peuvent être activés.

Le rollback logique cible un snapshot `RETIRED` de la lignée publiée courante et construit un plan de deltas qui
transforme la baseline `ACTIVE` actuelle vers le contenu métier de cette cible historique. Pour la comparaison
`ACTIVE current -> RETIRED target`, le plan produit :

```text
ADDED     => RequirementDelta.ADDED avec le contenu historique cible
MODIFIED  => RequirementDelta.MODIFIED avec le contenu historique cible
REMOVED   => RequirementDelta.REMOVED pour l'élément absent de la cible
UNCHANGED => aucun delta
```

La comparaison et l'applicabilité du rollback restent deux contrats distincts. Un requirement dont la
`DomainIdentity` reste stable mais dont la `SpecificationId` change est comparable comme `MODIFIED`, mais le rollback
rejette ce cas : ADR-0035 impose qu'un `RequirementDelta.MODIFIED` se résolve vers la même `SpecificationId` que la
baseline courante.

```text
cross-specification MODIFIED => comparable
cross-specification MODIFIED -X-> rollback
```

Une reconstruction cross-specification attend une politique explicite `MOVED`/reparenting au lieu d'affaiblir
ADR-0035.

Le résultat est destiné au pipeline d'application et de promotion des deltas (ADR-0035) :

```text
rollback plan
    -> APPLY explicite
    -> nouvelle SpecificationVersion
    -> nouveau snapshot BUILDING
    -> PROMOTE explicite
    -> READY
    -> ACTIVATE explicite
```

Donc :

```text
logical rollback != reactivate RETIRED
logical rollback != APPLY
logical rollback != PROMOTE
logical rollback != ACTIVATE
```

La reconstruction crée de nouvelles occurrences `EntityVersionId` ; elle ne réutilise jamais les occurrences
historiques, et l'historique source reste inchangé.

### Identités et absence de génération cachée

Le plan de rollback reçoit explicitement :

- un `ChangeId` représentant l'intention de rollback ;
- un `RequirementDeltaId` pour chaque identité effectivement modifiée ;
- la résolution `SpecificationId -> specificationKey` nécessaire au contrat `RequirementDelta`.

Le service ne génère aucun identifiant en cachette et n'effectue aucun fuzzy matching. Le plan expose également la
résolution inverse `specificationKey -> SpecificationId` réutilisable par `RequirementDeltaApplicationPlan`.

### Périmètre de la reconstruction

Le rollback logique reconstruit le vertical slice `Requirement` persisté par `VersionedRequirementStore`. Ses deltas
portent une liste de scénarios vide : il ne prétend pas reconstruire d'autres familles métier.

### Rétention

La politique est explicitement représentée par `PublishedHistoryRetentionPolicy.KEEP_ALL_PUBLISHED` : tous les
snapshots publiés de la lignée `ACTIVE/RETIRED`, leurs bindings de `SpecificationVersion` et leurs
`RequirementVersionRecord` sont conservés.

Il n'existe ni TTL, ni limite de cardinalité, ni purge destructive, ni compactage, ni suppression physique d'un
snapshot publié. Une autre politique devra préserver explicitement rollback, provenance, audit et traçabilité avant
d'être autorisée.

### Persistance

L'historique, la comparaison et le rollback n'ont pas de table propre : ils lisent `knowledge_snapshots`,
`specification_versions`, `snapshot_specification_versions` et `requirement_versions`. Après fermeture et réouverture
SQLite, l'`ACTIVE` reste observable, le `RETIRED` reste requêtable et la comparaison reste identique.

### Frontières

Les services vivent dans `morpheus-application` (`com.morpheus.application.history`) et ne dépendent que du domaine
MORPHEUS, de `SpecificationKnowledgeStore` et de `VersionedRequirementStore`.

```text
com.morpheus.domain      -X-> provider
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> provider implementation
com.morpheus.application -X-> SQLite
```

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
- [ADR-0140](DATA-0140-identite-de-domaine.md) — identité stable.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle et version
  d'entité.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des requirements.
- [ADR-0035](BE-0035-explicit-requirement-delta-application-and-promotion.md) — application et promotion des deltas.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — modèle normalisé, deltas de requirement.
- [VALIDATION_M3](../../project/validation/VALIDATION_M3.md)

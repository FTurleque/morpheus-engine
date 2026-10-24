---
id: BE-0035
title: "Séparer explicitement application, promotion et activation des RequirementDelta"
domain: backend
rules: false
---

# Séparer explicitement application, promotion et activation des RequirementDelta

- Statut : **Acceptée**
- Date : 2026-07-23

## Context

`RequirementDelta` est normalisé sans être appliqué (ADR-0121), et la persistance distingue identité logique,
occurrence versionnée, version métier, snapshot technique et projection `Requirement` (ADR-0136, ADR-0141).

Il faut transformer explicitement :

```text
ACTIVE snapshot / CURRENT baseline
        +
RequirementDelta[]
```

en une nouvelle projection candidate versionnée sans modifier la baseline observable.

Les dimensions suivantes ne doivent pas être fusionnées :

```text
RequirementDeltaKind
TemporalState
ChangeLifecycleState
KnowledgeSnapshotState
```

En particulier :

```text
normalized delta != applied delta
COMPLETED != promotion
COMPLETED != activation
```

## Decision

Trois opérations distinctes, portées par `com.morpheus.application.delta` (`RequirementDeltaApplicationService`,
`RequirementDeltaPromotionService`) et par `SnapshotLifecycleService` :

```text
APPLY
RequirementDelta[]
    -> nouvelle SpecificationVersion
    -> snapshot candidat BUILDING
    -> RequirementVersionRecord[] CURRENT du candidat

PROMOTE
snapshot candidat BUILDING
    -> validation explicite
    -> READY si la projection candidate est complète et cohérente
    -> FAILED sinon

ACTIVATE
snapshot READY
    -> activation atomique via SnapshotLifecycleService
    -> ancien ACTIVE devient RETIRED
    -> nouveau snapshot devient ACTIVE
```

`APPLY`, `PROMOTE` et `ACTIVATE` ne sont jamais des alias.

### Application explicite

L'application reçoit un plan entièrement explicite contenant :

- le projet ;
- la nouvelle `SpecificationVersion` ;
- le `KnowledgeSnapshotMetadata` candidat en `BUILDING` ;
- les `RequirementDelta` normalisés ;
- la résolution explicite `specificationKey -> SpecificationId` nécessaire aux nouveaux requirements ;
- les nouveaux `EntityVersionId` de toutes les occurrences `CURRENT` du candidat ;
- une `EvidenceId` identifiant la preuve de l'action d'application.

Les identités techniques sont fournies par le plan au lieu d'être générées implicitement par le service : deux
exécutions avec le même état et le même plan produisent la même projection logique et les mêmes identifiants.

Avant toute écriture, le service construit et valide la projection candidate complète en mémoire.

### Sémantique ADDED / MODIFIED / REMOVED

**ADDED.** `RequirementDelta.requirementId` est l'identité logique déjà normalisée (ADR-0121). L'application exige
que cette identité soit absente de la baseline `CURRENT`, n'effectue aucun rapprochement fuzzy, ne rapproche jamais
par titre, chemin, contenu ou similarité, et crée une nouvelle occurrence `EntityVersion` dans le candidat avec
l'identité logique du delta.

**MODIFIED.** L'application exige que l'identité existe dans la baseline `CURRENT`. Invariant :

```text
candidate.DomainIdentity == baseline.DomainIdentity
candidate.EntityVersionId != baseline.EntityVersionId
```

Le contenu normalisé du candidat est construit à partir du delta et conserve sa provenance source.

**REMOVED.** L'application exige que l'identité existe dans la baseline `CURRENT`. La suppression signifie
uniquement :

```text
absence de cette identité dans les RequirementVersionRecord du candidat
```

Aucune ligne ni occurrence du snapshot `ACTIVE` n'est supprimée ou modifiée.

### Conflits et déterminisme

Un lot est rejeté avant écriture lorsqu'il contient notamment :

- plusieurs deltas pour la même `RequirementId` logique ;
- plusieurs occurrences du même `RequirementDeltaId` ;
- `ADDED` sur une identité déjà présente ;
- `MODIFIED` ou `REMOVED` sur une identité absente ;
- une résolution `specificationKey -> SpecificationId` absente ou incompatible ;
- des `EntityVersionId` manquants, surnuméraires, dupliqués ou réutilisant une occurrence de la baseline ;
- une `SpecificationVersion` qui n'a pas pour predecessor la version liée au snapshot `ACTIVE` ;
- un snapshot candidat qui n'est pas `BUILDING` ou dont le predecessor n'est pas l'`ACTIVE` observé.

L'ordre d'entrée des deltas n'a aucune sémantique. Une dépendance à l'ordre est un conflit et est rejetée plutôt
qu'interprétée implicitement.

### Baseline et propositions

`APPLY` lit exclusivement les occurrences `CURRENT` du snapshot `ACTIVE` pour construire la nouvelle baseline
candidate. Les occurrences `PROPOSED` éventuellement présentes dans l'ancien `ACTIVE` restent stockées et inchangées ;
elles ne sont ni fusionnées ni promues implicitement. Ainsi :

```text
ACTIVE CURRENT baseline
ACTIVE PROPOSED occurrences
BUILDING candidate CURRENT baseline
```

coexistent sans fuite dans `CurrentRequirementQueryService`.

### Promotion explicite et preuve

La promotion est une commande applicative séparée. Elle exige le résultat exact de l'application et une preuve de
promotion explicite (`RequirementPromotionEvidence` : `EvidenceId`, raison non vide, instant de décision).

La promotion valide que le snapshot candidat contient exactement les `RequirementVersionRecord` attendus par le
résultat d'application, tous `CURRENT`, et qu'ils appartiennent à la nouvelle `SpecificationVersion`.

```text
validation réussie : BUILDING -> VALIDATING -> READY
validation échouée : BUILDING -> VALIDATING -> FAILED
```

La promotion ne déclenche jamais `activateSnapshot()`.

Les receipts d'application et de promotion conservent la preuve de décision. Les occurrences `ADDED` et `MODIFIED`
conservent la provenance et l'evidence du delta, les occurrences inchangées conservent la provenance de la baseline,
et un `REMOVED` conserve la provenance et l'evidence du delta dans le receipt d'application même s'il n'existe plus
dans la projection candidate.

Ces opérations n'introduisent aucun journal générique durable de décisions ou d'actions. Un tel journal se décide
séparément ; il n'est jamais détourné dans `sourceRevision` ou dans une payload JSON.

### Activation

L'activation reste exclusivement celle d'ADR-0143 :

```text
SnapshotLifecycleService.activate(candidateSnapshotId)
```

Avant activation, `CurrentRequirementQueryService` lit l'ancien `ACTIVE` et l'ancien `CURRENT` ; après activation
atomique, il lit le nouveau. Un candidat `FAILED` ne peut pas évincer l'ancien `ACTIVE`.

### Persistance

Ces opérations réutilisent les tables de la persistance versionnée des requirements (ADR-0141) :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

et n'exigent aucune migration propre.

Les métadonnées de snapshot et les occurrences métier peuvent être écrites par deux ports distincts sans transaction
de construction globale : un candidat partiellement construit reste `BUILDING`, donc non observable. La promotion
vérifie la projection complète avant `READY`, et seule l'activation publie atomiquement.

### Périmètre

`APPLY` n'applique que la projection persistée `Requirement`. `RequirementDelta.scenarios` reste du contenu normalisé
et n'est pas persisté par cette opération ; elle ne généralise pas la persistance à d'autres familles métier.

### Frontières

```text
com.morpheus.domain      -X-> provider
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> provider implementation
```

Les services d'application et de promotion dépendent uniquement des ports applicatifs et du domaine
provider-neutral.

## Do's and Don'ts

### Do

- Garder `APPLY`, `PROMOTE` et `ACTIVATE` comme trois opérations distinctes.
- Recevoir toutes les identités techniques du candidat dans le plan d'application.
- Construire et valider la projection candidate complète en mémoire avant toute écriture.
- Rejeter avant écriture tout lot ambigu ou incohérent.
- Exiger une preuve de promotion explicite (identité, raison, instant).
- Conserver la provenance et l'evidence des deltas dans les receipts, `REMOVED` compris.

### Don't

- Ne pas rapprocher un `ADDED` d'une identité existante par titre, chemin, contenu ou similarité.
- Ne pas supprimer ni modifier une occurrence du snapshot `ACTIVE`.
- Ne pas donner de sémantique à l'ordre d'entrée des deltas.
- Ne pas activer un snapshot depuis la promotion.
- Ne pas déclencher application, promotion ou activation depuis `ChangeLifecycleState.COMPLETED`.
- Ne pas fusionner ni promouvoir implicitement les occurrences `PROPOSED`.
- Ne pas faire dépendre les services d'un type provider ou SQLite.

## Consequences

### Positives

- La baseline observable ne change qu'à l'activation atomique.
- Un candidat incohérent échoue en `FAILED` sans évincer l'`ACTIVE`.
- Le résultat est reproductible pour un même état et un même plan.

### Négatives

- L'appelant doit fournir toutes les identités techniques du candidat.
- Les scénarios des deltas ne sont pas persistés par cette opération.

### Risques

- **Candidat partiellement écrit** : mitigé, il reste `BUILDING`, non observable, et la promotion vérifie la
  projection complète.
- **Lecture de `COMPLETED` comme publication** : mitigé, aucune opération n'est déclenchée par l'état de cycle de vie.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RequirementDeltaApplicationContractTest.java` :
  application, promotion et activation explicites en mémoire et en SQLite, rejet des lots incohérents avant écriture,
  indépendance à l'ordre des deltas, promotion échouée qui conserve l'`ACTIVE`, `COMPLETED` sans effet.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java` : `domain` et
  `application` ne dépendent d'aucun adaptateur.
- Revue : identités techniques fournies par le plan, absence de rapprochement fuzzy.

## References

- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — état courant et changements proposés.
- [ADR-0140](DATA-0140-identite-de-domaine.md) — identité de domaine stable.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle et versions
  d'entité.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des requirements.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — normalisation des deltas.
- [VALIDATION_M3](../../project/validation/VALIDATION_M3.md) — preuve du jalon M3.

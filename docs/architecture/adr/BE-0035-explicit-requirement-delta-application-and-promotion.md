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

Les trois opérations sont portées par `com.morpheus.application.delta` et par `SnapshotLifecycleService` :

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

Le plan d'application contient le projet, la nouvelle `SpecificationVersion`, le `KnowledgeSnapshotMetadata` candidat
en `BUILDING`, les `RequirementDelta` normalisés, la résolution `specificationKey -> SpecificationId` nécessaire aux
nouveaux requirements, les nouveaux `EntityVersionId` de toutes les occurrences `CURRENT` du candidat et une
`EvidenceId` qui identifie la preuve de l'action d'application. Les identités techniques sont fournies par le plan
plutôt que générées par le service : deux exécutions avec le même état et le même plan produisent la même projection
logique et les mêmes identifiants.

`RequirementDelta.requirementId` est l'identité logique déjà normalisée (ADR-0121) ; un `ADDED` crée une nouvelle
occurrence `EntityVersion` dans le candidat avec cette identité, sans aucun rapprochement fuzzy. Invariant d'un
`MODIFIED`, dont le contenu normalisé est construit à partir du delta :

```text
candidate.DomainIdentity == baseline.DomainIdentity
candidate.EntityVersionId != baseline.EntityVersionId
```

Un `REMOVED` signifie uniquement :

```text
absence de cette identité dans les RequirementVersionRecord du candidat
```

Les conflits qui font rejeter un lot avant écriture sont notamment :

- plusieurs deltas pour la même `RequirementId` logique ;
- plusieurs occurrences du même `RequirementDeltaId` ;
- `ADDED` sur une identité déjà présente ;
- `MODIFIED` ou `REMOVED` sur une identité absente ;
- une résolution `specificationKey -> SpecificationId` absente ou incompatible ;
- des `EntityVersionId` manquants, surnuméraires, dupliqués ou réutilisant une occurrence de la baseline ;
- une `SpecificationVersion` qui n'a pas pour predecessor la version liée au snapshot `ACTIVE` ;
- un snapshot candidat qui n'est pas `BUILDING` ou dont le predecessor n'est pas l'`ACTIVE` observé.

Une dépendance à l'ordre des deltas est un conflit, rejeté plutôt qu'interprété. Les occurrences `PROPOSED` de
l'ancien `ACTIVE` restent stockées et inchangées, de sorte que coexistent sans fuite dans
`CurrentRequirementQueryService` :

```text
ACTIVE CURRENT baseline
ACTIVE PROPOSED occurrences
BUILDING candidate CURRENT baseline
```

La promotion est une commande applicative séparée ; les enregistrements qu'elle vérifie appartiennent à la nouvelle
`SpecificationVersion`, et ses transitions sont :

```text
validation réussie : BUILDING -> VALIDATING -> READY
validation échouée : BUILDING -> VALIDATING -> FAILED
```

Les receipts d'application et de promotion conservent la preuve de décision. Les occurrences `ADDED` et `MODIFIED`
conservent la provenance et l'evidence du delta, les occurrences inchangées la provenance de la baseline, et un
`REMOVED` conserve la provenance et l'evidence du delta dans le receipt d'application même s'il n'existe plus dans la
projection candidate. Un journal générique durable de décisions ou d'actions se décide séparément.

L'activation est exclusivement celle d'ADR-0143, `SnapshotLifecycleService.activate(candidateSnapshotId)`. Avant
activation, `CurrentRequirementQueryService` lit l'ancien `ACTIVE` et l'ancien `CURRENT` ; après activation atomique, il
lit le nouveau.

Les tables réutilisées de la persistance versionnée des requirements (ADR-0141) sont :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Les métadonnées de snapshot et les occurrences métier peuvent être écrites par deux ports distincts : un candidat
partiellement construit reste `BUILDING`, donc non observable ; la promotion vérifie la projection complète avant
`READY`, et seule l'activation publie atomiquement. `RequirementDelta.scenarios` reste du contenu normalisé, et ces
opérations ne généralisent pas la persistance à d'autres familles métier. Frontières tenues :

```text
com.morpheus.domain      -X-> provider
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> provider implementation
```

## Decision

1. **Trois opérations, jamais des alias** : `APPLY` (`RequirementDeltaApplicationService`) crée une
   `SpecificationVersion` et un candidat `BUILDING` ; `PROMOTE` (`RequirementDeltaPromotionService`) le mène par
   `VALIDATING` à `READY` ou `FAILED` ; seul `ACTIVATE` (`SnapshotLifecycleService`, ADR-0143) change la baseline lue,
   jamais pour un `FAILED`. `COMPLETED` n'en déclenche aucune.
2. Le plan fournit toutes les identités du candidat (versions, snapshot, `EntityVersionId`,
   `specificationKey -> SpecificationId`, `EvidenceId`) ; le service n'en génère aucune et valide tout en mémoire
   avant d'écrire.
3. `ADDED` exige l'identité absente du `CURRENT`, sans rapprochement par titre, chemin, contenu ou similarité ;
   `MODIFIED` l'exige présente, même `DomainIdentity`, nouvel `EntityVersionId` ; `REMOVED` l'exige présente et n'est
   que son absence du candidat, sans toucher l'`ACTIVE`.
4. Un lot ambigu ou incohérent (doublon, kind contraire à la baseline, résolution, identifiant ou predecessor
   incohérent, candidat non `BUILDING`) est rejeté avant écriture. L'ordre des deltas n'a aucun sens.
5. `APPLY` ne lit que le `CURRENT` de l'`ACTIVE` ; les `PROPOSED` ne sont ni fusionnées ni promues.
6. `PROMOTE` exige le résultat exact d'`APPLY` et une `RequirementPromotionEvidence` (`EvidenceId`, raison non vide,
   instant), vérifie les enregistrements attendus, exacts et tous `CURRENT`, et n'appelle jamais
   `activateSnapshot()`.
7. Les receipts gardent preuve, provenance et evidence des deltas, `REMOVED` compris ; aucun journal générique
   durable, ni via `sourceRevision` ni dans une payload JSON.
8. Tables d'ADR-0141, sans migration ; écritures sans transaction globale, un candidat partiel reste `BUILDING`.
   Seule la projection `Requirement` est écrite, pas `RequirementDelta.scenarios`.
9. Services : ports applicatifs et domaine provider-neutral seulement.

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
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité de domaine stable.
- [ADR-0136](ARCH-0136-etat-temporel-et-occurrences-versionnees.md) — projection temporelle et versions
  d'entité.
- [ADR-0141](DATA-0141-contenu-metier-par-snapshot.md) — persistance versionnée des requirements.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés et activation atomique.
- [ADR-0121](ARCH-0121-modele-normalise-neutre-vis-a-vis-des-providers.md) — normalisation des deltas.
- [VALIDATION_M3](../../project/validation/VALIDATION_M3.md) — preuve du jalon M3.

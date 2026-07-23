# ADR-0035 — Séparer explicitement application, promotion et activation des RequirementDelta

- Statut : **Proposée — M3**
- Date : 23 juillet 2026
- Dépend de : ADR-0006, ADR-0009, ADR-0012, ADR-0013, ADR-0025, ADR-0031, ADR-0032, ADR-0033, ADR-0034
- Portée : M3-S5, application de `RequirementDelta`, promotion candidate, publication explicite

## Contexte

M2 a normalisé `RequirementDelta` sans l'appliquer. M3-S1 à S4 ont ensuite séparé identité logique, occurrence versionnée, version métier, snapshot technique et persistance du premier vertical slice `Requirement`.

Le point restant est de transformer explicitement :

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

## Décision

M3-S5 introduit trois opérations distinctes :

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

## Application explicite

L'application reçoit un plan entièrement explicite contenant :

- le projet ;
- la nouvelle `SpecificationVersion` ;
- le `KnowledgeSnapshotMetadata` candidat en `BUILDING` ;
- les `RequirementDelta` normalisés ;
- la résolution explicite `specificationKey -> SpecificationId` nécessaire aux nouveaux requirements ;
- les nouveaux `EntityVersionId` de toutes les occurrences `CURRENT` du candidat ;
- une `EvidenceId` identifiant la preuve de l'action d'application.

Les identités techniques sont fournies par le plan au lieu d'être générées implicitement par le service. Deux exécutions avec le même état et le même plan produisent donc la même projection logique et les mêmes identifiants.

Avant toute écriture, le service construit et valide la projection candidate complète en mémoire.

## Sémantique ADDED / MODIFIED / REMOVED

### ADDED

`RequirementDelta.requirementId` est l'identité logique déjà normalisée par M2/ADR-0025.

L'application :

- exige que cette identité soit absente de la baseline `CURRENT` ;
- n'effectue aucun rapprochement fuzzy ;
- ne rapproche jamais par titre, chemin, contenu ou similarité ;
- crée une nouvelle occurrence `EntityVersion` dans le candidat avec l'identité logique du delta.

### MODIFIED

L'application exige que l'identité existe dans la baseline `CURRENT`.

Invariant :

```text
candidate.DomainIdentity == baseline.DomainIdentity
candidate.EntityVersionId != baseline.EntityVersionId
```

Le contenu normalisé du candidat est construit à partir du delta et conserve sa provenance source.

### REMOVED

L'application exige que l'identité existe dans la baseline `CURRENT`.

La suppression signifie uniquement :

```text
absence de cette identité dans les RequirementVersionRecord du candidat
```

Aucune ligne ni occurrence du snapshot `ACTIVE` n'est supprimée ou modifiée.

## Conflits et déterminisme

Un lot est rejeté avant écriture lorsqu'il contient notamment :

- plusieurs deltas pour la même `RequirementId` logique ;
- plusieurs occurrences du même `RequirementDeltaId` ;
- `ADDED` sur une identité déjà présente ;
- `MODIFIED` ou `REMOVED` sur une identité absente ;
- une résolution `specificationKey -> SpecificationId` absente ou incompatible ;
- des `EntityVersionId` manquants, surnuméraires, dupliqués ou réutilisant une occurrence de la baseline ;
- une `SpecificationVersion` qui n'a pas pour predecessor la version liée au snapshot `ACTIVE` ;
- un snapshot candidat qui n'est pas `BUILDING` ou dont le predecessor n'est pas l'`ACTIVE` observé.

L'ordre d'entrée des deltas n'a aucune sémantique. Une dépendance à l'ordre serait un conflit et doit être rejetée plutôt qu'interprétée implicitement.

## Baseline et propositions

`APPLY` lit exclusivement les occurrences `CURRENT` du snapshot `ACTIVE` pour construire la nouvelle baseline candidate.

Les occurrences `PROPOSED` éventuellement présentes dans l'ancien `ACTIVE` restent stockées et inchangées. Elles ne sont ni fusionnées ni promues implicitement.

Donc :

```text
ACTIVE CURRENT baseline
ACTIVE PROPOSED occurrences
BUILDING candidate CURRENT baseline
```

peuvent coexister sans fuite dans `CurrentRequirementQueryService`.

## Promotion explicite et preuve

La promotion est une commande applicative séparée. Elle exige :

- le résultat exact de l'application ;
- une preuve de promotion explicite contenant au minimum `EvidenceId`, raison et instant de décision.

La promotion valide que le snapshot candidat contient exactement les `RequirementVersionRecord` attendus par le résultat d'application et qu'ils appartiennent à la nouvelle `SpecificationVersion`.

Validation réussie :

```text
BUILDING -> VALIDATING -> READY
```

Validation échouée :

```text
BUILDING -> VALIDATING -> FAILED
```

La promotion ne déclenche jamais `activateSnapshot()`.

Les receipts d'application/promotion conservent la preuve de décision ; les occurrences `ADDED`/`MODIFIED` conservent la provenance/evidence du delta, les occurrences inchangées conservent la provenance de la baseline, et un `REMOVED` conserve la provenance/evidence du delta dans le receipt d'application même s'il n'existe plus dans la projection candidate.

La persistance durable d'un journal générique de décisions/actions n'est pas introduite dans S5 : le schéma V004 suffit pour la projection métier candidate. Un éventuel audit/event log générique devra être décidé séparément plutôt que détourné dans `sourceRevision` ou une payload JSON.

## Activation

L'activation reste exclusivement celle d'ADR-0033 :

```text
SnapshotLifecycleService.activate(candidateSnapshotId)
```

Avant activation :

```text
CurrentRequirementQueryService -> ancien ACTIVE / ancien CURRENT
```

Après activation atomique :

```text
CurrentRequirementQueryService -> nouveau ACTIVE / nouveau CURRENT
```

Un candidat `FAILED` ne peut pas évincer l'ancien `ACTIVE`.

## Persistance et migration

M3-S5 réutilise le schéma V004 :

```text
specification_versions
snapshot_specification_versions
requirement_versions
```

Aucune migration SQLite supplémentaire n'est nécessaire pour le vertical slice retenu.

Le fait que les métadonnées snapshot et les occurrences métier puissent être écrites par deux ports/adapters distincts n'impose pas une transaction de construction globale : un candidat partiellement construit reste `BUILDING` et donc non observable. La promotion vérifie la projection complète avant `READY`, et seule l'activation S3 publie atomiquement.

## Périmètre du vertical slice

S5 applique uniquement la projection persistée `Requirement` démontrée par S4.

`RequirementDelta.scenarios` reste du contenu normalisé M2 et n'est pas persisté par ce slice, puisque S4 n'a volontairement pas encore introduit la persistance versionnée de `Scenario`. S5 ne généralise pas la persistance à toutes les familles métier pour contourner cette frontière.

## Frontières

```text
com.morpheus.domain      -X-> provider
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> provider implementation
```

Le service S5 dépend uniquement des ports applicatifs existants et du domaine provider-neutral.

## Critères d'acceptation

ADR-0035 pourra passer à **Acceptée — M3** lorsque le gate complet démontre :

1. baseline `ACTIVE` et propositions peuvent coexister pendant la construction du candidat ;
2. l'application ne dépend pas de l'ordre d'entrée des deltas ;
3. `ADDED` est rejeté si l'identité existe déjà et aucun rapprochement fuzzy n'existe ;
4. `MODIFIED` conserve `DomainIdentity` ;
5. `MODIFIED` crée une nouvelle `EntityVersionId` ;
6. `REMOVED` n'est absent que de la projection candidate ;
7. les lots ambigus/incohérents sont rejetés avant écriture ;
8. `ChangeLifecycleState.COMPLETED` ne déclenche aucune application, promotion ou activation ;
9. la promotion est une action explicite et séparée ;
10. avant activation, `CurrentRequirementQueryService` retourne l'ancienne baseline ;
11. après activation, la nouvelle baseline devient visible atomiquement ;
12. un candidat `FAILED` conserve l'ancien `ACTIVE` ;
13. provenance/evidence des deltas et preuves d'application/promotion restent accessibles dans les résultats applicatifs ;
14. Memory et SQLite respectent le même contrat comportemental ;
15. aucun type provider/SQLite ne fuite dans domain/application ;
16. aucune migration SQLite S5 n'est requise ;
17. `.\mvnw.cmd clean test` est vert.

## Preuve d'acceptation

À compléter uniquement après exécution du gate local complet.

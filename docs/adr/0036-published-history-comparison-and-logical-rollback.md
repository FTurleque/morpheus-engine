# ADR-0036 — Historique publié, comparaison de snapshots et rollback logique

- Statut : **Acceptée — M3**
- Date : 23 juillet 2026
- Dépend de : ADR-0006, ADR-0009, ADR-0012, ADR-0013, ADR-0025, ADR-0031, ADR-0033, ADR-0034, ADR-0035
- Portée : M3-S6, historique publié, comparaison, reconstruction logique et rétention

## Contexte

M3-S1 à S5 ont établi les invariants nécessaires pour publier une baseline cohérente sans fuite de candidats :

```text
DomainIdentity != EntityVersionId
SpecificationVersion != KnowledgeSnapshot
CURRENT / PROPOSED / HISTORICAL explicites
ACTIVE observable atomiquement
APPLY != PROMOTE != ACTIVATE
```

S6 rend l'état publié historique consultable et comparable, puis définit un rollback logique sans contourner le lifecycle de snapshot.

Le store possède déjà les informations structurelles nécessaires :

```text
KnowledgeSnapshotMetadata.predecessorId
KnowledgeSnapshotState.ACTIVE / RETIRED
snapshot -> SpecificationVersion binding
snapshot -> RequirementVersionRecord[]
```

Aucun nouveau modèle de persistance n'est nécessaire pour le vertical slice `Requirement`.

## Décision

M3-S6 sépare cinq notions :

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

## Historique publié

L'historique publié d'un projet est la lignée obtenue en partant du snapshot `ACTIVE` et en suivant `predecessorId` jusqu'à la racine.

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

## Sémantique de requête historique

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

La dimension historique est portée par le snapshot publié adressé, pas par une mutation rétroactive des occurrences persistées.

## Comparaison de snapshots

La taxonomie M3 reste strictement :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

Elle utilise `DomainIdentity` comme clé de continuité logique.

Pour une comparaison `from -> to` :

```text
absent from, présent to             => ADDED
présent from, absent to             => REMOVED
présent des deux, contenu différent => MODIFIED
présent des deux, contenu identique => UNCHANGED
```

Le contenu comparé est le `Requirement` normalisé complet. Les métadonnées d'occurrence suivantes n'entrent pas dans la classification :

```text
EntityVersionId
SpecificationVersionId
KnowledgeSnapshotId
TemporalState
```

La provenance/evidence fait partie du `Requirement` normalisé et reste donc observable dans une modification.

`MOVED` et `RENAMED` ne sont pas introduits implicitement. Un changement de clé, titre ou rattachement de spécification reste `MODIFIED` tant qu'une taxonomie dédiée n'est pas décidée.

L'ordre de sortie est déterministe par `DomainIdentity`.

## Rollback logique

Un rollback n'est jamais la réactivation d'un snapshot `RETIRED` :

```text
RETIRED -X-> ACTIVE
```

Le lifecycle S3 reste inchangé : seuls les snapshots `READY` peuvent être activés.

Le rollback logique cible un snapshot `RETIRED` de la lignée publiée courante et construit un plan de deltas qui transforme la baseline `ACTIVE` actuelle vers le contenu métier de cette cible historique.

Pour la comparaison :

```text
ACTIVE current -> RETIRED target
```

le plan produit :

```text
ADDED     => RequirementDelta.ADDED avec le contenu historique cible
MODIFIED  => RequirementDelta.MODIFIED avec le contenu historique cible
REMOVED   => RequirementDelta.REMOVED pour l'élément absent de la cible
UNCHANGED => aucun delta
```

La comparaison et l'applicabilité du rollback restent deux contrats distincts. Un requirement dont la `DomainIdentity` reste stable mais dont la `SpecificationId` change est comparable comme `MODIFIED`, mais le rollback S6 rejette ce cas : ADR-0035 impose qu'un `RequirementDelta.MODIFIED` se résolve vers la même `SpecificationId` que la baseline courante.

```text
cross-specification MODIFIED => comparable
cross-specification MODIFIED -X-> rollback S6
```

Une reconstruction cross-specification attend une politique explicite `MOVED`/reparenting au lieu d'affaiblir ADR-0035.

Le résultat est destiné au pipeline S5 :

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

La reconstruction crée de nouvelles occurrences `EntityVersionId` ; elle ne réutilise jamais les occurrences historiques.

## Identités et absence de génération cachée

Le plan de rollback reçoit explicitement :

- un `ChangeId` représentant l'intention de rollback ;
- un `RequirementDeltaId` pour chaque identité effectivement modifiée ;
- la résolution `SpecificationId -> specificationKey` nécessaire au contrat `RequirementDelta`.

Le service ne génère aucun identifiant en cachette et n'effectue aucun fuzzy matching.

Le plan expose également la résolution inverse `specificationKey -> SpecificationId` réutilisable par `RequirementDeltaApplicationPlan`.

## Scenarios et frontière du vertical slice

S4 et S5 ne persistent que le vertical slice `Requirement`.

Les `Scenario` n'étant pas reconstructibles depuis la persistance S4, les deltas de rollback S6 ont une liste de scénarios vide.

```text
rollback S6 = reconstruction du vertical slice Requirement persisté
```

S6 ne prétend pas reconstruire des familles métier non encore persistées.

## Rétention

La politique M3 est explicitement représentée par `PublishedHistoryRetentionPolicy` :

```text
KEEP_ALL_PUBLISHED
```

Tous les snapshots publiés de la lignée `ACTIVE/RETIRED`, leurs bindings de `SpecificationVersion` et leurs `RequirementVersionRecord` sont conservés.

S6 n'introduit ni TTL, ni limite de cardinalité, ni purge destructive, ni compactage, ni suppression physique d'un snapshot publié.

Une politique future devra préserver explicitement rollback, provenance, audit et traçabilité avant d'être autorisée.

## Persistance

Le schéma V004 suffit :

```text
knowledge_snapshots
specification_versions
snapshot_specification_versions
requirement_versions
```

Aucune migration V005 n'est requise pour S6.

La preuve SQLite démontre qu'après fermeture/réouverture :

```text
ACTIVE actuel reste observable
RETIRED historique reste requêtable
comparaison reste identique
```

## Frontières

Les nouveaux services vivent dans `morpheus-application` et ne dépendent que du domaine MORPHEUS, de `SpecificationKnowledgeStore` et de `VersionedRequirementStore`.

```text
com.morpheus.domain      -X-> provider
com.morpheus.domain      -X-> SQLite
com.morpheus.application -X-> provider implementation
com.morpheus.application -X-> SQLite
```

## Critères d'acceptation

Le gate complet doit démontrer :

1. lignée publiée `RETIRED* -> ACTIVE` déterministe ;
2. candidats non publiés jamais exposés comme historique ;
3. requêtes historiques limitées à `ACTIVE/RETIRED` ;
4. occurrences historiques filtrées sur `CURRENT` ;
5. comparaison `ADDED/MODIFIED/REMOVED/UNCHANGED` par identité et contenu ;
6. `EntityVersionId` différent n'implique pas `MODIFIED` ;
7. ordre déterministe ;
8. rollback ciblant uniquement un `RETIRED` de la lignée courante ;
9. aucune réactivation de `RETIRED` ;
10. deltas explicites réutilisables par S5 ;
11. cross-specification comparable mais non rollbackable sans `MOVED` ;
12. aucun identifiant généré implicitement ;
13. nouvelles occurrences lors de la reconstruction ;
14. historique source inchangé après rollback ;
15. `KEEP_ALL_PUBLISHED` ;
16. reopen SQLite préserve historique et comparaison ;
17. même contrat Memory/SQLite ;
18. aucune fuite provider/SQLite dans domain/application ;
19. aucune migration V005 ;
20. `.\mvnw.cmd clean test` vert.

## Preuve d'acceptation — 23 juillet 2026

Premier passage du gate : échec de compilation Java 21 sur le `switch` impératif de `RequirementLogicalRollbackService`. Le défaut a été corrigé par une switch expression exhaustive retournant un `RollbackMaterialization` immuable ; le gate complet a ensuite été réexécuté depuis une branche locale propre et synchronisée.

Commande officielle :

```text
.\mvnw.cmd clean test
javac release 21
```

Résultats :

```text
PublishedHistoryContractTest              5/5 PASS
RequirementDeltaApplicationContractTest   8/8 PASS

Domain                                  13 tests
Application                             54 tests
OpenSpec provider                       26 tests
Synthetic provider                       7 tests
SQLite store                             7 tests
Architecture tests                      40 tests
-----------------------------------------------
TOTAL                                  147/147 PASS
Failures                                 0
Errors                                   0
Skipped                                  0
BUILD SUCCESS
```

Gate terminé le **23 juillet 2026 à 10:50:47 +02:00**.

Les warnings Xerial SQLite/JDK24 native access et SLF4J NOP restent les warnings connus et non bloquants. Aucun logger n'est ajouté uniquement pour masquer SLF4J.

Les cinq tests S6 démontrent :

```text
Memory history/query/diff/rollback complet
SQLite même contrat + fermeture/réouverture
candidats non publiés rejetés
RequirementDeltaId explicites et exacts
cross-spec rollback rejeté sans politique MOVED
```

Décision finale :

```text
ADR-0036 = ACCEPTÉE — M3
M3-S6    = VALIDÉ — 147/147
M3       = 6/6 slices validés
```

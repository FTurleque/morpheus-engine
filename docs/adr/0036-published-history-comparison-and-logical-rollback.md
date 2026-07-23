# ADR-0036 — Historique publié, comparaison de snapshots et rollback logique

- Statut : **Proposée — M3**
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

S6 doit maintenant rendre l'état publié historique consultable et comparable, puis définir un rollback logique sans contourner le lifecycle de snapshot.

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

Elles ne doivent pas être fusionnées avec :

```text
TemporalState
ChangeLifecycleState
RequirementDeltaKind
KnowledgeSnapshot activation
```

## Historique publié

L'historique publié d'un projet est la lignée obtenue en partant du snapshot `ACTIVE` et en suivant `predecessorId` jusqu'à la racine.

Dans cette lignée :

```text
exactement un ACTIVE : la tête
0..N RETIRED          : les prédécesseurs publiés
```

Les snapshots `BUILDING`, `VALIDATING`, `READY` et `FAILED` ne font jamais partie de l'historique publié.

Un candidat échoué peut rester physiquement stocké ; il n'est pas pour autant une version historique publiée.

La reconstruction de lignée doit rejeter explicitement :

- un predecessor absent ;
- un predecessor appartenant à un autre projet ;
- un cycle ;
- un predecessor publié dont l'état n'est pas `RETIRED` ;
- une tête qui n'est pas `ACTIVE`.

## Sémantique de requête historique

Une requête historique est toujours adressée à un `KnowledgeSnapshotId` explicite et n'est autorisée que pour un snapshot publié :

```text
ACTIVE
RETIRED
```

La vue métier d'un snapshot publié ne retourne que ses occurrences :

```text
TemporalState.CURRENT
```

Une occurrence `CURRENT` d'un snapshot `RETIRED` reste `CURRENT` relativement à cette projection historique. S6 ne réécrit donc pas artificiellement son `TemporalState` en `HISTORICAL`.

Ainsi :

```text
snapshot state RETIRED != occurrence TemporalState.HISTORICAL
```

La dimension historique est portée par le snapshot publié adressé, pas par une mutation rétroactive des occurrences persistées.

## Comparaison de snapshots

La comparaison minimale reste strictement :

```text
ADDED
MODIFIED
REMOVED
UNCHANGED
```

Elle utilise `DomainIdentity` comme clé de continuité logique.

Pour une comparaison `from -> to` :

```text
absent from, présent to    => ADDED
présent from, absent to    => REMOVED
présent des deux, contenu différent => MODIFIED
présent des deux, contenu identique  => UNCHANGED
```

Le contenu comparé est le `Requirement` normalisé complet. Les métadonnées d'occurrence suivantes n'entrent pas dans la classification :

```text
EntityVersionId
SpecificationVersionId
KnowledgeSnapshotId
TemporalState
```

En revanche, la provenance/evidence fait partie du `Requirement` normalisé et reste donc observable dans une modification.

`MOVED` et `RENAMED` ne sont pas introduits dans S6. Même avec continuité d'identité démontrée, un changement de clé, titre ou rattachement de spécification reste classé `MODIFIED` tant qu'une taxonomie dédiée n'est pas décidée.

L'ordre de sortie est déterministe par `DomainIdentity`.

## Rollback logique

Un rollback n'est jamais la réactivation d'un snapshot `RETIRED`.

Invariant :

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

Le résultat est ensuite destiné au pipeline déjà validé en S5 :

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

La reconstruction crée de nouvelles occurrences `EntityVersionId`; elle ne réutilise jamais les occurrences historiques.

## Identités et absence de génération cachée

Le plan de rollback reçoit explicitement :

- un `ChangeId` représentant l'intention de rollback ;
- un `RequirementDeltaId` pour chaque identité effectivement modifiée par le rollback ;
- la résolution `SpecificationId -> specificationKey` nécessaire au contrat `RequirementDelta`.

Le service ne génère aucun identifiant en cachette et n'effectue aucun fuzzy matching.

Le plan expose également la résolution inverse `specificationKey -> SpecificationId` directement réutilisable par `RequirementDeltaApplicationPlan`.

## Scenarios et frontière du vertical slice

S4 et S5 ne persistent que le vertical slice `Requirement`.

Les `Scenario` n'étant pas reconstructibles depuis la persistance S4, les deltas de rollback S6 ont une liste de scénarios vide.

Cette limite est explicite :

```text
rollback S6 = reconstruction du vertical slice Requirement persisté
```

S6 ne prétend pas reconstruire des familles métier non encore persistées.

## Rétention

La politique M3 est :

```text
KEEP_ALL_PUBLISHED
```

Tous les snapshots publiés de la lignée `ACTIVE/RETIRED`, leurs bindings de `SpecificationVersion` et leurs `RequirementVersionRecord` sont conservés.

S6 n'introduit :

- ni TTL ;
- ni limite de cardinalité ;
- ni purge destructive ;
- ni compactage ;
- ni suppression physique d'un snapshot publié.

Une politique de purge future devra préserver explicitement les contraintes de rollback, provenance, audit et traçabilité avant d'être autorisée.

## Persistance

Le schéma V004 suffit :

```text
knowledge_snapshots
specification_versions
snapshot_specification_versions
requirement_versions
```

Aucune migration V005 n'est requise pour S6.

La preuve SQLite doit démontrer qu'après fermeture/réouverture :

```text
ACTIVE actuel reste observable
RETIRED historique reste requêtable
comparaison reste identique
```

## Frontières

Les nouveaux services vivent dans `morpheus-application` et ne dépendent que :

- du domaine MORPHEUS ;
- de `SpecificationKnowledgeStore` ;
- de `VersionedRequirementStore`.

Aucune dépendance provider, SQLite ou CLI n'est admise dans domain/application.

## Critères d'acceptation

ADR-0036 pourra passer à **Acceptée — M3** lorsque le gate complet démontre :

1. la lignée publiée est reconstruite `RETIRED* -> ACTIVE` dans un ordre déterministe ;
2. `BUILDING`, `VALIDATING`, `READY` et `FAILED` ne sont jamais exposés comme historique publié ;
3. une requête historique n'accepte que `ACTIVE` ou `RETIRED` ;
4. une requête historique ne retourne que les occurrences `CURRENT` du snapshot adressé ;
5. `ADDED`, `MODIFIED`, `REMOVED`, `UNCHANGED` sont classés par `DomainIdentity` et contenu normalisé ;
6. `EntityVersionId` différent n'implique pas à lui seul `MODIFIED` ;
7. l'ordre de comparaison est déterministe ;
8. le rollback logique cible uniquement un `RETIRED` appartenant à la lignée publiée courante ;
9. le rollback ne réactive jamais un snapshot `RETIRED` ;
10. le rollback produit des deltas explicites réutilisables par S5 ;
11. le rollback ne génère aucun `ChangeId`, `RequirementDeltaId`, `EntityVersionId` ou snapshot implicitement ;
12. l'application du plan de rollback construit une nouvelle projection avec de nouvelles occurrences ;
13. l'ancien snapshot historique reste inchangé après rollback ;
14. la politique `KEEP_ALL_PUBLISHED` conserve l'historique publié ;
15. fermeture/réouverture SQLite conserve l'ACTIVE et les RETIRED requêtables/comparables ;
16. Memory et SQLite respectent le même contrat ;
17. aucun type provider/SQLite ne fuite dans domain/application ;
18. aucune migration V005 n'est nécessaire ;
19. `.\mvnw.cmd clean test` est vert.

## Preuve d'acceptation

À compléter uniquement après exécution du gate local complet.
# ADR-0031 — Projeter explicitement l'état temporel sur des occurrences versionnées

- Statut : **Proposée — validation M3-S1 requise**
- Date : 22 juillet 2026
- Dépend de : ADR-0006, ADR-0009, ADR-0012, ADR-0022, ADR-0030
- Portée : M3-S1, temporalité, versions métier, occurrence d'entité

## Contexte

M2 a volontairement stabilisé le contenu normalisé sans y injecter `CURRENT / PROPOSED / HISTORICAL`.

M3 doit désormais représenter plusieurs états d'une même identité logique sans :

1. modifier l'identité stable lorsqu'un contenu évolue ;
2. confondre version métier et snapshot technique ;
3. déduire la temporalité depuis un chemin source, un dossier `archive`, un état de checkbox ou un lifecycle ;
4. laisser un contenu `PROPOSED` fuiter dans une vue `CURRENT`.

La preuve M0 E04 impose notamment :

```text
CURRENT baseline
  auth-session/session-expiration = 30 minutes

PROPOSED change A
  same logical requirement = 60 minutes

PROPOSED change B
  same logical requirement = 15 minutes
```

sans fusion ni choix arbitraire.

## Décision proposée

Introduire quatre concepts :

```text
TemporalState
EntityVersionId
SpecificationVersionId
EntityVersion<T>
SpecificationVersion
```

### TemporalState

```text
CURRENT
PROPOSED
HISTORICAL
```

Cette dimension reste orthogonale à :

```text
ChangeLifecycleState
KnowledgeSnapshotState
Resolution
Verification
```

### EntityVersion

Une occurrence versionnée contient :

```text
EntityVersionId           identité de l'occurrence
DomainIdentity            identité logique stable
SpecificationVersionId    version métier propriétaire
TemporalState             état temporel explicite
content                   contenu normalisé
```

Invariant :

```text
EntityVersionId != DomainIdentity
```

Une même `DomainIdentity` peut donc avoir plusieurs occurrences versionnées.

### SpecificationVersion

`SpecificationVersion` représente une version logique du contenu de spécification :

```text
SpecificationVersionId
ProjectSpecificationId
sequence?
providerVersion?
sourceRevision?
createdAt
predecessor?
```

Elle reste distincte de `KnowledgeSnapshot`.

Invariant :

```text
SpecificationVersion != KnowledgeSnapshot
```

Deux ingestions techniques peuvent reconstruire la même `SpecificationVersionId` sans créer implicitement une nouvelle version métier.

## Projection applicative

Introduire :

```text
TemporalProjection<T>
```

La projection fournit :

```text
all()
current()
proposed()
historical()
forEntity(identity)
currentFor(identity)
```

La vue `CURRENT` filtre exclusivement :

```text
TemporalState == CURRENT
```

Aucune autre propriété ne provoque de promotion implicite.

## Unicité CURRENT

Dans une projection observable donnée :

```text
une DomainIdentity -> au plus une occurrence CURRENT
```

Deux occurrences `CURRENT` pour la même identité logique rendent la projection ambiguë et sont rejetées.

En revanche, plusieurs occurrences `PROPOSED` concurrentes sont autorisées :

```text
same DomainIdentity
├── CURRENT   30 minutes
├── PROPOSED  60 minutes
└── PROPOSED  15 minutes
```

La vue courante reste :

```text
30 minutes
```

## Réingestion technique

Une reconstruction technique peut produire un nouvel `EntityVersionId` tout en réutilisant explicitement le même `SpecificationVersionId` :

```text
snapshot/reingestion A -> EntityVersionId A -> SpecificationVersionId V1
snapshot/reingestion B -> EntityVersionId B -> SpecificationVersionId V1
```

Cela matérialise l'invariant ADR-0012 :

```text
réingestion technique != nouvelle version métier implicite
```

La décision de créer une nouvelle `SpecificationVersionId` appartient à une politique explicite ultérieure, pas au simple fait qu'une ingestion s'exécute.

## Pourquoi ne pas modifier les records M2

Les records normalisés M2 (`Specification`, `Requirement`, `Scenario`, etc.) restent du contenu provider-neutral.

Ajouter directement `TemporalState` à chacun :

- couplerait normalisation structurelle et projection temporelle ;
- obligerait les providers à produire une temporalité qu'ils ne connaissent pas toujours ;
- casserait la frontière démontrée par ADR-0022 ;
- dupliquerait les mêmes champs sur toutes les entités.

L'occurrence versionnée enveloppe donc le contenu sans le modifier.

## Hors périmètre S1

- machine complète `ChangeLifecycleState` : S2 ;
- `KnowledgeSnapshot` complet et activation : S3 ;
- persistance métier versionnée : S4 ;
- application/promotion des deltas : S5 ;
- comparaison/rétention : S6 ;
- traçabilité M4.

## Critères d'acceptation

ADR-0031 passe à **Acceptée — M3** lorsque le build complet démontre :

1. les seuls états temporels sont `CURRENT / PROPOSED / HISTORICAL` ;
2. `EntityVersionId` est distinct de la `DomainIdentity` logique ;
3. une même identité logique peut avoir plusieurs occurrences/version states ;
4. plusieurs propositions concurrentes pour la même identité sont conservées séparément ;
5. `current()` et `currentFor()` excluent toujours `PROPOSED` et `HISTORICAL` ;
6. deux occurrences `CURRENT` pour la même identité sont rejetées ;
7. `SpecificationVersion` possède un predecessor explicite et ne peut pas se référencer lui-même ;
8. une réingestion technique peut réutiliser explicitement la même `SpecificationVersionId` ;
9. aucune entité M2 n'est modifiée pour ajouter la temporalité ;
10. `.\mvnw.cmd clean test` est vert.

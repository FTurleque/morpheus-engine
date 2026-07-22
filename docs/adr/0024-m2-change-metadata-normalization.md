# ADR-0024 — Normaliser les métadonnées de changement en M2 avant leurs effets temporels

- Statut : **Proposée — validation M2 requise**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0006, ADR-0009, ADR-0013, ADR-0022, ADR-0023
- Portée : modèle M2, normalisation OpenSpec, identité des éléments anonymes

## 1. Contexte

Le premier slice M2 a stabilisé `Specification`, `Requirement`, `Scenario`, `Provenance` et `Evidence`.

OpenSpec expose également, sous `openspec/changes/<change>/`, des sources décrivant :

```text
proposal.md
  -> intention, scope, contraintes, risques

design.md
  -> décisions de conception

tasks.md
  -> tâches d'implémentation
```

La fixture M0 `openspec-basic` contient pour `add-remember-me` :

```text
1 change
2 contraintes
2 décisions de conception
8 tâches
```

## 2. Problème

MORPHEUS doit normaliser ces informations sans :

1. transformer la position du dossier `changes/` en `TemporalState` public ;
2. introduire la machine complète `ChangeLifecycleState` de M3 ;
3. confondre un changement avec ses deltas de requirements ;
4. utiliser le texte libre d'une contrainte ou d'une tâche comme identité métier.

## 3. Décision proposée

M2 introduit les concepts provider-neutral :

```text
ChangeProposal
Constraint
DesignDecision
ImplementationTask
```

avec identités typées :

```text
ChangeId
ConstraintId
DesignDecisionId
TaskId
```

Chaque sous-entité référence explicitement son `ChangeId`.

```text
ChangeProposal
   ├── Constraint[]
   ├── DesignDecision[]
   └── ImplementationTask[]
```

La relation est structurelle. Les relations de traçabilité génériques restent une responsabilité M4.

## 4. Frontière M2 / M3

Ce slice ne crée aucun :

```text
TemporalState
ChangeLifecycleState public complet
transition de lifecycle
promotion CURRENT
```

Un `ImplementationTask` peut conserver uniquement le booléen source `completed` lorsqu'il est explicitement encodé par une checkbox. Ce booléen est un fait source minimal et ne constitue pas la machine de lifecycle du changement.

Invariant :

```text
change structure != temporal projection
source checkbox != ChangeLifecycleState
```

## 5. Delta requirements

Les fichiers :

```text
openspec/changes/<change>/specs/**/spec.md
```

restent hors de ce slice.

Ils seront normalisés séparément afin de distinguer explicitement :

```text
le changement comme intention
!=
les effets du changement sur les requirements
```

Cette séparation prépare la future traçabilité `AFFECTS` de M4 sans la coder prématurément.

## 6. Identité des éléments anonymes

OpenSpec ne fournit pas d'identifiant explicite pour les bullets de contraintes ni pour les tâches de `tasks.md`.

MORPHEUS ne doit pas utiliser leur texte comme identité.

Le provider génère donc des **clés externes structurelles provider-scoped** :

```text
constraint:<changeKey>:<ordinal>
task:<changeKey>:<ordinal>
```

Ces clés restent :

```text
externalId != DomainIdentity
```

Le `DomainIdentity` UUIDv7 est résolu/persisté via ADR-0023.

Conséquence assumée : un réordonnancement des éléments anonymes peut changer la clé externe structurelle. La continuité devra alors être déclarée explicitement ; aucune fusion par texte ou similarité n'est autorisée.

Les décisions de conception disposent d'un heading sémantique explicite ; leur external ID provider peut donc être dérivé du `changeKey` et de ce heading.

## 7. Provenance et preuves

Chaque entité normalisée possède :

```text
Provenance
  -> SourceLocator
  -> EvidenceId
  -> Evidence(range + SHA-256)
```

Les preuves sont localisées dans :

```text
proposal.md
 design.md
 tasks.md
```

Le texte source ne devient jamais une identité MORPHEUS.

## 8. Enveloppe normalisée

`NormalizedProjectContent` est étendue avec :

```text
changes
constraints
designDecisions
tasks
```

Elle vérifie :

- chaque changement appartient au projet ;
- chaque contrainte référence un changement connu ;
- chaque décision référence un changement connu ;
- chaque tâche référence un changement connu ;
- les identités sont uniques dans leur famille ;
- chaque provenance référence une evidence existante.

## 9. OpenSpec readers

Le provider sépare :

```text
OpenSpecCurrentSpecificationReader
OpenSpecChangeMetadataReader
          ↓
OpenSpecProjectContentReader
          ↓
NormalizedProjectContent
```

Cette composition évite de transformer un seul parser en composant monolithique et garde les slices M2 testables indépendamment.

## 10. Hors périmètre

- delta requirements ADDED/MODIFIED/REMOVED ;
- `AcceptanceCriterion` implicite ;
- `ExternalReference` ;
- sources partielles ;
- second provider ;
- persistance métier des entités ;
- temporalité M3 ;
- lifecycle complet M3 ;
- relations `AFFECTS` M4.

## 11. Critère d'acceptation

ADR-0024 passe à **Acceptée — M2** lorsque le build complet démontre :

1. `openspec-basic` produit exactement 1 `ChangeProposal` ;
2. 2 `Constraint` sont normalisées ;
3. 2 `DesignDecision` sont normalisées ;
4. 8 `ImplementationTask` sont normalisées ;
5. chaque entité possède provenance + evidence ;
6. les contraintes/tâches anonymes n'utilisent pas leur texte comme external ID ;
7. `NormalizedProjectContent` rejette une sous-entité liée à un `ChangeId` inconnu ;
8. le reader agrégé produit un graphe cohérent courant + changement ;
9. aucun `TemporalState` ni lifecycle complet n'est introduit ;
10. `.\mvnw.cmd clean test` est vert.

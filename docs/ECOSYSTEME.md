# MORPHEUS dans l'écosystème IA

Statut : **Proposition — à valider pendant C0**

Date : 22 juillet 2026

Ce document décrit la responsabilité de MORPHEUS et ses frontières avec MINOS, NEXUS, JARVIS, Alfred et Brainiac.

---

## 1. Vue d'orchestration

```text
                           JARVIS
                        Orchestration
                             │
          ┌──────────────────┼──────────────────┐
          │                  │                  │
          ▼                  ▼                  ▼
      MORPHEUS              MINOS              NEXUS
  Spec & Intent             Code              Context
   Intelligence          Intelligence        Intelligence
          │                  │                  │
          └──────────────────┼──────────────────┘
                             ▼
                     ALFRED / BRAINIAC
                       Agents / profils IA
```

Cette vue exprime des responsabilités fonctionnelles, pas des dépendances techniques obligatoires.

---

## 2. Questions auxquelles répond chaque brique

### MORPHEUS

> **Qu'est-ce qui doit être construit, pourquoi, sous quelles contraintes et comment déterminer que l'objectif est satisfait ?**

Connaissance principale :

- intention ;
- spécifications ;
- exigences ;
- changements ;
- contraintes ;
- décisions ;
- scénarios ;
- critères d'acceptation ;
- tâches ;
- traçabilité.

### MINOS

> **Que contient réellement le projet et comment ses éléments de code sont-ils reliés ?**

Connaissance principale :

- fichiers ;
- symboles ;
- références ;
- appels ;
- dépendances ;
- architecture ;
- impacts ;
- preuves issues du code.

### NEXUS

> **Parmi toutes les informations disponibles, lesquelles sont pertinentes pour cette tâche précise ?**

Responsabilités :

- sélection ;
- classement ;
- assemblage ;
- réduction ;
- production de contexte.

### JARVIS

> **Quelles capacités faut-il orchestrer et dans quel ordre pour accomplir la mission ?**

JARVIS coordonne les moteurs et agents sans absorber leur logique métier.

### Alfred / Brainiac

Ils représentent des agents ou profils spécialisés qui raisonnent et exécutent des tâches à partir des capacités exposées.

---

## 3. Vue du flux d'une tâche de développement

```text
Utilisateur / Mission
        │
        ▼
      JARVIS
        │
        ├──────────────► MORPHEUS
        │                 │
        │                 ├── intention
        │                 ├── exigences
        │                 ├── contraintes
        │                 └── critères d'acceptation
        │
        ├──────────────► MINOS
        │                 │
        │                 ├── code concerné
        │                 ├── relations
        │                 ├── dépendances
        │                 └── impact
        │
        ▼
      NEXUS
        │
        ▼
  contexte optimal
        │
        ▼
ALFRED / BRAINIAC / Agent
        │
        ▼
  implémentation / action
```

Le flux exact n'est pas imposé. JARVIS peut appeler seulement MORPHEUS, seulement MINOS, seulement NEXUS ou une combinaison selon le besoin.

---

## 4. MORPHEUS n'est pas placé en série devant MINOS

MORPHEUS et MINOS sont **complémentaires**.

Une spécification peut exister avant le code.

Du code peut exister sans spécification structurée.

Leur coopération ne doit donc pas être modélisée comme une dépendance obligatoire :

```text
MORPHEUS -> MINOS -> NEXUS
```

mais comme des sources spécialisées pouvant être orchestrées :

```text
             JARVIS
            /      \
      MORPHEUS     MINOS
            \      /
              NEXUS
```

---

## 5. Relations MORPHEUS / MINOS

Des liens explicites pourront être établis entre intention et code.

Exemples :

```text
Requirement         -> Symbol
Requirement         -> Module
ChangeProposal      -> SourceFile
ChangeProposal      -> Symbol
ImplementationTask  -> Symbol
AcceptanceCriterion -> Test
DesignDecision      -> ArchitecturalComponent
```

Ces liens ne doivent pas copier les objets du domaine MINOS dans MORPHEUS.

MORPHEUS conservera des références externes ou identifiants interopérables.

---

## 6. Relations MORPHEUS / NEXUS

MORPHEUS doit pouvoir produire des résultats compacts tels que :

```text
ChangeContext
├── objective
├── requirements
├── constraints
├── decisions
├── acceptanceCriteria
├── tasks
└── traceability
```

NEXUS décide ensuite quelles parties sont réellement nécessaires pour la tâche.

MORPHEUS ne doit pas gérer le budget de contexte global d'un agent.

---

## 7. Relations MORPHEUS / JARVIS

JARVIS pourra appeler des primitives MORPHEUS telles que :

```text
get_change
get_current_specification
find_requirements
get_constraints
get_acceptance_criteria
trace_requirement
get_change_context
```

Les workflows d'orchestration appartiennent à JARVIS.

Exemple :

```text
JARVIS
  │
  ├─ get_change(MORPHEUS)
  ├─ get_change_context(MORPHEUS)
  ├─ analyze_impact(MINOS)
  ├─ build_context(NEXUS)
  └─ execute(Agent)
```

---

## 8. Autonomie obligatoire

Chaque brique doit pouvoir fonctionner indépendamment.

### MORPHEUS doit fonctionner sans :

- MINOS ;
- NEXUS ;
- JARVIS ;
- LLM ;
- agent IA.

### MINOS ne dépend pas de MORPHEUS

L'analyse du code doit rester utilisable même lorsqu'aucune spécification n'existe.

### NEXUS ne dépend pas exclusivement de MORPHEUS

Il peut sélectionner du contexte provenant d'autres sources.

### JARVIS ne contient pas le domaine MORPHEUS

L'orchestrateur ne doit pas dupliquer les règles de spécification.

---

## 9. Vue sémantique de l'écosystème

```text
MORPHEUS
« Ce qui DEVRAIT être »
        │
        │
        ▼
      intention

MINOS
« Ce qui EST dans le code »
        │
        │
        ▼
     réalité code

NEXUS
« Ce qui est PERTINENT maintenant »
        │
        │
        ▼
      contexte

JARVIS
« Ce qu'il faut FAIRE et dans quel ordre »
        │
        ▼
 orchestration
```

Cette séparation constitue une frontière architecturale majeure de l'écosystème.
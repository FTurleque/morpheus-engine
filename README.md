# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une compréhension structurée, persistante et interrogeable de ce qu'un projet **doit devenir** : exigences, changements, contraintes, scénarios, décisions de conception, critères d'acceptation et tâches associées.

MORPHEUS ne remplace ni le code, ni les outils de gestion de projet, ni les agents IA. Il fournit une couche de connaissance dédiée à l'intention et aux spécifications.

## Question fondamentale

MORPHEUS répond principalement à la question :

> **Qu'est-ce qui doit être construit, pourquoi, et quelles règles doivent être respectées ?**

## Position dans l'écosystème

Vue fonctionnelle candidate :

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

Les responsabilités sont volontairement séparées :

- **MORPHEUS** comprend l'intention, les exigences, les changements, les contraintes et les critères d'acceptation ;
- **MINOS** comprend le code, les symboles, les relations, les dépendances et les impacts ;
- **NEXUS** sélectionne et classe le contexte pertinent pour une tâche donnée ;
- **JARVIS** orchestre les différentes capacités de l'écosystème ;
- **Alfred** et **Brainiac** représentent des agents ou profils spécialisés pouvant consommer ces capacités.

Chaque brique doit rester autonome et ne pas devenir une dépendance fonctionnelle obligatoire des autres.

## Principe d'architecture

MORPHEUS doit posséder son propre modèle métier et ne pas être couplé à un format ou un outil de spécification particulier.

Architecture candidate :

```text
Sources de spécifications
        │
        ▼
SpecificationProvider
        │
  ┌─────┼──────────────┐
  ▼     ▼              ▼
OpenSpec Markdown     Futur
        │
        ▼
Ingestion MORPHEUS
        │
        ▼
Modèle normalisé MORPHEUS
        │
        ├── Specification
        ├── Requirement
        ├── Scenario
        ├── ChangeProposal
        ├── Constraint
        ├── DesignDecision
        ├── AcceptanceCriterion
        └── ImplementationTask
        │
        ▼
SpecificationKnowledgeStore
        │
        ▼
Services d'intelligence MORPHEUS
        │
   ┌────┼─────┐
   ▼    ▼     ▼
  CLI  MCP   API
```

**OpenSpec est envisagé comme un premier fournisseur de spécifications, pas comme le domaine de MORPHEUS.**

## Principes directeurs

MORPHEUS doit être :

- indépendant des fournisseurs d'IA ;
- utilisable sans LLM ;
- local-first autant que possible ;
- indépendant du format de spécification à la frontière du domaine ;
- explicite sur la provenance et l'état des informations ;
- capable de distinguer les spécifications actuelles des changements proposés ;
- consommable par des humains, des outils CLI, des IDE, des serveurs MCP, des API et des agents IA ;
- conçu pour coopérer avec MINOS, NEXUS et JARVIS sans dépendre fonctionnellement d'eux.

## Phase actuelle

Le projet démarre en phase :

> **C0 — Cadrage fonctionnel et architectural**

Aucune implémentation fonctionnelle importante ne doit commencer avant validation du besoin, du périmètre initial, du modèle de domaine, des frontières avec les autres briques de l'écosystème et des principales décisions d'architecture.

La règle de travail est :

> **Documenter d'abord, décider ensuite, implémenter en dernier.**

## Documents de référence

La source de vérité du cadrage est :

- [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md).

Documents complémentaires :

- [`docs/ECOSYSTEME.md`](docs/ECOSYSTEME.md) — responsabilités et frontières avec MINOS, NEXUS et JARVIS ;
- [`docs/architecture/overview.md`](docs/architecture/overview.md) — architecture candidate ;
- [`docs/MVP.md`](docs/MVP.md) — périmètre MVP proposé ;
- [`docs/PLAN.md`](docs/PLAN.md) — plan de travail C0 / M0 ;
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — feuille de route ;
- [`docs/research/openspec-provider-study.md`](docs/research/openspec-provider-study.md) — étude d'OpenSpec comme provider candidat ;
- [`docs/adr/`](docs/adr/) — décisions d'architecture proposées et leurs critères de validation.

## Décisions structurantes actuellement proposées

- domaine MORPHEUS indépendant des providers ;
- OpenSpec comme premier provider de référence, sans verrouillage ;
- persistance derrière `SpecificationKnowledgeStore` ;
- cœur local-first sans LLM obligatoire ;
- traçabilité comme concept de premier ordre ;
- distinction structurelle état courant / proposé / historique ;
- intégrations cross-engine découplées ;
- providers read-first, avec écriture séparée et optionnelle.

Ces décisions restent **proposées** jusqu'à leur validation selon les conditions décrites dans leurs ADR respectives.
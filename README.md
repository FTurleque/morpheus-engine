# MORPHEUS

**MORPHEUS** est un moteur d'intelligence des spécifications et de l'intention (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une compréhension structurée, persistante, versionnée et interrogeable de ce qu'un projet **doit devenir** : exigences, changements, contraintes, scénarios, décisions de conception, critères d'acceptation et tâches associées.

MORPHEUS ne remplace ni le code, ni les outils de gestion de projet, ni les agents IA. Il fournit une couche de connaissance dédiée à l'intention et aux spécifications.

## Question fondamentale

MORPHEUS répond principalement à la question :

> **Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?**

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

MORPHEUS possède son propre modèle métier et ne doit pas être couplé à un format ou un outil de spécification particulier.

Architecture candidate :

```text
Sources de spécifications
        │
        ▼
SpecificationProvider Registry
        │
   ┌────┼──────────────┐
   ▼    ▼              ▼
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
        ├── ImplementationTask
        ├── Evidence
        └── TraceabilityLink
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

## Invariants actuellement proposés

MORPHEUS doit :

- rester indépendant des fournisseurs d'IA ;
- fonctionner sans LLM ;
- être local-first par défaut ;
- rester indépendant du format de spécification à la frontière du domaine ;
- distinguer identité logique, version, emplacement source et identifiant externe ;
- distinguer `CURRENT`, `PROPOSED` et `HISTORICAL` du cycle de vie d'un changement ;
- conserver provenance et preuves ;
- traiter la traçabilité comme un concept de premier ordre ;
- sélectionner les providers selon leurs capacités effectives ;
- publier l'état de connaissance par snapshots cohérents ;
- séparer lecture et écriture ;
- coopérer avec MINOS, NEXUS et JARVIS sans dépendre fonctionnellement d'eux.

## Phase actuelle

Le projet est en phase :

> **C0 — Cadrage fonctionnel et architectural**

Aucune implémentation fonctionnelle importante ne doit commencer avant validation du besoin, du périmètre initial, du modèle de domaine, des frontières avec les autres briques de l'écosystème et des principales décisions d'architecture.

La règle de travail est :

> **Documenter d'abord, décider ensuite, implémenter en dernier.**

## Documents de référence

La source de vérité du cadrage est :

- [`docs/CAHIER_DES_CHARGES.md`](docs/CAHIER_DES_CHARGES.md).

Documents fonctionnels et architecturaux :

- [`docs/ECOSYSTEME.md`](docs/ECOSYSTEME.md) — responsabilités et frontières avec MINOS, NEXUS et JARVIS ;
- [`docs/architecture/overview.md`](docs/architecture/overview.md) — architecture candidate ;
- [`docs/domain/MODEL.md`](docs/domain/MODEL.md) — modèle de domaine détaillé ;
- [`docs/domain/CHANGE_LIFECYCLE.md`](docs/domain/CHANGE_LIFECYCLE.md) — machine d'état candidate des changements ;
- [`docs/USE_CASES.md`](docs/USE_CASES.md) — cas d'usage et priorités ;
- [`docs/MVP.md`](docs/MVP.md) — périmètre MVP proposé ;
- [`docs/PLAN.md`](docs/PLAN.md) — plan de travail C0 / M0 ;
- [`docs/ROADMAP.md`](docs/ROADMAP.md) — feuille de route.

Contrats conceptuels :

- [`docs/contracts/SPECIFICATION_PROVIDER.md`](docs/contracts/SPECIFICATION_PROVIDER.md) — contrat des providers et capacités ;
- [`docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md`](docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md) — contrat du store de connaissance.

Recherche et expérimentations :

- [`docs/research/openspec-provider-study.md`](docs/research/openspec-provider-study.md) — étude d'OpenSpec comme provider candidat ;
- [`docs/research/M0_EXPERIMENT_MATRIX.md`](docs/research/M0_EXPERIMENT_MATRIX.md) — datasets, expériences, mesures et portes de décision M0.

Décisions :

- [`docs/adr/`](docs/adr/) — registre des ADR, alternatives, risques, preuves attendues et conditions d'acceptation.

## Décisions structurantes actuellement proposées

- domaine MORPHEUS indépendant des providers ;
- OpenSpec comme premier provider de référence, sans verrouillage ;
- persistance derrière `SpecificationKnowledgeStore` ;
- cœur local-first sans LLM obligatoire ;
- traçabilité comme concept de premier ordre ;
- séparation structurelle état courant / proposé / historique ;
- intégrations cross-engine découplées ;
- providers read-first, avec écriture séparée et optionnelle ;
- identité MORPHEUS opaque distincte de la version, du locator et des IDs externes ;
- taxonomie contrôlée des relations ;
- providers sélectionnés par capacités effectives ;
- publication par snapshots versionnés avec activation atomique observable ;
- cycle de vie des changements normalisé par machine d'état.

Ces décisions restent **proposées** jusqu'à leur validation selon les conditions décrites dans leurs ADR respectives et, lorsque nécessaire, les preuves M0.
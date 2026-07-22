# Feuille de route — MORPHEUS

Statut : **Proposition initiale — à valider pendant C0**

Date : 22 juillet 2026

La roadmap est guidée par les preuves. Un jalon peut être modifié si une expérimentation invalide une hypothèse.

---

## C0 — Cadrage fonctionnel et architectural

### Objectif

Définir précisément ce que MORPHEUS doit fournir avant de développer ses fonctionnalités.

### Livrables

- cahier des charges ;
- position dans l'écosystème ;
- périmètre MVP ;
- modèle de domaine détaillé ;
- cycle de vie des changements ;
- cas d'usage prioritaires ;
- stratégie `SpecificationProvider` ;
- négociation de capacités ;
- étude OpenSpec ;
- stratégie `SpecificationKnowledgeStore` ;
- stratégie d'identité ;
- stratégie de snapshots/versionnement ;
- modèle et taxonomie de traçabilité ;
- critères de validation ;
- ADR structurantes ;
- matrice d'expérimentation M0.

### Porte de décision

> Savons-nous précisément ce que MORPHEUS doit comprendre, pourquoi, à partir de quelles sources, avec quelles frontières, quels invariants et selon quels critères mesurables ?

Aucune implémentation fonctionnelle significative ne commence avant validation C0.

---

## M0 — Faisabilité technique

### Objectif

Valider les choix structurants par des expérimentations réelles et mesurables.

### Périmètre

- provider OpenSpec de référence ;
- fake/second provider de découplage ;
- ingestion et normalisation ;
- identité stable ;
- séparation `CURRENT / PROPOSED / HISTORICAL` ;
- mapping du cycle de vie ;
- snapshots et versionnement ;
- taxonomie de traçabilité ;
- backend mémoire ;
- backend persistant local candidat ;
- option graph store seulement si les mesures la justifient ;
- recherche lexicale ;
- vertical slice de requêtes ;
- diagnostics ;
- contexte compact ;
- premières mesures d'incrémental.

### Plan de preuves

La source de vérité des expériences est :

[`research/M0_EXPERIMENT_MATRIX.md`](research/M0_EXPERIMENT_MATRIX.md)

### Porte de décision

> L'architecture provider + ingestion + modèle normalisé + snapshots + knowledge store permet-elle de conserver MORPHEUS indépendant du provider et du backend tout en répondant efficacement aux cas d'usage prioritaires ?

Décisions possibles :

```text
ADOPTER
ADOPTER_AVEC_CONTRAINTES
REVOIR
REMPLACER
```

---

## M1 — Découverte des projets et providers

### Objectif

Détecter les sources de spécification et sélectionner les providers adaptés selon leurs capacités effectives.

### Périmètre

- registre local des projets ;
- découverte de workspace ;
- détection des sources ;
- exclusions ;
- `SpecificationProviderRegistry` ;
- `ProviderCapabilitySet` ;
- probes ;
- versions de format ;
- capacités obligatoires/préférées ;
- préférence local-first ;
- ambiguïtés de sélection ;
- diagnostics.

### Critère de sortie

Une source supportée est détectée et un provider compatible est sélectionné de manière déterministe et explicable.

---

## M2 — Ingestion et modèle normalisé

### Objectif

Transformer une source supportée en concepts MORPHEUS indépendants du provider.

### Périmètre

- `ProjectSpecification` ;
- `Specification` ;
- `Requirement` ;
- `ChangeProposal` ;
- `Constraint` ;
- `Scenario` ;
- `DesignDecision` ;
- `AcceptanceCriterion` ;
- `ImplementationTask` ;
- `Evidence` ;
- `Provenance` ;
- `ExternalReference` ;
- résolution d'identité ;
- locators ;
- diagnostics de normalisation.

### Critère de sortie

Aucun type du provider de référence ne traverse le domaine ou les services publics.

---

## M3 — État temporel, cycle de vie, snapshots et versions

### Objectif

Reconstruire de manière fiable l'état de référence, distinguer les évolutions proposées et maintenir un historique cohérent.

### Périmètre

#### État temporel

```text
CURRENT
PROPOSED
HISTORICAL
```

#### Cycle de vie

```text
DRAFT
PROPOSED
SPECIFIED
DESIGNED
PLANNED
IMPLEMENTING
VERIFYING
COMPLETED
ARCHIVED
ABANDONED
```

#### Versionnement

- `SpecificationVersion` ;
- `KnowledgeSnapshot` ;
- predecessor ;
- activation atomique observable ;
- idempotence ;
- rétention ;
- comparaison `ADDED / MODIFIED / REMOVED / UNCHANGED` ;
- `MOVED / RENAMED` lorsque l'identité le permet.

### Critère de sortie

`get_current_specification` ne contient jamais implicitement un delta seulement proposé, même pendant une resynchronisation.

---

## M4 — Traçabilité

### Objectif

Relier les éléments de spécification et expliquer leur origine.

### Périmètre

- `TraceabilityLink` ;
- taxonomie contrôlée ;
- direction canonique ;
- relations inverses ;
- exigences ↔ scénarios ;
- exigences ↔ critères d'acceptation ;
- exigences ↔ tâches ;
- changements ↔ exigences ;
- contraintes ↔ portée ;
- décisions ↔ changements ;
- liens cassés ;
- relations non résolues ;
- origin/résolution/confiance ;
- preuves ;
- chemins de traçabilité.

### Critère de sortie

```text
morpheus trace <requirement>
```

retourne un sous-graphe normalisé, directionnel et explicable.

---

## M5 — Requêtes et contexte compact

### Objectif

Rendre MORPHEUS directement exploitable par des humains, scripts et agents.

### Périmètre

- `get_current_specification` ;
- `find_requirements` ;
- `get_change` ;
- `list_changes` ;
- `get_constraints` ;
- `get_acceptance_criteria` ;
- `get_design_decisions` ;
- `get_implementation_tasks` ;
- `trace_requirement` ;
- `get_change_context` ;
- recherche lexicale ;
- JSON compact ;
- limites de résultats ;
- pagination ;
- warnings et provenance.

Ce jalon constitue le premier cœur MORPHEUS directement utilisable par un agent sans intégration NEXUS obligatoire.

---

## M6 — Qualité et couverture des spécifications

### Objectif

Identifier les lacunes de traçabilité, de complétude et de vérification.

### Périmètre

- exigences orphelines ;
- tâches sans exigence ;
- critères d'acceptation non reliés ;
- critères non vérifiés ;
- changements incomplets ;
- décisions sans justification ;
- références cassées ;
- couverture de traçabilité ;
- blocages de transition ;
- explication des diagnostics ;
- distinction diagnostics déterministes / heuristiques.

---

## M7 — Synchronisation incrémentale et fraîcheur

### Objectif

Maintenir la connaissance MORPHEUS à jour sans réingestion complète systématique.

### Périmètre

- empreintes ;
- révisions source ;
- fichiers ajoutés/modifiés/supprimés ;
- mouvements/renommages ;
- archives ;
- `INCREMENTAL_READ` ;
- invalidation ;
- construction de snapshot à partir de delta ;
- watcher local ;
- détection de format/version modifiée ;
- repli vers ingestion complète ;
- métriques de fraîcheur.

### Invariant

La fiabilité prime sur la performance : en cas de doute, full rebuild.

---

## M8 — Analyse des changements

### Objectif

Analyser l'étendue fonctionnelle et documentaire d'un changement à partir des relations connues.

### Périmètre

- comparaison current/proposed ;
- exigences ajoutées/modifiées/supprimées ;
- contraintes affectées ;
- décisions associées ;
- critères d'acceptation ;
- changements dépendants ;
- chemins explicatifs ;
- projections contrôlées ;
- limites explicites des inférences.

MORPHEUS analyse l'intention et la spécification ; l'analyse du code reste la responsabilité de MINOS.

---

## M9 — CLI stabilisée

### Objectif

Stabiliser l'interface développeur et automatisation.

Commandes candidates :

```text
morpheus project add
morpheus project list
morpheus sync
morpheus specs
morpheus requirements
morpheus change get
morpheus change list
morpheus change status
morpheus constraints
morpheus acceptance
morpheus decisions
morpheus tasks
morpheus trace
morpheus context
morpheus versions
morpheus inspect
morpheus health
```

Les commandes de mutation restent hors périmètre tant qu'une ADR d'écriture n'a pas été acceptée.

---

## M10 — Serveur MCP

### Objectif

Exposer des primitives compactes aux agents IA.

Outils candidats :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_design_decisions
get_implementation_tasks
trace_requirement
get_change_context
get_specification_context
get_change_status
get_blocking_conditions
get_sync_status
```

Aucune logique métier essentielle ne doit résider dans les handlers MCP.

---

## M11 — API

### Objectif

Permettre à des systèmes externes de consommer MORPHEUS sans connaître ses providers ou son backend.

### Périmètre

- projets ;
- spécifications ;
- exigences ;
- changements ;
- contraintes ;
- critères ;
- traçabilité ;
- versions ;
- contexte ;
- synchronisation ;
- diagnostics ;
- DTO stables.

Le choix du framework serveur reste différé jusqu'à ce jalon.

---

## M12 — Intégration MINOS

### Objectif

Relier intention et code sans fusionner les domaines.

### Périmètre

- `ExternalReference(system=MINOS, ...)` ;
- références vers symboles/fichiers/modules/tests ;
- résolution via MINOS ;
- Requirement → code ;
- ChangeProposal → code ;
- AcceptanceCriterion → tests ;
- traçabilité cross-engine ;
- références non résolues conservées ;
- indisponibilité de MINOS tolérée.

---

## M13 — Intégration NEXUS

### Objectif

Fournir des vues de spécifications exploitables par la sélection de contexte.

### Frontière

- MORPHEUS fournit intention, exigences, contraintes, décisions, critères, tâches, provenance et chemins ;
- NEXUS sélectionne, classe, fusionne et compresse le contexte global.

MORPHEUS reste utilisable sans NEXUS.

---

## M14 — Orchestration JARVIS

### Objectif

Permettre à JARVIS d'orchestrer MORPHEUS dans des workflows de développement et d'analyse.

MORPHEUS peut exposer :

```text
change state
allowed transitions
blocking conditions
acceptance status
unresolved references
specification context
```

JARVIS décide de la séquence d'actions.

MORPHEUS reste autonome et ne contient aucune logique JARVIS.

---

## Explorations futures

Non engagées dans la roadmap principale :

- génération assistée de spécifications par LLM ;
- recherche sémantique ;
- embeddings ;
- analyse automatique de contradictions avancées ;
- synchronisation Jira / GitHub Issues / autres trackers ;
- éditeur visuel ;
- collaboration temps réel ;
- providers distants ;
- composition multi-provider de production ;
- federation multi-projets ;
- event sourcing complet ;
- conformité automatique code ↔ spécification ;
- mutations orchestrées de specs par agents.
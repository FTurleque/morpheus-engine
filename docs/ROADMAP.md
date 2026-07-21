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
- modèle de domaine ;
- cycle de vie ;
- stratégie `SpecificationProvider` ;
- étude OpenSpec ;
- stratégie `SpecificationKnowledgeStore` ;
- modèle de traçabilité ;
- critères de validation ;
- ADR structurantes ;
- plan M0.

### Porte de décision

> Savons-nous précisément ce que MORPHEUS doit comprendre, pourquoi, à partir de quelles sources, avec quelles frontières et selon quels critères mesurables ?

---

## M0 — Faisabilité technique

### Objectif

Valider les choix structurants par des expérimentations réelles.

### Périmètre

- provider OpenSpec ;
- ingestion ;
- modèle normalisé ;
- identité ;
- versionnement ;
- traçabilité ;
- backend mémoire ;
- backend persistant candidat ;
- vertical slice de requêtes ;
- mesures de performance.

### Porte de décision

> L'architecture provider + modèle normalisé + knowledge store permet-elle de conserver MORPHEUS indépendant d'OpenSpec et du backend tout en répondant efficacement aux cas d'usage prioritaires ?

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

Détecter les sources de spécification et sélectionner les providers adaptés.

### Périmètre

- registre local des projets ;
- découverte de workspace ;
- détection des sources ;
- exclusions ;
- `SpecificationProviderRegistry` ;
- capacités providers ;
- état de découverte ;
- diagnostics.

---

## M2 — Ingestion et modèle normalisé

### Objectif

Transformer une source supportée en concepts MORPHEUS indépendants du provider.

### Périmètre

- `Specification` ;
- `Requirement` ;
- `ChangeProposal` ;
- `Constraint` ;
- `Scenario` ;
- `DesignDecision` ;
- `AcceptanceCriterion` ;
- `ImplementationTask` ;
- provenance ;
- identité stable ;
- erreurs de normalisation.

---

## M3 — État courant, changements et versionnement

### Objectif

Reconstruire de manière fiable l'état courant et distinguer les évolutions proposées.

### Périmètre

- états courant/proposé/archivé ;
- versions ;
- changements actifs ;
- changements terminés ;
- historique ;
- supersession ;
- comparaison de versions.

---

## M4 — Traçabilité

### Objectif

Relier les éléments de spécification et expliquer leur origine.

### Périmètre

- `TraceabilityLink` ;
- exigences ↔ changements ;
- exigences ↔ critères d'acceptation ;
- changements ↔ tâches ;
- décisions ↔ changements ;
- liens cassés ;
- provenance ;
- chemins de traçabilité.

### Critère de sortie

```text
morpheus trace <requirement>
```

retourne un chemin normalisé et explicable.

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
- `get_change_context` ;
- JSON compact ;
- limites de résultats.

---

## M6 — Qualité et couverture des spécifications

### Objectif

Identifier les lacunes de traçabilité et de vérification.

### Périmètre

- exigences orphelines ;
- tâches sans exigence ;
- critères d'acceptation non reliés ;
- changements incomplets ;
- décisions sans justification ;
- couverture de traçabilité ;
- explication des diagnostics.

---

## M7 — Synchronisation incrémentale

### Objectif

Maintenir la connaissance MORPHEUS à jour sans réingestion complète systématique.

### Périmètre

- empreintes ;
- changements de fichiers ;
- suppressions ;
- renommages ;
- révisions Git ;
- snapshots ;
- invalidation ;
- watcher local ;
- repli vers ingestion complète.

---

## M8 — Analyse des changements

### Objectif

Analyser l'étendue fonctionnelle et documentaire d'un changement à partir des relations connues.

### Périmètre

- exigences affectées ;
- contraintes affectées ;
- décisions associées ;
- critères d'acceptation ;
- changements dépendants ;
- chemins explicatifs ;
- limites explicites des inférences.

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
morpheus constraints
morpheus acceptance
morpheus decisions
morpheus tasks
morpheus trace
morpheus context
morpheus inspect
morpheus health
```

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
- traçabilité ;
- contexte ;
- synchronisation ;
- DTO stables.

---

## M12 — Intégration MINOS

### Objectif

Relier intention et code sans fusionner les domaines.

### Périmètre

- références externes vers symboles/fichiers/modules ;
- résolution via MINOS ;
- Requirement → code ;
- ChangeProposal → code ;
- AcceptanceCriterion → tests ;
- traçabilité cross-engine ;
- indisponibilité tolérée de MINOS.

---

## M13 — Intégration NEXUS

### Objectif

Fournir des vues de spécifications exploitables par la sélection de contexte.

### Frontière

- MORPHEUS fournit intention, exigences, contraintes, décisions et critères d'acceptation ;
- NEXUS sélectionne et classe ce qui doit être injecté pour la tâche.

---

## M14 — Orchestration JARVIS

### Objectif

Permettre à JARVIS d'orchestrer MORPHEUS dans des workflows de développement et d'analyse.

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
- federation multi-projets ;
- conformité automatique code ↔ spécification.
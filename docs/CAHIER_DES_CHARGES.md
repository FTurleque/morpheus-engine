# Cahier des charges — MORPHEUS

Statut : **Brouillon de cadrage — à valider avant toute implémentation fonctionnelle**

Date : 22 juillet 2026

Ce document constitue la **source de vérité fonctionnelle et technique de haut niveau** de MORPHEUS pendant la phase C0.

> **Règle de travail : documenter d'abord, décider ensuite, implémenter en dernier.**

---

# 1. Présentation du projet

## 1.1 Nom

**MORPHEUS**

Dépôt technique : `morpheus-engine`.

## 1.2 Nature du produit

MORPHEUS est un **moteur d'intelligence des spécifications et de l'intention** (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une représentation structurée, persistante, versionnée, traçable et interrogeable de ce qu'un projet **doit devenir**.

Cette représentation couvre notamment :

- les spécifications actuelles ;
- les exigences ;
- les contraintes ;
- les scénarios ;
- les changements proposés ou en cours ;
- les décisions de conception ;
- les critères d'acceptation ;
- les tâches d'implémentation ;
- les relations de traçabilité entre ces éléments ;
- leur provenance, leur état et leur historique.

## 1.3 Formulation synthétique

> **MORPHEUS transforme l'intention et les spécifications d'un projet en modèle de connaissance versionné, traçable et interrogeable.**

## 1.4 Question fondamentale

MORPHEUS répond principalement à la question :

> **Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?**

## 1.5 Non-objectifs

MORPHEUS n'est pas :

- un moteur d'intelligence du code ;
- un moteur de recherche de symboles ;
- un gestionnaire de tickets généraliste ;
- un outil de planification d'équipe ;
- un chatbot ;
- un LLM ;
- un agent autonome ;
- un moteur de génération de code ;
- un remplaçant de Git ;
- un remplaçant des tests ;
- un remplaçant d'un format de spécification particulier ;
- un produit dépendant d'OpenSpec, d'un fournisseur IA ou d'un service cloud.

---

# 2. Positionnement dans l'écosystème

## 2.1 Vue fonctionnelle

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

## 2.2 Frontière MORPHEUS / MINOS

MORPHEUS répond à :

> **Que voulons-nous construire, pourquoi et sous quelles contraintes ?**

MINOS répond à :

> **Que contient réellement le code, où se trouvent ses éléments et comment sont-ils reliés ?**

MORPHEUS ne doit pas parser le code source pour remplacer MINOS.

MINOS ne doit pas devenir le système de vérité des exigences et changements.

Les deux moteurs peuvent coopérer via des références explicites et découplées.

## 2.3 Frontière MORPHEUS / NEXUS

MORPHEUS produit des faits et vues compactes sur les spécifications, changements et contraintes.

NEXUS décide quelles informations doivent être sélectionnées, classées, combinées ou compressées pour une tâche donnée.

MORPHEUS ne doit pas devenir un moteur général de sélection de contexte.

## 2.4 Frontière MORPHEUS / JARVIS

JARVIS orchestre les capacités disponibles.

MORPHEUS expose des opérations spécialisées, mais ne doit pas intégrer de logique d'orchestration propre à JARVIS.

## 2.5 Alfred et Brainiac

Alfred et Brainiac peuvent consommer les capacités MORPHEUS pour comprendre l'intention d'une tâche, les contraintes et les critères d'acceptation.

Ils ne constituent pas des dépendances du cœur MORPHEUS.

## 2.6 Autonomie

MORPHEUS doit être pleinement utilisable sans MINOS, NEXUS, JARVIS, Alfred ou Brainiac.

---

# 3. Problème à résoudre

Dans un projet logiciel, la connaissance de l'intention est souvent dispersée entre :

- README ;
- documents d'architecture ;
- ADR ;
- issues ;
- tickets ;
- fichiers Markdown ;
- spécifications formelles ;
- commentaires ;
- échanges humains ;
- plans d'implémentation ;
- critères d'acceptation ;
- historiques de changements.

Cette dispersion entraîne :

- contradictions entre documents ;
- perte du pourquoi d'une décision ;
- difficulté à savoir quelle spécification est courante ;
- confusion entre état actuel et changement proposé ;
- implémentations conformes au code existant mais non à l'intention ;
- critères d'acceptation incomplets ou oubliés ;
- redécouverte répétitive du contexte ;
- dépendance excessive à l'historique de conversation d'un agent IA ;
- difficulté à relier exigences, décisions, tâches, tests et code ;
- consommation inutile de contexte et de tokens.

MORPHEUS doit transformer cette connaissance en un modèle exploitable, persistant et traçable.

---

# 4. Utilisateurs et consommateurs visés

MORPHEUS doit pouvoir être consommé à terme par :

- des développeurs ;
- des architectes ;
- des responsables techniques ;
- des outils CLI ;
- des IDE ;
- des agents IA ;
- des serveurs MCP ;
- des API ;
- JARVIS ;
- NEXUS ;
- MINOS via intégration explicite ;
- des pipelines CI/CD ;
- des outils de validation automatisée.

Les consommateurs ne doivent pas connaître les détails internes d'OpenSpec ou d'un autre provider.

---

# 5. Principes architecturaux fondamentaux

## 5.1 Domaine possédé par MORPHEUS

Le domaine MORPHEUS doit être indépendant des formats externes.

Les concepts publics ne doivent pas être des copies directes de structures OpenSpec, Markdown ou d'une API tierce.

## 5.2 Provider-agnostic

MORPHEUS doit pouvoir ingérer plusieurs sources via une abstraction conceptuelle :

```text
SpecificationProvider
```

Providers envisagés :

- OpenSpec ;
- Markdown structuré ;
- sources Git ;
- formats futurs ;
- connecteurs externes éventuels.

La sélection doit être fondée sur les capacités réelles du provider.

## 5.3 Local-first

Par défaut :

- les spécifications privées restent locales ;
- aucun LLM n'est requis ;
- aucun service cloud n'est obligatoire ;
- les intégrations externes sont opt-in ;
- le fonctionnement hors ligne doit rester possible pour le cœur.

## 5.4 Traçabilité native

Chaque information importante doit pouvoir conserver :

- son origine ;
- son identifiant externe éventuel ;
- sa version ;
- son statut ;
- sa date de création ou de modification lorsque disponible ;
- ses relations ;
- les preuves ou sources qui justifient son interprétation.

## 5.5 Distinction entre état courant et changement proposé

MORPHEUS doit distinguer explicitement :

```text
CURRENT
PROPOSED
SUPERSEDED
ARCHIVED
```

La terminologie finale sera validée pendant C0.

Une proposition de changement ne doit jamais être présentée comme si elle décrivait déjà l'état courant du produit.

## 5.6 Versionnement

MORPHEUS doit être capable de représenter l'évolution d'une spécification et de relier une modification à son état précédent.

## 5.7 Explicabilité

Une réponse MORPHEUS doit pouvoir expliquer pourquoi une exigence, contrainte ou décision a été retenue comme pertinente.

## 5.8 Efficacité pour les agents

Les réponses doivent être compactes, structurées et ciblées.

Le chargement de documents complets doit rester explicite.

---

# 6. Modèle de domaine candidat

Concepts initiaux :

```text
ProjectSpecification
Specification
Requirement
Scenario
ChangeProposal
Constraint
DesignDecision
AcceptanceCriterion
ImplementationTask
SpecificationVersion
Evidence
TraceabilityLink
ExternalReference
```

## 6.1 ProjectSpecification

Représente l'espace de spécification d'un projet ou workspace.

## 6.2 Specification

Représente une capacité, un comportement, une règle ou un domaine spécifié.

## 6.3 Requirement

Exprime une exigence vérifiable ou normative.

## 6.4 Scenario

Décrit un cas d'utilisation ou un scénario permettant d'illustrer ou de valider un comportement attendu.

## 6.5 ChangeProposal

Représente une modification intentionnelle du système par rapport à l'état courant.

## 6.6 Constraint

Exprime une limite ou obligation qui doit être respectée indépendamment de l'implémentation choisie.

## 6.7 DesignDecision

Représente une décision de conception structurée et traçable.

Une ADR peut être une source possible d'un `DesignDecision`, mais les deux concepts ne doivent pas être confondus automatiquement.

## 6.8 AcceptanceCriterion

Exprime une condition vérifiable permettant de déterminer si un changement ou une exigence est satisfait.

## 6.9 ImplementationTask

Décrit une unité de travail dérivée d'un changement ou d'un plan.

Une tâche n'est pas une exigence.

## 6.10 SpecificationVersion

Représente un état versionné de la connaissance de spécification.

## 6.11 Evidence

Conserve l'origine ou la preuve utilisée pour produire une information normalisée.

## 6.12 TraceabilityLink

Relie explicitement deux concepts.

Relations candidates :

```text
REFINES
IMPLEMENTS
SATISFIES
VALIDATES
CONSTRAINS
DERIVES_FROM
SUPERSEDES
DEPENDS_ON
AFFECTS
DECIDED_BY
VERIFIED_BY
LINKS_TO_CODE
```

La liste exacte reste à valider.

---

# 7. Cycle de vie candidat d'un changement

```text
DRAFT
  ↓
PROPOSED
  ↓
SPECIFIED
  ↓
DESIGNED
  ↓
PLANNED
  ↓
IMPLEMENTING
  ↓
VERIFYING
  ↓
COMPLETED
  ↓
ARCHIVED
```

Ce cycle doit rester configurable et ne pas être une copie stricte d'un outil externe.

Les états doivent permettre au minimum de distinguer :

- une idée non engagée ;
- une proposition ;
- une spécification suffisamment claire ;
- un travail en cours ;
- une vérification ;
- un changement terminé ;
- un historique archivé.

---

# 8. Architecture conceptuelle cible

```text
Sources de spécification
        │
        ▼
Découverte / Project Specification Registry
        │
        ▼
SpecificationProvider Registry
        │
   ┌────┼───────────────┐
   ▼    ▼               ▼
OpenSpec Markdown     Autres
   │    │             providers
   └────┴──────┬────────┘
               ▼
      Ingestion MORPHEUS
               │
               ▼
     Modèle normalisé MORPHEUS
               │
               ▼
   SpecificationKnowledgeStore
               │
               ▼
      Intelligence MORPHEUS
               │
      ┌────────┼────────┐
      ▼        ▼        ▼
  Querying  Traceability  Change analysis
               │
               ▼
          CLI / MCP / API
               │
      ┌────────┼────────┐
      ▼        ▼        ▼
    JARVIS    NEXUS    autres
```

---

# 9. SpecificationProvider

Le provider doit décrire ses capacités.

Capacités candidates :

```text
DISCOVER
READ_CURRENT_SPECS
READ_CHANGES
READ_REQUIREMENTS
READ_SCENARIOS
READ_DESIGN
READ_TASKS
READ_ACCEPTANCE_CRITERIA
VERSION_HISTORY
WATCH_CHANGES
WRITE_CHANGES
ARCHIVE_CHANGES
```

Une capacité d'écriture n'est pas requise pour tous les providers.

Le cœur MORPHEUS doit pouvoir fonctionner avec un provider uniquement en lecture.

---

# 10. OpenSpec

OpenSpec est envisagé comme **premier provider de référence** à évaluer.

La décision proposée n'est pas :

> MORPHEUS = OpenSpec.

La stratégie candidate est :

> **OpenSpec-first, not OpenSpec-locked.**

Il faudra valider :

- la stabilité du format ;
- la qualité des fichiers générés ;
- la facilité d'ingestion ;
- la gestion des changements ;
- l'archivage ;
- la compatibilité avec plusieurs agents ;
- l'évolution du format ;
- les contraintes de licence ;
- la capacité à reconstruire l'état courant à partir des specs et changements.

---

# 11. SpecificationKnowledgeStore

MORPHEUS doit posséder son abstraction de stockage.

Opérations conceptuelles candidates :

```text
storeSpecification
storeRequirement
storeChange
storeTraceabilityLink
getCurrentSpecification
findRequirements
findChanges
findConstraints
findAcceptanceCriteria
findDesignDecisions
findTasks
getHistory
traceRequirement
findRelatedElements
```

Le contrat sera dérivé des cas d'usage et non d'une base de données particulière.

---

# 12. Cas d'usage prioritaires

Premiers cas d'usage à spécifier :

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
```

Cas d'usage avancés possibles :

```text
compare_specification_versions
find_conflicts
find_uncovered_requirements
find_unverified_acceptance_criteria
analyze_change_scope
```

---

# 13. Intelligence propre à MORPHEUS

MORPHEUS ne doit pas seulement stocker des documents.

Il doit progressivement pouvoir :

- résoudre les liens entre exigences, changements, décisions et tâches ;
- détecter les éléments orphelins ;
- identifier des critères d'acceptation non couverts ;
- distinguer une règle normative d'un texte descriptif ;
- identifier les changements qui modifient une spécification existante ;
- reconstruire une vue courante ;
- expliquer l'origine d'une décision ;
- détecter des contradictions explicites lorsque les preuves le permettent.

Toute inférence doit conserver sa provenance et son niveau de confiance.

---

# 14. Intégration future avec MINOS

MORPHEUS pourra référencer des objets de code via des identifiants externes sans absorber le domaine MINOS.

Exemples :

```text
Requirement -> Symbol
ChangeProposal -> Module
ImplementationTask -> SourceFile
AcceptanceCriterion -> Test
DesignDecision -> ArchitecturalComponent
```

Ces liens doivent pouvoir exister sous forme d'`ExternalReference` ou de `TraceabilityLink` spécialisé.

MORPHEUS ne doit pas dépendre de la disponibilité de MINOS pour conserver ses propres spécifications.

---

# 15. Intégration future avec NEXUS

MORPHEUS doit fournir des vues compactes adaptées à la construction de contexte :

- intention du changement ;
- exigences pertinentes ;
- contraintes ;
- décisions de conception ;
- critères d'acceptation ;
- tâches ;
- liens de traçabilité utiles.

NEXUS reste responsable du classement et de la sélection finale.

---

# 16. Orchestration future par JARVIS

JARVIS pourra orchestrer des séquences comme :

```text
1. récupérer le changement courant dans MORPHEUS ;
2. obtenir les exigences et critères d'acceptation ;
3. demander à MINOS les éléments de code associés ;
4. demander à NEXUS de construire le contexte ;
5. confier la tâche à un agent ;
6. vérifier les critères d'acceptation ;
7. mettre à jour l'état du changement.
```

Cette orchestration ne doit pas être codée dans le cœur MORPHEUS.

---

# 17. Exposition

À terme, MORPHEUS pourra être exposé via :

- CLI ;
- MCP ;
- API.

Ces couches doivent rester fines et appeler des services de domaine.

Aucune logique métier essentielle ne doit résider dans un handler MCP ou REST.

---

# 18. Sécurité et confidentialité

MORPHEUS doit considérer les spécifications comme potentiellement sensibles.

Principes :

- aucune exfiltration par défaut ;
- secrets exclus ou masqués lorsque possible ;
- chemins ignorés configurables ;
- providers cloud explicitement activés ;
- journalisation sans contenu sensible par défaut ;
- possibilité de fonctionner entièrement localement.

---

# 19. Périmètre MVP candidat

Le MVP doit permettre sur un projet local :

1. découvrir une source de spécifications ;
2. ingérer au moins un provider réel ;
3. normaliser spécifications, exigences et changements ;
4. distinguer état courant et changement proposé ;
5. stocker les données ;
6. rechercher une exigence ;
7. lire un changement et ses critères d'acceptation ;
8. suivre au moins une relation de traçabilité ;
9. produire une sortie JSON compacte ;
10. fonctionner sans LLM ni service cloud obligatoire.

---

# 20. Critères de validation

Les critères C0/M0 devront mesurer :

- fidélité de l'ingestion ;
- absence de fuite de types propres au provider ;
- reconstruction correcte de l'état courant ;
- traçabilité ;
- latence des requêtes ;
- coût mémoire et disque ;
- comportement avec spécifications invalides ;
- comportement avec changements incomplets ;
- migration de version de provider ;
- fonctionnement local et hors ligne ;
- capacité à utiliser un backend mémoire de test.

---

# 21. Questions ouvertes de C0

À trancher avant implémentation significative :

- identité stable des concepts ;
- granularité d'une `Specification` ;
- format de versionnement ;
- cycle de vie final ;
- modèle de traçabilité ;
- stratégie de provider ;
- statut exact d'OpenSpec ;
- choix du premier backend ;
- gestion de l'écriture ;
- stratégie de synchronisation ;
- intégration Git ;
- politique de conflits ;
- critères d'acceptation du MVP.

---

# 22. Condition de sortie C0

La phase C0 peut être considérée comme terminée lorsque la réponse à la question suivante est affirmative :

> **Savons-nous précisément ce que MORPHEUS doit fournir, pourquoi, à qui, avec quelles frontières, quel modèle de connaissance, quels critères mesurables et quelles décisions structurantes ?**

Aucune implémentation fonctionnelle importante ne doit commencer avant cette validation.
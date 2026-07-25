# Cahier des charges — MORPHEUS

Statut : **BASELINE C0 VALIDÉE — document de cadrage historique**

Date de cadrage : 22 juillet 2026  
Contextualisation D0 : 26 juillet 2026

Ce document constitue la **baseline fonctionnelle et technique de haut niveau validée pendant C0**. La baseline C0→M14 est désormais validée et intégrée ; l'état courant des jalons est porté par [`../governance/ROADMAP.md`](../governance/ROADMAP.md) et la trajectoire post-M14 par [`../roadmap/POST_M14_EXECUTION.md`](../roadmap/POST_M14_EXECUTION.md).

Le vocabulaire prospectif conservé ci-dessous (`candidat`, `à terme`, `il faudra valider`, questions ouvertes C0) décrit le cadrage au moment où il a été établi. Les ADR acceptées, contrats machine, validations et roadmaps ultérieures peuvent le raffiner explicitement sans réécrire cette preuve historique. Voir [`../governance/DOCUMENTATION_STATUS.md`](../governance/DOCUMENTATION_STATUS.md).

> **Règle de travail historique C0 : documenter d'abord, décider ensuite, implémenter en dernier.**

---

# 1. Présentation du projet

## 1.1 Nom

**MORPHEUS**

Dépôt technique : `morpheus-engine`.

## 1.2 Nature du produit

MORPHEUS est un **moteur d'intelligence des spécifications et de l'intention** (*Specification & Intent Intelligence Engine*).

Sa responsabilité est de construire, maintenir et exposer une représentation :

- structurée ;
- persistante ;
- versionnée ;
- traçable ;
- interrogeable ;
- explicable ;
- exploitable par des machines ;

de ce qu'un projet **doit devenir**.

Cette représentation couvre notamment :

- les spécifications actuelles ;
- les exigences ;
- les contraintes ;
- les scénarios ;
- les changements proposés ou en cours ;
- les décisions de conception ;
- les critères d'acceptation ;
- les tâches d'implémentation ;
- les relations de traçabilité ;
- la provenance ;
- les preuves ;
- les versions et l'historique nécessaires à l'explicabilité.

## 1.3 Formulation synthétique

> **MORPHEUS transforme l'intention et les spécifications d'un projet en modèle de connaissance versionné, traçable et interrogeable.**

## 1.4 Question fondamentale

MORPHEUS répond principalement à :

> **Qu'est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l'intention ?**

## 1.5 Non-objectifs

MORPHEUS n'est pas :

- un moteur d'intelligence du code ;
- un moteur de recherche de symboles ;
- un gestionnaire de tickets généraliste ;
- un outil de planification RH ou de sprint ;
- un chatbot ;
- un LLM ;
- un agent autonome ;
- un moteur de génération de code ;
- un remplaçant de Git ;
- un remplaçant des tests ;
- un remplaçant d'un format de spécification particulier ;
- un système général de ranking de contexte ;
- un moteur d'orchestration ;
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

Cette vue décrit des responsabilités, pas des dépendances techniques obligatoires.

## 2.2 Frontière MORPHEUS / MINOS

MORPHEUS répond à :

> **Que voulons-nous construire, pourquoi et sous quelles contraintes ?**

MINOS répond à :

> **Que contient réellement le code, où se trouvent ses éléments et comment sont-ils reliés ?**

MORPHEUS ne doit pas parser le code source pour remplacer MINOS.

MINOS ne doit pas devenir le système de vérité des exigences et changements.

Les deux moteurs peuvent coopérer via des références explicites et découplées.

## 2.3 Frontière MORPHEUS / NEXUS

MORPHEUS produit des faits et vues compactes sur :

- les spécifications ;
- les changements ;
- les exigences ;
- les contraintes ;
- les décisions ;
- les critères d'acceptation ;
- la traçabilité.

NEXUS décide quelles informations doivent être sélectionnées, classées, combinées ou compressées pour une tâche donnée.

MORPHEUS ne doit pas devenir un moteur général de sélection de contexte.

## 2.4 Frontière MORPHEUS / JARVIS

JARVIS orchestre les capacités disponibles.

MORPHEUS expose des opérations spécialisées mais ne doit pas intégrer la logique d'orchestration propre à JARVIS.

## 2.5 Alfred et Brainiac

Alfred et Brainiac peuvent consommer MORPHEUS pour comprendre :

- l'intention ;
- les exigences ;
- les contraintes ;
- les décisions ;
- les critères d'acceptation ;
- l'état d'un changement.

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
- plans ;
- critères d'acceptation ;
- historiques de changements ;
- échanges humains ;
- historiques de conversations avec des agents.

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

MORPHEUS doit transformer cette connaissance en modèle exploitable, persistant et traçable.

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

La sélection doit être fondée sur les capacités effectives du provider pour une source et une version données.

## 5.3 Local-first

Par défaut :

- les spécifications privées restent locales ;
- aucun LLM n'est requis ;
- aucun service cloud n'est obligatoire ;
- les intégrations externes sont opt-in ;
- le fonctionnement hors ligne doit rester possible pour le cœur ;
- à capacités équivalentes, un provider local doit être préféré à un provider distant.

## 5.4 Traçabilité native

Chaque information importante doit pouvoir conserver :

- son origine ;
- son identifiant externe éventuel ;
- sa version ;
- son état ;
- son locator source ;
- ses relations ;
- les preuves utilisées ;
- la résolution et la confiance lorsqu'une information est dérivée ou ambiguë.

## 5.5 États orthogonaux

MORPHEUS ne doit pas utiliser un unique statut mélangeant plusieurs dimensions.

### État temporel

```text
CURRENT
PROPOSED
HISTORICAL
```

`CURRENT` décrit l'état de référence applicable.

`PROPOSED` décrit une évolution envisagée ou en cours qui ne doit pas être présentée comme déjà applicable.

`HISTORICAL` décrit une information conservée pour l'historique et l'explicabilité.

### Cycle de vie d'un changement

Candidat :

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

### Résolution

```text
RESOLVED
PARTIALLY_RESOLVED
UNRESOLVED
HEURISTIC
```

### Vérification

```text
NOT_VERIFIED
PARTIALLY_VERIFIED
VERIFIED
FAILED
UNKNOWN
```

Ces dimensions ne doivent pas être déduites les unes des autres sans règle explicite.

## 5.6 Identité stable

MORPHEUS doit distinguer :

```text
identity
version
source locator
external identifier
```

Un chemin de fichier, un titre ou un identifiant provider ne constitue pas automatiquement l'identité publique principale d'une entité.

Les identités MORPHEUS doivent pouvoir rester stables lorsque la continuité logique est démontrable malgré un déplacement ou une modification de source.

Une ambiguïté d'identité doit être signalée plutôt que fusionnée silencieusement.

## 5.7 Versionnement et snapshots

MORPHEUS doit être capable de représenter l'évolution d'une spécification et de relier une modification à son état précédent.

L'état de connaissance doit pouvoir être publié sous forme de snapshot cohérent :

```text
before activation -> consumers see Vn
after activation  -> consumers see Vn+1
```

Un consommateur ne doit pas observer un état courant partiellement remplacé.

La version logique d'une spécification doit rester distincte du snapshot technique d'une ingestion.

## 5.8 Explicabilité

Une réponse MORPHEUS doit pouvoir expliquer :

- d'où vient l'information ;
- pourquoi une relation existe ;
- si elle est explicite, dérivée ou heuristique ;
- quelle version de source a été utilisée ;
- quelles preuves soutiennent le résultat.

## 5.9 Efficacité pour les agents

Les réponses doivent être compactes, structurées et ciblées.

Le chargement de documents complets doit rester explicite.

## 5.10 Read-first

Le fonctionnement de base est orienté lecture et compréhension.

Une capacité d'écriture :

- est optionnelle ;
- doit être explicitement déclarée par le provider ;
- ne doit jamais être déduite d'une capacité de lecture ;
- doit disposer de règles de conflit, permission et confirmation.

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
KnowledgeSnapshot
Evidence
Provenance
TraceabilityLink
ExternalReference
```

Le modèle détaillé est documenté dans [`../domain/MODEL.md`](../domain/MODEL.md).

## 6.1 ProjectSpecification

Représente l'espace de spécification d'un projet ou workspace.

## 6.2 Specification

Représente un ensemble cohérent de comportements, règles ou capacités dans l'état de référence.

Une `Specification` n'est pas nécessairement équivalente à un fichier.

## 6.3 Requirement

Exprime une exigence fonctionnelle ou non fonctionnelle observable ou vérifiable.

## 6.4 Scenario

Décrit un comportement attendu dans un contexte donné.

Le domaine ne doit pas imposer Gherkin même si un provider peut le supporter.

## 6.5 ChangeProposal

Représente une modification intentionnelle du système par rapport à l'état courant.

Un changement ne devient pas automatiquement `CURRENT` parce qu'il est `COMPLETED`.

## 6.6 Constraint

Exprime une limite ou obligation qui restreint les solutions acceptables.

## 6.7 DesignDecision

Représente une décision de conception structurée et traçable.

Une ADR peut être une source d'un `DesignDecision`, mais les deux concepts ne doivent pas être confondus automatiquement.

## 6.8 AcceptanceCriterion

Exprime une condition vérifiable permettant de déterminer si un changement ou une exigence est satisfait.

L'existence d'un test lié ne signifie pas automatiquement que le critère est vérifié.

## 6.9 ImplementationTask

Décrit une unité de travail dérivée d'un changement ou d'un plan.

Une tâche n'est pas une exigence et MORPHEUS ne doit pas devenir un gestionnaire général de tickets.

## 6.10 SpecificationVersion

Représente une version logique de la connaissance de spécification.

## 6.11 KnowledgeSnapshot

Représente un état technique cohérent ingéré et normalisé par MORPHEUS.

Plusieurs snapshots techniques peuvent représenter la même version logique après reconstruction ou évolution d'un provider.

## 6.12 Evidence

Conserve la preuve ou référence source utilisée pour produire une information ou une relation.

## 6.13 Provenance

Répond à :

> D'où cette information vient-elle et dans quel état de la source a-t-elle été observée ?

## 6.14 TraceabilityLink

Relie explicitement deux concepts.

Taxonomie initiale candidate :

```text
REFINES
DERIVES_FROM
CONSTRAINS
IMPLEMENTS
SATISFIES
VALIDATES
VERIFIED_BY
DECIDED_BY
DEPENDS_ON
AFFECTS
SUPERSEDES
LINKS_TO_CODE
LINKS_TO_TEST
RELATED_TO
```

Le type de relation reste distinct de son origine, de sa résolution et de sa confiance.

## 6.15 ExternalReference

Représente un lien vers un système non possédé par MORPHEUS.

Exemples :

```text
MINOS / SYMBOL
GIT / COMMIT
GITHUB / ISSUE
JIRA / ISSUE
```

Aucun type du système externe ne doit traverser la frontière du domaine.

---

# 7. Cycle de vie candidat d'un changement

La machine d'état candidate est documentée dans [`../domain/CHANGE_LIFECYCLE.md`](../domain/CHANGE_LIFECYCLE.md).

Cycle nominal :

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

`ABANDONED` est un état terminal métier accessible depuis plusieurs étapes.

Des retours arrière explicites sont possibles lorsqu'une spécification, un design, un plan ou une implémentation doit être repris.

Le cycle de vie reste orthogonal à `TemporalState` et `VerificationStatus`.

Le modèle final doit être suffisamment générique pour normaliser les providers sans être une copie stricte de l'un d'eux.

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
   Querying Traceability Change analysis
               │
               ▼
          CLI / MCP / API
               │
      ┌────────┼────────┐
      ▼        ▼        ▼
    JARVIS    NEXUS    autres
```

L'architecture détaillée est décrite dans [`../architecture/overview.md`](../architecture/overview.md).

---

# 9. SpecificationProvider

Le contrat conceptuel est documenté dans [`../contracts/SPECIFICATION_PROVIDER.md`](../contracts/SPECIFICATION_PROVIDER.md).

Le provider doit décrire ses capacités effectives.

Taxonomie candidate :

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
READ_REQUIREMENTS
READ_CONSTRAINTS
READ_SCENARIOS
READ_DESIGN_DECISIONS
READ_ACCEPTANCE_CRITERIA
READ_IMPLEMENTATION_TASKS
READ_HISTORY
READ_ARCHIVES
INCREMENTAL_READ
WATCH_CHANGES
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

Un provider read-only doit être parfaitement valide.

Les capacités d'écriture sont distinctes des capacités de lecture.

La sélection doit tenir compte :

- de la source ;
- de la version de format ;
- des capacités obligatoires ;
- des capacités préférées ;
- de la politique local-first ;
- de la configuration explicite.

---

# 10. OpenSpec

OpenSpec est envisagé comme **premier provider de référence** à évaluer.

La décision proposée n'est pas :

> MORPHEUS = OpenSpec.

La stratégie candidate est :

> **OpenSpec-first, not OpenSpec-locked.**

Il faudra valider :

- la stabilité du format ;
- la qualité de l'information disponible ;
- la facilité d'ingestion ;
- la gestion des changements ;
- l'archivage ;
- la compatibilité avec plusieurs agents ;
- l'évolution du format ;
- les contraintes de licence ;
- la capacité à reconstruire l'état courant ;
- la capacité à mapper le cycle de vie sans perte critique.

L'étude dédiée est [`../research/openspec-provider-study.md`](../research/openspec-provider-study.md).

---

# 11. SpecificationKnowledgeStore

MORPHEUS doit posséder son abstraction de stockage.

Le contrat conceptuel est documenté dans [`../contracts/SPECIFICATION_KNOWLEDGE_STORE.md`](../contracts/SPECIFICATION_KNOWLEDGE_STORE.md).

Familles de capacités attendues :

```text
WRITE
READ
SEARCH
TRAVERSE
SNAPSHOT / VERSION
MAINTENANCE
DIAGNOSTICS
```

Le contrat doit permettre notamment :

```text
storeSnapshot
applyDelta
getCurrentVersion
findSpecification
findRequirements
findChanges
findConstraints
findAcceptanceCriteria
findDesignDecisions
findImplementationTasks
findOutgoingLinks
findIncomingLinks
traverse
findPath
getHistory
getEvidence
getProvenance
```

Le contrat est dérivé des cas d'usage et non d'une technologie de base de données.

Un backend mémoire de tests doit exister.

Au moins un backend persistant local doit être évalué pendant M0.

Un backend graphe n'est retenu que si les mesures démontrent un bénéfice supérieur à sa complexité.

---

# 12. Cas d'usage prioritaires

Les cas d'usage détaillés sont documentés dans [`USE_CASES.md`](USE_CASES.md).

Le premier noyau doit couvrir au minimum :

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
```

Cas avancés :

```text
compare_specification_versions
find_conflicts
find_uncovered_requirements
find_unverified_acceptance_criteria
analyze_change_scope
resolve_external_reference
```

Les contrats techniques doivent être dérivés de ces cas d'usage.

---

# 13. Intelligence propre à MORPHEUS

MORPHEUS ne doit pas seulement stocker des documents.

Il doit progressivement pouvoir :

- résoudre les liens entre exigences, changements, décisions et tâches ;
- détecter les éléments orphelins ;
- identifier des critères d'acceptation non couverts ;
- distinguer une règle normative d'un texte descriptif lorsque les preuves le permettent ;
- identifier les changements qui modifient une spécification existante ;
- reconstruire une vue courante ;
- expliquer l'origine d'une décision ;
- détecter des contradictions explicites ;
- produire des chemins de justification ;
- comparer état courant et changement proposé ;
- identifier des références cassées ou non résolues.

Toute inférence doit conserver sa provenance, son origine, sa résolution et sa confiance lorsque pertinente.

---

# 14. Intégration future avec MINOS

MORPHEUS pourra référencer des objets de code via des identifiants externes sans absorber le domaine MINOS.

Exemples :

```text
Requirement -> MINOS Symbol
ChangeProposal -> MINOS Module
ImplementationTask -> MINOS SourceFile
AcceptanceCriterion -> MINOS Test
DesignDecision -> MINOS ArchitecturalComponent
```

Ces liens doivent exister via `ExternalReference` ou `TraceabilityLink` spécialisé.

MORPHEUS doit fonctionner lorsque MINOS est indisponible.

La résolution de la référence est une capacité d'intégration, pas une condition de conservation de la spécification.

---

# 15. Intégration future avec NEXUS

MORPHEUS doit fournir des vues compactes :

- intention du changement ;
- exigences pertinentes ;
- contraintes ;
- décisions de conception ;
- critères d'acceptation ;
- tâches ;
- chemins de traçabilité utiles ;
- références non résolues ;
- avertissements.

NEXUS reste responsable :

- du ranking global ;
- du budget de contexte ;
- de la fusion avec MINOS, Git, documentation et autres sources ;
- de la sélection finale.

---

# 16. Orchestration future par JARVIS

JARVIS pourra orchestrer des séquences comme :

```text
1. récupérer le changement dans MORPHEUS ;
2. obtenir exigences, contraintes et critères ;
3. demander à MINOS les éléments de code associés ;
4. demander à NEXUS de construire le contexte ;
5. confier la tâche à un agent ;
6. recueillir les preuves de vérification ;
7. demander à MORPHEUS les transitions autorisées ;
8. faire évoluer la source via une capacité d'écriture explicite si autorisée.
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

Les sorties machine doivent :

- être compactes ;
- être versionnables ;
- conserver provenance et états ;
- ne pas exposer de types provider-specific.

---

# 18. Sécurité et confidentialité

MORPHEUS doit considérer les spécifications comme potentiellement sensibles.

Principes :

- aucune exfiltration par défaut ;
- aucun provider distant sans opt-in ;
- secrets exclus ou masqués lorsque possible ;
- chemins ignorés configurables ;
- journalisation sans contenu sensible par défaut ;
- fichiers de spécification traités comme données, pas comme code exécutable ;
- liens externes non suivis automatiquement ;
- fonctionnement entièrement local possible ;
- écriture séparée de la lecture et soumise à permissions explicites.

---

# 19. Périmètre MVP candidat

Le MVP doit permettre sur un projet local :

1. découvrir une source de spécifications ;
2. ingérer au moins un provider réel ;
3. normaliser spécifications, exigences, contraintes et changements ;
4. distinguer `CURRENT`, `PROPOSED` et `HISTORICAL` ;
5. maintenir des identités stables selon les règles MVP ;
6. stocker un snapshot cohérent ;
7. rechercher une exigence ;
8. lire un changement ;
9. obtenir ses contraintes et critères d'acceptation ;
10. suivre des relations de traçabilité ;
11. exposer provenance et références non résolues ;
12. produire une sortie JSON compacte ;
13. fonctionner sans LLM ni service cloud obligatoire.

L'écriture des spécifications n'est pas obligatoire pour le MVP.

---

# 20. Critères de validation

Les critères C0/M0 doivent mesurer :

- fidélité de l'ingestion ;
- absence de fuite de types provider ;
- stabilité des identités ;
- reconstruction correcte de l'état courant ;
- séparation current/proposed ;
- fidélité du cycle de vie ;
- couverture de la traçabilité ;
- latence des requêtes ;
- temps d'ingestion ;
- coût mémoire et disque ;
- robustesse face aux spécifications invalides ;
- comportement avec changements incomplets ;
- compatibilité de versions ;
- idempotence ;
- activation atomique observable des snapshots ;
- fonctionnement local et hors ligne ;
- capacité à utiliser un backend mémoire ;
- capacité à reconstruire le store depuis les sources.

La matrice d'expérimentation est [`../research/M0_EXPERIMENT_MATRIX.md`](../research/M0_EXPERIMENT_MATRIX.md).

---

# 21. Documents détaillés normatifs de C0

Sous réserve de validation C0, les documents suivants précisent ce cahier des charges :

- [`../domain/MODEL.md`](../domain/MODEL.md) — concepts et invariants du domaine ;
- [`../domain/CHANGE_LIFECYCLE.md`](../domain/CHANGE_LIFECYCLE.md) — machine d'état candidate ;
- [`USE_CASES.md`](USE_CASES.md) — cas d'usage et priorités ;
- [`../contracts/SPECIFICATION_PROVIDER.md`](../contracts/SPECIFICATION_PROVIDER.md) — contrat provider ;
- [`../contracts/SPECIFICATION_KNOWLEDGE_STORE.md`](../contracts/SPECIFICATION_KNOWLEDGE_STORE.md) — contrat de store ;
- [`../research/openspec-provider-study.md`](../research/openspec-provider-study.md) — étude provider de référence ;
- [`../research/M0_EXPERIMENT_MATRIX.md`](../research/M0_EXPERIMENT_MATRIX.md) — plan de preuves M0 ;
- [`../adr/`](../adr/) — décisions structurantes proposées.

En cas de divergence, la contradiction doit être résolue explicitement ; elle ne doit pas rester implicite dans le dépôt.

---

# 22. Questions ouvertes de C0

À trancher avant implémentation significative :

- format concret des identifiants opaques ;
- règles finales de résolution d'identité ;
- granularité exacte d'une `Specification` ;
- représentation précise des deltas ;
- stratégie des étapes facultatives du cycle de vie ;
- mapping final du provider OpenSpec ;
- taxonomie MVP définitive des relations ;
- choix du premier backend persistant ;
- politique de rétention des snapshots ;
- granularité de l'ingestion incrémentale ;
- gestion future de la composition multi-provider ;
- stratégie d'écriture ;
- politique de conflits ;
- règles de promotion d'un changement terminé vers l'état courant ;
- critères chiffrés de performance du MVP.

---

# 23. Condition de sortie C0

La phase C0 peut être considérée comme terminée lorsque la réponse à la question suivante est affirmative :

> **Savons-nous précisément ce que MORPHEUS doit fournir, pourquoi, à qui, avec quelles frontières, quel modèle de connaissance, quels critères mesurables et quelles décisions structurantes ?**

En pratique, avant validation C0 :

- le modèle de domaine candidat doit être cohérent ;
- les cas d'usage MVP doivent être priorisés ;
- les ADR structurantes doivent être examinées ;
- les hypothèses nécessitant une preuve M0 doivent être identifiées ;
- la matrice d'expérimentation M0 doit être prête ;
- aucune contradiction majeure ne doit subsister entre les documents de référence.

Aucune implémentation fonctionnelle importante ne doit commencer avant cette validation.

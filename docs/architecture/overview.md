# Vue d'ensemble de l'architecture — MORPHEUS

Statut : **Proposition — à valider pendant C0**

Date : 22 juillet 2026

La source de vérité fonctionnelle reste [`../CAHIER_DES_CHARGES.md`](../CAHIER_DES_CHARGES.md).

---

## 1. Finalité

MORPHEUS transforme des sources de spécification hétérogènes en une représentation normalisée, traçable et interrogeable de l'intention d'un projet.

Il doit être capable de répondre sans dépendance obligatoire à un LLM ni à un format de spécification particulier.

---

## 2. Objectifs architecturaux

MORPHEUS doit être :

- indépendant du format de spécification ;
- local-first ;
- indépendant des fournisseurs d'IA ;
- utilisable sans LLM ;
- versionné ;
- traçable ;
- explicable ;
- capable de distinguer état courant et changement proposé ;
- agnostique du backend à la frontière du domaine ;
- extensible vers plusieurs providers ;
- consommable par CLI, MCP, API et autres moteurs.

---

## 3. Architecture générale candidate

```text
Sources / dépôts / workspaces
           │
           ▼
Découverte des sources de spécification
           │
           ▼
SpecificationProviderRegistry
           │
     ┌─────┼────────────────────────┐
     ▼     ▼                        ▼
 OpenSpec Markdown structuré      Futurs providers
     │     │                        │
     └─────┴───────────┬────────────┘
                       ▼
              Ingestion MORPHEUS
                       │
                       ▼
            Modèle normalisé MORPHEUS
                       │
                       ▼
          SpecificationKnowledgeStore
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
        Requêtes   Traçabilité  Historique
                       │
                       ▼
            Intelligence MORPHEUS
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
         Changes    Coverage    Conflicts
         Context    Orphans     Analysis
                       │
                       ▼
               Services de cas d'usage
                       │
            ┌──────────┼──────────┐
            ▼          ▼          ▼
           CLI        MCP        API
                       │
          ┌────────────┼────────────┐
          ▼            ▼            ▼
       JARVIS        NEXUS        autres
```

---

## 4. Découverte des sources

Responsabilités :

- identifier la racine de projet ;
- détecter les répertoires et fichiers de spécification ;
- détecter les formats reconnus ;
- appliquer les exclusions ;
- détecter plusieurs providers potentiels ;
- construire un inventaire sans interpréter le métier.

La découverte ne doit pas contenir de logique propre à OpenSpec en dehors d'adaptateurs dédiés.

---

## 5. SpecificationProviderRegistry

Le registre expose les providers disponibles et leurs capacités.

Contrat conceptuel :

```text
SpecificationProvider
├── id
├── capabilities
├── supports(source)
├── discover(source)
├── readCurrentSpecifications(...)
├── readChanges(...)
└── readHistory(...)
```

Capacités candidates :

```text
DISCOVER
CURRENT_SPECIFICATIONS
CHANGES
REQUIREMENTS
SCENARIOS
DESIGN
TASKS
ACCEPTANCE_CRITERIA
HISTORY
WATCH
WRITE
ARCHIVE
```

La sélection doit se faire selon les capacités et la source réelle, pas via une liste figée de formats dans le cœur.

---

## 6. Ingestion MORPHEUS

Cette couche transforme des objets externes en concepts du domaine MORPHEUS.

Elle doit :

- valider les données ;
- normaliser les identités ;
- conserver la provenance ;
- transformer les relations externes ;
- signaler les éléments inconnus ou partiellement compris ;
- préserver les identifiants externes comme métadonnées lorsque pertinent.

Aucun type spécifique à OpenSpec ne doit fuiter hors de l'adaptateur.

---

## 7. Modèle normalisé

Concepts candidats :

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

### 7.1 Identité

Chaque concept durable doit posséder une identité MORPHEUS indépendante d'un chemin de fichier lorsque possible.

Points à expérimenter :

- identifiant explicite fourni par la source ;
- clé logique normalisée ;
- identifiant dérivé stable ;
- gestion des renommages ;
- collision entre providers ;
- identité inter-version.

### 7.2 Provenance

Chaque élément doit pouvoir exposer :

```text
sourceProvider
sourceLocation
externalId
sourceRevision
observedAt
```

### 7.3 Statut

Le modèle doit distinguer les dimensions suivantes :

- état de validité ;
- état du cycle de vie ;
- état courant ou proposé ;
- état d'archivage.

Une seule enum universelle ne doit pas être imposée prématurément à des dimensions différentes.

---

## 8. TraceabilityLink

La traçabilité est un concept de premier ordre.

Un lien doit pouvoir conserver :

```text
source
relationType
target
evidence
origin
confidence
```

Relations candidates :

```text
REFINES
DERIVES_FROM
CONSTRAINS
SATISFIES
IMPLEMENTS
VALIDATES
VERIFIED_BY
SUPERSEDES
DEPENDS_ON
AFFECTS
DECIDED_BY
LINKS_TO_CODE
```

Les relations déterministes et inférées doivent être distinguées.

---

## 9. SpecificationKnowledgeStore

`SpecificationKnowledgeStore` est une abstraction possédée par MORPHEUS.

Le contrat doit être dérivé des cas d'usage.

Opérations conceptuelles :

```text
storeProjectSpecification
storeSpecifications
storeRequirements
storeChanges
storeTraceabilityLinks
getCurrentSpecification
findSpecification
findRequirements
findChanges
findConstraints
findAcceptanceCriteria
findDesignDecisions
findTasks
findRelatedElements
trace
getHistory
```

Le domaine ne doit pas connaître :

- SQL ;
- Cypher ;
- un moteur documentaire ;
- un moteur graphe ;
- un schéma propre à un produit.

Un backend mémoire doit pouvoir implémenter le contrat pour les tests.

---

## 10. Intelligence MORPHEUS

La couche d'intelligence ajoute des connaissances dérivées propres au produit.

Capacités candidates :

```text
CHANGE_SCOPE
REQUIREMENT_COVERAGE
ACCEPTANCE_COVERAGE
ORPHAN_DETECTION
SPEC_CONFLICT
TRACEABILITY_PATH
CURRENT_STATE_RECONSTRUCTION
```

Toute information dérivée doit exposer :

```text
origin
confidence
evidence
path
```

---

## 11. Services de requêtes

Premiers services candidats :

```text
getCurrentSpecification
findRequirements
getChange
listChanges
getConstraints
getAcceptanceCriteria
getDesignDecisions
getImplementationTasks
traceRequirement
getChangeContext
getSpecificationContext
```

Services ultérieurs :

```text
compareSpecificationVersions
findConflicts
findUncoveredRequirements
findUnverifiedAcceptanceCriteria
analyzeChangeScope
```

Les services ne doivent pas exposer directement les objets propres au backend.

---

## 12. Provider OpenSpec candidat

Architecture :

```text
OpenSpec files
     │
     ▼
OpenSpecSpecificationProvider
     │
     ▼
MORPHEUS ingestion
     │
     ▼
MORPHEUS domain
```

L'adaptateur OpenSpec sera responsable de :

- découvrir la structure OpenSpec ;
- interpréter les specs courantes ;
- interpréter les changements ;
- lire proposal/design/tasks lorsque disponibles ;
- conserver les emplacements sources ;
- gérer les versions de format prises en charge ;
- signaler clairement ce qui n'est pas compris.

---

## 13. Écriture et mutation

Le MVP doit privilégier la lecture et la compréhension.

L'écriture via provider doit être considérée comme une capacité séparée.

Raisons :

- écrire implique davantage de risques de corruption ;
- les formats peuvent imposer leurs propres invariants ;
- un provider en lecture seule doit rester valide ;
- les agents doivent pouvoir proposer des changements sans mutation automatique.

Une future abstraction d'écriture devra donc être explicitement validée.

---

## 14. Synchronisation et fraîcheur

MORPHEUS devra pouvoir détecter qu'une source a changé.

Approches candidates :

- empreinte de fichiers ;
- révision Git ;
- timestamp ;
- watcher local ;
- mécanisme natif du provider.

Les règles d'invalidation devront être documentées avant M1/M2.

---

## 15. Intégration MINOS

L'intégration ne doit pas créer de dépendance de domaine.

Approche candidate :

```text
MORPHEUS concept
      │
      ▼
ExternalReference
      │
      ├── system = "minos"
      ├── project
      ├── targetType
      └── targetId
```

Le format concret devra être stabilisé conjointement avec les contrats d'intégration de l'écosystème.

---

## 16. Intégration NEXUS

MORPHEUS expose des vues compactes et structurées.

Exemple conceptuel :

```text
SpecificationContext
├── objective
├── requirements[]
├── constraints[]
├── decisions[]
├── acceptanceCriteria[]
├── tasks[]
├── traceability[]
└── provenance
```

NEXUS reste responsable de la sélection finale.

---

## 17. Exposition

Architecture attendue :

```text
Domain / Application Services
          │
   ┌──────┼──────┐
   ▼      ▼      ▼
  CLI    MCP    API
```

Les handlers ne doivent contenir ni parsing de provider, ni règles de traçabilité, ni logique métier.

---

## 18. Non-objectifs de C0 et M0

Ne pas construire immédiatement :

- un éditeur complet de spécifications ;
- une plateforme collaborative ;
- un moteur LLM ;
- une génération automatique de specs par IA ;
- une intégration complète avec tous les trackers ;
- une API publique de production ;
- une orchestration JARVIS complète ;
- un couplage runtime obligatoire à MINOS ou NEXUS.

C0 cadre.

M0 doit ensuite valider les choix structurants par des expérimentations mesurables.
# MVP — MORPHEUS

Statut : **Proposition — à valider pendant C0**

Date : 22 juillet 2026

---

## 1. Objectif du MVP

Le MVP doit démontrer que MORPHEUS peut transformer une source réelle de spécifications en un modèle normalisé, traçable et interrogeable **sans dépendance obligatoire à un LLM, à un service cloud ou à un format public exposé directement aux consommateurs**.

Le MVP ne cherche pas à couvrir tous les formats ni tous les workflows.

Il doit valider la valeur fondamentale du produit :

> **comprendre l'intention structurée d'un projet et la rendre exploitable par des humains et des machines.**

---

## 2. Source de validation principale

Le premier provider candidat est OpenSpec.

Cette sélection sert à valider l'architecture de provider et ne signifie pas que le domaine MORPHEUS dépend d'OpenSpec.

Le MVP doit prouver qu'un second provider minimal ou un backend de test peut coexister sans modifier les contrats publics du domaine.

---

## 3. Capacités obligatoires

### 3.1 Découverte

MORPHEUS doit pouvoir :

- identifier un projet local ;
- détecter une source de spécifications supportée ;
- sélectionner un provider compatible ;
- signaler clairement l'absence ou l'ambiguïté de provider.

### 3.2 Ingestion

Le système doit ingérer au minimum :

- spécifications courantes ;
- exigences ;
- changements ;
- critères d'acceptation lorsque disponibles ;
- tâches lorsque disponibles ;
- provenance.

### 3.3 Normalisation

Le système doit produire des objets MORPHEUS indépendants du provider.

Au minimum :

```text
Specification
Requirement
ChangeProposal
AcceptanceCriterion
ImplementationTask
TraceabilityLink
Evidence
```

### 3.4 État courant vs changement proposé

Le MVP doit distinguer de manière fiable :

- ce qui décrit l'état courant ;
- ce qui décrit une évolution proposée ou en cours.

### 3.5 Stockage

Le modèle normalisé doit pouvoir être persisté et interrogé via :

```text
SpecificationKnowledgeStore
```

Un backend mémoire doit exister pour les tests.

Le choix du backend de production sera décidé après expérimentation.

### 3.6 Requêtes

Le MVP doit exposer au minimum :

```text
get_current_specification
find_requirements
get_change
list_changes
get_acceptance_criteria
get_implementation_tasks
trace_requirement
get_change_context
```

### 3.7 Traçabilité

Le MVP doit être capable de suivre au moins les relations suivantes :

```text
ChangeProposal -> Requirement
Requirement -> AcceptanceCriterion
ChangeProposal -> ImplementationTask
```

### 3.8 Sortie machine

Une sortie JSON compacte et stable doit être disponible.

---

## 4. Fonctionnement sans IA

Toutes les fonctions obligatoires du MVP doivent fonctionner :

- sans LLM ;
- sans embeddings ;
- sans API externe ;
- sans connexion Internet après installation des dépendances nécessaires.

L'IA pourra être ajoutée plus tard comme capacité complémentaire.

---

## 5. Fonctions explicitement hors MVP

Sont différés :

- génération automatique de spécifications ;
- édition riche collaborative ;
- modification automatique des sources via agent ;
- recherche vectorielle ;
- analyse sémantique par LLM ;
- intégration complète MINOS ;
- intégration complète NEXUS ;
- orchestration JARVIS ;
- support exhaustif de plusieurs formats ;
- synchronisation bidirectionnelle avec outils de tickets ;
- API REST de production ;
- interface graphique.

---

## 6. Projet de référence

M0 devra utiliser au moins :

1. un projet de référence contenant un cycle de changement réel ;
2. plusieurs exigences ;
3. plusieurs critères d'acceptation ;
4. une décision de conception ;
5. des tâches ;
6. au moins un changement archivé ou terminé.

Un second jeu de données minimal devra valider que le domaine n'est pas couplé au premier provider.

---

## 7. Critères mesurables de réussite

### Fidélité

- 100 % des éléments explicitement supportés du jeu de référence doivent être découverts ou signalés comme invalides ;
- aucun changement proposé ne doit être présenté comme état courant ;
- la provenance doit être disponible pour 100 % des objets ingérés.

### Découplage

- aucun type spécifique au provider dans les interfaces publiques du domaine ;
- aucun type spécifique au backend dans les services publics ;
- le backend mémoire doit implémenter le même port de stockage.

### Traçabilité

- les liens supportés doivent pouvoir être traversés dans les deux sens lorsque le modèle le permet ;
- chaque lien dérivé doit exposer sa provenance.

### Performance initiale

Les seuils exacts seront fixés pendant M0, mais les mesures obligatoires sont :

- temps de découverte ;
- temps d'ingestion ;
- taille de l'index ;
- latence de requête ;
- mémoire utilisée.

### Robustesse

Le système doit fournir des erreurs explicites en cas de :

- source absente ;
- structure invalide ;
- élément partiellement lisible ;
- version de format non supportée ;
- collision d'identité ;
- relation vers une cible absente.

---

## 8. Critère de sortie du MVP

Le MVP est validé lorsque :

> **un projet réel peut être ingéré, normalisé, stocké et interrogé à travers des contrats MORPHEUS indépendants du provider, avec distinction fiable entre état courant et changement proposé et avec une traçabilité exploitable.**
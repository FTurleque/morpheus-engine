# MVP — MORPHEUS

Statut : **Proposition — à valider pendant C0**

Date : 22 juillet 2026

---

## 1. Objectif du MVP

Le MVP doit démontrer que MORPHEUS peut transformer une source réelle de spécifications en un modèle normalisé, versionné, traçable et interrogeable **sans dépendance obligatoire à un LLM, à un service cloud ni à un format externe exposé directement aux consommateurs**.

Le MVP ne cherche pas à couvrir tous les formats ni tous les workflows.

Il doit valider la valeur fondamentale du produit :

> **comprendre l'intention structurée d'un projet et la rendre exploitable par des humains et des machines sans perdre la provenance ni confondre l'état courant avec les changements proposés.**

---

## 2. Source de validation principale

Le premier provider candidat est OpenSpec.

Cette sélection sert à valider l'architecture de provider et ne signifie pas que le domaine MORPHEUS dépend d'OpenSpec.

Le MVP doit également utiliser un fake provider ou un second provider minimal afin de vérifier que :

- les contrats publics restent provider-agnostic ;
- le registry fonctionne par capacités ;
- les tests de domaine ne dépendent pas des structures OpenSpec.

---

## 3. Capacités obligatoires

### 3.1 Découverte

MORPHEUS doit pouvoir :

- identifier un projet local ;
- détecter une source de spécifications supportée ;
- déterminer la version de format lorsque possible ;
- sélectionner un provider compatible selon ses capacités effectives ;
- signaler clairement absence, ambiguïté ou incompatibilité de provider.

### 3.2 Ingestion

Le système doit ingérer au minimum :

- spécifications courantes ;
- exigences ;
- contraintes ;
- changements ;
- critères d'acceptation lorsque disponibles ;
- tâches lorsque disponibles ;
- décisions lorsque disponibles ;
- provenance ;
- références et diagnostics utiles.

### 3.3 Normalisation

Le système doit produire des concepts MORPHEUS indépendants du provider.

Au minimum :

```text
ProjectSpecification
Specification
Requirement
Constraint
ChangeProposal
AcceptanceCriterion
ImplementationTask
TraceabilityLink
Evidence
Provenance
ExternalReference
SpecificationVersion
KnowledgeSnapshot
```

`Scenario` et `DesignDecision` doivent être supportables par le modèle même si certaines fixtures n'en contiennent pas.

### 3.4 Identité stable

Le MVP doit distinguer :

```text
DomainIdentity
EntityVersion
SourceLocator
ExternalReference
```

Règles minimales :

- aucun chemin source seul comme identité métier ;
- déplacement de source sans changement logique conservant l'identité lorsque la continuité est démontrable ;
- collision signalée explicitement ;
- aucune fusion heuristique silencieuse.

### 3.5 État temporel

Le MVP doit distinguer de manière fiable :

```text
CURRENT
PROPOSED
HISTORICAL
```

Une requête d'état courant ne doit jamais inclure implicitement une modification seulement proposée.

### 3.6 Cycle de vie

Le MVP doit être capable de représenter le cycle normalisé candidat :

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

Le provider de référence n'est pas obligé d'exposer naturellement chaque état ; le mapping et son niveau de résolution doivent être explicites.

### 3.7 Snapshots et versionnement

Le modèle normalisé doit être publié sous forme de snapshot cohérent.

Le MVP doit démontrer :

- activation observable atomique ;
- idempotence du rejeu ;
- conservation de la provenance ;
- predecessor identifiable ;
- comparaison minimale `ADDED / MODIFIED / REMOVED / UNCHANGED`.

### 3.8 Stockage

Le modèle normalisé doit pouvoir être persisté et interrogé via :

```text
SpecificationKnowledgeStore
```

Deux implémentations sont requises pour valider le découplage :

1. backend mémoire ;
2. backend persistant local candidat.

Le choix du backend de référence sera décidé après les expériences M0.

### 3.9 Requêtes

Le MVP doit exposer au minimum :

```text
get_current_specification
find_requirements
get_change
list_changes
get_constraints
get_acceptance_criteria
get_implementation_tasks
trace_requirement
get_change_context
```

`get_design_decisions` doit être supporté si le dataset de référence expose des décisions.

### 3.10 Traçabilité

Le MVP doit pouvoir représenter et traverser au minimum :

```text
Scenario -> Requirement
AcceptanceCriterion -> Requirement
ImplementationTask -> Requirement
ChangeProposal -> Requirement
Constraint -> ChangeProposal ou Requirement
```

La direction canonique finale dépend de la taxonomie ADR-0010 ; les requêtes doivent également pouvoir exposer les inverses utiles.

Chaque lien retourne selon le cas :

```text
type
origin
resolution
confidence
evidence
```

### 3.11 Diagnostics

Le MVP doit distinguer :

```text
NOT_FOUND
UNSUPPORTED
INVALID
PARTIAL
AMBIGUOUS
UNRESOLVED_REFERENCE
```

Une erreur provider ne doit pas être convertie silencieusement en collection vide.

### 3.12 Sortie machine

Une sortie JSON compacte et stable doit être disponible.

Elle doit :

- exposer des identités MORPHEUS ;
- permettre l'approfondissement ciblé ;
- conserver version, provenance et warnings ;
- ne pas exposer les types internes du provider ou du backend.

---

## 4. Fonctionnement sans IA

Toutes les fonctions obligatoires du MVP doivent fonctionner :

- sans LLM ;
- sans embeddings ;
- sans API externe ;
- sans connexion Internet après installation des dépendances nécessaires.

L'IA pourra être ajoutée plus tard comme capacité complémentaire.

---

## 5. Read-first

Le MVP est **read-first**.

Ne sont pas obligatoires :

- création d'un changement depuis MORPHEUS ;
- modification des tâches dans la source ;
- archivage écrit par MORPHEUS.

Une capacité d'écriture ne doit être ajoutée que derrière une capability explicite et des règles de permission/conflit.

---

## 6. Fonctions explicitement hors MVP

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
- composition multi-provider de production ;
- synchronisation bidirectionnelle avec outils de tickets ;
- API REST de production ;
- interface graphique ;
- graph database obligatoire ;
- event sourcing complet.

---

## 7. Jeux de référence

M0 doit utiliser les datasets définis dans [`research/M0_EXPERIMENT_MATRIX.md`](../research/M0_EXPERIMENT_MATRIX.md), notamment :

- fixture minimale valide ;
- multi-spécifications ;
- historique/renommage/archivage ;
- données invalides ou partielles ;
- jeu de volume ;
- au moins un projet réaliste.

Le MVP ne sera pas validé uniquement sur un happy path synthétique.

---

## 8. Critères mesurables de réussite

### Fidélité

- 100 % des éléments explicitement supportés du jeu de référence doivent être découverts ou signalés comme invalides ;
- aucun changement proposé ne doit être présenté comme état courant ;
- la provenance doit être disponible pour 100 % des objets ingérés ;
- toute information non représentable doit produire un diagnostic ou être conservée explicitement comme métadonnée, jamais disparaître silencieusement.

### Découplage

- aucun type spécifique au provider dans les interfaces publiques du domaine ;
- aucun type spécifique au backend dans les services publics ;
- backend mémoire et backend persistant passent les tests de contrat obligatoires ;
- un fake/second provider peut être enregistré sans modification du domaine.

### Identité

- aucun déplacement de source démontrablement équivalent ne doit provoquer artificiellement une nouvelle identité ;
- les collisions et ambiguïtés sont signalées ;
- aucune fusion heuristique silencieuse.

### Snapshot

- une ingestion interrompue avant activation ne remplace pas l'état courant ;
- le rejeu d'un même état est idempotent ;
- la version active est identifiable ;
- Vn/Vn+1 sont comparables.

### Traçabilité

- les liens supportés sont traversables dans les deux directions lorsque la sémantique le permet ;
- chaque lien dérivé expose son origine et ses preuves ;
- une cible absente reste visible comme non résolue.

### Performance initiale

Les seuils exacts seront fixés à partir de M0, mais les mesures obligatoires sont :

- temps de découverte ;
- temps d'ingestion complète ;
- temps d'ingestion incrémentale si supportée ;
- taille du store ;
- mémoire ;
- temps de démarrage ;
- lookup par identité ;
- lookup par clé ;
- recherche textuelle ;
- traversal profondeur 1 et 3 ;
- recherche de chemin explicatif.

### Robustesse

Le système doit fournir des erreurs explicites en cas de :

- source absente ;
- structure invalide ;
- élément partiellement lisible ;
- version de format non supportée ;
- collision d'identité ;
- relation vers une cible absente ;
- capacité provider manquante ;
- snapshot invalide.

---

## 9. Critère de sortie du MVP

Le MVP est validé lorsque :

> **un projet réaliste peut être découvert, ingéré, normalisé, versionné, stocké et interrogé à travers des contrats MORPHEUS indépendants du provider et du backend, avec identité stable, séparation fiable entre état courant et changement proposé, snapshot cohérent, provenance complète et traçabilité exploitable.**
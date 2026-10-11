# Audit de cohérence C0 — MORPHEUS

Statut : **Audit interne du cadrage — 22 juillet 2026**

Cet audit vérifie l'alignement entre :

- le cahier des charges ;
- le modèle de domaine ;
- le cycle de vie ;
- les cas d'usage ;
- l'architecture ;
- le MVP ;
- la roadmap ;
- les contrats conceptuels ;
- les ADR ;
- la matrice M0.

L'objectif n'est pas de déclarer C0 terminée automatiquement, mais de rendre explicites les incohérences restantes avant validation humaine.

---

## 1. Résultat global

### État

**Cohérence globale : bonne, C0 presque prête pour revue de validation.**

Les principaux concepts structurants sont désormais représentés de manière cohérente dans les documents de référence :

```text
Provider abstraction
Capability negotiation
Normalized domain
Stable identity
Temporal state
Change lifecycle
Traceability
Provenance / Evidence
Knowledge snapshots
Knowledge store abstraction
Read-first
Cross-engine decoupling
Local-first / no mandatory LLM
```

Aucune implémentation fonctionnelle n'est engagée à ce stade.

---

## 2. Incohérences corrigées pendant l'audit

### 2.1 État temporel vs archivage

Ancienne formulation ambiguë :

```text
CURRENT
PROPOSED
SUPERSEDED
ARCHIVED
```

Elle mélangeait :

- position temporelle ;
- cycle de vie ;
- historique.

Formulation maintenant retenue comme candidate :

```text
TemporalState:
CURRENT
PROPOSED
HISTORICAL
```

et séparément :

```text
ChangeLifecycleState:
...
COMPLETED
ARCHIVED
ABANDONED
```

`SUPERSEDES` est une relation de traçabilité/historique, pas un état temporel.

### 2.2 Taxonomie de capabilities

Les anciens noms courts :

```text
DISCOVER
CHANGES
WRITE
ARCHIVE
```

ont été remplacés par une taxonomie explicite :

```text
DISCOVER_PROJECT
READ_CURRENT_SPECIFICATIONS
READ_CHANGES
...
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

L'ADR read-first a été réalignée sur ADR-0011.

### 2.3 Versionnement

La notion unique de `SpecificationVersion` était insuffisante pour distinguer :

- évolution logique de la spécification ;
- reconstruction technique de l'index.

Le cadrage distingue maintenant :

```text
SpecificationVersion
KnowledgeSnapshot
```

### 2.4 Identité

Le cadrage initial mentionnait une identité stable sans distinguer explicitement :

```text
identity
version
locator
external identifier
```

Cette distinction est maintenant un invariant architectural et une ADR dédiée.

---

## 3. Alignement du modèle de domaine

### Source haut niveau

`CAHIER_DES_CHARGES.md`

### Détail

`domain/MODEL.md`

### État

**Aligné.**

Concepts communs :

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

### Point restant à décider

Le format concret des identifiants opaques reste volontairement ouvert.

---

## 4. Alignement des états

### État temporel

```text
CURRENT
PROPOSED
HISTORICAL
```

### Cycle de vie

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

### Snapshot technique

```text
BUILDING
VALIDATING
READY
ACTIVE
FAILED
RETIRED
```

### État

**Aligné et explicitement orthogonal.**

### Point restant à décider

La phase `DESIGNED` doit-elle être obligatoire ou contournable pour les changements triviaux ?

Cette question est réservée à la validation C0/M0.

---

## 5. Alignement Provider

### Contrats

- `SpecificationProvider` ;
- `SpecificationProviderRegistry` ;
- `ProviderCapabilitySet` ;
- `ProviderSelectionPolicy`.

### Principes

- provider-agnostic ;
- OpenSpec comme provider de référence ;
- read-first ;
- write optionnel ;
- capacités effectives selon source/version ;
- préférence local-first ;
- diagnostics de dégradation.

### État

**Aligné.**

### Point restant à décider

La composition de plusieurs providers pour une même source reste différée.

---

## 6. Alignement OpenSpec

### Position retenue

```text
OpenSpec-first, not OpenSpec-locked
```

### État

**Aligné.**

OpenSpec apparaît uniquement comme :

- provider candidat ;
- source d'expérimentation ;
- format externe derrière l'ingestion.

Il ne constitue pas :

- le domaine MORPHEUS ;
- le contrat public ;
- le backend ;
- le workflow imposé à tous les providers.

### Validation encore requise

M0 doit confirmer la fidélité du mapping et la capacité à reconstruire l'état courant.

---

## 7. Alignement stockage

### Port

```text
SpecificationKnowledgeStore
```

### Implémentations attendues M0

```text
InMemorySpecificationKnowledgeStore
PersistentCandidateStore
```

Les noms d'implémentation sont conceptuels.

### État

**Aligné.**

### Décision non prise

Aucun backend persistant n'est encore accepté.

SQLite est un candidat naturel pour expérimentation mais n'est pas une décision C0.

Une graph database n'est pas une exigence.

---

## 8. Alignement traçabilité

### Concept

```text
TraceabilityLink
```

### Dimensions

```text
relationType
origin
resolution
confidence
evidence
```

### Taxonomie candidate

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

### État

**Aligné.**

### Questions encore ouvertes

- distinction finale `IMPLEMENTS` / `SATISFIES` ;
- politique exacte de transitivité ;
- cardinalités devant devenir diagnostics ou invariants.

---

## 9. Alignement cas d'usage / MVP

Les cas d'usage prioritaires sont maintenant explicitement documentés.

Noyau MVP :

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

Les contrats provider et store sont dérivés de ces usages.

### État

**Aligné.**

---

## 10. Alignement snapshots / synchronisation

### Invariant

Un consommateur ne doit pas observer un état courant partiellement remplacé.

### Modèle

```text
ProviderSnapshot
  ↓
Normalization
  ↓
KnowledgeSnapshot BUILDING
  ↓
VALIDATING
  ↓
READY
  ↓
atomic activation
  ↓
ACTIVE
```

### État

**Aligné.**

### Validation nécessaire

E05 doit prouver l'activation atomique observable sur le backend mémoire et le backend persistant candidat.

---

## 11. Alignement MORPHEUS / MINOS

### MORPHEUS

Intention et spécifications.

### MINOS

Code Intelligence.

### Contrat futur

```text
ExternalReference(system=MINOS, resourceType=..., externalId=...)
```

### État

**Aligné.**

MORPHEUS reste utilisable sans MINOS.

---

## 12. Alignement MORPHEUS / NEXUS

MORPHEUS fournit les données structurées du domaine.

NEXUS sélectionne, classe, fusionne et compresse le contexte global.

### État

**Aligné.**

MORPHEUS ne calcule pas le budget de tokens global.

---

## 13. Alignement MORPHEUS / JARVIS

MORPHEUS peut exposer :

```text
change state
allowed transitions
blocking conditions
acceptance status
unresolved references
```

JARVIS décide de l'orchestration.

### État

**Aligné.**

Aucune logique JARVIS dans le cœur MORPHEUS.

---

## 14. Alignement C0 / M0

La matrice M0 couvre maintenant les hypothèses principales :

```text
E01 provider detection
E02 domain mapping
E03 stable identity
E04 current reconstruction
E05 snapshots
E06 traceability
E07 memory store
E08 persistent store
E09 optional graph store
E10 lexical search
E11 incremental ingestion
E12 diagnostics
E13 compact context
E14 external references
```

### État

**Aligné.**

Les ADR dépendantes d'une hypothèse technique contiennent des conditions d'acceptation liées à ces preuves.

---

# 15. Décisions encore réellement ouvertes

Les points suivants ne sont **pas** considérés décidés :

1. langage d'implémentation ;
2. version de runtime ;
3. système de build ;
4. framework CLI ;
5. framework serveur ;
6. format concret des `DomainIdentity` ;
7. backend persistant initial ;
8. nécessité d'un graph store ;
9. stratégie exacte d'incrémental ;
10. politique de rétention des snapshots ;
11. forme API `SpecificationReader` / `SpecificationWriter` ou interface capability-based ;
12. étapes facultatives du cycle de vie ;
13. écriture de specs dans le MVP ou après le MVP — actuellement différée ;
14. composition multi-provider ;
15. recherche sémantique — actuellement exploration future.

Ces points ne doivent pas être figés par accident dans le premier squelette de code.

---

# 16. Checklist de validation C0

## Vision

- [x] nature du produit définie ;
- [x] problème défini ;
- [x] non-objectifs définis ;
- [x] frontières écosystème documentées.

## Domaine

- [x] concepts candidats définis ;
- [x] identité cadrée conceptuellement ;
- [x] états orthogonaux définis ;
- [x] cycle de vie détaillé ;
- [x] traçabilité détaillée ;
- [x] provenance/evidence définies.

## Architecture

- [x] provider abstraction définie ;
- [x] capability negotiation définie ;
- [x] ingestion anti-corruption définie ;
- [x] knowledge store défini ;
- [x] snapshots définis ;
- [x] read-first défini ;
- [x] local-first défini.

## Produit

- [x] cas d'usage priorisés ;
- [x] MVP proposé ;
- [x] roadmap proposée ;
- [x] intégrations futures positionnées.

## Validation

- [x] ADR structurantes détaillées ;
- [x] matrice M0 définie ;
- [ ] seuils chiffrés de performance à arrêter après baseline M0 ;
- [ ] revue humaine et validation explicite de C0.

---

# 17. Conclusion

Le cadrage possède désormais un niveau de précision suffisant pour **préparer M0 sans démarrer encore l'implémentation fonctionnelle du produit**.

La prochaine décision importante n'est pas de choisir immédiatement une stack complète, mais de valider C0 puis de préparer un environnement de spikes qui permette de tester les hypothèses sans les transformer en architecture définitive par défaut.

Recommandation de sortie de cet audit :

> **C0 prête pour revue de validation, mais pas encore déclarée terminée.**
---
id: SEC-0008
title: "Le cœur MORPHEUS est read-first ; l'écriture est une capacité séparée"
domain: security
rules: false
---

# Le cœur MORPHEUS est read-first ; l'écriture est une capacité séparée

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

MORPHEUS doit d'abord comprendre et interroger les spécifications existantes. Certains providers permettent aussi de
créer un changement, modifier une exigence, mettre à jour une tâche, archiver un changement ou réécrire une
spécification.

Lire une source et la modifier présentent des contraintes très différentes. L'écriture introduit notamment : risque
de corruption, gestion de concurrence, respect d'invariants propres au format, gestion de Git, conflits, atomicité,
rollback, permissions et sécurité d'un agent autonome.

Raisons d'une séparation :

- **Sécurité** : comprendre une spécification ne doit jamais modifier sa source par effet de bord.
- **Compatibilité** : certains providers ou sources sont naturellement en lecture seule.
- **Simplicité** : la valeur de compréhension se prouve avant de résoudre les problèmes de mutation.
- **Agents IA** : une lecture peut être accordée largement alors qu'une mutation exige des garde-fous et une intention
  explicite.
- **Formats externes** : chaque format peut imposer ses propres invariants de réécriture.

Options considérées :

- **Provider CRUD universel obligatoire** : rejetée, elle exclurait les sources read-only et mélangerait compréhension
  et mutation.
- **MORPHEUS strictement read-only pour toujours** : non retenue, potentiellement trop restrictive pour certains
  workflows orchestrés.
- **Read-first + écriture optionnelle** : retenue.

## Decision

Le contrat provider est **read-first**. Les capacités de lecture et de découverte forment le socle :

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
```

Les capacités de mutation sont séparées :

```text
WRITE_CHANGE
WRITE_TASK_STATE
ARCHIVE_CHANGE
```

Ces capacités sont des valeurs de `com.morpheus.domain.provider.ProviderCapability`. Un provider valide peut être
entièrement read-only. Aucun service métier de lecture ne dépend de la présence des capacités d'écriture. ADR-0011
définit la négociation de ces capacités et leur caractère effectif selon la source et la version du format.

La forme exacte de l'API d'écriture (capacités déclarées par un provider, ou ports de lecture et d'écriture séparés)
n'est pas fixée par cette décision ; l'invariant est que **l'écriture n'est jamais obligatoire**.

### Invariants

1. Une lecture ne modifie jamais la source.
2. `READ_CHANGES` n'implique jamais `WRITE_CHANGE`.
3. `READ_IMPLEMENTATION_TASKS` n'implique jamais `WRITE_TASK_STATE`.
4. `READ_ARCHIVES` n'implique jamais `ARCHIVE_CHANGE`.
5. Un provider read-only satisfait l'ensemble des cas d'usage de compréhension : découverte, ingestion, requêtes,
   traçabilité, reconstruction courant / proposé et lecture de l'historique disponible.
6. Toute mutation requiert une intention explicite et des permissions.
7. Un succès d'écriture est confirmable par la source ou une preuve adaptée.

Une écriture expérimentale reste isolée et ne devient une dépendance d'aucun service de lecture.

### Garde-fous de toute écriture

Toute capacité d'écriture considère :

- l'intention explicite de l'utilisateur ou d'un orchestrateur autorisé ;
- une précondition sur la version source ;
- la détection de conflit ;
- la validation MORPHEUS avant écriture ;
- une capacité provider explicite ;
- une écriture atomique lorsque possible ;
- un diff ou une preview observable ;
- un rollback via Git ou un mécanisme provider lorsque disponible ;
- la journalisation de la mutation sans fuite de secrets ;
- la resynchronisation après succès ;
- la confirmation de l'état réellement observé.

L'autorisation de lire n'implique jamais l'autorisation d'écrire. Toute activation fonctionnelle de l'écriture fait
l'objet d'un ADR qui précise permissions, concurrence, conflits, confirmation et rollback (ADR-0083 pour les mutations
contrôlées du cycle de vie).

## Do's and Don'ts

### Do

- Déclarer séparément les capacités de lecture et les capacités de mutation d'un provider.
- Accepter un provider qui ne déclare aucune capacité d'écriture.
- Exiger une intention explicite, une permission et une capacité provider explicite avant toute mutation.
- Confirmer un succès d'écriture par la source ou une preuve adaptée.
- Écrire un ADR avant d'activer fonctionnellement une écriture.

### Don't

- Ne pas modifier une source pendant une lecture ou une ingestion.
- Ne pas déduire une capacité d'écriture d'une capacité de lecture.
- Ne pas faire dépendre un service de lecture d'une capacité ou d'une méthode d'écriture.
- Ne pas traiter une autorisation de lecture comme une autorisation d'écriture.

## Consequences

### Positives

- Sécurité accrue et séparation claire des permissions.
- Providers read-only possibles.
- Absence d'effets de bord pendant l'ingestion.
- Possibilité de comprendre ou proposer sans appliquer.
- Meilleure compatibilité avec le local-first et meilleure testabilité.

### Négatives

- Une seconde abstraction peut être nécessaire pour l'écriture.
- Certains workflows exigent deux chemins, et les providers lecture + écriture portent plus de code.
- Une synchronisation est obligatoire après mutation, et une politique de conflits doit être définie.

### Risques

- **Divergence après écriture** : mitigée par la réingestion ou la confirmation provider avant publication du nouveau
  snapshot.
- **Agent sur-privilégié** : mitigé par des permissions séparées et l'opt-in explicite des capacités de mutation.
- **Provider partiellement transactionnel** : mitigé par la précondition de version, la preview, les diagnostics et une
  stratégie de récupération propre au provider.
- **Contrats lecture / écriture trop couplés** : mitigé par des tests qui démontrent qu'un provider read-only satisfait
  les cas d'usage de lecture.

## Compliance and Enforcement

- `morpheus-provider-openspec/src/test/java/com/morpheus/provider/openspec/OpenSpecSpecificationProviderTest.java` :
  le provider de référence OpenSpec ne déclare ni `WRITE_CHANGE`, ni `WRITE_TASK_STATE`, ni `ARCHIVE_CHANGE`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ControlledLifecycleMutationContractTest.java`
  et `ControlledLifecycleMutationRejectionContractTest.java` (même répertoire) : une mutation du cycle de vie exige une
  capacité d'écriture observée et refuse sans état ni audit une transition bloquée.
- Revue : aucun service de lecture ne dépend d'une capacité ou d'une méthode d'écriture.

## References

- [ADR-0001](ARCH-0001-morpheus-owned-domain.md) — domaine propre à MORPHEUS.
- [ADR-0002](ARCH-0002-openspec-reference-provider.md) — OpenSpec, provider de référence.
- [ADR-0011](BE-0011-provider-capability-negotiation.md) — négociation des capacités des providers.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture provider unifié.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — mutations contrôlées du cycle de vie.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md) — preuve du jalon M0.

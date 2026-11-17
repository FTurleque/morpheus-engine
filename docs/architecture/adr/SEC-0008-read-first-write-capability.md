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

1. **Read-first** : les capacités de lecture et de découverte forment le socle — `DISCOVER_PROJECT`,
   `READ_CURRENT_SPECIFICATIONS`, `READ_CHANGES`, `READ_REQUIREMENTS`, `READ_CONSTRAINTS`, `READ_SCENARIOS`,
   `READ_DESIGN_DECISIONS`, `READ_ACCEPTANCE_CRITERIA`, `READ_IMPLEMENTATION_TASKS`, `READ_HISTORY`, `READ_ARCHIVES`.
   Les capacités de mutation sont séparées : `WRITE_CHANGE`, `WRITE_TASK_STATE`, `ARCHIVE_CHANGE`. Toutes sont des
   valeurs de `com.morpheus.domain.provider.ProviderCapability` ; leur négociation relève d'ADR-0011.
2. Un provider valide peut être entièrement read-only et satisfait alors tous les cas d'usage de compréhension
   (découverte, ingestion, requêtes, traçabilité, courant / proposé, historique).
3. **L'écriture n'est jamais obligatoire** (forme de son API non fixée ici). Aucun service de lecture ne dépend d'une
   capacité d'écriture ; une écriture expérimentale reste isolée.
4. Une lecture ne modifie jamais la source. `READ_CHANGES` n'implique jamais `WRITE_CHANGE`,
   `READ_IMPLEMENTATION_TASKS` jamais `WRITE_TASK_STATE`, `READ_ARCHIVES` jamais `ARCHIVE_CHANGE`.
5. L'autorisation de lire n'implique jamais celle d'écrire. Toute mutation requiert une intention explicite (utilisateur
   ou orchestrateur autorisé), des permissions et une capacité provider explicite ; son succès est confirmable par la
   source ou une preuve adaptée.
6. **Garde-fous** de toute écriture : précondition sur la version source, détection de conflit, validation MORPHEUS
   préalable, atomicité si possible, diff ou preview observable, rollback (Git ou provider) si disponible,
   journalisation sans secret, resynchronisation après succès, confirmation de l'état réellement observé.
7. Toute activation fonctionnelle de l'écriture a son ADR (permissions, concurrence, conflits, confirmation,
   rollback) : ADR-0083 pour les mutations contrôlées du cycle de vie.

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

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine propre à MORPHEUS.
- [ADR-0169](ARCH-0169-openspec-provider-de-reference-et-schemas-qui-derivent-de-spec-driven.md) — OpenSpec, provider de référence.
- [ADR-0011](BE-0011-provider-capability-negotiation.md) — négociation des capacités des providers.
- [ADR-0028](BE-0028-unified-provider-read-contract.md) — contrat de lecture provider unifié.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — mutations contrôlées du cycle de vie.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md) — preuve du jalon M0.

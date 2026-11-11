---
id: BE-0083
title: "Mutations lifecycle contrôlées, CAS, idempotency et audit"
domain: backend
rules: false
---

# Mutations lifecycle contrôlées, CAS, idempotency et audit

- Statut : **Acceptée**
- Date : 2026-07-26

## Context

MORPHEUS produit des décisions de transition lifecycle read-only et explicables, qui ne mutent rien. Il faut un chemin
d'écriture qui ne transforme jamais une décision `ALLOWED` en effet implicite. Les snapshots publiés restent
immuables : l'état lifecycle mutable est un état opérationnel séparé.

Options rejetées :

| Option | Raison du rejet |
|---|---|
| Muter depuis `transition-check` | Confond décision et effet |
| Stocker le lifecycle mutable dans le snapshot | Casserait l'immuabilité des snapshots publiés |
| Last-write-wins | Écrasement concurrent implicite |
| Capacité d'écriture déduite de la lecture | `read capability != write capability` |
| Réessayer sans idempotency | Risque de double mutation et de double audit |

## Decision

1. **Évaluation et mutation sont séparées.**

   ```text
   evaluate transition -> read-only decision
   apply transition    -> explicit mutation command
   ```

   `ALLOWED != applied` est un invariant structurel.

2. **État lifecycle opérationnel versionné** (`ChangeLifecycleOperationalState`) : projet, changement,
   `ChangeLifecycle`, révision, `updatedAt`, `lastMutationId`. La révision est monotone, vaut au moins 1 pour un état
   persisté (la révision 0 est l'état initial virtuel, sans métadonnée de mutation) et sert au compare-and-set.

3. **Commande explicite** (`ChangeLifecycleMutationCommand`) : `mutationId`, `idempotencyKey`, `projectId`,
   `changeId`, `expectedRevision`, `targetState`, `targetAbandonmentReason` optionnelle, confirmation, `actor`,
   `requestedAt`. MORPHEUS n'invente aucune de ces valeurs.

4. **Contrôle de concurrence.** Une mutation n'est appliquée que si `expectedRevision` correspond à l'état courant ;
   sinon le résultat est `CONFLICT`. Aucun last-write-wins silencieux.

5. **Idempotency.** Une `idempotencyKey` déjà appliquée avec la même empreinte logique de commande retourne
   `ALREADY_APPLIED` et le résultat original, sans nouvel audit ni nouvelle révision. La même clé avec une commande
   différente est un `CONFLICT` déterministe.

6. **Capacité d'écriture explicite.** Le chemin d'écriture exige une capacité `WRITE_CHANGE` réellement observée pour
   le provider du projet ; son absence produit `NOT_AUTHORIZED`. `READ_CHANGES != WRITE_CHANGE`. OpenSpec reste
   read-only tant que son adaptateur ne déclare pas explicitement cette capacité ; le provider synthétique sert de
   preuve positive contrôlée.

7. **Confirmation.** Quand la policy de mutation l'impose (`ChangeLifecycleMutationPolicy`), l'absence de
   confirmation produit `REQUIRES_CONFIRMATION`, sans écriture ni audit d'application.

8. **Validation métier avant écriture.** Le service réutilise l'évaluation lifecycle et contraintes :

   ```text
   ALLOWED         -> éligible à mutation
   BLOCKED         -> REJECTED
   UNKNOWN         -> REJECTED
   REQUIRES_INPUT  -> REJECTED
   ```

   Une commande explicite reste nécessaire même lorsque la décision est `ALLOWED`.

9. **Audit append-only.** Chaque mutation réellement appliquée produit exactement un enregistrement
   (`ChangeLifecycleMutationAuditRecord`) : `mutationId`, `idempotencyKey`, projet, changement, `fromState`,
   `targetState`, `fromRevision`, `toRevision`, `actor`, `providerId`, `reason`, `appliedAt`. L'audit survit au
   redémarrage SQLite. Une cible `ABANDONED` exige une `ChangeAbandonmentReason` explicite, dans la tentative
   persistée et dans l'audit ; une raison d'abandon n'est admise que pour cette cible.

10. **Surfaces séparées.** Les surfaces de mutation sont distinctes des surfaces d'évaluation ; un outil ou un handler
    read-only reste read-only. Les surfaces d'écriture sont déclarées `WRITE` dans `contracts/public-surfaces.tsv`
    (ligne `lifecycle.apply`).

## Do's and Don'ts

### Do

- Exiger `expectedRevision`, `idempotencyKey` et une confirmation explicite sur toute commande de mutation lifecycle.
- Vérifier la capacité `WRITE_CHANGE` observée du provider avant toute écriture.
- Réévaluer la transition avant d'écrire, et ne muter que sur `ALLOWED`.
- Écrire exactement un enregistrement d'audit par mutation appliquée.
- Garder l'état lifecycle mutable hors du snapshot publié.

### Don't

- Ne pas muter depuis une évaluation ou un `transition-check`.
- Ne pas déduire `WRITE_CHANGE` de `READ_CHANGES`.
- Ne pas convertir `UNKNOWN` ou `REQUIRES_INPUT` en mutation éligible.
- Ne pas écraser silencieusement une révision concurrente.
- Ne pas réutiliser une `idempotencyKey` pour une commande différente.
- Ne pas ajouter d'écriture à une surface déclarée `READ`.

## Consequences

### Positives

- Décision et effet ne se confondent jamais.
- Les écritures concurrentes et les rejeux sont sûrs et explicites.
- Chaque mutation laisse une trace durable et unique.

### Négatives

- Commande plus lourde à construire pour le client (révision, clé, confirmation).
- Un état opérationnel et un audit supplémentaires à persister, avec parité mémoire/SQLite.

### Risques

- Un provider qui déclarerait `WRITE_CHANGE` à tort ouvrirait l'écriture : la capacité est observée, pas supposée, et
  OpenSpec reste read-only tant que son adaptateur ne la déclare pas.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ControlledLifecycleMutationContractTest.java` :
  une évaluation `ALLOWED` seule ne mute pas, idempotency, gardes indépendantes (capacité, confirmation, révision
  périmée), clé réutilisée pour une autre commande, persistance SQLite de l'état et de l'audit entre connexions, refus
  d'OpenSpec read-only.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ControlledLifecycleMutationRejectionContractTest.java`
  : transition bloquée rejetée sans état ni audit, parité mémoire/SQLite sur un projet non enregistré.
- `contracts/public-surfaces.tsv` (ligne `lifecycle.apply`, intention `WRITE`) et les gates de convergence des
  surfaces publiques.

## References

- [ADR-0008](SEC-0008-read-first-write-capability.md) — providers read-first, écriture optionnelle.
- [ADR-0011](BE-0011-provider-capability-negotiation.md) — sélection par capacités.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie et transition tri-état.
- [ADR-0143](DATA-0143-snapshots-versionnes-et-concurrence-bornee.md) — snapshots versionnés immuables.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — qualité et faits tri-état.
- [ADR-0139](ARCH-0139-frontiere-morpheus-jarvis-en-lecture-seule.md) — frontière d'orchestration read-only.
- [ADR-0082](DATA-0082-explicit-constraint-semantics-and-blocking-policy.md) — sémantique des contraintes.
- [VALIDATION_M17](../../project/validation/VALIDATION_M17.md).

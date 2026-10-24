---
id: DATA-0086
title: "Recovery failure-atomic des snapshots et concurrence SQLite bornée"
domain: data
rules: false
---

# Recovery failure-atomic des snapshots et concurrence SQLite bornée

- Statut : **Acceptée**
- Date : 2026-07-26

## Context

Les snapshots MORPHEUS sont construits avant activation et l'activation SQLite est transactionnelle. Les garanties
doivent être explicites en cas d'interruption, de candidat invalide, de concurrence entre commandes, de lecteurs
simultanés et de base verrouillée.

## Decision

1. Un état publié est observable uniquement via un snapshot `ACTIVE` complet.
2. Un candidat `BUILDING` ou `VALIDATING` laissé par une interruption peut être récupéré vers `FAILED` uniquement s'il
   est plus ancien qu'un cutoff explicite.
3. Le cutoff par défaut est de dix minutes (`SnapshotRecoveryPolicy`, `DEFAULT_STALE_AFTER`), afin qu'une recovery ne
   tue pas un candidat fraîchement détenu par une autre commande locale.
4. `READY`, `ACTIVE`, `RETIRED` et `FAILED` ne sont jamais modifiés par la recovery stale.
5. `SpecificationKnowledgeStore` expose un listing déterministe des snapshots par projet (`listSnapshots`) pour la
   recovery et les diagnostics.
6. L'activation SQLite est atomique : l'ancien `ACTIVE` est retiré et le nouveau activé dans la même transaction.
7. Deux commandes concurrentes ciblant le même prédécesseur ne peuvent pas publier deux successeurs ; une seule gagne,
   l'autre échoue explicitement.
8. Les lecteurs concurrents observent l'ancien ou le nouveau `ACTIVE`, jamais une absence transitoire produite par
   l'activation.
9. Le verrou SQLite utilise un timeout borné. Le défaut de production est de 5 000 ms
   (`DEFAULT_BUSY_TIMEOUT_MILLIS`) ; les tests peuvent utiliser une borne plus courte sans changer le contrat de
   production.
10. Après libération d'un verrou, le même store reprend les opérations normales.

### Invariants

```text
failed candidate != ACTIVE
interrupted BUILDING/VALIDATING -> FAILED only after explicit stale cutoff
previous valid ACTIVE remains authoritative after failed publication
published history = RETIRED* -> ACTIVE
concurrent successor activation -> one winner + explicit loser failure
concurrent readers -> previous ACTIVE or next ACTIVE, never partial
SQLite locked != indefinite hang
stale recovery != overwrite of fresh concurrent work
```

## Do's and Don'ts

### Do

- Ne rendre observable qu'un snapshot `ACTIVE` complet.
- Récupérer vers `FAILED` un candidat `BUILDING` ou `VALIDATING` seulement au-delà du cutoff explicite.
- Retirer l'ancien `ACTIVE` et activer le nouveau dans la même transaction.
- Faire échouer explicitement la commande perdante d'une activation concurrente.
- Borner l'attente d'un verrou SQLite.
- Porter le même invariant dans `store-memory` et `store-sqlite`.

### Don't

- Ne pas laisser un candidat échoué devenir `ACTIVE`.
- Ne pas modifier un snapshot `READY`, `ACTIVE`, `RETIRED` ou `FAILED` lors d'une recovery stale.
- Ne pas exposer d'instant sans `ACTIVE` pendant une activation.
- Ne pas publier deux successeurs d'un même prédécesseur.
- Ne pas attendre un verrou SQLite sans borne.

## Consequences

### Positives

- L'état publié reste failure-atomic.
- Les interruptions sont diagnostiquables et récupérables.
- La contention a une durée bornée.
- Les tests Memory et SQLite portent le même invariant métier.

### Négatives

- La détection stale repose sur une politique temporelle explicite.
- Un candidat récent réellement abandonné n'est pas récupéré avant expiration du cutoff.

### Risques

- Le verrou multi-processus reste soumis aux primitives SQLite et système de fichiers de la plateforme.
- Plusieurs stores SQLite écrivent la valeur du timeout en littéral au lieu de lire la constante : une modification du
  défaut doit les reprendre tous.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/SnapshotRecoveryContractTest.java` :
  recovery Memory et SQLite des seuls candidats stale, `ACTIVE` préservé à la réouverture.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/RuntimeSnapshotRecoveryContractTest.java` :
  recovery multi-projets en ordre déterministe.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/FailedPublishRecoveryContractTest.java` :
  un candidat invalide ne fuit jamais dans `ACTIVE`, une reconstruction valide publie ensuite.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteConcurrencyHardeningTest.java` : base
  verrouillée en échec dans la borne puis reprise, activation concurrente à gagnant unique.
- `morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteConcurrentReaderContractTest.java` : lecteurs
  qui voient l'ancien ou le nouveau `ACTIVE`, jamais un état vide ou partiel.

## References

- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite comme store persistant.
- [ADR-0120](DATA-0120-snapshots-versionnes-et-activation-atomique.md) — snapshots versionnés et activation atomique.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie gardée avant migration du schéma.
- [ADR-0114](BE-0114-database-work-runs-on-a-bounded-pool-of-platform-threads.md) — pool borné pour le travail en
  base.
- [VALIDATION_M19](../../project/validation/VALIDATION_M19.md)

# ADR-0086 — Recovery failure-atomic des snapshots et concurrence SQLite bornée

Statut : **Proposée — M19**

Date : 26 juillet 2026

## Contexte

Les snapshots MORPHEUS sont construits avant activation et l'activation SQLite est transactionnelle. M19 doit rendre explicites les garanties en cas d'interruption, de candidat invalide, de concurrence entre commandes, de lecteurs simultanés et de base verrouillée.

## Décision proposée

1. Un état publié est observable uniquement via un snapshot `ACTIVE` complet.
2. Un candidat `BUILDING` ou `VALIDATING` laissé par une interruption peut être récupéré vers `FAILED` uniquement s'il est plus ancien qu'un cutoff explicite.
3. Le cutoff par défaut est de dix minutes afin qu'un processus de recovery ne tue pas un candidat fraîchement détenu par une autre commande locale.
4. `READY`, `ACTIVE`, `RETIRED` et `FAILED` ne sont jamais modifiés par le recovery stale.
5. `SpecificationKnowledgeStore` expose un listing déterministe des snapshots par projet pour permettre recovery et diagnostics.
6. L'activation SQLite reste atomique : l'ancien `ACTIVE` est retiré et le nouveau activé dans la même transaction.
7. Deux commandes concurrentes ciblant le même prédécesseur ne peuvent pas publier deux successeurs ; une seule gagne, l'autre échoue explicitement.
8. Les lecteurs concurrents peuvent observer l'ancien ou le nouveau `ACTIVE`, jamais une absence transitoire produite par l'activation.
9. Le lock SQLite utilise un timeout borné. Le défaut de production reste 5 000 ms ; les tests peuvent utiliser une borne plus courte sans changer le contrat de production.
10. Après libération d'un lock, le même store doit pouvoir reprendre les opérations normales.

## Invariants

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

## Conséquences

### Positives

- l'état publié reste failure-atomic ;
- les interruptions deviennent diagnostiquables et récupérables ;
- la contention a une durée bornée ;
- les tests Memory et SQLite portent le même invariant métier.

### Contraintes

- la détection stale repose sur une politique temporelle explicite ;
- un candidat récent réellement abandonné n'est pas récupéré avant expiration du cutoff ;
- le lock multi-processus reste soumis aux primitives SQLite/filesystem de la plateforme.

## Preuve requise avant acceptation

ADR-0086 ne pourra devenir **Acceptée — M19** qu'après preuve sur le SHA final de :

- recovery Memory + SQLite + reopen ;
- failed candidate puis rebuild valide ;
- lecteurs concurrents ;
- commandes concurrentes ;
- lock SQLite borné et reprise ;
- migration compatible ;
- gate complet M19.
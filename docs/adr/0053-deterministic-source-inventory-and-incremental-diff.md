# ADR-0053 — Inventaire de sources déterministe et diff incrémental conservateur

- Statut : **Proposée — M7**
- Date : 23 juillet 2026
- Dépend de : ADR-0012, ADR-0033, ADR-0036
- Portée : M7, inventaire, fingerprint, source revision, diff, moves/renames

## Décision candidate

MORPHEUS représente l'état observable des sources locales par un inventaire snapshot-scoped de chemins relatifs normalisés et fingerprints SHA-256.

```text
SourceFingerprint = SHA-256(bytes)
SourceInventory = project + capturedAt + optional sourceRevision + sorted entries
SourceChangeKind = ADDED / MODIFIED / DELETED / MOVED / UNCHANGED
```

`sourceRevision` reste opaque : MORPHEUS compare l'égalité mais n'invente aucun ordre entre deux revisions provider/VCS.

Un move/rename n'est reconnu que lorsqu'un fingerprint supprimé correspond à exactement un fingerprint ajouté. Toute correspondance ambiguë rend l'incrémental non démontrable et impose `FULL_REBUILD`.

Invariant : **la fiabilité prime ; en cas de doute, full rebuild.**

## Frontières

- aucun fingerprint basé uniquement sur mtime/size ;
- aucun fuzzy rename ;
- aucune heuristique textuelle ;
- aucun ordre supposé sur les source revisions ;
- ordre des résultats stable par chemin canonique.

## Acceptation

À compléter après le gate local final M7.

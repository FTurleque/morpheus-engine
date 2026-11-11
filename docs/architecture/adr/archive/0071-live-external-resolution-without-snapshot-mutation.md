# ADR-0071 — Résolution externe live sans mutation d'un snapshot publié

> **Archivée.** Cette décision est consolidée dans [ADR-0142](../DATA-0142-references-externes.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0142**
- Date : 2026-07-24

## Context

`ExternalReferenceStore` est snapshot-scoped. ADR-0041 impose l'immutabilité logique d'une même référence dans un
snapshot : une valeur différente sous le même `ExternalReferenceId` est une collision. Un `TraceabilityLink` conserve
aussi sa résolution au moment de l'observation. Une cible externe (un symbole MINOS, par exemple) peut pourtant
évoluer, et l'utilisateur veut en connaître l'état présent.

## Decision

La référence persistée dans un snapshot et l'observation live de sa cible sont deux choses distinctes :

```text
référence persistée dans le snapshot  !=  observation live de sa cible
```

Une résolution live (`ExternalReferenceResolutionService.resolve`) produit une copie transitionnée de la référence et
une vue de résultat, mais n'écrit jamais dans le snapshot publié : elle n'appelle pas `putReference`.

```text
ACTIVE snapshot reference
       -> ExternalReferenceResolutionService.resolve(...)
       -> observed ExternalReference
       -> response
       -X-> mutation ACTIVE
```

Le résultat live est visible sans être persisté. Une cible indisponible (aucun résolveur, ou pair externe absent)
produit une observation non résolue, avec le même comportement sur la référence publiée. Seule une publication
ultérieure peut persister un nouvel état de référence, dans un nouveau snapshot.

## Do's and Don'ts

### Do

- Retourner une copie transitionnée de la référence pour toute résolution live.
- Persister un nouvel état de référence uniquement par une nouvelle publication de snapshot.
- Garder un comportement identique sur le store mémoire et sur SQLite.

### Don't

- Ne pas appeler `putReference` depuis une résolution live.
- Ne pas modifier le `TraceabilityResolutionState` d'une arête existante à la suite d'une résolution live.
- Ne pas faire dépendre la lecture d'une référence publiée de la disponibilité de sa cible.

## Consequences

### Positives

- L'audit historique reste fidèle à l'observation publiée.
- Une cible externe peut évoluer sans réécrire le passé MORPHEUS.
- Les arêtes existantes ne changent pas rétroactivement de `TraceabilityResolutionState`.

### Négatives

- L'état live et l'état publié peuvent diverger ; le lecteur doit savoir lequel il consulte.

### Risques

- Un appelant qui persisterait l'observation live dans le snapshot actif casserait l'immutabilité : le service de
  résolution ne détient aucun store.

## Compliance and Enforcement

- Règle Archgate `DATA-0071-live-external-resolution-without-snapshot-mutation.rules.ts` : `live-resolution-writes-no-reference`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LiveExternalReferenceResolutionContractTest.java`
  : sur mémoire et sur SQLite, la résolution live retourne une copie observée et laisse intacte la référence active, y
  compris après réouverture de la base.
- Revue : `ExternalReferenceResolutionService` ne dépend d'aucun port de stockage.

## References

- [ADR-0026](0026-optional-external-reference-resolution.md) — résolution externe optionnelle.
- [ADR-0041](0041-snapshot-external-reference-traceability.md) — références externes snapshot-scoped.
- [ADR-0120](0120-snapshots-versionnes-et-activation-atomique.md) — snapshots versionnés et activation atomique.
- [ADR-0128](../ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS.
- [VALIDATION_M12](../../../project/validation/VALIDATION_M12.md).

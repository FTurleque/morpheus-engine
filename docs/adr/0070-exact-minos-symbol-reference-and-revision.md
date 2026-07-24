# ADR-0070 — Référence MINOS par `symbolKey` exact et révision explicite

- Statut : **Acceptée — M12**
- Date : 24 juillet 2026
- Dépend de : ADR-0009, ADR-0026, ADR-0041, ADR-0069
- Portée : M12 — identité externe MINOS

## Décision

Une cible code MINOS M12 utilise :

```text
system       = MINOS
resourceType = SYMBOL
project      = identifiant ou nom unique MINOS
externalId   = symbolKey MINOS exact
revision     = activeSnapshotId MINOS attendu, optionnel
```

`project` et `externalId` sont obligatoires.

## Résolution

Le resolver peut utiliser la recherche lexicale MINOS comme mécanisme de récupération, mais la promotion en `FOUND` est exclusivement :

```text
result.symbolKey.equals(target.externalId)
```

Puis :

```text
0 exact  -> NOT_FOUND
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
```

Aucun nom simple, `qualifiedName`, fichier, module ou score lexical ne constitue seul une identité suffisante.

## Révision

Si `target.revision` est présente :

```text
minos_index_status.activeSnapshotId == target.revision
```

est obligatoire avant la résolution. Sinon : `REVISION_MISMATCH`.

## Critères d'acceptation

1. exact `symbolKey` unique résolu ;
2. match lexical non exact rejeté ;
3. plusieurs exacts explicitement ambigus ;
4. project absent/rejeté explicite ;
5. révision identique acceptée ;
6. révision différente jamais masquée ;
7. cible résolue conserve snapshot/provenance MINOS ;
8. aucun fuzzy matching.

## Preuve M12

```text
MinosMcpExternalReferenceResolverTest  4/4 PASS
MINOS Integration                      8/8 PASS
Maven total                          331/331 PASS
```

Voir `docs/VALIDATION_M12.md`.

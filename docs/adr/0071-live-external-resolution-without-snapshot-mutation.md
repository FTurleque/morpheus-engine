# ADR-0071 — Résolution externe live sans mutation d'un snapshot publié

- Statut : **Proposée — M12 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0026, ADR-0033, ADR-0041
- Portée : M12 — observation live d'une référence externe

## Contexte

`ExternalReferenceStore` est snapshot-scoped. ADR-0041 impose l'immutabilité logique d'une même référence dans un snapshot : une valeur différente sous le même `ExternalReferenceId` est une collision. Un `TraceabilityLink` conserve aussi sa résolution au moment de l'observation.

## Décision proposée

M12 sépare :

```text
référence persistée dans le snapshot
!=
observation live de sa cible MINOS
```

Une résolution live produit une copie transitionnée et une vue de résultat, mais n'appelle jamais `putReference` sur le snapshot publié.

```text
ACTIVE snapshot reference
       -> ExternalReferenceResolutionService.resolve(...)
       -> observed ExternalReference
       -> response
       -X-> mutation ACTIVE
```

## Conséquences

- l'audit historique reste fidèle à l'observation publiée ;
- une cible MINOS peut évoluer sans réécrire le passé MORPHEUS ;
- une publication future peut persister un nouvel état de référence dans un nouveau snapshot ;
- les arêtes existantes ne changent pas rétroactivement de `TraceabilityResolutionState`.

## Critères d'acceptation

1. résolution live retourne une observation transitionnée ;
2. référence persistée reste byte/logiquement identique ;
3. reopen SQLite retrouve la référence source inchangée ;
4. historique du résultat live est visible sans être persisté ;
5. aucun `TraceabilityLink` existant muté ;
6. comportement identique avec MINOS indisponible ;
7. tests contractuels Memory/SQLite.

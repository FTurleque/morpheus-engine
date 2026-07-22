# E06b — Traceability through SpecificationKnowledgeStore implementations

Statut : **PASS**

Date : 22 juillet 2026

## Pourquoi cette preuve complémentaire

E06 valide le modèle de graphe de traçabilité en mémoire.

Les critères d'acceptation ADR-0003 / ADR-0005 / ADR-0010 demandent aussi de démontrer que la même sémantique reste disponible derrière les implémentations du `SpecificationKnowledgeStore`.

## Stores exercés

```text
InMemorySpecificationKnowledgeStore
SQLiteSpecificationKnowledgeStore
```

Les deux stores exposent maintenant :

```text
trace(start, max_depth, bidirectional)
```

sur le snapshot actif uniquement.

## Test contractuel

```text
experiments/m0/spikes/e06b_store_traceability_python/test_store_traceability.py
```

Scénarios exercés sur **les deux stores** :

1. traversée directe ;
2. requête inverse sans duplication physique obligatoire ;
3. traversée profondeur 3 ;
4. lien `UNRESOLVED` vers une cible externe conservé.

Résultat :

```text
4 scénarios contractuels
memory  = PASS
SQLite  = PASS
```

## Persistance SQLite

Le candidat SQLite matérialise les relations dans :

```text
trace_links
```

avec index sur :

```text
(snapshot_id, source)
(snapshot_id, target)
```

Les relations sont attachées à un `KnowledgeSnapshot`, ce qui empêche de mélanger les liens appartenant à deux générations de connaissance différentes.

Le schéma reste expérimental et ne constitue pas encore le schéma de production.

## Invariants confirmés

- [x] même contrat de traversée sur deux backends ;
- [x] snapshot actif comme frontière de cohérence ;
- [x] direct/inverse ;
- [x] profondeur bornée ;
- [x] provenance/résolution disponibles ;
- [x] lien externe cassé conservé ;
- [x] aucun SQL/Cypher exposé au consommateur métier.

## Impact ADR

### ADR-0003

Le port `SpecificationKnowledgeStore` n'empêche pas une capacité de traversée utile et peut être implémenté par deux familles de store différentes.

### ADR-0005

`TraceabilityLink` reste exploitable après persistance.

### ADR-0010

La taxonomie contrôlée peut être stockée et interrogée sans dépendre d'un moteur graphe dédié.

## Décision

```text
E06b = PASS
STORE_BACKED_TRACEABILITY = RETAIN
BACKEND_QUERY_LANGUAGE_IN_DOMAIN = REJECT
```

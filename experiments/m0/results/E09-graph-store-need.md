# E09 — Optional graph store

Statut : **PASS — décision M0 : NOT_NEEDED_FOR_MVP**

Date : 22 juillet 2026

## Question

> Un graph store dédié améliore-t-il suffisamment les traversées de traçabilité MVP pour justifier immédiatement sa complexité opérationnelle ?

## Contexte

E06 a validé le modèle de traçabilité et les traversées bornées en mémoire.

E08 a démontré qu'un backend SQLite embarqué peut fournir une persistance locale et des snapshots cohérents.

E09 ne cherche donc pas à prouver qu'une graph database n'aura **jamais** de valeur. Il vérifie si le MVP dispose déjà d'une solution suffisamment simple pour les traversées attendues.

## Spike

```text
experiments/m0/spikes/e09_graph_store_need_python/benchmark_graph.py
```

## Dataset synthétique

```text
nodes   = 20 000
edges   = 59 994
fanout  = 3
queries = 200
depth   = 3
seed    = 42
```

Relation utilisée :

```text
DEPENDS_ON
```

Le graphe est volontairement synthétique. Il sert à mesurer l'ordre de grandeur d'une traversée bornée, pas à reproduire toute la topologie d'une codebase réelle.

## Environnement

```text
Python 3.13.5
sqlite3 standard library
Linux container
```

## Résultats observés

```text
memory traversal p50 : ~0.0029 ms
memory traversal max : ~0.0317 ms

SQLite edge load     : ~53.9 ms
SQLite traversal p50 : ~0.0285 ms
SQLite traversal p95 : ~0.0380 ms
SQLite traversal max : ~0.1918 ms
SQLite database size : ~2 699 264 bytes
```

La requête SQLite utilise un `WITH RECURSIVE` borné à profondeur 3 avec index sur `source`.

## Interprétation

Sur ce corpus et cette profondeur :

- le graphe mémoire est évidemment très rapide ;
- SQLite exécute également les traversées avec une latence très faible ;
- aucune infrastructure externe n'est nécessaire ;
- aucune limite du modèle relationnel embarqué n'est mise en évidence pour le MVP.

Le résultat ne justifie donc pas l'ajout immédiat de :

- FalkorDB ;
- Neo4j ;
- autre serveur de graphe dédié.

## Limites

Cette conclusion doit être relue si l'un des besoins suivants apparaît :

- plusieurs millions d'arêtes ;
- profondeur transitive importante ;
- algorithmes de graphe avancés ;
- communautés / centralité ;
- requêtes multi-hop complexes fréquentes ;
- concurrence élevée ;
- latences SQLite mesurées incompatibles avec les objectifs réels ;
- besoin de Cypher ou d'un moteur analytique de graphe spécialisé.

Le dataset synthétique est peu dense et ne couvre pas toutes les distributions de graphe possibles.

## Impact architecture

La décision M0 proposée est :

```text
Graph model conceptuel : OUI
Graph database obligatoire : NON
Graph store dédié MVP : NOT_NEEDED
```

MORPHEUS peut conserver un modèle de relations naturellement graphique sans imposer un moteur graphe comme infrastructure.

## Impact ADR-0003

Le résultat renforce l'intérêt de garder :

```text
SpecificationKnowledgeStore
```

comme frontière, plutôt que d'exposer un langage ou un produit graphe dans le domaine.

## Décision

```text
E09 = PASS
GRAPH_STORE_FOR_MVP = NOT_NEEDED
REVISIT_ON_MEASURED_NEED = YES
```

Un graph store pourra être réévalué plus tard uniquement sur preuve de besoin, pas par préférence architecturale.

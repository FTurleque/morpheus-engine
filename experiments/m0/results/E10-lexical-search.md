# E10 — Lexical search

Statut : **PASS**

Date : 22 juillet 2026

## Objectif

Valider une recherche déterministe minimale sur requirements et changes sans LLM ni embeddings.

## Spike

```text
experiments/m0/spikes/e10_lexical_search_python/
```

## Résultat

```text
Ran 7 tests
7 PASS
0 FAIL
```

## Capacités exercées

- recherche par clé exacte ;
- recherche par titre ;
- recherche dans le statement ;
- filtre par type (`Requirement`, `ChangeProposal`) ;
- filtre par `TemporalState` ;
- limite de résultats ;
- ordre déterministe ;
- requête vide sans résultat.

Ordre de priorité expérimental :

```text
exact key
exact title
key prefix
title prefix
key contains
title contains
statement contains
```

Puis départage stable par type, état, clé et titre.

## Invariant important

Une recherche sur `30 minutes` peut retourner simultanément un fait `CURRENT` et un delta `PROPOSED`, mais chaque résultat conserve son état temporel. La recherche ne fusionne pas les deux vérités.

## Limites

Ce spike n'introduit :

- ni fuzzy search ;
- ni stemming ;
- ni full-text index persistant ;
- ni embeddings ;
- ni ranking NEXUS.

Ces besoins ne sont pas nécessaires pour valider E10.

## Décision

```text
E10 = PASS
DETERMINISTIC_LEXICAL_SEARCH = RETAIN
SEMANTIC_SEARCH_REQUIRED_FOR_MVP = NO
```

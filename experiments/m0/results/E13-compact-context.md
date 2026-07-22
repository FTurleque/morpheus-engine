# E13 — Compact context

Statut : **PASS**

Date : 22 juillet 2026

## Objectif

Construire une vue compacte et déterministe d'un changement pour les futurs consommateurs machine, sans absorber les responsabilités de ranking, budget de tokens ou compression globale de NEXUS.

## Spike

```text
experiments/m0/spikes/e13_compact_context_python/
├── context_builder.py
└── test_context_builder.py
```

## Résultat

```text
Ran 7 tests
7 PASS
0 FAIL
```

Le JSON compact du corpus `add-remember-me` reste sous **4 KiB** dans cette expérience (ordre de grandeur observé autour de quelques kilo-octets sur ce corpus minimal).

## Contrat exercé

```text
change
temporal_state
objective
requirements[]
constraints[]
decisions[]
acceptance_criteria[]
tasks[]
traceability[]
provenance[]
capability_gaps[]
```

## Invariants validés

### Intention et état préservés

La vue conserve :

```text
change = add-remember-me
temporal_state = PROPOSED
objective = intention du proposal
```

### Artefacts ciblés

Le contexte contient uniquement les éléments structurés nécessaires au changement au lieu de charger systématiquement les documents complets.

### Capability gap explicite

Le provider E01/E02 ne revendique pas :

```text
READ_ACCEPTANCE_CRITERIA
```

Le contexte retourne donc :

```text
acceptance_criteria = []
capability_gaps = [READ_ACCEPTANCE_CRITERIA]
```

Aucun `AcceptanceCriterion` n'est inventé à partir des scenarios.

### Traçabilité utile

Le changement produit des liens compacts :

```text
ChangeProposal AFFECTS Requirement
```

avec evidence/provenance issue des requirements du delta.

### Sérialisation déterministe

Deux sérialisations du même contexte produisent le même JSON compact.

## Frontière NEXUS

E13 ne réalise pas :

- ranking global ;
- sélection entre plusieurs moteurs ;
- budget de tokens ;
- compression sémantique ;
- fusion avec le code MINOS ;
- choix du meilleur contexte pour un agent.

Ces responsabilités restent à NEXUS.

MORPHEUS fournit seulement une **vue spécialisée, structurée et compacte de l'intention**.

## Décision

```text
E13 = PASS
COMPACT_SPEC_CONTEXT = RETAIN
GLOBAL_CONTEXT_RANKING_IN_MORPHEUS = REJECT
```

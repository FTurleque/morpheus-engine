# ADR-0075 — Séparer l'intention MORPHEUS du contexte technique NEXUS

- Statut : **Acceptée — M13**
- Date : 24 juillet 2026
- Dépend de : ADR-0001, ADR-0007, ADR-0047
- Portée : M13 — frontière sémantique du contexte augmenté

## Contexte

MORPHEUS possède les exigences, changements, contraintes, décisions et tâches. NEXUS possède la sélection, le ranking, la fusion et la compression du contexte technique.

Copier le ranking NEXUS dans MORPHEUS créerait deux sources de vérité et rendrait les résultats impossibles à attribuer.

## Décision

M13 sépare strictement :

```text
MORPHEUS intent context
!=
NEXUS technical ContextBundle
```

MORPHEUS compose uniquement une requête d'intention déterministe depuis un snapshot ACTIVE.

### Requirement

```text
key
title
statement
```

### Change

```text
key
title
intent
scope
affected requirements
constraints
design decisions
implementation tasks
```

Cette requête est transmise à NEXUS avec budget/sources/contraintes.

NEXUS retourne un `ContextBundle` déjà sélectionné. MORPHEUS le projette sans reranking, sans fusion supplémentaire et sans recalcul de budget.

## Temporalité

Le contexte NEXUS est une observation live :

```text
ACTIVE snapshot
 -> intent seed
 -> NEXUS context
 -> response
 -X-> KnowledgeSnapshot mutation
 -X-> persisted ContextBundle
```

La réponse expose `persisted=false`.

## Preuve M13

```text
MorpheusAugmentedContextApiContractTest  2/2 PASS
MorpheusM13McpStdioIntegrationTest       1/1 PASS
API                                      7/7 PASS
CLI                                    17/17 PASS
TOTAL                                 346/346 PASS
```

Les tests de contrat HTTP prouvent notamment que projet, budget, sources, contraintes et `explain` sont transmis au provider et que scores, raisons et exclusions du bundle externe sont conservés sans reranking MORPHEUS.

## Critères d'acceptation

1. seed Requirement déterministe ;
2. seed Change déterministe ;
3. budget NEXUS respecté sans second budget technique ;
4. scores/reasons NEXUS conservés ;
5. aucune mutation du snapshot ;
6. aucune persistance du bundle NEXUS ;
7. absence NEXUS représentée explicitement, sans inventer de contexte.

Tous les critères sont satisfaits par la validation M13.
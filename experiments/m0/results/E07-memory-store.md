# E07 — Memory store

Statut : **PASS**

Date : 22 juillet 2026

## Hypothèse

Un backend mémoire peut implémenter une première sémantique de `SpecificationKnowledgeStore` suffisante pour le vertical slice M0 sans dépendre du provider OpenSpec.

## Spike

```text
experiments/m0/spikes/e05_e07_memory_store_python/store.py
```

## Protocole

La suite E05/E07 contient 8 tests et passe intégralement.

Le test vertical slice charge le résultat normalisé du provider expérimental puis le publie comme snapshot actif dans le store mémoire.

## Requêtes exercées

```text
get_current_specification
find_requirements
get_change
compare snapshots
```

Sur la fixture OpenSpec principale :

```text
CURRENT requirements = 2
find_requirements("expiration") = 1
get_change("add-remember-me") != null
change.temporal_state = PROPOSED
```

Le store ne lit aucun fichier OpenSpec et ne dépend d'aucun concept propre au provider. Il reçoit uniquement le payload normalisé.

## Ce que E07 démontre

- [x] backend mémoire sans infrastructure externe ;
- [x] même payload normalisé que celui produit par E02 ;
- [x] requêtes sur snapshot actif ;
- [x] séparation store/provider ;
- [x] activation atomique observable ;
- [x] fonctionnement local et hors réseau ;
- [x] base suffisante pour les tests contractuels d'un backend persistant.

## Limites

Le port complet décrit dans `docs/contracts/SPECIFICATION_KNOWLEDGE_STORE.md` n'est pas encore implémenté.

Il manque notamment :

- traçabilité E06 ;
- recherche plus complète E10 ;
- historique avancé ;
- références externes E14 ;
- persistance E08 ;
- intégration des `DomainIdentity` E03 dans le store.

Ces absences n'empêchent pas E07 de valider le rôle du backend mémoire comme implémentation de référence pour les tests.

## Impact ADR-0003

**Preuve positive sur l'existence et la valeur du backend mémoire.**

ADR-0003 reste `Proposée` jusqu'à ce qu'un backend persistant candidat passe les mêmes tests contractuels et démontre que l'abstraction ne masque pas une capacité indispensable.

## Décision

```text
E07 = PASS
MEMORY_STORE = RETAIN_AS_TEST_REFERENCE
```

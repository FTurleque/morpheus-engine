---
id: GEN-0085
title: "Budgets de performance pré-déclarés et fixtures larges déterministes"
domain: general
rules: false
---

# Budgets de performance pré-déclarés et fixtures larges déterministes

- Statut : **Acceptée**
- Date : 2026-07-26

## Context

Une couverture fonctionnelle forte ne dit rien de la capacité. Définir les seuils après optimisation créerait un biais
de validation et ne permettrait pas de distinguer une régression d'une simple variation de machine. Un budget écrit
après la correction qu'il juge mesurerait la correction, pas le besoin.

## Decision

1. Les volumes et seuils sont versionnés avant toute optimisation dans
   `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`.
2. Les fixtures de volume sont générées de façon déterministe depuis un seed et un manifeste stable.
3. Les gates mesurent le même profil `M19-LARGE-GATE-1` sur Windows et sur Linux lorsqu'une preuve de plateforme
   existe.
4. Les latences sont mesurées après warmup, sur cinq itérations, au p95 nearest-rank.
5. Les budgets temporels, mémoire et taille SQLite ne sont bloquants que sur un environnement satisfaisant le minimum
   de référence documenté ; le validateur refuse une machine en dessous au lieu de rendre un verdict.
6. Les résultats par phase sont observables localement ; aucune télémétrie externe obligatoire n'est introduite.
7. Une hausse de seuil après observation d'un échec n'est pas une optimisation : c'est un changement de contrat qui
   exige une justification explicite et une nouvelle décision.

**Où les budgets s'exécutent.** Les classes de gate (`M19*PerformanceGate`) ne correspondent à aucun motif Surefire :
`clean verify` ne les exécute pas. Elles s'exécutent chaque nuit, sur Linux et sur Windows, par la lane
`M19 performance budgets` de `.github/workflows/nightly.yml`, qui lance `scripts/validate-m19.sh` et
`scripts/validate-m19.ps1` ; un budget manqué fait échouer la lane. Un déclencheur `schedule` exécute le workflow tel
qu'il existe sur la branche par défaut ; ailleurs, la lane se lance par `workflow_dispatch`. Cette lane est un
**signal de régression**, un job d'un workflow générique : elle n'est ni la preuve de qualification de M19, ni un
remplacement des validateurs locaux. Tout gate déclaré doit être atteignable par un validateur qu'un workflow lance.

**Ajouter un budget après le gel.** Un budget peut être ajouté quand un coût est trouvé sans budget :

1. il est daté, et déclaré **avant** que le code qu'il jugera ne soit modifié : la ligne précède son gate et sa
   correction dans l'historique ;
2. sa valeur vient de ce à quoi sert l'opération, pas d'une exécution ; la première mesure se fait sur le code non
   corrigé, un échec est la reproduction dont la correction part, un succès est consigné comme tel et la correction se
   justifie alors par sa preuve de comptage ;
3. il ne modifie aucune ligne existante : ni seuil, ni fixture, ni protocole, ni tas ; une mesure qui a besoin de
   lignes que la fixture du gel ne contient pas les publie dans une base à elle ;
4. un budget de la lane nocturne est une preuve tardive par construction : une correction qu'il juge porte aussi, dans
   la suite ordinaire, une preuve déterministe, un compte qui ne croît pas avec les données.

Les budgets ajoutés après le gel vivent dans la section 4 bis de `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`.

Invariants :

```text
budget defined before optimization
same seed -> same logical fixture
same baseline + same query -> same ordering
Windows proof != Linux proof
performance failure != permission to move threshold
```

## Do's and Don'ts

### Do

- Déclarer un budget, daté, avant de modifier le code qu'il juge.
- Générer les fixtures de volume depuis un seed et un manifeste stables.
- Mesurer après warmup, sur cinq itérations, au p95 nearest-rank.
- Accompagner une correction jugée par un budget nocturne d'une preuve déterministe dans la suite ordinaire.
- Faire nommer toute classe de gate hors sélection Surefire par un validateur lancé par un workflow.

### Don't

- Ne pas relever un seuil pour faire passer un budget manqué sans justification explicite et nouvelle décision.
- Ne pas modifier un seuil, une fixture, un protocole ou un tas existant en ajoutant un budget.
- Ne pas présenter le résultat d'une machine sous l'environnement de référence comme une preuve de conformité.
- Ne pas présenter la lane nocturne comme la preuve de qualification de M19.
- Ne pas introduire de télémétrie externe obligatoire.

## Consequences

### Positives

- Les optimisations sont évaluées contre une cible préexistante, et les régressions deviennent détectables.
- Windows et Linux utilisent la même fixture logique.
- Le coût de rétention et la croissance SQLite deviennent des faits mesurés.

### Négatives

- Les gates de performance sont plus longs que les gates ordinaires et ne tournent pas dans `clean verify`.
- Une régression de performance n'est signalée qu'au run nocturne suivant.

### Risques

- Une assertion trop proche du bruit d'ordonnancement rendrait un gate instable ; les tests de performance doivent
  l'éviter.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/` : `M19PerformanceGate`,
  `M19QueryPerformanceGate`, `M19CompositionPerformanceGate`, `M19TraceabilityPerformanceGate`,
  `M19FullPublishPerformanceGate` (budgets), `M19LargeFixtureContractTest` (fixture déterministe).
- `scripts/validate-m19.ps1` et `scripts/validate-m19.sh` : contrôle de l'environnement de référence, puis exécution
  des budgets.
- `.github/workflows/nightly.yml` : lane `M19 performance budgets`, Linux et Windows.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/GateReachabilityArchitectureTest.java` : refuse
  une classe de test hors sélection Surefire qu'aucun validateur lancé par un workflow ne nomme.
- Revue : qu'un budget ajouté précède sa correction dans l'historique, et qu'aucune ligne existante du document des
  budgets ne bouge.

## References

- [ADR-0089](GEN-0089-production-integrity-surface-convergence.md) — CI durable et non liée aux milestones.
- [Budgets M19](../../project/roadmap/M19_PERFORMANCE_BUDGETS.md) — budgets, fixture et protocole.
- [Validation M19](../../project/validation/VALIDATION_M19.md)

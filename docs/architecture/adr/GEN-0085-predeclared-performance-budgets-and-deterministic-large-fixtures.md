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

Une hausse de seuil après un échec n'est pas une optimisation. Un déclencheur `schedule` exécute le workflow nocturne
tel qu'il existe sur la branche par défaut ; ailleurs, la lane se lance par `workflow_dispatch`, job d'un workflow
générique. Un budget s'ajoute après le gel quand un coût est trouvé sans budget : sa ligne précède son gate et sa
correction dans l'historique, et sa valeur vient de ce à quoi sert l'opération. Une mesure qui a besoin de lignes que
la fixture du gel ne contient pas les publie dans une base à elle. Un budget de la lane nocturne est une preuve tardive
par construction : la preuve déterministe de la suite ordinaire est un compte qui ne croît pas avec les données.

Invariants :

```text
budget defined before optimization
same seed -> same logical fixture
same baseline + same query -> same ordering
Windows proof != Linux proof
performance failure != permission to move threshold
```

## Decision

1. Volumes et seuils sont versionnés avant toute optimisation dans `docs/project/roadmap/M19_PERFORMANCE_BUDGETS.md`.
2. Les fixtures de volume sont générées de façon déterministe depuis un seed et un manifeste stable.
3. Les gates mesurent le profil `M19-LARGE-GATE-1` sur Windows et sur Linux quand une preuve de plateforme existe.
4. Latences mesurées après warmup, sur cinq itérations, au p95 nearest-rank.
5. Les budgets de temps, de mémoire et de taille SQLite ne bloquent que sur l'environnement de référence minimal
   documenté ; en dessous, le validateur refuse au lieu de rendre un verdict.
6. Résultats par phase observables localement, sans télémétrie externe obligatoire.
7. Relever un seuil après un échec est un changement de contrat : justification explicite et nouvelle décision.
8. **Exécution** : les classes `M19*PerformanceGate` restent hors des motifs Surefire et de `clean verify` ; la lane
   `M19 performance budgets` de `.github/workflows/nightly.yml` les lance chaque nuit, Linux et Windows, par
   `scripts/validate-m19.sh` et `scripts/validate-m19.ps1`, et échoue sur un budget manqué. C'est un signal de
   régression, ni la preuve de qualification de M19 ni un remplacement des validateurs locaux. Tout gate déclaré est
   atteignable par un validateur qu'un workflow lance.
9. **Budget ajouté après le gel**, dans la section 4 bis du document des budgets : daté et déclaré avant toute
   modification du code qu'il juge ; valeur tirée de l'usage, pas d'une exécution ; première mesure sur le code non
   corrigé, un échec en étant la reproduction, un succès consigné, la correction justifiée alors par sa preuve de
   comptage ; aucune ligne existante modifiée (seuil, fixture, protocole, tas), une base à lui s'il faut d'autres
   lignes ; une correction qu'il juge porte aussi une preuve déterministe dans la suite ordinaire.

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

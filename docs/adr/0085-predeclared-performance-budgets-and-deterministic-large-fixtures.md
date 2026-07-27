# ADR-0085 — Budgets de performance pré-déclarés et fixtures larges déterministes

Statut : **Acceptée — M19**

Date : 26 juillet 2026

## Contexte

MORPHEUS entre en M19 avec une couverture fonctionnelle forte mais sans contrat de capacité explicite. Définir les seuils après optimisation créerait un biais de validation et ne permettrait pas de distinguer une régression d'une simple variation de machine.

## Décision

1. Les volumes et seuils M19 sont versionnés avant toute optimisation dans `docs/roadmap/M19_PERFORMANCE_BUDGETS.md`.
2. Les fixtures de volume sont générées déterministiquement depuis un seed et un manifeste stable.
3. Les gates mesurent le même profil `M19-LARGE-GATE-1` sur Windows et Linux lorsqu'une preuve de plateforme existe.
4. Les latences sont mesurées après warmup, sur cinq itérations, au p95 nearest-rank.
5. Les budgets temporels, mémoire et taille SQLite sont bloquants uniquement sur un environnement satisfaisant le minimum de référence documenté.
6. Les résultats par phase sont observables localement ; aucune télémétrie externe obligatoire n'est introduite.
7. Une hausse de seuil après observation d'un échec n'est pas une optimisation : c'est un changement de contrat qui exige justification explicite et nouvelle décision.

## Conséquences

### Positives

- les optimisations sont évaluées contre une cible préexistante ;
- les régressions deviennent détectables ;
- Windows et Linux utilisent la même fixture logique ;
- le coût de rétention et la croissance SQLite deviennent des faits mesurés.

### Contraintes

- le gate M19 devient plus long que les gates précédents ;
- les tests de performance doivent éviter les assertions trop proches du bruit de scheduling ;
- les résultats d'une machine sous-dimensionnée sont informatifs, pas présentés comme preuve de conformité.

## Invariants

```text
budget defined before optimization
same seed -> same logical fixture
same baseline + same query -> same ordering
Windows proof != Linux proof
performance failure != permission to move threshold
```

## Preuve d'acceptation

Le SHA de code `dca27db969b426ad43941ccb8cee7e926efb931b` a passé les validateurs locaux Windows et Linux avec la même fixture logique, les budgets inchangés et les métriques enregistrées dans `docs/validation/VALIDATION_M19.md`.

- générateur et manifeste de fixture testés ;
- harness de benchmark reproductible ;
- 449/449 tests et 178/178 tests d'architecture sur les deux plateformes ;
- tous les budgets temporels, mémoire et SQLite respectés.

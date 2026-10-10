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

## Amendement du 10 octobre 2026 — où les budgets s'exécutent

Constat de l'audit du 9 octobre 2026 (AUD-TST-01) : les cinq gates de performance portent des noms de classe qui ne
correspondent à aucun motif par défaut de Surefire, et aucun workflow ne lançait leur validateur. Les budgets, gelés et
respectés au SHA M19, n'étaient plus exécutés par aucun pipeline ; une régression de performance ne cassait aucun build.

La décision 5 dit **quand** les budgets sont bloquants (sur un environnement de référence). Elle ne disait pas **où** ils
sont exécutés. Précision :

1. Les budgets s'exécutent chaque nuit, sur Linux et sur Windows, par la lane `M19 performance budgets` de `nightly.yml`, qui
   lance `validate-m19.sh` et `validate-m19.ps1`. Un budget manqué fait échouer la lane. Le validateur refuse une machine
   sous l'environnement de référence au lieu de rendre un verdict.
2. Cette lane est un **signal de régression**, pas la preuve de qualification de M19. La phrase de `VALIDATION_M19.md` « GitHub
   Actions n'est pas une source de vérité M19 » vise la qualification du SHA M19 et reste vraie ; la tentative de workflow
   dédié à M19 qui y est mentionnée était un workflow par milestone, ce que l'ADR-0089 (§ 1, « CI durable et non liée aux
   milestones ») écarte. Ici c'est un job d'un workflow générique, qui n'est pas un remplacement des validateurs locaux.
3. La décision 7 est inchangée : un budget manqué n'autorise pas à relever le seuil. Les budgets et la fixture ne sont pas
   modifiés par cet amendement.
4. La règle générale « tout gate déclaré est atteignable » n'a pas d'ADR propre : elle est portée par un test,
   `GateReachabilityArchitectureTest` (ADR-0103 : le test dit quoi), qui refuse une classe de test hors sélection Surefire
   qu'aucun validateur lancé par un workflow ne nomme.

Limite : un déclencheur `schedule` exécute le fichier de workflow de la branche par défaut. La lane ne tourne donc seule
qu'une fois `nightly.yml` sur `main` ; avant cela, elle se lance par `workflow_dispatch`.

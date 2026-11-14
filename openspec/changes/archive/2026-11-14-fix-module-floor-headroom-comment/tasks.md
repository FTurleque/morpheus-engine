# Tâches : le commentaire de la borne du mou dit neuf dispersions

## 1. Commentaires des constantes de marge

- [x] 1.1 Corriger le commentaire de `MAXIMUM_HEADROOM_SPREADS` dans `CoverageQualificationVerifier` : « at most nine
  times » — exigence : aucune modifiée (`per-module-coverage-floor` documentée telle quelle) ; vérification : le
  commentaire et la constante disent neuf, et `git diff` ne touche aucune ligne de code.
- [x] 1.2 Écrire au-dessus de `HEADROOM_SPREADS` ce qu'elle borne (ratchet et seuil de module hors plancher D2, au moins
  trois dispersions, ADR-0167) et pourquoi — vérification : le commentaire nomme ses deux usages, relus dans la classe.

## 2. Vérification d'ensemble

- [x] 2.1 Lancer `openspec validate fix-module-floor-headroom-comment --strict` — vérification : valide, sans
  avertissement.
- [x] 2.2 Lancer `./mvnw -o test -pl morpheus-architecture-tests -Dtest=CoverageQualificationEvidenceTest` et les
  contrôles de cohérence (`-Dgroups=coherence`) — vérification : 0 échec.
- [x] 2.3 Lancer `archgate check` — vérification : aucune violation.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.

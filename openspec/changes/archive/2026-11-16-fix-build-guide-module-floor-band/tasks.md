# Tâches : le guide de build énonce toute la fenêtre d'un seuil de module

## 1. Paragraphe de la fenêtre

- [x] 1.1 Réécrire en place la phrase de la fenêtre dans `docs/developer/BUILD_AND_TEST.md` : au moins trois et au plus
  neuf dispersions retenues sous la plus basse mesure conservée, plancher D2 admis quand la bande ne contient aucun
  seuil plus haut, module absent de la preuve au plancher — exigence : aucune modifiée (`per-module-coverage-floor`
  documentée telle quelle) ; vérification : la section nomme « neuf » et relit comme l'exigence de la spécification.
- [x] 1.2 Corriger la définition de la dispersion du module : référence de l'échelle multipliée par la part du module
  dans la population de la grandeur, arrondie à l'unité supérieure — vérification : conforme au Javadoc et au calcul de
  la fenêtre dans `CoverageQualificationVerifier`.

## 2. Vérification d'ensemble

- [x] 2.1 Lancer `openspec validate fix-build-guide-module-floor-band --strict` — vérification : valide.
- [x] 2.2 Lancer `./mvnw -o test -pl morpheus-architecture-tests -Dgroups=coherence` — vérification : 0 échec.
- [x] 2.3 Lancer `archgate check` — vérification : aucune violation.

## Workflow follow-up

- Archiver le changement après la revue, sur demande explicite.

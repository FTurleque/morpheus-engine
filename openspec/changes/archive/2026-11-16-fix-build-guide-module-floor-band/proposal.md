# Proposition : le guide de build énonce toute la fenêtre d'un seuil de module

## Why

`docs/developer/BUILD_AND_TEST.md`, section « Le seuil propre à chaque module », décrit la règle que tient
`CoverageQualificationEvidenceTest#everyModuleFloorStaysInsideItsQualifiedWindow` par sa seule marge minimale : un seuil
refusé s'il ne garde pas trois fois la dispersion du module sous sa plus basse mesure conservée. La seconde moitié de
la règle n'y est pas : un seuil que la preuve conservée porte ne laisse **jamais plus de neuf** dispersions sous cette
mesure, et le plancher D2 n'est admis que quand aucun seuil plus haut ne tient dans la bande. La page conclut pourtant
« Poser ou relever un seuil de module est donc une décision dans cette fenêtre ». C'est le constat `AUD-TST-28` de
l'audit du 2026-10-09, ouvert le 14/11/2026 sur `cb63896a`.

La même phrase définit la dispersion du module par « la dispersion de référence de l'échelle ». Le vérificateur ne lit
pas la référence entière : il la multiplie par la part du module dans la population de la grandeur, arrondie à l'unité
supérieure (`GEN-0167`, Javadoc de la fenêtre dans `CoverageQualificationVerifier`). Écart trouvé en relisant la phrase
du constat, corrigé dans le même changement.

Rien n'est faux à l'exécution, et le refus du gate nomme le plus petit seuil admis. Mais cette page est celle qu'on lit
au moment de poser un seuil, et `rules/meta.md` la nomme parmi les destinations d'une hausse.

## What Changes

- La phrase de la fenêtre dans `BUILD_AND_TEST.md` énonce les deux bornes : au moins trois, au plus neuf dispersions
  retenues sous la plus basse mesure conservée, et le plancher D2 admis seulement quand la bande ne contient aucun seuil
  plus haut ; un module que la preuve ne porte pas reste au plancher.
- La définition de la dispersion du module dit la référence de l'échelle multipliée par la part du module, arrondie à
  l'unité supérieure.
- Aucun changement de code, de gate, de valeur ni de message de refus.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune : aucune exigence ne change. Le changement pose `skip_specs: true`.

## Périmètre et exclusions

**Inclus :** le paragraphe de la fenêtre d'un seuil de module dans `docs/developer/BUILD_AND_TEST.md`.

**Exclus :** un gate qui comparerait les bornes écrites en toutes lettres à `HEADROOM_SPREADS` et
`MAXIMUM_HEADROOM_SPREADS` ; l'action du constat ne le demande pas, et la règle s'énonce en prose sous des formes trop
variées pour un motif fiable. La fiche d'audit est mise à jour par son auteur, lors de la revérification suivante.

## Acteurs et surfaces concernés

Le mainteneur qui pose ou relève un seuil de module. Aucune surface publique.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le guide n'énonce que la marge minimale de trois dispersions | existant vérifié | `BUILD_AND_TEST.md`, section « Le seuil propre à chaque module » |
| La règle en vigueur est « au moins trois, au plus neuf dispersions », D2 admis quand la bande est vide | existant vérifié | `GEN-0167` ; `openspec/specs/per-module-coverage-floor/spec.md`, exigence de la fenêtre |
| La référence d'un module est celle de l'échelle multipliée par sa part, arrondie au-dessus | existant vérifié | `CoverageQualificationVerifier`, Javadoc et calcul de la fenêtre |
| Aucun comportement ne change | décision | seule une page de documentation est modifiée |

## Impact

- `docs/developer/BUILD_AND_TEST.md` (un paragraphe).
- Aucun ADR, aucun contrat, aucune spécification, aucun ratchet.

## Questions ouvertes

Aucune.

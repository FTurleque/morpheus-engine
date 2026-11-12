# Proposition : borner le mou sous chaque seuil de couverture d'un module

## Why

Le gate de l'échelle par module borne le seuil de chaque module **par le haut** : au plus la plus basse mesure
conservée du module moins trois fois sa dispersion retenue. Rien ne le borne par le bas, hors du plancher D2. Un seuil
posé très loin sous sa mesure passe donc tous les gates, et il se lit dans le fichier exactement comme un seuil juste :
le module peut perdre l'essentiel de sa couverture sans qu'aucun seuil ne morde.

Constat `AUD-TST-26` de l'audit du 2026-10-09 (sévérité Faible, revérifié le 12/11/2026 sur `12cd8874`, relevé
`docs/project/audits/2026-10-09/reverification-2026-11-12.md`) : `morpheus-integration-nexus.lineCoverageMinimum`
vaut 0,473 pour une borne haute de 0,873, et `morpheus-integration-minos.lineCoverageMinimum` vaut 0,560 pour 0,905,
soit 40,0 et 34,5 points de mou. L'audit demande de poser ces deux clés dans leur fenêtre, puis de fermer la porte en
refusant un seuil abandonné loin sous sa mesure.

Mesure du 12/11/2026 sur la preuve conservée (`config/coverage-qualification/per-module/`, quatre résumés de
`dbd65062`) : avec une borne basse à neuf dispersions, **vingt-trois** clés sur trente-deux sont hors de la bande, et
pas seulement les deux du constat. Les seuils posés le 07/11/2026 sur la dispersion de référence entière de l'échelle
(72 lignes, 33 branches) n'ont pas été relevés quand la marge proportionnelle d'ADR-0166 (archivé) est entrée le 11/11/2026, qui
n'a touché que les clés au plancher D2.

## What Changes

- `CoverageQualificationEvidenceTest` refuse le seuil d'un module que la preuve conservée porte quand il laisse sous
  la plus basse mesure de ce module plus de **neuf** fois sa dispersion retenue, soit trois fois sa marge, et nomme
  dans le refus le plus petit seuil admis. Un seuil au plancher D2 n'est refusé que si un seuil plus haut tient dans la
  bande. Un module absent de la preuve reste au plancher, sans borne basse.
- Les vingt-trois clés hors de la bande sont relevées au plus petit millième qu'elle admet, sur la preuve conservée,
  sans nouvelle mesure.
- **ADR consolidé** : `GEN-0167` remplace `GEN-0166` (archivé), dont la décision 5 gagne la borne basse ; `GEN-0166` (archivé) part en
  archive. Accepté par le mainteneur le 12/11/2026.
- `.claude/rules/testing.md`, la skill `coverage-ratchet`, l'en-tête de `config/m21-per-module-coverage-floors.properties`
  et la page durable `per-module-coverage-floor.md` décrivent la bande.

## Capabilities

### New Capabilities

### Modified Capabilities

- `per-module-coverage-floor` : l'exigence « Le seuil d'un module reste dans sa fenêtre qualifiée » gagne une borne
  basse, à neuf dispersions retenues sous la plus basse mesure conservée.

## Périmètre et exclusions

**Inclus :** la borne basse du seuil d'un module, les vingt-trois clés, l'ADR consolidé, la règle, la skill et la page
durable.

**Exclus :** les ratchets et plafonds des deux échelles, que la borne ne touche pas ; toute requalification (aucune
nouvelle mesure) ; la clé de branches de `morpheus-provider-reference`, dont la fenêtre reste vide sous le plancher D2 ;
la réécriture du relevé d'audit, qui reste un relevé daté.

## Acteurs et surfaces concernés

Mainteneur qui requalifie la couverture ou pose un seuil de module ; contributeur dont la PR retire des tests d'un
module ; CI des deux plateformes.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| nexus 0,473 / 0,873, minos 0,560 / 0,905 | existant vérifié | relevé du 12/11/2026 ; recalcul sur la preuve conservée |
| Vingt-trois clés hors d'une bande à neuf dispersions | existant vérifié (calcul) | `design.md`, tableau des seuils |
| Aucune borne basse dans le gate | existant vérifié | `CoverageQualificationEvidenceTest#windowProblem` ; `GEN-0166` (archivé), Risques |
| Borne basse à neuf dispersions, seuils en bas de bande | décision | réponses du mainteneur du 12/11/2026 ; `design.md`, D1 et D3 |
| Les vingt-trois valeurs tiennent sur la tête `12cd8874` | existant vérifié | artefact `m21-integrity-Windows` du run `43270298003` |

## Impact

- `morpheus-architecture-tests` : `CoverageQualificationEvidenceTest` (`m21`).
- `config/m21-per-module-coverage-floors.properties` (fichier de gouvernance, hausse justifiée en PR).
- `docs/architecture/adr/` : `GEN-0167`, `GEN-0166` (archivé), index du registre, `docs/architecture/arc42/09-decisions.md`,
  et chaque lien vers `GEN-0166` (archivé) repointé.
- `.claude/rules/testing.md`, `.claude/skills/coverage-ratchet/SKILL.md`,
  `docs/developer/specifications/per-module-coverage-floor.md`.

## Questions ouvertes

Aucune : la borne et le placement sont tranchés par le mainteneur.

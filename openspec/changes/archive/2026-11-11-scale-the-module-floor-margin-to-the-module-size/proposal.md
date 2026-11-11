# Proposition : proportionner à la taille du module la marge de son seuil de couverture

## Why

Chaque module de la population de l'échelle par module a son propre seuil, posé dans une fenêtre qualifiée : au plus
la plus basse mesure conservée du module, moins trois fois sa dispersion retenue. Cette dispersion est la plus grande
de l'écart observé entre résumés conservés et de la **dispersion de référence de l'échelle**, fixe : 24 lignes et 11
branches (`CoverageQualificationEvidenceTest.REFERENCE_LINE_SPREAD` / `REFERENCE_BRANCH_SPREAD`). Pour un petit module,
la marge absolue de 72 lignes ou 33 branches dépasse sa taille : la fenêtre est vide par construction, et le seuil
reste au plancher absolu D2, où il ne mord pas.

Constat `AUD-TRV-02` (sévérité Moyenne, **partiel**), revérifié le 10/11/2026 sur `38a4079f` : six clés sur trente-deux
de `config/m21-per-module-coverage-floors.properties` sont au plancher D2 — `morpheus-provider-testkit` et
`morpheus-provider-reference` en lignes et en branches, `morpheus-integration-minos` et `morpheus-integration-nexus`
en branches. L'audit conclut qu'une marge proportionnelle fermerait le constat. Un de ces modules peut aujourd'hui
cesser de se tester sans qu'aucun gate ne le nomme.

Mesure du 11/11/2026 sur la preuve conservée (`config/coverage-qualification/per-module/`, quatre résumés de
`dbd65062`, compteurs identiques sur les quatre) : `provider-testkit` 63 lignes / 32 branches, `provider-reference`
70 / 10, `integration-minos` 71 branches, `integration-nexus` 33 branches ; population 29 900 lignes / 10 148 branches.

## What Changes

- La dispersion de référence d'un module devient la dispersion de référence de l'échelle rapportée à la part du module
  dans la population, arrondie à l'unité supérieure — donc au moins une unité. La dispersion retenue reste la plus
  grande de cette valeur et de l'écart observé ; la marge reste trois fois la dispersion retenue.
- `CoverageQualificationEvidenceTest` calcule la fenêtre ainsi, et son message nomme la dispersion de référence
  proportionnelle.
- Les clés au plancher dont la fenêtre devient non vide sont posées au plus haut millième de leur fenêtre, selon la
  skill `coverage-ratchet`, sans nouvelle mesure.
- **ADR à proposer** : un ADR consolidé qui remplace `GEN-0163` (sa décision 4 fixe la marge sur « la référence »).
  Le changement ne s'applique qu'après son acceptation par le mainteneur.
- `.claude/rules/testing.md` (§ « Un seuil propre à chaque module ») et la page durable décrivent la nouvelle fenêtre.

## Capabilities

### New Capabilities

### Modified Capabilities

- `coverage-qualification-evidence` : l'exigence « La dispersion d'un module se lit dans la preuve conservée » retient
  une dispersion de référence proportionnelle à la taille du module au lieu de la dispersion de référence de l'échelle.

## Périmètre et exclusions

**Inclus :** le calcul de la fenêtre d'un seuil de module, les six clés au plancher, l'ADR consolidé, la règle et la
page durable.

**Exclus :** les plafonds qualifiés et les ratchets des deux échelles, dont la marge reste calculée sur la dispersion
de référence entière (`everyCoverageRatchetKeepsThreeTimesTheSpreadUnderTheLowestKeptMeasurement`) ; toute
requalification (aucune nouvelle mesure n'est prise) ; l'écart entre les deux échelles et les neuf classes de
`morpheus-application` sans test propre, que l'audit suit comme indicateurs sans en faire la condition de clôture ;
l'écriture de tests dans `morpheus-provider-reference` (voir Questions ouvertes).

## Acteurs et surfaces concernés

Mainteneur qui requalifie la couverture ou relève un seuil de module ; contributeur dont la PR retire des tests d'un
petit module ; CI.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Six clés sur trente-deux au plancher D2 | existant vérifié | `config/m21-per-module-coverage-floors.properties`, lu le 11/11/2026 |
| Dispersion de référence fixe 24 / 11 | existant vérifié | `CoverageQualificationEvidenceTest.java:66-67` |
| Tailles des quatre modules et de la population | existant vérifié | résumés de `config/coverage-qualification/per-module/`, compteurs `module.<artifactId>.*` |
| Une dispersion de référence proportionnelle, au moins une unité | proposition à valider | ADR consolidé à proposer ; `design.md`, D1 |
| Cinq des six clés ont alors une fenêtre non vide ; les branches de `provider-reference` non | existant vérifié (calcul) | `design.md`, tableau des fenêtres |
| La décision 4 de `GEN-0163` change | existant vérifié | `docs/architecture/adr/GEN-0163-…md`, §Decision, point 4 |

## Impact

- `morpheus-architecture-tests` : `CoverageQualificationEvidenceTest` (`m21`).
- `config/m21-per-module-coverage-floors.properties` (fichier de gouvernance, hausse justifiée en PR).
- `docs/architecture/adr/` : ADR consolidé qui remplace `GEN-0163`, `GEN-0163` archivé, index du registre et
  `docs/architecture/arc42/09-decisions.md` ; `.claude/rules/testing.md`, `.claude/skills/coverage-ratchet/SKILL.md`
  si elle décrit la fenêtre ; `docs/developer/specifications/coverage-qualification-evidence.md` et
  `per-module-coverage-floor.md`.

## Questions ouvertes

- **À trancher par le mainteneur avant l'application** : accepter la dispersion de référence proportionnelle (D1) et
  l'ADR consolidé qui la porte. Sans cette acceptation, le changement reste `Proposée` et n'est pas appliqué.
- Les branches de `morpheus-provider-reference` (6 couvertes sur 10) gardent une fenêtre vide même avec la marge
  proportionnelle : elles s'ouvrent à partir de 7 branches couvertes, ce qui demande un test dans ce module puis une
  requalification. À traiter dans la requalification suivante, hors de ce changement.

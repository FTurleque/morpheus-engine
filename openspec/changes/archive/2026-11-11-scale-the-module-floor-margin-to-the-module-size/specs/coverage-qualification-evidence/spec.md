## MODIFIED Requirements

### Requirement: La dispersion d'un module se lit dans la preuve conservée

La dispersion retenue pour une grandeur d'un module DOIT (MUST) être la plus grande de deux valeurs : l'écart, en
lignes ou en branches couvertes, entre les résumés conservés pour ce module, et la dispersion de référence de
l'échelle multipliée par la part du module dans la population de cette grandeur, arrondie à l'unité supérieure. Elle
NE DOIT PAS (MUST NOT) être saisie à la main pour un module.

#### Scenario: Un module plus instable que la référence

- **GIVEN** un module dont les lignes couvertes diffèrent, entre les résumés conservés, de plus que sa dispersion de
  référence proportionnelle de lignes
- **WHEN** la fenêtre de son seuil de lignes est calculée
- **THEN** la marge exigée est trois fois cet écart observé

#### Scenario: Un module stable

- **GIVEN** un module dont les lignes couvertes sont identiques dans tous les résumés conservés
- **WHEN** la fenêtre de son seuil de lignes est calculée
- **THEN** la marge exigée est trois fois sa dispersion de référence proportionnelle de lignes, jamais zéro

#### Scenario: Un petit module stable

- **GIVEN** un module si petit que la dispersion de référence de lignes, rapportée à sa part de la population, est
  inférieure à une ligne, et dont les compteurs sont identiques dans tous les résumés conservés
- **WHEN** la fenêtre de son seuil de lignes est calculée
- **THEN** sa dispersion retenue est d'une ligne et la marge exigée de trois lignes
- **AND** le gate nomme, dans un refus, la dispersion de référence proportionnelle et la part du module

#### Scenario: Les plafonds et les ratchets ne changent pas de marge

- **WHEN** la marge d'un ratchet ou d'un plafond qualifié d'une échelle est calculée
- **THEN** elle reste trois fois la plus grande de la dispersion observée et de la dispersion de référence entière de
  l'échelle

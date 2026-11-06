# Spec delta : coverage-qualification-evidence

## ADDED Requirements

### Requirement: La preuve de l'échelle par module porte les compteurs de chaque module

La preuve écrite par le gate de l'échelle par module et chaque résumé conservé de cette échelle DOIVENT (MUST) porter,
pour chaque module de la population, ses lignes et ses branches couvertes et manquées, dont les sommes égalent les
totaux du résumé. Le build DOIT refuser un résumé conservé qui omet un module, en nomme un en trop ou dont les sommes
diffèrent des totaux.

#### Scenario: Un résumé conservé sans les compteurs d'un module

- **GIVEN** la preuve conservée de l'échelle par module
- **WHEN** un de ses résumés ne porte pas les compteurs d'un module de la population qu'il déclare
- **THEN** le build échoue et nomme le fichier et le module manquant

#### Scenario: Des compteurs par module qui ne font pas le total

- **WHEN** la somme des lignes couvertes par module d'un résumé conservé diffère de son `lineCovered`
- **THEN** le build échoue, nomme le fichier et les deux valeurs

#### Scenario: Deux résumés d'une qualification nomment des modules différents

- **WHEN** deux résumés conservés de l'échelle par module ne portent pas le même ensemble de modules
- **THEN** le build échoue et nomme la différence, comme pour deux populations

#### Scenario: La preuve écrite par le gate porte ses lignes par module

- **WHEN** le gate de l'échelle par module conclut sur un réacteur complet
- **THEN** sa preuve, qui commence toujours par `coverageScope=per-module`, porte une ligne de compteurs par module de
  la population en plus des totaux

### Requirement: La dispersion d'un module se lit dans la preuve conservée

La dispersion retenue pour une grandeur d'un module DOIT (MUST) être la plus grande de deux valeurs : l'écart, en
lignes ou en branches couvertes, entre les résumés conservés pour ce module, et la dispersion de référence de
l'échelle. Elle NE DOIT PAS (MUST NOT) être saisie à la main pour un module.

#### Scenario: Un module plus instable que la référence

- **GIVEN** un module dont les lignes couvertes diffèrent, entre les résumés conservés, de plus que la dispersion de
  référence de lignes
- **WHEN** la fenêtre de son seuil de lignes est calculée
- **THEN** la marge exigée est trois fois cet écart observé

#### Scenario: Un module stable

- **GIVEN** un module dont les lignes couvertes sont identiques dans tous les résumés conservés
- **WHEN** la fenêtre de son seuil de lignes est calculée
- **THEN** la marge exigée est trois fois la dispersion de référence de lignes, jamais zéro

# risk-register-references Specification

## Purpose
Garantir qu'un workflow qui renvoie le mainteneur vers le registre des risques désigne une entrée qui n'est pas close,
pour qu'un échec de gate ne l'envoie jamais lire qu'un problème encore actif est réglé. Page durable :
[docs/developer/specifications/risk-register-references.md](/docs/developer/specifications/risk-register-references.md).

## Requirements

### Requirement: Un workflow ne renvoie qu'à une entrée du registre qui n'est pas close

Chaque identifiant `RT-NN` ou `DT-NN` écrit dans un fichier `.github/workflows/*.yml`, commentaires compris, DOIT
(MUST) désigner une entrée ouverte ou une position de `docs/architecture/risks/register.md`. Un identifiant qui désigne
une entrée close, ou qu'aucune ligne du registre ne nomme, DOIT faire échouer la suite d'architecture en nommant le
fichier, la ligne, l'identifiant et l'état trouvé.

#### Scenario: Les renvois actuels sont admis

- **GIVEN** les workflows et le registre de la révision courante, où `security.yml` cite `RT-13` et `nightly.yml`
  cite `RT-08`, tous deux au tableau actif
- **WHEN** la suite d'architecture les lit
- **THEN** la règle passe
- **AND** aucun fichier n'est modifié

#### Scenario: Une entrée close et toujours citée

- **GIVEN** une PR qui retire `RT-13` du tableau actif et date son en-tête de détail « clos », sans toucher à
  `security.yml`
- **WHEN** la suite d'architecture lit les workflows
- **THEN** elle échoue en nommant `.github/workflows/security.yml`, chaque ligne qui cite `RT-13`, et l'état « close »

#### Scenario: Un identifiant inconnu

- **GIVEN** un workflow qui cite `RT-99`, que le registre ne mentionne nulle part
- **WHEN** la suite d'architecture lit les workflows
- **THEN** elle échoue en nommant le fichier, la ligne, `RT-99` et l'état « inconnue »

#### Scenario: Un renvoi au registre sans identifiant

- **GIVEN** un workflow qui cite `docs/architecture/risks/register.md` sans identifiant d'entrée
- **WHEN** la suite d'architecture lit les workflows
- **THEN** ce renvoi n'est pas jugé, faute d'entrée désignée

### Requirement: Une entrée au tableau actif ou requalifiée en position n'est pas close

Une entrée DOIT (MUST) être lue ouverte quand son identifiant est la première cellule d'une ligne du tableau de la
section « Risques techniques » ou de la section « Dette technique / documentaire » du registre, et position quand il
figure dans un en-tête `###` de requalification qui la dit position. Toute autre entrée que le registre mentionne DOIT
être lue close.

#### Scenario: Un risque accepté sans correctif n'est pas clos

- **GIVEN** `RT-14` (distribution Windows non signée), au tableau des risques techniques alors qu'aucun correctif ne
  l'éteint
- **WHEN** un workflow cite `RT-14`
- **THEN** le renvoi est admis, l'entrée étant ouverte

#### Scenario: Une position est citable

- **GIVEN** `DT-13`, sorti du tableau de la dette et nommé par l'en-tête « Requalification du 22/09/2026 — `DT-13` est
  une position, pas une dette »
- **WHEN** un workflow cite `DT-13`
- **THEN** le renvoi est admis, l'entrée étant une position

#### Scenario: Une dette retirée du tableau est close

- **GIVEN** `DT-06`, que le registre dit « résolue et retirée du tableau actif »
- **WHEN** un workflow cite `DT-06`
- **THEN** la suite d'architecture échoue en nommant `DT-06` et l'état « close »

### Requirement: Le registre ne dit pas close une entrée qu'il garde au tableau actif

Une entrée au tableau actif dont l'en-tête de détail `### <identifiant> — …` porte, dans sa parenthèse finale, le mot
« clos » ou « close » DOIT (MUST) faire échouer la suite d'architecture en nommant l'entrée et les deux lignes du
registre qui se contredisent.

#### Scenario: Une clôture écrite à moitié

- **GIVEN** l'en-tête de `RT-13` passe à « (constaté le 09/09/2026, clos le 22/09/2026) » et sa ligne reste au tableau
  des risques techniques
- **WHEN** la suite d'architecture lit le registre
- **THEN** elle échoue en nommant `RT-13`, la ligne du tableau et la ligne de l'en-tête
- **AND** aucun renvoi de workflow n'est jugé admis tant que le registre se contredit

### Requirement: La règle des renvois ne passe pas à vide

La règle DOIT (MUST) échouer, en disant ce qui manque, quand elle ne lit aucune ligne de tableau actif dans le registre
ou aucun identifiant d'entrée dans les workflows.

#### Scenario: Un registre réorganisé

- **GIVEN** une PR qui renomme la section « Risques techniques » du registre
- **WHEN** la suite d'architecture lit le registre
- **THEN** elle échoue en disant qu'aucune ligne de tableau actif n'a été lue, au lieu de lire toutes les entrées closes

# single-integration-branch Specification

## Purpose
Fixer le modèle de branches du dépôt MORPHEUS : `main` est la seule branche longue, cible de toute pull request, et
toute release est un tag posé sur elle. Page durable : [docs/developer/specifications/single-integration-branch.md](../../../docs/developer/specifications/single-integration-branch.md).

## Requirements

### Requirement: Aucune configuration versionnée ne DOIT nommer une branche d'intégration autre que main

Les workflows de `.github/workflows/`, `.github/dependabot.yml`, les validateurs `scripts/validate-*.ps1` et
`scripts/validate-*.sh` et `.archgate/config.json` NE DOIVENT PAS (MUST NOT) nommer la branche `develop`. Un test
d'architecture DOIT (MUST) faire échouer le build et nommer chaque fichier fautif.

#### Scenario: Un workflow qui filtre encore develop est refusé

- **GIVEN** un workflow dont le filtre de déclenchement est `branches: [main, develop]`
- **WHEN** les tests d'architecture s'exécutent
- **THEN** le build échoue, et le message nomme le fichier du workflow et la branche `develop`
- **AND** aucun autre fichier n'est signalé

#### Scenario: Un mot qui contient develop n'est pas une branche

- **GIVEN** un workflow qui contient le mot `developer` ou le texte `docs/developer/`
- **WHEN** les tests d'architecture s'exécutent
- **THEN** ce fichier n'est pas signalé

#### Scenario: Un relevé daté peut citer develop

- **GIVEN** un audit daté sous `docs/project/audits/` qui cite une mesure prise sur `develop`
- **WHEN** les tests d'architecture s'exécutent
- **THEN** ce fichier n'est pas signalé, puisque la règle ne lit que la configuration listée par l'exigence

### Requirement: Toute pull request DOIT être comparée à main pour la couverture des lignes modifiées

La garde de couverture des lignes et des branches modifiées de `ci.yml` DOIT (MUST) comparer une pull request à
`origin/main`, et DOIT refuser, avec l'erreur `Unsupported pull-request base branch`, une pull request dont la base
n'est pas `main`.

#### Scenario: Une pull request vers main est comparée à main

- **GIVEN** une pull request dont la base est `main`
- **WHEN** la voie Linux de `ci.yml` s'exécute
- **THEN** le diff passé à `scripts/check-diff-coverage.py` est `origin/main...HEAD`, avec les minima de lignes et de
  branches inchangés

#### Scenario: Une pull request vers une autre base est refusée

- **GIVEN** une pull request dont la base est une branche autre que `main`
- **WHEN** la voie Linux de `ci.yml` s'exécute
- **THEN** l'étape échoue avec `Unsupported pull-request base branch` et le code de sortie 2

### Requirement: Chaque fusion dans main DOIT recevoir une analyse statique

Le job `sonar` de `ci.yml` DOIT (MUST) s'exécuter sur chaque push sur `main`, et seulement sur un push sur `main`.
Aucun autre workflow NE DOIT (MUST NOT) porter `SONAR_TOKEN`.

#### Scenario: Une fusion est analysée

- **GIVEN** une pull request fusionnée dans `main`
- **WHEN** l'événement `push` de `main` déclenche `ci.yml`
- **THEN** le job `sonar` analyse ce commit comme la branche principale du projet SonarQube Cloud, sans nommer
  de branche, et un portail de qualité illisible y reste une erreur

#### Scenario: La lane nocturne ne porte plus le jeton

- **GIVEN** le workflow `nightly.yml`
- **WHEN** les tests d'architecture le lisent
- **THEN** il ne contient ni `SONAR_TOKEN` ni job d'analyse SonarQube Cloud

### Requirement: Dependabot DOIT viser la branche par défaut

Chaque entrée de `.github/dependabot.yml` DOIT (MUST) laisser Dependabot viser la branche par défaut du dépôt, sans
clé `target-branch`.

#### Scenario: Une mise à jour ouvre sa pull request vers main

- **GIVEN** la configuration Dependabot des écosystèmes `maven` et `github-actions`
- **WHEN** Dependabot ouvre une pull request de mise à jour
- **THEN** sa base est `main`

### Requirement: La base de comparaison par défaut d'un validateur DOIT être main

Un validateur qui prend une base de comparaison (`-BaseRef` en PowerShell, variable `MORPHEUS_<CIBLE>_BASE_REF` en
shell) DOIT (MUST) prendre `origin/main` par défaut et `main` en repli, et DOIT échouer en nommant les deux quand
aucune n'existe.

#### Scenario: Le validateur prend main sans argument

- **GIVEN** un clone où `origin/main` existe
- **WHEN** `scripts/validate-d2.sh` est lancé sans `MORPHEUS_D2_BASE_REF`
- **THEN** il compare le commit courant à `origin/main`

#### Scenario: Aucune base n'existe

- **GIVEN** un clone sans `origin/main` ni `main`
- **WHEN** un validateur qui prend une base est lancé sans base explicite
- **THEN** il échoue avec un message qui nomme `origin/main` et `main`

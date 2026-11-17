# Spec delta : single-integration-branch

## ADDED Requirements

### Requirement: Le run d'une fusion dans main NE DOIT PAS être annulé par une fusion suivante

Un run de `ci.yml` ou de `codeql.yml` déclenché par un push sur `main` NE DOIT PAS (MUST NOT) être annulé, ni abandonné
en attente, du fait d'un autre run du même workflow. Un run de pull request PEUT être annulé par un push suivant de la
même pull request. Un test de contrat DOIT (MUST) faire échouer le build et nommer le workflow fautif.

#### Scenario: Deux fusions rapprochées reçoivent chacune leur verdict

- **GIVEN** une fusion A dans `main` dont le run de `ci.yml` est en cours
- **WHEN** une fusion B arrive sur `main` avant la fin du run de A
- **THEN** le run de A va à son terme et publie les verdicts `exact-head` des deux plateformes et l'analyse du job
  `sonar` pour le commit de A
- **AND** le run de B publie les siens pour le commit de B

#### Scenario: Trois fusions rapprochées

- **GIVEN** deux fusions dans `main` dont les runs de `ci.yml` ne sont pas terminés
- **WHEN** une troisième fusion arrive
- **THEN** aucun des trois runs n'est annulé ni abandonné en attente

#### Scenario: Une pull request garde l'annulation de son run périmé

- **GIVEN** une pull request dont le run de `ci.yml` est en cours
- **WHEN** un nouveau commit est poussé sur la branche de cette pull request
- **THEN** le run périmé est annulé et seul le run du nouveau commit donne le verdict de la pull request

#### Scenario: Une concurrence qui annule un run de main est refusée

- **GIVEN** `ci.yml` ou `codeql.yml` dont le groupe de concurrence est commun à deux pushes sur `main` avec
  `cancel-in-progress: true`, ou commun à deux pushes sur `main` sans annulation
- **WHEN** les tests d'architecture s'exécutent
- **THEN** le build échoue, et le message nomme le fichier du workflow et l'événement `push`

# Spec delta : suppressed-failure-trace

## ADDED Requirements

### Requirement: Un échec imprévu d'une commande CLI est compté sous une famille nommée

Quand une commande de la CLI répond à un échec par le code de sortie `10` (`INTERNAL_ERROR`), elle DOIT (MUST)
compter cet échec sur le recorder du processus sous la famille `cli-internal-error` avant la fin du processus, et
émettre son événement `FAILURE_SUPPRESSED` quand un journal opérationnel est actif. Un refus rendu avec un autre code
NE DOIT PAS (MUST NOT) être compté sous cette famille.

#### Scenario: Un défaut interne avec le journal JSON sur la sortie d'erreur

- **GIVEN** un processus CLI lancé avec le journal opérationnel en lignes JSON sur la sortie d'erreur
- **WHEN** une commande `portfolio` échoue sur un `NullPointerException` imprévu
- **THEN** la sortie d'erreur porte un événement `FAILURE_SUPPRESSED` dont `family` vaut `cli-internal-error` et
  `failureType` vaut `NullPointerException`, puis la ligne d'erreur de code `10`
- **AND** le message de l'échec n'apparaît pas dans l'événement

#### Scenario: Le compteur bouge journal éteint

- **GIVEN** un recorder dont le journal opérationnel est éteint
- **WHEN** une commande échoue sur un échec imprévu
- **THEN** le compteur `failure.suppressed.cli-internal-error` de ce recorder a avancé de un

#### Scenario: Un refus d'état n'est pas compté

- **GIVEN** un projet sans snapshot `ACTIVE`
- **WHEN** une commande de lecture est refusée avec le code `4`
- **THEN** le compteur `failure.suppressed.cli-internal-error` n'a pas bougé
- **AND** aucun événement de cette famille n'est émis

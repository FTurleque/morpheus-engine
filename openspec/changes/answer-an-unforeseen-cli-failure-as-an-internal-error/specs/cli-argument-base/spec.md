# Spec delta : cli-argument-base

## MODIFIED Requirements

### Requirement: Every CLI failure SHALL be reported through one error line

A failure of any CLI command, the `api`, `mcp` and remote server launchers included, MUST be written to the error
stream as one line that carries the exit code and the failure's text, in one format, produced by one writer:
`MORPHEUS error [<code>]: <message>`, or its JSON object when the invocation carries `--json`. The text of a failure
MUST be its message, or the name of its type when it has no message or a blank one. Writing the line MUST NOT change the exit code the command answers.

#### Scenario: Two sub-commands, the same kind of failure

- **WHEN** two different sub-commands are called with a required option missing
- **THEN** both write an error line of the same format with the usage exit code

#### Scenario: A failure without a message

- **WHEN** a sub-command fails with a failure that has no message
- **THEN** the error line carries the name of the failure's type, on every sub-command

#### Scenario: The format after the change

- **WHEN** the CLI suites run
- **THEN** every error line they expect is `MORPHEUS error [<code>]: <message>` or its JSON object, and every exit code
  they expect is the one the command answered before the format was unified

#### Scenario: A command family that answered with its own prefix

- **GIVEN** the commands `update-check`, `reason analyze`, `provider-plugins probe` and `server restore`
- **WHEN** each is called with an option it does not declare
- **THEN** each writes `MORPHEUS error [2]: unknown option: --<option>` and exits with the usage exit code

#### Scenario: A refused launch of a server

- **GIVEN** the `mcp`, `api` or remote server launcher
- **WHEN** it is called with an option it does not accept
- **THEN** it writes one line `MORPHEUS error [2]: <message>` and exits with the usage exit code

#### Scenario: A refusal under the JSON output

- **WHEN** a command of any family is refused and the invocation carries `--json`
- **THEN** the error stream carries one JSON object with `exitCode`, `code` and `message`, and the exit code is the
  same as without `--json`

## ADDED Requirements

### Requirement: Un échec qu'aucune commande ne prévoit sort avec le code d'erreur interne

Toute commande de la CLI DOIT (MUST) répondre à un échec qu'aucun de ses refus ne classe — ni usage, ni entité
absente, ni état, ni entrée-sortie — par le code de sortie `10` (`INTERNAL_ERROR`) et sa ligne d'erreur, quelle que
soit sa famille. Un échec qu'une commande classe comme refus d'état DOIT garder le code `4` (`STATE_ERROR`). Les
valeurs numériques des codes NE DOIVENT PAS (MUST NOT) changer.

#### Scenario: Un défaut interne sous une famille qui le rendait en 4

- **GIVEN** une base de connaissances valide et une commande `portfolio`, `policy` ou `query` dont l'exécution lève un
  `NullPointerException` qu'aucun refus de la commande ne classe
- **WHEN** la commande est appelée
- **THEN** la sortie d'erreur porte la ligne `MORPHEUS error [10]: <texte de l'échec>` et le code de sortie est `10`
- **AND** aucune écriture n'est persistée au-delà de ce que la commande avait déjà validé avant l'échec

#### Scenario: Le même défaut sur deux familles

- **GIVEN** le même échec imprévu levé sous `projects` et sous `portfolio`
- **WHEN** chacune des deux commandes est appelée
- **THEN** les deux sortent avec le code `10` et la même forme de ligne d'erreur

#### Scenario: Un refus d'état garde son code

- **GIVEN** un projet sans snapshot `ACTIVE`
- **WHEN** une commande de lecture de ces familles est appelée sur ce projet
- **THEN** elle sort avec le code `4` et la ligne d'erreur nomme l'absence de snapshot actif
- **AND** rien n'est persisté

#### Scenario: Un défaut interne sous la sortie JSON

- **GIVEN** une invocation qui porte `--json`
- **WHEN** la commande échoue sur un échec imprévu
- **THEN** la sortie d'erreur porte un objet JSON dont `exitCode` vaut `10`, et le code de sortie est `10`

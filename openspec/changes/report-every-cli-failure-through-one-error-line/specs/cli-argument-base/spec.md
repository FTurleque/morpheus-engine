# Spec delta : cli-argument-base

## MODIFIED Requirements

### Requirement: Every CLI failure SHALL be reported through one error line

A failure of any CLI command, the `api`, `mcp` and remote server launchers included, MUST be written to the error
stream as one line that carries the exit code and the failure's text, in one format, produced by one writer:
`MORPHEUS error [<code>]: <message>`, or its JSON object when the invocation carries `--json`. The text of a failure
MUST be its message, or the name of its type when it has no message or a blank one. The exit codes MUST NOT change.

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

### Requirement: Aucune commande n'écrit son propre préfixe d'erreur

Le build DOIT (MUST) échouer, en nommant le fichier, quand une source principale de la CLI autre que l'écrivain de la
ligne d'erreur contient, hors commentaire, un littéral qui commence par `MORPHEUS ` et contient le mot `error`.

#### Scenario: Un préfixe propre réintroduit

- **GIVEN** une classe de commande de la CLI
- **WHEN** elle écrit `err.println("MORPHEUS server error: " + texte)`
- **THEN** la suite d'architecture échoue et nomme le fichier
- **AND** aucune exception de liste ne permet de l'admettre

#### Scenario: Une ligne d'information n'est pas un préfixe d'erreur

- **GIVEN** un lanceur qui écrit `MORPHEUS API listening on <adresse>`
- **WHEN** la suite d'architecture lit les sources
- **THEN** cette ligne n'est pas refusée, car elle ne contient pas le mot `error`

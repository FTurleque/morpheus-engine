# Spec delta : failure-text-ownership

## Purpose
Définir qui forme le texte d'un échec, filtré ou non, dans toutes les classes de production de MORPHEUS, et ce qui
refuse une copie de cette conversion, pour le mainteneur et la CI. Documentation durable :
[`failure-text-ownership.md`](/docs/developer/specifications/failure-text-ownership.md).

## ADDED Requirements

### Requirement: Le texte d'un échec a un seul propriétaire

Toute classe de production qui forme le texte d'un échec, c'est-à-dire son message, ou le nom simple de son type quand
le message est absent ou blanc, DOIT (MUST) l'obtenir de `FailureText.of` (`com.morpheus.application.foundation`),
quel que soit le type déclaré de l'échec, `Throwable`, `Exception` ou `RuntimeException`.

#### Scenario: Un échec qui porte un message

- **GIVEN** une opération de la CLI, d'un outil MCP, d'une intégration MINOS ou NEXUS, du raisonnement, du scan des
  sources ou d'un plugin de provider
- **WHEN** elle échoue avec une exception dont le message est `boom`
- **THEN** le texte rendu est `boom`, comme avant ce changement
- **AND** rien n'est persisté par la formation de ce texte

#### Scenario: Un échec sans message ou à message blanc

- **GIVEN** les mêmes opérations
- **WHEN** l'exception n'a pas de message, ou un message fait d'espaces
- **THEN** le texte rendu est le nom simple du type de l'exception
- **AND** aucun texte `null` ni vide n'atteint un appelant

#### Scenario: Aucun filtre ajouté ni retiré

- **GIVEN** un échec dont le message nomme un chemin du serveur, levé sous l'une des opérations qui formaient leur
  texte par une copie privée
- **WHEN** son texte est formé
- **THEN** il est le même qu'avant ce changement : `FailureText.of` ne filtre aucun chemin

### Requirement: Le texte relayé d'un échec a un seul propriétaire

Le texte d'un échec filtré pour un appelant hors de la machine DOIT (MUST) être obtenu de
`ServerLocationDisclosure.relayableText` : le texte de `FailureText.of`, remplacé par le nom simple du type quand
`ServerLocationDisclosure.isSafeToRelay` le refuse. Aucune autre classe de production NE DOIT (MUST NOT) refaire ce
filtre sur un échec.

#### Scenario: Un message qui nomme un emplacement du serveur

- **GIVEN** un échec dont le message contient un chemin absolu du serveur
- **WHEN** une route HTTP ou l'attribution d'une source OpenSpec en forme le texte relayé
- **THEN** le texte est le nom simple du type de l'exception et le chemin n'y apparaît pas
- **AND** le statut et le code d'erreur de la réponse HTTP sont ceux d'avant ce changement

#### Scenario: Un message écrit par MORPHEUS qui ne nomme aucun emplacement

- **GIVEN** un refus de validation dont le message ne nomme aucun chemin
- **WHEN** son texte relayé est formé
- **THEN** il est le message tel quel

### Requirement: Une copie de la conversion d'un échec en texte est refusée par sa signature

La suite d'architecture DOIT (MUST) échouer, en nommant la classe et la méthode, quand une méthode déclarée d'une
classe de production de `com.morpheus..` prend un seul paramètre assignable à `Throwable` et rend une `String`, hors des
deux propriétaires et des méthodes nommées avec leur autre usage ; une méthode nommée qui n'a plus cette signature DOIT
aussi la faire échouer.

#### Scenario: Une copie sous un autre nom dans un adaptateur

- **GIVEN** une classe d'un adaptateur qui déclare `private static String describe(RuntimeException failure)`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme la classe et la méthode
- **AND** aucune liste ne l'admet sans une raison écrite

#### Scenario: Une méthode d'autre usage

- **GIVEN** une méthode qui ne rend que le nom du type pour la frontière distante, nommée avec cette raison
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle passe

#### Scenario: Une exception devenue sans objet

- **GIVEN** une méthode nommée dont la signature a changé ou qui a disparu
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et demande de retirer l'entrée

### Requirement: Le corps de la conversion n'est écrit que chez ses propriétaires

Le build DOIT (MUST) échouer, en nommant le fichier, quand une source principale du dépôt autre que celles des deux
propriétaires lit, dans un même membre et hors commentaire, le message d'un échec et retombe sur le nom simple de son
type, sauf fichier nommé avec la raison de son autre usage.

#### Scenario: Une copie écrite dans une lambda ou en ligne

- **GIVEN** une source principale qui écrit
  `failure.getMessage() == null ? failure.getClass().getSimpleName() : failure.getMessage()` dans une expression
- **WHEN** la suite d'architecture lit les sources
- **THEN** elle échoue et nomme le fichier

#### Scenario: Un module hors du classpath des tests d'architecture

- **GIVEN** une copie écrite dans un module que la suite d'architecture n'importe pas
- **WHEN** la suite d'architecture lit les sources
- **THEN** elle échoue aussi, car le scan lit tout `src/main/java` du dépôt

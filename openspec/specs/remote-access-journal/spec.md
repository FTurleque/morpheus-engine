# remote-access-journal Specification

## Purpose
Define the record the remote server keeps of who was authorized to do what.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/remote-access-journal.md](../../../docs/developer/specifications/remote-access-journal.md).

## Requirements

### Requirement: Every authorized remote request SHALL leave a record that attributes it

The remote server MUST record, for each request it authorizes, the time, the request identifier it returns, the
principal, the role held and the role required, the method, the route template and the status.

#### Scenario: A write is attributed

- **WHEN** a principal with the WRITE role registers a project
- **THEN** the journal holds a record with that principal, `POST`, the template `projects`, the status and the
  identifier returned in `X-Request-Id`

#### Scenario: The request identifier joins the two sides

- **WHEN** a client reports the `X-Request-Id` of a response
- **THEN** exactly the records of that request carry it

### Requirement: A refused authorization SHALL be recorded and a failed authentication SHALL NOT

A request refused for want of a role MUST be recorded with its principal. A request that fails authentication MUST NOT
create a record.

#### Scenario: A reader who tries to write is recorded

- **WHEN** a principal with the READ role calls a WRITE route
- **THEN** the journal holds a record with that principal and the status 403

#### Scenario: An unauthenticated caller cannot fill the journal

- **WHEN** requests arrive without a valid token
- **THEN** the journal does not grow

### Requirement: A record SHALL carry no secret and no caller-chosen text

A record MUST NOT contain a token, a token fingerprint, a path parameter, a query string, a header or a body.

#### Scenario: The template, not the path

- **WHEN** a request is made on a path containing identifiers
- **THEN** the record contains the route template and none of the identifiers

#### Scenario: No token in any form

- **WHEN** the journal is read after authenticated traffic
- **THEN** it contains neither a token nor a fingerprint of one

### Requirement: The journal SHALL be bounded, private and separate from the identity file

The journal MUST be a file of its own, with a bound in size and in generations, created with the permissions of a
private file, and MUST NOT be written into the identity file or its credential audit.

#### Scenario: Rotation at the bound

- **WHEN** the journal reaches its size bound
- **THEN** it is rotated and the number of generations kept does not exceed its bound

### Requirement: A record that cannot be written SHALL have a decided outcome

When a record cannot be written, the server MUST apply the policy the decision record states, MUST count the lost
record, and MUST NOT continue as if it had been written.

#### Scenario: The loss is visible

- **WHEN** the journal cannot be written
- **THEN** the counter of lost records moves, and the request is refused or served as the policy says

### Requirement: Une écriture est consignée avant d'être servie

Le serveur distant DOIT (MUST) écrire un enregistrement de phase `accepted` pour toute requête autorisée sur une route
`WRITE`, ou sur une route `ADMIN` avec une méthode autre que `GET` et `HEAD`, avant de la servir, puis un
enregistrement de phase `completed` qui porte son statut. Une lecture DOIT (MUST) ne laisser que l'enregistrement
`completed`.

#### Scenario: Une écriture laisse deux enregistrements

- **GIVEN** un principal de rôle `WRITE` et un journal accessible en écriture
- **WHEN** il enregistre un projet par `POST` sur la route `projects`
- **THEN** le journal contient un enregistrement `accepted` puis un enregistrement `completed` pour le même
  identifiant de requête
- **AND** l'enregistrement `completed` porte le statut renvoyé

#### Scenario: Une lecture laisse un seul enregistrement

- **GIVEN** un principal de rôle `READ` et un journal accessible en écriture
- **WHEN** il lit une route `READ`
- **THEN** le journal contient un seul enregistrement pour cette requête, de phase `completed`

#### Scenario: La consultation d'une route d'administration est une lecture

- **GIVEN** un principal de rôle `ADMIN`
- **WHEN** il consulte une route `ADMIN` par `GET`
- **THEN** le journal contient un seul enregistrement pour cette requête, de phase `completed`

### Requirement: Une écriture dont l'acceptation ne peut être consignée est refusée

Quand l'enregistrement `accepted` d'une requête d'écriture ou d'administration ne peut pas être écrit, le serveur
distant DOIT (MUST) la refuser par un 503 `ACCESS_JOURNAL_UNAVAILABLE` sans la servir, DOIT (MUST) servir une lecture
dans la même situation, et DOIT (MUST) incrémenter le compteur `remote.access.journal.lost` à chaque enregistrement perdu.

#### Scenario: Une écriture n'est pas servie sans son enregistrement

- **GIVEN** un journal qui ne peut plus être écrit
- **WHEN** un principal de rôle `WRITE` envoie une requête d'écriture
- **THEN** la réponse est 503, code `ACCESS_JOURNAL_UNAVAILABLE`
- **AND** la requête n'est pas relayée au serveur local et aucun état n'est modifié
- **AND** le compteur `remote.access.journal.lost` augmente

#### Scenario: Une lecture reste servie

- **GIVEN** un journal qui ne peut plus être écrit
- **WHEN** un principal de rôle `READ` lit une route `READ`
- **THEN** la lecture est servie avec son statut habituel
- **AND** le compteur `remote.access.journal.lost` augmente pour l'enregistrement `completed` perdu

### Requirement: Le serveur distant ne démarre pas sans son journal

Le serveur distant DOIT (MUST) refuser de démarrer quand le journal d'accès ne peut pas être ouvert à côté du fichier
d'identités, avec un refus dont le message commence par `ACCESS_JOURNAL_UNAVAILABLE` et NE DOIT PAS (MUST NOT) nommer
le chemin du fichier.

#### Scenario: Un répertoire inutilisable empêche le démarrage

- **GIVEN** un fichier d'identités dont le répertoire ne permet pas d'écrire le journal
- **WHEN** le serveur distant démarre
- **THEN** le démarrage échoue avec un message qui commence par `ACCESS_JOURNAL_UNAVAILABLE`
- **AND** le message ne contient aucun chemin du serveur

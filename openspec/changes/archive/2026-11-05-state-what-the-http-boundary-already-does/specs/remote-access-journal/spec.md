# Spec delta : remote-access-journal

## ADDED Requirements

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

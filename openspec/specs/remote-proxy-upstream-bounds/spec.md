# remote-proxy-upstream-bounds Specification

## Purpose
Define the bounds on every call the remote proxy makes to the local server.

## Requirements

### Requirement: Every upstream call SHALL have a read timeout

The remote proxy MUST bound the wait for the upstream response on every route: sixty seconds for a READ route, three
hundred seconds for a WRITE or ADMIN route.

#### Scenario: A write that never answers ends

- **WHEN** the upstream does not answer a WRITE request
- **THEN** the facade answers within the writes' timeout and releases the request's permits

#### Scenario: The privileged ceiling recovers

- **WHEN** as many WRITE requests as the privileged ceiling have timed out
- **THEN** a new WRITE request is admitted

#### Scenario: Every role has the deadline of its role

- **WHEN** any route and method of the HTTP route table is authorized
- **THEN** a READ route carries the reads' deadline and a WRITE or ADMIN route the writes' deadline

### Requirement: Every proxied response SHALL be read within the memory budget

A response to a WRITE or ADMIN request MUST take a response slot and be bounded in size as a response to a READ is. A
call that may change state takes its slot once the upstream has answered, and waits for it rather than being refused
at once, but MUST NOT wait beyond the deadline of its call, counted from the moment the call was sent upstream.

#### Scenario: An oversized response to a write is refused

- **WHEN** the upstream answers a WRITE request with a body beyond the bound
- **THEN** the facade refuses it by name, writes nothing downstream and gives its slot back

#### Scenario: A write waits for a slot that frees in time

- **GIVEN** every response slot is held and a WRITE request has been answered upstream before its deadline
- **WHEN** a slot is given back before that deadline
- **THEN** the write takes the slot, its upstream response is relayed with the upstream status, and the slot is given
  back once the response is written

### Requirement: A timed-out write SHALL be answered as an unknown outcome

When a WRITE or ADMIN request times out upstream, the answer MUST carry a code distinct from a timed-out read and MUST
state that the operation may have completed.

#### Scenario: The client is told to read before retrying

- **WHEN** a WRITE request times out
- **THEN** the status is 504, the code is `UPSTREAM_WRITE_TIMEOUT` and the message says the state must be read before a
  retry

### Requirement: Une écriture sans créneau de réponse à son échéance reçoit un refus de résultat inconnu

Une requête WRITE ou ADMIN que l'amont a répondue et qui n'obtient aucun créneau de réponse avant l'échéance de son
appel DOIT (MUST) recevoir `504 RESPONSE_BUDGET_WRITE_TIMEOUT`, dont le message dit que l'opération a pu aboutir et
qu'il faut relire l'état avant de rejouer. La réponse amont DOIT être fermée sans être relayée, et la requête NE DOIT
PAS (MUST NOT) rendre un créneau qu'elle n'a pas pris.

#### Scenario: Aucun créneau ne se libère avant l'échéance

- **GIVEN** chaque créneau de réponse est tenu au-delà de l'échéance d'une requête WRITE
- **WHEN** l'amont répond à cette requête par `201`
- **THEN** le client reçoit `504` avec le code `RESPONSE_BUDGET_WRITE_TIMEOUT` et un message qui demande de relire
  l'état avant de rejouer
- **AND** aucun octet de la réponse amont n'est écrit vers le client, et le nombre de créneaux libres est celui d'avant
  la requête

#### Scenario: Le refus n'est pas celui d'un amont lent

- **GIVEN** une requête WRITE dont l'amont répond avant l'échéance
- **WHEN** elle n'obtient aucun créneau avant l'échéance
- **THEN** son code est `RESPONSE_BUDGET_WRITE_TIMEOUT`, distinct de `UPSTREAM_WRITE_TIMEOUT` qui reste celui d'un amont
  sans réponse à l'échéance

#### Scenario: Le refus est consigné une fois

- **GIVEN** une requête WRITE autorisée et consignée `accepted` au journal d'accès
- **WHEN** elle reçoit `504 RESPONSE_BUDGET_WRITE_TIMEOUT`
- **THEN** le journal d'accès porte un seul enregistrement `completed` pour cette requête, avec le statut `504`
- **AND** la requête rend son créneau de requête et son créneau privilégié

#### Scenario: Le refus est documenté pour les clients distants

- **WHEN** l'OpenAPI du serveur distant est lue
- **THEN** elle nomme `RESPONSE_BUDGET_WRITE_TIMEOUT` parmi les réponses `504` d'un appel WRITE ou ADMIN relayé, avec
  son résultat inconnu

### Requirement: L'adaptateur HTTP n'attend aucun sémaphore sans délai

Aucune classe de `morpheus-api` NE DOIT (MUST NOT) appeler une attente de `java.util.concurrent.Semaphore` sans délai
(`acquire`, `acquireUninterruptibly`) ; une attente d'un sémaphore DOIT passer par `tryAcquire`, avec ou sans délai. Un
appel ajouté DOIT faire échouer la suite d'architecture en nommant la classe et la méthode.

#### Scenario: Une attente non bornée réintroduite

- **GIVEN** une classe de `com.morpheus.api` qui appelle `Semaphore.acquire()`
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue en nommant la classe appelante et la méthode appelée
- **AND** aucune liste d'exceptions ne permet de l'admettre

### Requirement: La réponse à une écriture relayée commence avant l'échéance de son appel

La façade DOIT (MUST) commencer sa réponse à une requête WRITE ou ADMIN relayée, réponse amont ou refus, au plus tard à
l'échéance de son appel comptée depuis l'envoi amont, et la terminer au plus tard après le délai total d'écriture d'une
réponse. Cette borne composée DOIT être écrite dans la documentation et calculée par un test depuis les constantes qui
la composent.

#### Scenario: La borne écrite suit les constantes

- **GIVEN** la documentation développeur et la documentation utilisateur du serveur distant
- **WHEN** l'échéance des écritures ou le délai total d'écriture d'une réponse change
- **THEN** le test qui calcule la borne composée échoue tant que la documentation ne la porte pas

# Proposition : borner l'attente d'un créneau de réponse du proxy distant

## Why

Sur la façade distante, une écriture (route WRITE ou ADMIN) prend son créneau de réponse une fois l'amont répondu, par
`MorpheusRemoteProxyTransport.awaitResponseBudget` (`morpheus-api/src/main/java/com/morpheus/api/remote/MorpheusRemoteProxyTransport.java:166-175`),
qui appelle `responseSlots.acquire()` sans délai (`:168`). Une lecture, elle, prend son créneau par `tryAcquire()` avant
l'appel (`:80`) et reçoit `429 RESPONSE_BUDGET_EXHAUSTED` (`:81-85`). L'attente d'une écriture n'est donc bornée que par
composition — l'échéance amont `UpstreamDeadline.WRITE` (`:250`), puis au moins un tour d'écriture d'un détenteur sous
`TimedBoundedResponseWriter.RESPONSE_TOTAL_TIMEOUT` (`transport/TimedBoundedResponseWriter.java:51`), davantage si
d'autres écritures la précèdent dans la file équitable (`:41`) — et cette borne n'est écrite nulle part ;
`docs/developer/REMOTE_SERVER_PLATFORM.md:230` dit que l'écriture « l'attend ». Un client ou un répartiteur réglé sur
l'échéance documentée des écritures abandonne alors une requête dont le commit a eu lieu, sans recevoir le refus qui
dit « résultat inconnu ». C'est le constat `AUD-PRF-20` (Faible) de l'audit
`docs/project/audits/2026-10-09/findings.md`, revérifié le 2026-11-06 sur `30520bd3` (inchangé). Le dépôt sait faire
ce qui manque : la sauvegarde distante attend son travailleur dans une admission bornée et rend deux refus nommés,
`429 DATABASE_WORKERS_BUSY` et `504 BACKUP_TIMEOUT` (ADR-0159, `MorpheusRemoteHttpServer.java:58-60`, `:366-378`).

## What Changes

- L'attente d'un créneau de réponse par une écriture est bornée par l'échéance de son appel, comptée depuis l'envoi
  amont : la façade commence sa réponse à une écriture au plus tard à cette échéance.
- À l'expiration, la façade rend un refus nommé, `504 RESPONSE_BUDGET_WRITE_TIMEOUT`, dont le message dit que
  l'opération a pu aboutir et qu'il faut relire l'état avant de rejouer, comme `UPSTREAM_WRITE_TIMEOUT`. La réponse
  amont est fermée, le créneau jamais pris n'est pas rendu.
- **Nouveau code de refus visible des clients distants** : documenté dans l'OpenAPI du serveur distant
  (`docs/developer/reference/openapi/morpheus-v1-remote-m26.yaml`) et dans la documentation utilisateur, comme
  `BACKUP_TIMEOUT`. Aucune route ne change : `contracts/public-surfaces.tsv` ne bouge pas.
- Gate ajouté : aucune classe de l'adaptateur HTTP n'attend un sémaphore sans délai.
- La borne composée d'une écriture vue du client (échéance de l'appel, puis délai total d'écriture) est écrite dans la
  documentation et calculée par un test depuis les constantes.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `remote-proxy-upstream-bounds` : l'exigence du budget mémoire des réponses ne laisse plus une écriture attendre son
  créneau sans fin ; deux exigences ajoutées disent le refus à l'expiration et l'interdit d'une attente non bornée.

## Périmètre et exclusions

**Inclus :** l'attente du créneau de réponse d'une écriture relayée par la façade distante ; son refus ; sa
documentation (OpenAPI du serveur distant, guide utilisateur, guide développeur) ; un gate sur les attentes de
sémaphore de `morpheus-api`.

**Exclus :**
- la lecture, déjà refusée avant l'appel ;
- les valeurs de `UpstreamDeadline.READ`, `UpstreamDeadline.WRITE` et des délais de `TimedBoundedResponseWriter`,
  inchangées ;
- le nombre de créneaux (`MAX_PROXY_IN_FLIGHT_BYTES / MAX_PROXY_RESPONSE_BYTES`) et le plafond privilégié, inchangés ;
- le serveur local, qui ne relaie rien.

## Acteurs et surfaces concernés

Client distant d'une route WRITE ou ADMIN relayée (rôle WRITE ou ADMIN) et répartiteur placé devant la façade ;
opérateur qui lit le journal d'accès et les compteurs de `server/status`.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| L'écriture attend son créneau par `acquire()` sans délai | existant vérifié | `MorpheusRemoteProxyTransport.java:168` |
| La lecture est refusée avant l'appel en `429 RESPONSE_BUDGET_EXHAUSTED` | existant vérifié | `MorpheusRemoteProxyTransport.java:80-85` |
| Une écriture expirée en amont reçoit `504 UPSTREAM_WRITE_TIMEOUT`, résultat inconnu | existant vérifié | `MorpheusRemoteProxyTransport.java:266-270` ; `remote-proxy-upstream-bounds` › A timed-out write SHALL be answered as an unknown outcome |
| `BACKUP_TIMEOUT` est documenté par une réponse `504` de l'opération dans l'OpenAPI distante, pas dans `contracts/public-surfaces.tsv` | existant vérifié | `morpheus-v1-remote-m26.yaml:51-58` ; `contracts/public-surfaces.tsv:54` |
| La borne de l'attente est l'échéance de l'appel, comptée depuis l'envoi | décision | `design.md`, D1 |
| Le refus est un code distinct, `504 RESPONSE_BUDGET_WRITE_TIMEOUT` | décision | `design.md`, D2 |
| Le journal d'accès n'a pas de branche propre pour ce refus | décision | `design.md`, D4 |

## Impact

- Code : `MorpheusRemoteProxyTransport` (`morpheus-api`, paquet `com.morpheus.api.remote`).
- Tests : `MorpheusRemoteProxyResponseBudgetTest` (`morpheus-api`) — deux tests existants changent de forme, des tests
  ajoutés ; une règle ajoutée dans la suite d'architecture (paquet `m26`).
- Contrats : `docs/developer/reference/openapi/morpheus-v1-remote-m26.yaml` ; `contracts/public-surfaces.tsv` inchangé.
- Documentation : `docs/developer/REMOTE_SERVER_PLATFORM.md`, `docs/user/TEAM_REMOTE_SERVER.md`, notes de version de la
  version en préparation (`docs/user/release-notes/`, lire la version dans `ProductMetadata`), page durable
  `docs/developer/specifications/remote-proxy-upstream-bounds.md` (à créer : la capacité n'en a pas).
- ADR : ADR-0159 décide des travailleurs de base de données, pas des créneaux de réponse ; ce changement suit son patron
  sans en relever (filtre de `.claude/rules/adr.md` : décision locale, réversible).

## Questions ouvertes

- Faut-il dire, dans le message du refus, le statut que l'amont avait répondu (par exemple `201`) ? Il renseigne le
  client sans relayer le corps ; à trancher par le mainteneur (voir `design.md`).

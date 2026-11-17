# Proposition : fermer une connexion remote qui s'arrête avant l'authentification (AUD-SEC-19)

## Why

Le serveur HTTPS remote garde ouverte, sans limite de temps ni de nombre, une connexion qui n'a pas fini sa poignée
TLS ou ses en-têtes HTTP. Constat `AUD-SEC-19` (Élevée) de l'audit du 17/11/2026
(`docs/project/audits/2026-11-17/findings.md`), reproduit par l'auditeur sur OpenJDK 21 : un client qui envoie six
octets d'un ClientHello puis se tait, et un client qui envoie `GET / HTTP/1.1` et `Host:` sans la ligne vide, sont
encore ouverts après 100 s ; une connexion muette est fermée en 39 s.

Revérifié sur `1a539c22` : `MorpheusRemoteHttpServerBootstrap` crée le serveur par `HttpsServer.create` sans poser de
propriété `jdk.httpserver.*` ni `sun.net.httpserver.*` ; aucune source principale ni aucun script de distribution ne
les pose. Les échéances de la façade (`MorpheusRemoteHttpServer.REQUEST_BODY_READ_TIMEOUT`, budgets de
`TimedBoundedResponseWriter` d'ADR-0099) ne commencent qu'après la lecture des en-têtes par `jdk.httpserver`. Ces
connexions n'atteignent jamais l'authentification : ni le sémaphore d'authentification, ni le compteur de 401, ni le
journal d'accès ne les voient. Un simple accès réseau au port suffit à épuiser descripteurs et fils virtuels.

## What Changes

- Le serveur remote ferme en temps borné une connexion qui n'a pas fini sa poignée TLS ou ses en-têtes.
- Le nombre de connexions ouvertes simultanément sur le serveur remote est borné.
- La borne et ce qu'elle ne couvre pas sont écrits dans `SECURITY.md` et dans le guide `docs/user/TEAM_REMOTE_SERVER.md`.
- Le **mécanisme** dépend d'une décision du mainteneur (design.md, D1) : ADR-0099 écarte l'appui sur une propriété
  interne de `jdk.httpserver` pour la durée de vie d'une réponse ; la même raison vaut pour la phase qui précède la
  requête.

## Capabilities

### New Capabilities
- `remote-pre-authentication-bounds` : ce que le serveur remote borne avant qu'une requête soit authentifiée — durée
  d'une connexion qui n'a pas fini sa poignée ou ses en-têtes, nombre de connexions ouvertes.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `MorpheusRemoteHttpServerBootstrap`, le lancement du mode remote (`morpheus-cli`, lanceur natif et
`distribution/` selon l'option retenue), `SECURITY.md`, `docs/user/TEAM_REMOTE_SERVER.md`,
`docs/developer/REMOTE_SERVER_PLATFORM.md`, un test d'intégration remote, la page durable de la capacité.

**Exclus :** le serveur local (loopback, sans TLS) — sa frontière est le changement `decide-who-the-local-api-trusts` ;
les budgets d'écriture de réponse (ADR-0099), inchangés ; le remplacement de `jdk.httpserver`.

## Acteurs et surfaces concernés

Un client réseau non authentifié qui atteint le port du serveur remote ; l'opérateur qui déploie le mode remote.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Aucune propriété `jdk.httpserver.*` / `sun.net.httpserver.*` n'est posée | existant vérifié | `grep` sur les sources, scripts et `distribution/` à `1a539c22` |
| Les échéances de la façade commencent après les en-têtes | existant vérifié | `MorpheusRemoteHttpServer.REQUEST_BODY_READ_TIMEOUT`, ADR-0099 |
| Connexion à poignée partielle encore ouverte après 100 s | mesure datée | audit du 17/11/2026, OpenJDK 21 |
| Le mécanisme de borne | question ouverte | design.md, D1 — décision du mainteneur |
| Les valeurs des deux bornes | question ouverte | design.md, Questions ouvertes |

## Impact

`morpheus-api` (bootstrap remote), éventuellement `morpheus-cli` et `distribution/` ; un test d'intégration sous
`morpheus-api/src/test`. ADR-0099 est concerné : selon l'option retenue, un ADR consolidé le remplace (« ADR à
proposer », jamais accepté par ce changement). Aucune ligne de `contracts/public-surfaces.tsv`, aucune OpenAPI.

## Questions ouvertes

- **Décision du mainteneur** : borner par les propriétés de `jdk.httpserver` posées au lancement, exiger un terminateur
  TLS devant le serveur, ou les deux (design.md, D1).
- Valeurs de la durée maximale avant requête et du nombre maximal de connexions (design.md).

# Proposition : empêcher qu'un flot de lectures chasse l'attribution du journal d'accès (AUD-DEP-32)

## Why

Un principal de rôle READ peut faire tourner le journal d'accès distant jusqu'à en chasser les traces des écritures et
des actions d'administration. Constat `AUD-DEP-32` (Faible) de l'audit du 17/11/2026
(`docs/project/audits/2026-11-17/findings.md`).

Revérifié sur `1a539c22` :

- `RemoteAccessJournal` borne le journal à `MAX_FILE_BYTES` par fichier et `KEPT_GENERATIONS` générations gardées,
  par `RotatingOperationalLogFile`, dont la rotation supprime la génération la plus ancienne ;
- `completed(...)` écrit une ligne pour **toute** requête autorisée, lectures comprises, dans le `finally` de
  `MorpheusRemoteHttpServer.serve` ; la seule limite est de concurrence (`429`), aucune de débit ;
- ADR-0113 écarte la journalisation des 401 parce qu'un journal qu'un appelant non authentifié peut remplir est un
  journal qu'il peut faire tourner ; il ne traite pas le même risque pour un principal authentifié de rôle READ.

L'auditeur estime qu'environ 85 000 lectures (`GET /api/v1/health`) suffisent à remplir la fenêtre : l'attribution
qu'ADR-0113 promet disparaît précisément après l'abus d'un jeton WRITE ou ADMIN.

## What Changes

- La décision de rétention est prise par le mainteneur (design.md, D1) entre : séparer la rétention des
  enregistrements d'écriture et d'administration de celle des lectures, borner le débit de lecture par principal, ou
  écrire le risque et sa parade (collecte continue) dans `SECURITY.md` et ADR-0113.
- Selon l'option recommandée (à valider) : les enregistrements des requêtes qui modifient quelque chose sont gardés
  dans une rétention que les lectures ne consomment pas ; un flot de lectures ne chasse plus une ligne d'écriture.
- Dans tous les cas, `SECURITY.md` (section access journal) dit ce qui peut faire tourner le journal et ce qui reste
  garanti.

## Capabilities

### New Capabilities

### Modified Capabilities
- `remote-access-journal` : un flot de lectures ne chasse pas l'enregistrement d'une écriture ou d'une action
  d'administration, et le risque de rotation est écrit.

## Périmètre et exclusions

**Inclus :** `RemoteAccessJournal` (`morpheus-api`, `com.morpheus.api.remote`), éventuellement
`RotatingOperationalLogFile` (`morpheus-application`, `com.morpheus.application.operability`), leurs tests,
`SECURITY.md`, `docs/user/TEAM_REMOTE_SERVER.md`, la page durable `remote-access-journal`, et selon D1 un ADR consolidé
qui remplace ADR-0113.

**Exclus :** une route de lecture du journal (écartée par ADR-0113) ; la journalisation des 401, inchangée ; le
plafond de concurrence de la façade.

## Acteurs et surfaces concernés

Un principal remote de rôle READ (ou l'attaquant qui en détient le jeton) ; l'opérateur qui enquête sur l'usage d'un
jeton WRITE ou ADMIN.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Une ligne par requête autorisée, lectures comprises, dans une seule rotation | existant vérifié | `RemoteAccessJournal.completed`, `MorpheusRemoteHttpServer.serve` |
| La rotation supprime la génération la plus ancienne | existant vérifié | `RotatingOperationalLogFile` |
| ADR-0113 ne traite le risque de rotation que pour un appelant non authentifié | existant vérifié | `docs/architecture/adr/SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.md` |
| Environ 85 000 requêtes remplissent la fenêtre | estimation datée | audit du 17/11/2026 (≈ 240 octets par ligne) |
| La parade retenue | question ouverte | design.md, D1 — décision du mainteneur |

## Impact

`morpheus-api` (remote), éventuellement `morpheus-application` ; aucune surface publique ni OpenAPI. ADR-0113 est
concerné : si la décision change (rétention séparée ou borne de débit), un ADR consolidé le remplace (« ADR à
proposer », jamais accepté par ce changement).

## Questions ouvertes

- **Décision du mainteneur** : rétention séparée des écritures, borne de débit de lecture par principal, ou risque
  documenté seulement (design.md, D1).

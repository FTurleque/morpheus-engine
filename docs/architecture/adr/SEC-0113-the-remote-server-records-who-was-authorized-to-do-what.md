---
id: SEC-0113
title: "Le serveur distant consigne qui a été autorisé à faire quoi"
domain: security
rules: true
files: ["docs/user/TEAM_REMOTE_SERVER.md"]
---

# Le serveur distant consigne qui a été autorisé à faire quoi

- Statut : **Acceptée**
- Date : 2026-10-13

## Context

Le serveur distant authentifie chaque requête et autorise une seule fois sa route
(`MorpheusRemoteRoutePolicy.authorize`), qui fournit le gabarit de route et le rôle exigé ; chaque réponse porte un
identifiant de requête dans `X-Request-Id`. L'audit tenu à côté du fichier d'identités consigne ce qui arrive aux
identifiants (création, révocation, rotation), jamais ce à quoi ils servent. Sans autre trace, après l'usage abusif
d'un jeton d'écriture ou d'administration, rien ne dit quel principal a enregistré un projet, lancé une
synchronisation ou pris une sauvegarde, ni quand : le jeton à révoquer ne peut pas être désigné.

Options écartées :

- **Tout servir et compter.** Une mutation non attribuée passerait dès que le disque est plein, c'est-à-dire au moment
  où quelqu'un a pu le remplir exprès.
- **Tout refuser, lectures comprises.** Un disque plein rendrait le serveur muet, y compris pour la route de statut
  qui dit pourquoi.
- **Consigner le chemin brut.** Il porte des identifiants et son contenu est choisi par l'appelant.
- **Consigner les 401.** Le 401 n'a pas de principal : un journal qu'un appelant non authentifié peut remplir est un
  journal qu'il peut faire tourner jusqu'à en chasser ce qui compte ; il reste un compteur. Le 403, lui, a un
  principal, et c'est ce qu'une enquête cherche.
- **Étendre l'audit du fichier d'identités.** Il est borné à un nombre d'événements et réécrit avec le fichier : le
  trafic y chasserait les mutations d'identifiants, et chaque requête réécrirait le fichier qui authentifie.
- **Une route pour lire le journal.** C'est une surface de plus, avec sa ligne au manifeste et son rôle ; une décision
  séparée. Le journal est un fichier que l'opérateur lit.

Raisons des règles retenues. Une ligne d'écriture produite seulement après la réponse manquerait pour la requête qui
a fait tomber le serveur, d'où la ligne `accepted` avant le service. Une route d'administration consultée en `GET`
(comme les métriques) compte comme une lecture : la refuser faute de journal retirerait à l'opérateur le compteur
`remote.access.journal.lost` au moment où il en a besoin. Une écriture de fichier placée entre la réponse et la
fermeture laisse le serveur HTTP du JDK fermer une connexion dont le corps de requête n'a pas été lu (après un 403
rendu sans lire le corps), et la requête suivante du client part sur une connexion en cours de fermeture. Attribuer une
mutation est la raison d'être du journal, d'où le refus d'une écriture qu'il ne peut consigner.

## Decision

1. **Fichier à part** : `morpheus-remote-access.jsonl` (`RemoteAccessJournal.FILE_NAME`), dans le répertoire du
   fichier d'identités, jamais dans ce fichier ni dans son audit. Un objet JSON par ligne, privé au compte, borné en
   taille et en générations gardées (`RemoteAccessJournal.MAX_FILE_BYTES`, `RemoteAccessJournal.KEPT_GENERATIONS`),
   avec l'écrivain et la discipline d'écriture du journal opérationnel en fichier (ADR-0146).
2. **Contenu** : l'instant, l'identifiant de requête, le principal, le rôle détenu, le rôle exigé, la méthode, le
   **gabarit** de route (`projects/{projectId}/sync`, issu de la table des routes), le statut. Rien d'autre : ni
   chemin, ni requête, ni en-tête, ni corps, ni jeton, ni empreinte de jeton ; le rédacteur passe sur chaque champ.
3. **Quoi consigner** : un 403 est consigné ; un 401 ne l'est pas (il reste un compteur), ni un 404 ou un 405 d'un
   appelant authentifié.
4. **Deux lignes pour une mutation** : une écriture ou une administration laisse `accepted` avant d'être servie, puis
   `completed` avec son statut ; une lecture laisse une ligne, à la fin. Une route d'administration en `GET` est une
   lecture, par le critère de `usesPrivilegedConcurrency`.
5. **Un seul endroit** : l'issue s'écrit dans le `finally` de `serve`, par lequel passent toutes les sorties. Ordre :
   lire le statut sur l'échange, fermer l'échange, **puis** écrire ; une réponse jamais envoyée est consignée
   `unsent`.
6. **Échec d'écriture** : une écriture ou une administration dont la ligne `accepted` ne s'écrit pas est refusée,
   `503 ACCESS_JOURNAL_UNAVAILABLE`, avant d'être servie ; une lecture est servie ; dans les deux cas
   `remote.access.journal.lost` monte. Un serveur dont le journal ne peut pas être ouvert ne démarre pas.

## Do's and Don'ts

### Do

- Écrire le journal d'accès dans `morpheus-remote-access.jsonl`, à côté du fichier d'identités, distinct de ce fichier
  et de son audit.
- Consigner les 403 avec le principal, le rôle détenu et le rôle exigé.
- Écrire la ligne `accepted` d'une écriture ou d'une administration après le contrôle du rôle et avant tout service.
- Écrire l'issue une seule fois, dans le `finally` de `serve`, après avoir lu le statut et fermé l'échange.
- Consigner `unsent` comme statut d'une réponse jamais envoyée.
- Refuser par `503 ACCESS_JOURNAL_UNAVAILABLE` une écriture ou une administration dont la ligne `accepted` ne s'écrit
  pas, servir une lecture, et compter la perte dans `remote.access.journal.lost`.
- Refuser de démarrer quand le journal ne peut pas être ouvert.

### Don't

- Ne pas consigner les 401, ni les 404 et 405 d'un appelant authentifié.
- Ne pas consigner le chemin, la requête, un en-tête, le corps, un jeton ni une empreinte de jeton : seulement le
  gabarit de route issu de la table des routes.
- Ne pas laisser `RemoteAccessJournal` lire l'échange HTTP ou un jeton.
- Ne pas écrire l'issue branche par branche, ni entre l'envoi de la réponse et `exchange.close()`.
- Ne pas faire figurer l'emplacement du journal dans une réponse distante ni dans le message du refus de démarrage.

## Consequences

### Positives

- Une mutation distante est attribuable à un principal, et l'enregistrement se joint à la réponse par `X-Request-Id`.
- Un appelant non authentifié ne peut ni remplir ni faire tourner le journal.

### Négatives

- Un disque plein ou un journal rendu inécrivable refuse les écritures distantes. C'est voulu, et le guide de
  l'opérateur le dit avec le nom du refus.
- Chaque requête ajoute une ligne sous verrou, deux pour une mutation ; le fichier n'est pas tenu ouvert. Les ajouts se
  sérialisent sur le verrou : le coût croît avec le nombre de requêtes simultanées, accepté devant celui d'une
  écriture SQLite et à réévaluer si le profil distant devient une contrainte.
- `503` s'ajoute aux statuts que la façade peut rendre sur une route d'écriture ou d'administration.

### Risques

- La rotation perd les enregistrements les plus anciens : un journal à conserver doit être collecté par l'opérateur.
- Le coût du verrou sous forte concurrence n'est pas mesuré sous Linux.

## Compliance and Enforcement

- Règle Archgate `SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.rules.ts` : `operator-guide-names-the-journal-refusal`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m26/RemoteAccessJournalArchitectureTest.java` :
  `theOutcomeIsRecordedOnceInTheFinallyOfServeAndTheAcceptanceBeforeAnythingIsServed` (issue écrite une fois dans le
  `finally`, statut lu avant la fermeture, fermeture avant l'écriture, acceptation après le contrôle du rôle et avant
  tout service), `aRecordIsBuiltFromTheRouteTemplateAndNeverFromThePath` (gabarit de route, jamais le chemin ; le
  journal ne lit ni l'échange ni un jeton).
- `morpheus-api/src/test/java/com/morpheus/api/remote/RemoteAccessJournalHttpTest.java` : à travers un vrai serveur, lecture,
  refus de rôle et écritures laissent les enregistrements attendus joints par `X-Request-Id` ; un appelant sans jeton
  valide et une route inconnue n'en laissent aucun ; ni jeton, ni empreinte, ni identifiant de chemin ; journal
  inécrivable : `503 ACCESS_JOURNAL_UNAVAILABLE` sans emplacement pour l'écriture, lecture servie.
- `morpheus-api/src/test/java/com/morpheus/api/remote/RemoteAccessJournalTest.java` : une ligne pour une lecture, deux pour une
  écriture ou une administration, principal en forme de secret rédigé, rotation à la borne, perte comptée, refus
  d'ouverture sans emplacement.

## References

- [ADR-0105](SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) — aucun secret réutilisable persisté.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — une réponse dit ce qu'elle n'a pas pu observer.
- [ADR-0146](BE-0146-journal-operationnel-slf4j-et-fichier-borne.md) — journal opérationnel borné en fichier.
- [Guide du serveur d'équipe](../../user/TEAM_REMOTE_SERVER.md)
- `SECURITY.md`

# ADR-0113 — Le serveur distant consigne qui a été autorisé à faire quoi

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 13 octobre 2026
- Dépend de : ADR-0105 (aucun secret réutilisable persisté), ADR-0108 (une réponse dit ce qu'elle n'a pas pu
  observer), ADR-0112 (journal opérationnel borné en fichier)
- Portée : `morpheus-api` (`RemoteAccessJournal`, `MorpheusRemoteHttpServer.serve`), `morpheus-application`
  (`RotatingOperationalLogFile.openFile`, `OperationalEventCode.REMOTE_ACCESS`)

## Contexte

Le serveur distant authentifiait chaque requête dans une variable locale et n'en lisait que le rôle. Le nom du
principal quittait la méthode avec sa pile. L'audit tenu à côté du fichier d'identités consigne ce qui arrive aux
identifiants - création, révocation, rotation - jamais ce à quoi ils servent. Après l'usage abusif d'un jeton
d'écriture ou d'administration, rien ne disait quel principal avait enregistré un projet, lancé une synchronisation ou
pris une sauvegarde, ni quand : le jeton à révoquer ne pouvait pas être désigné (constat AUD-SEC-04 de l'audit du
9 octobre 2026).

Les pièces d'un enregistrement existaient déjà : un identifiant de requête renvoyé dans `X-Request-Id`, et, depuis
l'autorisation unique (`MorpheusRemoteRoutePolicy.authorize`), le gabarit de route et le rôle exigé.

## Décision

1. **Un journal d'accès, fichier à part.** `morpheus-remote-access.jsonl`, dans le répertoire du fichier
   d'identités, jamais dans ce fichier ni dans son audit. Un objet JSON par ligne, borné à 4 Mio, quatre générations
   gardées, privé au compte : la discipline d'écriture du journal opérationnel en fichier (ADR-0112), dont il reprend
   l'écrivain.

2. **Ce qu'un enregistrement contient.** L'instant, l'identifiant de requête, le principal, le rôle détenu, le rôle
   exigé, la méthode, le **gabarit** de route, le statut. Rien d'autre.

3. **Rien que l'appelant ait choisi, aucun jeton sous aucune forme.** Le gabarit vient de la table des routes
   (`projects/{projectId}/sync`, jamais l'identifiant) ; la méthode est une de celles que la table a acceptées. Ni
   chemin, ni requête, ni en-tête, ni corps, ni jeton, ni empreinte de jeton. Le rédacteur passe sur chaque champ.

4. **Un refus de rôle est consigné ; un échec d'authentification ne l'est pas.** Le 403 a un principal, et c'est ce
   qu'une enquête cherche. Le 401 n'en a pas : un journal qu'un appelant non authentifié peut remplir est un journal
   qu'il peut faire tourner jusqu'à en chasser ce qui compte. Il reste le compteur qu'il était. Une requête
   authentifiée pour une route qui n'existe pas (404) ou une méthode refusée (405) n'a pas été autorisée et n'est pas
   consignée non plus.

5. **Une écriture ou une administration laisse deux lignes.** `accepted` avant d'être servie, `completed` avec son
   statut. Une ligne écrite seulement après la réponse manquerait pour la requête qui a fait tomber le serveur. Une
   lecture laisse une ligne, à la fin. La consultation d'une route d'administration (`GET`, comme les métriques)
   compte comme une lecture, par le même critère que la concurrence privilégiée
   (`usesPrivilegedConcurrency`) : refuser cette route faute de journal retirerait à l'opérateur le compteur
   `remote.access.journal.lost` au moment où il en a besoin.

6. **L'issue est écrite à un seul endroit.** Le `finally` de `serve`, par lequel passent toutes les sorties - servie
   par la façade, relayée, refusée, en échec. Le statut y est lu sur l'échange ; une réponse jamais envoyée est
   consignée `unsent`.

7. **Quand un enregistrement ne peut pas être écrit.** Une requête d'écriture ou d'administration dont la ligne
   `accepted` ne s'écrit pas est refusée, `503 ACCESS_JOURNAL_UNAVAILABLE`, avant d'être servie : attribuer une
   mutation est la raison d'être du journal. Une lecture est servie. Dans les deux cas le compteur
   `remote.access.journal.lost` monte. Un serveur dont le journal ne peut pas être ouvert ne démarre pas.

Le point 7 était laissé au mainteneur par la spécification
(`openspec/changes/record-who-acted-on-the-remote-server`, décision 4). Il a lancé le lot sans l'amender ; c'est
l'option recommandée qui est appliquée.

## Alternatives écartées

- **Tout servir et compter.** Une mutation non attribuée passerait dès que le disque est plein, c'est-à-dire au moment
  où quelqu'un a pu le remplir exprès.
- **Tout refuser, lectures comprises.** Un disque plein rendrait le serveur muet, y compris pour la route de statut
  qui dit pourquoi.
- **Consigner le chemin brut.** Il porte des identifiants et son contenu est choisi par l'appelant.
- **Consigner les 401.** Voir le point 4.
- **Étendre l'audit du fichier d'identités.** Il est borné à un nombre d'événements et réécrit avec le fichier : le
  trafic y chasserait les mutations d'identifiants, et chaque requête réécrirait le fichier qui authentifie.
- **Une route pour lire le journal.** C'est une surface de plus, avec sa ligne au manifeste et son rôle ; une décision
  séparée. Le journal est un fichier que l'opérateur lit.

## Conséquences

- Un disque plein ou un journal rendu inécrivable refuse les écritures distantes. C'est voulu, et c'est dit dans le
  guide de l'opérateur avec le nom du refus.
- Un ajout sous verrou par requête, deux pour une mutation. Le fichier n'est pas tenu ouvert.
- La rotation perd les enregistrements les plus anciens : un journal à conserver doit être collecté.
- L'emplacement du journal n'apparaît dans aucune réponse distante ; le message du refus de démarrage ne le nomme pas.
- `503` s'ajoute aux statuts que la façade peut rendre sur une route d'écriture ou d'administration.

## Preuves exécutables

- `RemoteAccessJournalHttpTest`, à travers un vrai serveur : une lecture, un refus de rôle, une écriture et une
  écriture sur un chemin à identifiant laissent les enregistrements attendus, joints à la réponse par `X-Request-Id` ;
  un appelant sans jeton valide et une route inconnue n'en laissent aucun ; le journal ne contient ni jeton, ni
  empreinte, ni identifiant de chemin ; journal inécrivable, l'écriture reçoit `503 ACCESS_JOURNAL_UNAVAILABLE` sans
  emplacement du serveur et la lecture est servie.
- `RemoteAccessJournalTest` : une ligne pour une lecture, deux pour une écriture ou une administration ; un principal
  en forme de secret est rédigé ; rotation à la borne ; enregistrement perdu compté ; refus d'ouverture sans
  emplacement.
- `RemoteAccessJournalArchitectureTest` : l'issue est écrite une fois, dans le `finally` de `serve`, avant la fermeture
  de l'échange ; l'acceptation précède tout service et suit le contrôle du rôle ; le journal ne peut lire ni l'échange
  ni un jeton. Rompue une fois le 13 octobre 2026 (l'écriture de l'issue retirée), puis rétablie.

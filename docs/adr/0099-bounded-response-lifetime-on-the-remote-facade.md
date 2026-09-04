# ADR-0099 — Durée de vie bornée d'une réponse sur la façade remote

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 4 septembre 2026
- Dépend de : ADR-0065, ADR-0094
- Portée : façade HTTPS remote, écriture des réponses, disponibilité sous client hostile

## Contexte

ADR-0065 retient `jdk.httpserver` : aucune dépendance, aucun cycle de vie à gérer, aucune surface de
configuration à sécuriser. ADR-0094 en fait la base de la façade remote, avec TLS, bearer, RBAC et
plusieurs budgets explicites — concurrence globale, concurrence privilégiée, mémoire de réponse
proxifiée, taille de réponse, deadline de lecture du corps de requête.

Ces budgets partagent une propriété que l'audit a rendue visible : **ils sont tous pris avant
l'écriture de la réponse et rendus après elle**, dans le `finally` du handler. Le handler ne rend la
main qu'une fois le dernier octet écrit.

Un client qui négocie TLS, s'authentifie, envoie une requête parfaitement valide puis **cesse
simplement de lire** n'a donc rien de malformé à envoyer. L'écriture bloque sur une socket pleine, et
permit de requête, slot de réponse et thread restent pris aussi longtemps que le client reste
connecté. Quelques clients de ce type ferment la façade à tous les autres. Borner la taille d'une
réponse n'y change rien : le coût est du **temps**, pas de la mémoire.

`jdk.httpserver` n'expose aucun timeout d'écriture. Sa seule deadline de réponse est la propriété
système `sun.net.httpserver.maxRspTime`, non documentée, non spécifiée, et posée hors du processus :
un déploiement qui l'oublie n'a aucune borne du tout.

## Décision

### 1. Deux budgets, parce que « lent » et « arrêté » ne sont pas la même chose

Toute écriture de réponse de la façade remote — enveloppe JSON **et** corps proxifié — passe par
`TimedBoundedResponseWriter`, qui applique :

| Budget | Valeur | Ce qu'il borne |
|---|---|---|
| Stall | 15 s | temps **sans progression**, réarmé à chaque bloc réellement écrit vers le client |
| Total | 120 s | durée complète d'une réponse, quelle que soit la progression |

Un seul budget ne suffit pas dans les deux sens : un budget total seul ferait du débit du client une
condition de service et couperait le premier client honnête sur une mauvaise liaison ; un budget de
stall seul autoriserait un client à accepter un bloc par fenêtre indéfiniment.

Le chemin de l'enveloppe est couvert comme celui du corps. C'est le demi-mesure tentante — les
grosses réponses proxifiées sont là où ça fait mal — mais l'enveloppe est ce que **tout refus et
toute erreur** empruntent.

### 2. La deadline s'appuie sur un contrat spécifié, pas sur une propriété interne

La deadline interrompt le thread écrivain. Interrompre un thread bloqué sur un canal
`java.nio.channels.InterruptibleChannel` **ferme ce canal** et lève `ClosedByInterruptException` :
c'est le contrat documenté de `java.nio.channels`, et `jdk.httpserver` écrit à travers un
`SocketChannel` bloquant.

```text
deadline -> interrupt(thread écrivain) -> canal fermé -> écriture débloquée -> finally -> slots rendus
```

MORPHEUS **ne s'appuie pas** sur `sun.net.httpserver.maxRspTime`, et aucun code de production ne la
positionne.

### 3. L'écriture reste sur le thread appelant

La réponse est écrite par le thread du handler, pas par un thread emprunté. Confier l'écriture à un
autre thread laisserait `exchange.close()` courir après le moniteur du flux de réponse contre un
écrivain encore bloqué — le seul endroit où le remède pourrait bloquer plus durablement que le mal.
Le coût est un thread démon de surveillance par façade, pas un par requête.

### 4. L'événement est compté à part

`GET /api/v1/server/status` expose `responseWriteTimeouts`, distinct de `requestTimeouts`. Un timeout
de requête est un client qui a cessé d'**envoyer** ; celui-ci est un client qui a cessé de
**recevoir**, et seul le second retient un slot tant qu'il reste connecté. Les agréger effacerait la
seule différence utile à l'exploitation.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| S'appuyer sur `sun.net.httpserver.maxRspTime` | Propriété interne non documentée, posée hors du processus : un déploiement qui l'oublie n'a aucune borne. Interdit par `rules/security.md` (pas de garantie reposant sur une propriété JDK interne) |
| Fermer l'`HttpExchange` depuis un thread de surveillance | `exchange.close()` ferme le flux de réponse, dont `close()` est synchronisé sur le moniteur que l'écrivain bloqué détient. Le surveillant se bloquerait à son tour |
| Écrire la réponse sur un thread emprunté et l'annuler | Même course de moniteur, déplacée sur le thread du handler au moment du `close()` |
| Augmenter le nombre de threads / de permits | Déplace le seuil sans le supprimer, et affaiblit des budgets dont l'existence est la protection |
| Remplacer `jdk.httpserver` par Jetty/Netty | Décision de dépendance majeure pour une seule propriété. Les critères objectifs de remplacement sont documentés dans `docs/user/TEAM_REMOTE_SERVER.md` et aucun n'est franchi |
| Renvoyer une enveloppe d'erreur au client coupé | Impossible : la connexion qui la porterait est précisément la ressource récupérée |

## Conséquences

- Une réponse abandonnée est **avortée**, pas terminée proprement : le client voit un corps tronqué
  sur une connexion fermée, sans enveloppe d'erreur. C'est la limite résiduelle assumée, documentée
  dans `SECURITY.md` et dans le guide opérateur. L'alternative serait de garder le slot jusqu'à ce
  que le client veuille bien le rendre.
- Un client lent mais qui continue de consommer est servi intégralement, quel que soit son débit.
- `BoundaryResilienceContractTest` verrouille l'application de la deadline sur les deux chemins
  d'écriture, l'interdiction de la propriété JDK interne, et le fait que l'abandon soit exercé depuis
  une socket réelle.
- `MorpheusRemoteAdversarialClientTest` (socket TLS brute contre la vraie façade) et
  `TimedBoundedResponseWriterTest` (canaux TCP réels) prouvent les trois comportements : arrêt de
  lecture, lecture lente, disparition brutale.

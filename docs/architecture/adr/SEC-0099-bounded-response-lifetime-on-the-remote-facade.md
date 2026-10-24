---
id: SEC-0099
title: "Durée de vie bornée d'une réponse sur la façade remote"
domain: security
rules: false
---

# Durée de vie bornée d'une réponse sur la façade remote

- Statut : **Acceptée**
- Date : 2026-09-04

## Context

ADR-0065 retient `jdk.httpserver` : aucune dépendance, aucun cycle de vie à gérer, aucune surface de configuration à
sécuriser. ADR-0094 en fait la base de la façade remote, avec TLS, bearer, RBAC et plusieurs budgets explicites :
concurrence globale, concurrence privilégiée, mémoire de réponse proxifiée, taille de réponse, deadline de lecture du
corps de requête.

Ces budgets sont tous pris avant l'écriture de la réponse et rendus après elle, dans le `finally` du handler, qui ne
rend la main qu'une fois le dernier octet écrit. Un client qui négocie TLS, s'authentifie, envoie une requête valide
puis **cesse de lire** n'a rien de malformé à envoyer : l'écriture bloque sur une socket pleine, et permis de requête,
slot de réponse et thread restent pris tant que le client reste connecté. Quelques clients de ce type ferment la
façade à tous les autres. Borner la taille d'une réponse n'y change rien : le coût est du temps, pas de la mémoire.

`jdk.httpserver` n'expose aucun timeout d'écriture. Sa seule deadline de réponse est la propriété système
`sun.net.httpserver.maxRspTime`, non documentée, non spécifiée et posée hors du processus : un déploiement qui
l'oublie n'a aucune borne.

Options écartées :

| Option | Raison du rejet |
|---|---|
| S'appuyer sur `sun.net.httpserver.maxRspTime` | Propriété interne non documentée, posée hors du processus : un déploiement qui l'oublie n'a aucune borne |
| Fermer l'`HttpExchange` depuis un thread de surveillance | `close()` du flux de réponse est synchronisé sur le moniteur que l'écrivain bloqué détient : le surveillant se bloquerait à son tour |
| Écrire la réponse sur un thread emprunté et l'annuler | Même course de moniteur, déplacée sur le thread du handler au moment du `close()` |
| Augmenter le nombre de threads ou de permis | Déplace le seuil sans le supprimer, et affaiblit des budgets dont l'existence est la protection |
| Remplacer `jdk.httpserver` par Jetty ou Netty | Décision de dépendance majeure pour une seule propriété ; les critères de remplacement sont dans le guide opérateur et aucun n'est franchi |
| Renvoyer une enveloppe d'erreur au client coupé | Impossible : la connexion qui la porterait est précisément la ressource récupérée |

## Decision

**1. Deux budgets, parce que « lent » et « arrêté » ne sont pas la même chose.** Toute écriture de réponse de la
façade remote, enveloppe JSON **et** corps proxifié, passe par `TimedBoundedResponseWriter`, qui applique :

| Budget | Constante | Ce qu'il borne |
|---|---|---|
| Stall | `TimedBoundedResponseWriter.RESPONSE_STALL_TIMEOUT` | temps **sans progression**, réarmé à chaque bloc réellement écrit vers le client |
| Total | `TimedBoundedResponseWriter.RESPONSE_TOTAL_TIMEOUT` | durée complète d'une réponse, quelle que soit la progression |

Un budget total seul ferait du débit du client une condition de service et couperait un client honnête sur une
mauvaise liaison ; un budget de stall seul autoriserait un client à accepter un bloc par fenêtre indéfiniment. Le
chemin de l'enveloppe est couvert comme celui du corps, parce que l'enveloppe est ce que tout refus et toute erreur
empruntent.

**2. La deadline s'appuie sur un contrat spécifié, pas sur une propriété interne.** La deadline interrompt le thread
écrivain. Interrompre un thread bloqué sur un `java.nio.channels.InterruptibleChannel` ferme ce canal et lève
`ClosedByInterruptException` : c'est le contrat documenté de `java.nio.channels`, et `jdk.httpserver` écrit à travers
un `SocketChannel` bloquant.

```text
deadline -> interrupt(thread écrivain) -> canal fermé -> écriture débloquée -> finally -> slots rendus
```

MORPHEUS ne s'appuie pas sur `sun.net.httpserver.maxRspTime`, et aucun code de production ne la positionne.

**3. L'écriture reste sur le thread appelant.** La réponse est écrite par le thread du handler, pas par un thread
emprunté : sinon `exchange.close()` courrait après le moniteur du flux de réponse contre un écrivain encore bloqué. Le
coût est un thread démon de surveillance par façade, pas un par requête.

**4. L'événement est compté à part.** `GET /api/v1/server/status` expose `responseWriteTimeouts`, distinct de
`requestTimeouts`. Un timeout de requête est un client qui a cessé d'**envoyer** ; celui-ci est un client qui a cessé
de **recevoir**, et seul le second retient un slot tant qu'il reste connecté.

## Do's and Don'ts

### Do

- Écrire toute réponse de la façade remote, enveloppe et corps proxifié, par `TimedBoundedResponseWriter`.
- Appliquer les deux budgets, stall et total, à chaque réponse.
- Écrire la réponse sur le thread du handler et faire appliquer la deadline par interruption de ce thread.
- Compter les abandons d'écriture dans `responseWriteTimeouts`, séparément de `requestTimeouts`.

### Don't

- Ne pas positionner ni invoquer `sun.net.httpserver.maxRspTime` dans le code de production.
- Ne pas fermer l'`HttpExchange` depuis un thread de surveillance, ni déléguer l'écriture à un autre thread.
- Ne pas agréger les timeouts de réponse et de requête dans un même compteur.

## Consequences

### Positives

- Un client qui cesse de lire ne retient pas indéfiniment un permis, un slot de réponse et un thread.
- Un client lent mais qui continue de consommer est servi intégralement, quel que soit son débit.
- L'exploitation distingue un client qui n'envoie plus d'un client qui ne reçoit plus.

### Négatives

- Une réponse abandonnée est **avortée**, pas terminée proprement : le client voit un corps tronqué sur une connexion
  fermée, sans enveloppe d'erreur. Cette limite résiduelle est documentée dans `SECURITY.md` et dans le guide
  opérateur.

### Risques

- La garantie repose sur le fait que `jdk.httpserver` écrit à travers un canal interruptible ; un changement de son
  implémentation devrait être détecté par les tests sur socket réelle.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/BoundaryResilienceContractTest.java` :
  `bothRemoteResponseWritePathsRunUnderTheResponseDeadline` et
  `theResponseDeadlineDoesNotRestOnAnInternalJdkProperty`.
- `morpheus-api/src/test/java/com/morpheus/api/remote/MorpheusRemoteAdversarialClientTest.java` (socket TLS brute contre la
  vraie façade) et `morpheus-api/src/test/java/com/morpheus/api/TimedBoundedResponseWriterTest.java` (canaux TCP
  réels) prouvent l'arrêt de lecture, la lecture lente et la disparition brutale du client.

## References

- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API locale sur `jdk.httpserver`.
- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — mode serveur remote d'équipe.
- [Serveur remote d'équipe](../../user/TEAM_REMOTE_SERVER.md)
- `SECURITY.md`

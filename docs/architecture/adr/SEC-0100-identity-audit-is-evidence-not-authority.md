---
id: SEC-0100
title: "L'audit d'identités est une preuve, pas une autorité"
domain: security
rules: false
---

# L'audit d'identités est une preuve, pas une autorité

- Statut : **Acceptée**
- Date : 2026-09-04

## Context

ADR-0094 place les identités remote et leur audit dans un **même fichier**, écrit atomiquement : les identités en
lignes `principal|role|sha256[|expiresAt]`, l'audit en lignes de commentaire `# audit|instant|mutation|principal|role`.
L'audit est retenu comme fenêtre glissante bornée (`MAX_AUDIT_RECORDS`) pour que sa croissance ne puisse jamais
empêcher une rotation urgente.

La croissance n'est pas le seul risque. À chaque écriture, l'audit historique est relu pour être préservé ; une
lecture stricte ferait échouer l'opération entière sur une seule ligne `# audit|…` illisible — écriture partielle,
édition manuelle, copie tronquée. Le résultat serait exactement inversé par rapport au besoin :

```text
credential compromis toujours valide
+ audit historique corrompu
= revoke impossible
```

Le lecteur d'identités ignore les lignes commençant par `#` : l'état des credentials n'est jamais en cause, seule la
**préservation** de l'audit pourrait bloquer la mutation que l'audit existe pour enregistrer.

Options écartées :

| Option | Raison du rejet |
|---|---|
| Ignorer silencieusement les lignes illisibles | L'opérateur perd la preuve **et** l'information qu'il l'a perdue ; c'est une dégradation silencieuse |
| Jeter tout l'audit dès qu'une ligne est illisible | Même perte, sans même dire quelle partie était endommagée |
| Séparer l'audit dans un second fichier | Casse l'atomicité qu'ADR-0094 obtient en écrivant identités et audit dans un même instantané ; deux fichiers introduisent un état intermédiaire où l'un est écrit et l'autre non |
| Journaliser un avertissement | `morpheus-api` n'a aucune dépendance de journalisation, et en ajouter une pour un avertissement serait un changement plus grand que le besoin. L'entrée d'audit est l'avertissement durable, déjà bornée et sans secret |
| Rendre `audit(Path)` tolérante elle aussi | Elle deviendrait incapable de signaler une corruption ; c'est la seule surface dont le rôle est de la montrer |

## Decision

### 1. Ordre de priorité explicite

```text
état des credentials
  > capacité de revoke / rotate
    > conservation fidèle de l'audit historique
```

Une entrée d'audit historique illisible n'empêche jamais `revoke`, `rotate`, `create` ni une autre mutation
d'identités par ailleurs valide.

### 2. Quarantaine, pas suppression silencieuse

Le chemin d'écriture récupère les entrées lisibles et **compte** celles qu'il a dû abandonner. Quand il y en a, la
perte est elle-même écrite comme entrée d'audit `AUDIT_QUARANTINED`, de sujet réservé `morpheus.audit`. Le fichier est
ainsi soigné par la première mutation : la ligne illisible disparaît, et sa disparition est enregistrée.

Rien de la ligne rejetée n'est repris dans cette entrée ni dans aucun message. Une ligne qui n'a pas pu être analysée
est de provenance inconnue — une écriture tronquée peut y avoir laissé la fin de n'importe quoi — donc la seule chose
sûre à en dire est qu'elle a existé.

### 3. La lecture stricte reste stricte

`MorpheusRemoteIdentityFile.audit(Path)` échoue en nommant la ligne fautive. C'est la surface de **rapport** : elle dit
ce qui est sur le disque plutôt que ce qu'on peut en sauver, et elle ne bloque aucune opération de sécurité. Après la
première mutation, elle redevient lisible sur le fichier même qu'elle refusait.

## Do's and Don'ts

### Do

- Laisser aboutir `revoke`, `rotate` et `create` quand l'audit historique contient une ligne illisible.
- Conserver les entrées d'audit lisibles de part et d'autre d'une entrée illisible.
- Écrire une entrée `AUDIT_QUARANTINED`, de sujet `morpheus.audit`, quand des entrées ont été abandonnées.
- Garder l'audit retenu borné par `MAX_AUDIT_RECORDS`.
- Garder `MorpheusRemoteIdentityFile.audit(Path)` stricte.

### Don't

- Ne pas faire échouer une mutation d'identités à cause de l'audit historique.
- Ne pas abandonner une ligne illisible sans l'enregistrer.
- Ne reprendre aucun contenu d'une ligne rejetée dans une entrée, un message ou une erreur.
- Ne pas séparer identités et audit dans deux fichiers.

## Consequences

### Positives

- Un credential compromis reste révocable quel que soit l'état de l'audit historique.
- La perte d'une entrée d'audit est elle-même prouvée.
- La surface de rapport continue de signaler une corruption.

### Négatives

- `Mutation` porte une valeur de plus, `AUDIT_QUARANTINED`. Le format de fichier ne change pas et les entrées
  existantes restent lisibles.
- Le contenu d'une ligne illisible est perdu.

### Risques

- **Croissance de l'audit par la récupération** : écartée, l'audit retenu reste borné par `MAX_AUDIT_RECORDS`.
- **Quarantaine répétée** : écartée, elle n'est enregistrée qu'une fois puisque la ligne endommagée ne survit pas à la
  première écriture.
- **Fuite du contenu rejeté** : mitigée, rien de la ligne n'est repris dans l'erreur ni dans le fichier écrit.

## Compliance and Enforcement

- `morpheus-api/src/test/java/com/morpheus/api/remote/MorpheusRemoteIdentityAuditRecoveryTest.java` : revoke, rotate et
  create sur un historique corrompu, rétention des entrées valides de part et d'autre de l'entrée illisible, borne,
  unicité de la quarantaine, absence de tout écho du contenu rejeté dans l'erreur comme dans le fichier écrit.

## References

- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — mode serveur remote d'équipe, fichier d'identités.
- [ADR-0105](SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) — aucun secret réutilisable au repos.
- [ADR-0113](SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.md) — journal d'accès remote, distinct
  de l'audit d'identités.

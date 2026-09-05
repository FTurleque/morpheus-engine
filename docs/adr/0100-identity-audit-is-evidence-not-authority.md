# ADR-0100 — L'audit d'identités est une preuve, pas une autorité

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 4 septembre 2026
- Dépend de : ADR-0094
- Portée : fichier d'identités remote, révocation, rotation, audit sans secret

## Contexte

ADR-0094 place les identités remote et leur audit dans un **même fichier**, écrit atomiquement : les
identités en lignes `principal|role|sha256[|expiresAt]`, l'audit en lignes de commentaire
`# audit|instant|mutation|principal|role`. L'audit est retenu comme fenêtre glissante bornée
(`MAX_AUDIT_RECORDS`) précisément pour que sa croissance ne puisse jamais empêcher une rotation
urgente.

La croissance n'était pas le seul risque. À chaque écriture, l'audit historique était relu en mode
strict pour être préservé, et une seule ligne `# audit|…` illisible — écriture partielle, édition
manuelle, copie tronquée — faisait échouer l'opération entière.

Le résultat est exactement inversé par rapport au besoin :

```text
credential compromis toujours valide
+ audit historique corrompu
= revoke impossible
```

Le lecteur d'identités, lui, ignore les lignes commençant par `#`. L'état des credentials n'était donc
jamais en cause : seule la **préservation** de l'audit bloquait la mutation que l'audit existe pour
enregistrer.

## Décision

### 1. Ordre de priorité explicite

```text
état des credentials
  > capacité de revoke / rotate
    > conservation fidèle de l'audit historique
```

Une entrée d'audit historique illisible ne doit jamais empêcher `revoke`, `rotate`, `create` ni une
autre mutation d'identités par ailleurs valide.

### 2. Quarantaine, pas suppression silencieuse

Le chemin d'écriture récupère les entrées lisibles et **compte** celles qu'il a dû abandonner. Quand
il y en a, la perte est elle-même écrite comme entrée d'audit `AUDIT_QUARANTINED`, de sujet réservé
`morpheus.audit`. Le fichier est ainsi soigné par la première mutation : la ligne illisible disparaît,
et sa disparition est enregistrée.

Rien de la ligne rejetée n'est repris dans cette entrée ni dans aucun message. Une ligne qui n'a pas
pu être analysée est de provenance inconnue — une écriture tronquée peut y avoir laissé la fin de
n'importe quoi — donc la seule chose sûre à en dire est qu'elle a existé.

### 3. La lecture stricte reste stricte

`MorpheusRemoteIdentityFile.audit(Path)` continue d'échouer en nommant la ligne fautive. C'est la
surface de **rapport** : elle dit ce qui est sur le disque plutôt que ce qu'on peut en sauver, et
elle ne bloque aucune opération de sécurité. Après la première mutation, elle redevient lisible sur
le fichier même qu'elle refusait.

## Alternatives écartées

| Alternative | Raison du rejet |
|---|---|
| Ignorer silencieusement les lignes illisibles | L'opérateur perd la preuve **et** l'information qu'il l'a perdue. Contredit `rules/code-style.md` (jamais de dégradation silencieuse) |
| Jeter tout l'audit dès qu'une ligne est illisible | Même perte, sans même dire quelle partie était endommagée |
| Séparer l'audit dans un second fichier | Casse l'atomicité qu'ADR-0094 obtient en écrivant identités et audit dans un même instantané ; deux fichiers introduisent un état intermédiaire où l'un est écrit et l'autre non |
| Journaliser un avertissement | `morpheus-api` n'a aucune dépendance de logging, et en ajouter une pour un avertissement serait un changement plus grand que le correctif. L'entrée d'audit est l'avertissement durable, et elle est déjà bornée et sans secret |
| Rendre `audit(Path)` tolérante elle aussi | Elle deviendrait incapable de signaler une corruption ; c'est la seule surface dont le rôle est justement de la montrer |

## Conséquences

- `Mutation` gagne une valeur, `AUDIT_QUARANTINED`. Ajout compatible : le format de fichier ne change
  pas et les entrées existantes restent lisibles.
- La quarantaine n'est enregistrée qu'une fois, puisque la ligne endommagée ne survit pas à la
  première écriture.
- L'audit retenu reste borné par `MAX_AUDIT_RECORDS` : la récupération n'ouvre aucune nouvelle voie de
  croissance.
- `MorpheusRemoteIdentityAuditRecoveryTest` prouve revoke, rotate et create sur un historique corrompu,
  la rétention des entrées valides de part et d'autre de l'entrée illisible, la borne, l'unicité de la
  quarantaine, et l'absence de tout écho du contenu rejeté dans l'erreur comme dans le fichier écrit.

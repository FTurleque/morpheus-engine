# ADR-0112 — Le répertoire de logs contient le journal opérationnel, borné, quand l'opérateur le demande

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 13 octobre 2026
- Dépend de : ADR-0087 (opérabilité local-first), ADR-0088 (séparation programme / données persistantes), ADR-0111
  (un seul binding de journalisation)
- Portée : `morpheus-application` (`OperationalLogMode.FILE`, `RotatingOperationalLogFile`,
  `DeferredOperationalEventSink`, `LocalOperationalRuntime.writeLogFileIn`), `morpheus-cli` (`MorpheusMain`)

## Contexte

Le produit publiait un répertoire de logs : résolu par la disposition du processus, réglable par `MORPHEUS_LOGS_DIR`,
imprimé par la commande `paths`, nommé dans trois pages utilisateur. Il n'y écrivait rien. Le seul journal était la
sortie d'erreur, que l'opérateur d'un service devait capturer lui-même (constat AUD-TRV-04 de l'audit du 9 octobre
2026).

Le puits du journal est choisi dans un initialiseur statique de `morpheus-application`, qui ne connaît pas la
disposition du processus : elle est résolue dans `morpheus-cli`, à partir de l'environnement et de l'option
`--data-dir`, que chaque commande lit pour son compte.

## Décision

1. **Un mode `file`.** `MORPHEUS_OPERATIONAL_LOGS=file` écrit les événements, un objet JSON par ligne, dans
   `morpheus-operational.jsonl` sous le répertoire de logs. Rien n'est alors écrit sur la sortie d'erreur par le
   journal. Les autres modes ne changent pas.

2. **Borné en taille et en nombre.** Le fichier courant ne dépasse pas 4 Mio : l'événement qui franchirait la borne
   ouvre un nouveau fichier, et les précédents sont gardés de `.1` (le plus récent) à `.4`, le plus ancien étant
   supprimé. Le journal occupe donc au plus cinq fois la borne. Un événement plus grand que la borne est écrit seul
   plutôt que perdu.

3. **Privé au compte.** Le répertoire et chaque fichier passent par `LocalWritePermissionHardener`, comme la base et
   la configuration.

4. **Le lanceur nomme le répertoire, une fois.** `MorpheusMain` lit le mode, résout le répertoire comme la commande
   `paths` le fait pour les mêmes arguments, et le donne au runtime avant toute autre action. Le puits du mode `file`
   est différé (`DeferredOperationalEventSink`) : sa destination se nomme une seule fois, une seconde est refusée.

5. **Un répertoire inutilisable est un refus, jamais un repli.** Si le répertoire ne peut pas être créé, n'est pas
   privé au compte ou si le fichier ne peut pas être écrit, le processus s'arrête avec
   `OPERATIONAL_LOG_FILE_UNUSABLE` et le code d'erreur d'entrée-sortie, avant d'avoir rien fait. Il ne retombe ni sur
   la sortie d'erreur ni sur « aucun journal ».

6. **Une écriture qui échoue ensuite est comptée.** Le journal ne peut pas rapporter sa propre panne et le produit ne
   s'arrête pas pour elle : l'événement est perdu et le compteur `operational.log.write.failed` monte.

7. **Rien n'est tenu ouvert.** La taille est relue et le fichier rouvert à chaque événement : plusieurs processus
   MORPHEUS d'un même compte peuvent écrire dans le même journal. Deux processus qui franchissent la borne au même
   instant peuvent tourner deux fois ; le journal reste borné et aucune ligne n'est coupée, mais une génération peut
   être plus courte que la borne.

8. **Les utilisateurs de `System.Logger` restent sur la sortie d'erreur.** Décision 5 d'ADR-0111 les avait renvoyés
   ici. Les router exigerait un `System.LoggerFinder`, qui capte aussi ce que le JDK journalise pour lui-même et
   remplace, journal coupé, un comportement que l'opérateur observe aujourd'hui. Le mode `file` ne les contient donc
   pas, et la documentation le dit. Les échecs que ces classes signalent et qui comptent pour l'exploitation passent
   par les compteurs `failure.suppressed.*`, pas par leur texte.

Cette décision a été prise sur la recommandation de la spécification
(`openspec/changes/leave-a-trace-when-a-failure-is-swallowed`, décision 6), le mainteneur ayant lancé le lot sans
choisir entre le mode fichier et le retrait du répertoire.

## Alternatives écartées

- **Retirer le répertoire de logs** de la disposition, de `MORPHEUS_LOGS_DIR` et des pages. Cohérent, mais l'opérateur
  d'un service resterait sans autre journal que la sortie d'erreur à capturer lui-même.
- **Écrire dans le fichier dès que le répertoire existe.** Un comportement qui dépend de la présence d'un répertoire
  n'est pas un choix de l'opérateur.
- **Résoudre le répertoire dans `morpheus-application`.** Ce serait recopier la disposition du processus dans un
  module qui n'a pas à la connaître.
- **Injecter le runtime opérationnel partout d'abord** (`inject-the-operational-runtime`). C'est la direction, et ce
  changement ne la contrarie pas : le lanceur décide du puits. Mais l'injection touche les stores et les racines de
  composition ; le fichier n'a besoin que d'une destination nommée une fois.
- **Tenir le fichier ouvert et compter les octets en mémoire.** Plus rapide, faux dès que deux processus écrivent, et
  la rotation d'un fichier ouvert échoue sous Windows.

## Conséquences

- En mode `file`, tout lancement crée le répertoire de logs s'il manque, y compris une commande qui n'émet rien.
- Un événement émis avant que le lanceur ait nommé le répertoire n'a pas de destination et n'est pas gardé. Le lanceur
  le nomme en premier ; un code qui utiliserait le runtime hors du lanceur, en mode `file`, n'écrirait rien.
- Le message du refus nomme le répertoire. Il sort sur la sortie d'erreur locale, comme la commande `paths` l'imprime.
- La sortie d'erreur d'un processus en mode `file` peut encore porter le texte des trois utilisateurs de
  `System.Logger`.

## Preuves exécutables

- `RotatingOperationalLogFileTest` : une ligne JSON rédigée par événement ; reprise d'un journal existant ; rotation à
  la borne et générations gardées ; événement plus grand que la borne ; permissions privées (POSIX) ; refus nommé d'un
  répertoire qui ne peut pas en être un ; écriture perdue comptée ; borne nulle refusée.
- `DeferredOperationalEventSinkTest` : destination nommée une fois, seconde refusée.
- `OperationalLogFileLaunchTest` : aucun répertoire nommé hors du mode `file` ; le répertoire nommé est celui que
  `paths` imprime pour les mêmes arguments (environnement, `--data-dir`, `MORPHEUS_LOGS_DIR`) ; dans un processus réel,
  le fichier est créé et la sortie d'erreur reste vide, ce qu'une bibliothèque embarquée journalise arrive dans le
  fichier, et un répertoire inutilisable arrête le processus sur une seule ligne nommée.

# ADR-0112 — Le répertoire de logs contient le journal opérationnel, borné, quand l'opérateur le demande

> **Archivée.** Cette décision est consolidée dans [ADR-0146](../BE-0146-journal-operationnel-slf4j-et-fichier-borne.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0146**
- Date : 2026-10-13

## Context

Le produit publie un répertoire de logs : résolu par la disposition du processus, réglable par `MORPHEUS_LOGS_DIR`,
imprimé par la commande `paths`, nommé dans les pages utilisateur. Sans journal écrit dans ce répertoire, le seul
journal est la sortie d'erreur, que l'opérateur d'un service doit capturer lui-même.

Le puits du journal est choisi dans `morpheus-application`, qui ne connaît pas la disposition du processus : elle est
résolue dans `morpheus-cli`, à partir de l'environnement et de l'option `--data-dir`, que chaque commande lit pour son
compte.

Options écartées :

- **Retirer le répertoire de logs** de la disposition, de `MORPHEUS_LOGS_DIR` et des pages : cohérent, mais
  l'opérateur d'un service resterait sans autre journal que la sortie d'erreur à capturer lui-même.
- **Écrire dans le fichier dès que le répertoire existe** : un comportement qui dépend de la présence d'un répertoire
  n'est pas un choix de l'opérateur.
- **Résoudre le répertoire dans `morpheus-application`** : ce serait recopier la disposition du processus dans un
  module qui n'a pas à la connaître.
- **Tenir le fichier ouvert et compter les octets en mémoire** : plus rapide, faux dès que deux processus écrivent, et
  la rotation d'un fichier ouvert échoue sous Windows.

## Decision

1. **Un mode `file`.** `MORPHEUS_OPERATIONAL_LOGS=file` (`OperationalLogMode.FILE`) écrit les événements, un objet
   JSON par ligne, dans `morpheus-operational.jsonl` (`RotatingOperationalLogFile.FILE_NAME`) sous le répertoire de
   logs. Le journal n'écrit alors rien sur la sortie d'erreur. Les autres modes gardent leur comportement.

2. **Borné en taille et en nombre.** Le fichier courant ne dépasse pas `RotatingOperationalLogFile.MAX_FILE_BYTES` :
   l'événement qui franchirait la borne ouvre un nouveau fichier, et les précédents sont gardés de `.1` (le plus
   récent) à `.<KEPT_GENERATIONS>`, le plus ancien étant supprimé. Le journal occupe donc au plus
   `KEPT_GENERATIONS + 1` fois la borne. Un événement plus grand que la borne est écrit seul plutôt que perdu.

3. **Privé au compte.** Le répertoire et chaque fichier passent par `LocalWritePermissionHardener`, comme la base et
   la configuration.

4. **Le lanceur nomme le répertoire, une fois.** `MorpheusMain` lit le mode, résout le répertoire comme la commande
   `paths` le fait pour les mêmes arguments, et le donne au runtime (`OperationalRuntime.writeLogFileIn`, ADR-0115)
   avant toute autre action. Le puits du mode `file` est différé (`DeferredOperationalEventSink`) : sa destination se
   nomme une seule fois, une seconde est refusée.

5. **Un répertoire inutilisable est un refus, jamais un repli.** Si le répertoire ne peut pas être créé, n'est pas
   privé au compte ou si le fichier ne peut pas être écrit, le processus s'arrête avec
   `OPERATIONAL_LOG_FILE_UNUSABLE` et le code de sortie d'entrée-sortie (`CliExitCode.IO_ERROR`), avant d'avoir rien
   fait. Il ne retombe ni sur la sortie d'erreur ni sur « aucun journal ».

6. **Une écriture qui échoue ensuite est comptée.** Le journal ne peut pas rapporter sa propre panne et le produit ne
   s'arrête pas pour elle : l'événement est perdu et le compteur `operational.log.write.failed` monte.

7. **Rien n'est tenu ouvert.** La taille est relue et le fichier rouvert à chaque événement : plusieurs processus
   MORPHEUS d'un même compte peuvent écrire dans le même journal. Deux processus qui franchissent la borne au même
   instant peuvent tourner deux fois ; le journal reste borné et aucune ligne n'est coupée, mais une génération peut
   être plus courte que la borne.

8. **Les utilisateurs de `System.Logger` restent sur la sortie d'erreur.** Les router exigerait un
   `System.LoggerFinder`, qui capte aussi ce que le JDK journalise pour lui-même et remplace, journal coupé, un
   comportement que l'opérateur observe. Le mode `file` ne les contient donc pas, et la documentation le dit. Les
   échecs que ces classes signalent et qui comptent pour l'exploitation passent par les compteurs
   `failure.suppressed.*`, pas par leur texte.

Le lanceur décide du puits ; c'est la même direction que le runtime opérationnel transmis par le lanceur
(ADR-0115), sans exiger davantage qu'une destination nommée une fois.

## Do's and Don'ts

### Do

- N'écrire le fichier que si l'opérateur choisit `MORPHEUS_OPERATIONAL_LOGS=file`.
- Borner le fichier courant par `MAX_FILE_BYTES` et le nombre de générations par `KEPT_GENERATIONS`.
- Durcir le répertoire et chaque fichier par `LocalWritePermissionHardener`.
- Nommer le répertoire depuis `MorpheusMain`, une seule fois, avant toute autre action.
- Arrêter le processus sur `OPERATIONAL_LOG_FILE_UNUSABLE` quand le répertoire ou le fichier est inutilisable.
- Compter une écriture perdue dans `operational.log.write.failed`.
- Rouvrir le fichier et relire sa taille à chaque événement.

### Don't

- Ne pas résoudre le répertoire de logs dans `morpheus-application`.
- Ne pas retomber sur la sortie d'erreur ni sur l'absence de journal quand le répertoire est inutilisable.
- Ne pas accepter une seconde destination pour le puits différé.
- Ne pas tenir le fichier ouvert entre deux événements.
- Ne pas installer de `System.LoggerFinder` pour router `System.Logger` vers le fichier.

## Consequences

### Positives

- L'opérateur d'un service obtient un journal borné et privé sans capturer la sortie d'erreur.
- Plusieurs processus d'un même compte partagent le journal sans le corrompre.
- Un répertoire inutilisable est signalé au lancement au lieu d'être découvert par l'absence de journal.

### Négatives

- En mode `file`, tout lancement crée le répertoire de logs s'il manque, y compris une commande qui n'émet rien.
- Un événement émis avant que le lanceur ait nommé le répertoire n'a pas de destination et n'est pas gardé ; un code
  qui utiliserait le runtime hors du lanceur, en mode `file`, n'écrirait rien.
- La sortie d'erreur d'un processus en mode `file` peut encore porter le texte des utilisateurs de `System.Logger`.
- Rouvrir le fichier à chaque événement coûte plus qu'un fichier tenu ouvert.

### Risques

- **Rotation concurrente** : deux processus peuvent tourner deux fois ; le journal reste borné et aucune ligne n'est
  coupée.
- **Chemin divulgué par le refus** : le message du refus nomme le répertoire ; il sort sur la sortie d'erreur locale,
  comme la commande `paths` l'imprime.

## Compliance and Enforcement

- Règle Archgate `BE-0112-the-logs-directory-holds-a-bounded-operational-log.rules.ts` : `no-logger-finder`.
- `morpheus-application/src/test/java/com/morpheus/application/operability/RotatingOperationalLogFileTest.java` : une
  ligne JSON par événement, reprise d'un journal existant, rotation et générations gardées, événement plus grand que
  la borne, permissions privées (POSIX), refus nommé d'un répertoire inutilisable, écriture perdue comptée, borne
  nulle refusée.
- `morpheus-application/src/test/java/com/morpheus/application/operability/DeferredOperationalEventSinkTest.java` :
  destination nommée une fois, seconde refusée.
- `morpheus-cli/src/test/java/com/morpheus/cli/OperationalLogFileLaunchTest.java` : aucun répertoire nommé hors du
  mode `file`, répertoire identique à celui que `paths` imprime, fichier créé et sortie d'erreur vide dans un processus
  réel, arrêt sur une seule ligne nommée quand le répertoire est inutilisable.

## References

- [ADR-0087](../SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — opérabilité local-first.
- [ADR-0088](../GEN-0088-product-release-installation-and-persistent-data-separation.md) — séparation programme /
  données persistantes.
- [ADR-0111](0111-the-product-ships-one-logging-binding-its-own.md) — un seul binding de journalisation.
- [ADR-0115](../ARCH-0115-the-operational-runtime-is-a-value-the-launcher-hands-on.md) — runtime opérationnel transmis
  par le lanceur.

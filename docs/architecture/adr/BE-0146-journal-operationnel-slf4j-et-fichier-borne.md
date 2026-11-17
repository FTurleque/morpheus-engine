---
id: BE-0146
title: "Journal opérationnel : un seul binding SLF4J, un fichier borné à la demande"
domain: backend
rules: true
files: ["**/pom.xml", "**/src/main/java/**/*.java"]
---

# Journal opérationnel : un seul binding SLF4J, un fichier borné à la demande

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0111, ADR-0112

## Context

Le SDK MCP et ses dépendances journalisent par SLF4J, et par rien d'autre : une poignée de main qui expire, un flux
rompu, une trame qui ne se décode pas ne sont dits nulle part ailleurs. Un binding qui jette tout (`slf4j-nop`) perd
ces informations, que l'opérateur ait demandé un journal opérationnel ou non.

Les classes du produit qui journalisent par `System.Logger` (dans `morpheus-mcp-transport` et la persistance SQLite)
écrivent leurs enregistrements de niveau `INFO` et au-dessus sur la sortie d'erreur, en texte, dans tous les modes de
lancement, et jamais sur la sortie standard. Elles ne sont pas perdues ; elles ne produisent simplement pas
d'événements opérationnels.

Le produit publie un répertoire de logs : résolu par la disposition du processus, réglable par `MORPHEUS_LOGS_DIR`,
imprimé par la commande `paths`, nommé dans les pages utilisateur. Sans journal écrit dans ce répertoire, le seul
journal est la sortie d'erreur, que l'opérateur d'un service doit capturer lui-même.

Le puits du journal est choisi dans `morpheus-application`, qui ne connaît pas la disposition du processus : elle est
résolue dans `morpheus-cli`, à partir de l'environnement et de l'option `--data-dir`, que chaque commande lit pour son
compte.

Options écartées pour le binding :

- **Garder `slf4j-nop`** : ce que les bibliothèques embarquées rapportent reste jeté.
- **Embarquer `slf4j-simple` ou `slf4j-jdk14`** : ils écrivent du texte libre sur la sortie d'erreur, sans rédaction
  et quel que soit le mode ; un opérateur qui n'a rien demandé recevrait des lignes, et celui qui a demandé du JSON
  par ligne recevrait un flux qui n'en est plus.
- **Embarquer Logback ou Log4j** : une dépendance de plus, sa configuration et sa surface de vulnérabilités, pour
  router deux niveaux vers un puits qui existe déjà.
- **Reprendre le message d'un échec joint** : un message d'exception peut nommer un emplacement du serveur ; comme
  pour `FAILURE_SUPPRESSED`, seul le type de l'échec est gardé.

Options écartées pour le fichier :

- **Retirer le répertoire de logs** de la disposition, de `MORPHEUS_LOGS_DIR` et des pages : cohérent, mais
  l'opérateur d'un service resterait sans autre journal que la sortie d'erreur à capturer lui-même.
- **Écrire dans le fichier dès que le répertoire existe** : un comportement qui dépend de la présence d'un répertoire
  n'est pas un choix de l'opérateur.
- **Résoudre le répertoire dans `morpheus-application`** : ce serait recopier la disposition du processus dans un
  module qui n'a pas à la connaître.
- **Tenir le fichier ouvert et compter les octets en mémoire** : plus rapide, faux dès que deux processus écrivent, et
  la rotation d'un fichier ouvert échoue sous Windows.

Ce que la décision tient, et pourquoi :

- Refuser les niveaux sous `WARN` à la source épargne à la bibliothèque tout formatage ; journal coupé, le fournisseur
  se comporte comme `slf4j-nop`.
- Le puits opérationnel masque un secret qu'une bibliothèque aurait placé dans son message comme dans tout autre
  événement. La sortie standard est le flux de protocole du serveur MCP.
- Le mode `file` s'ajoute aux autres modes, qui gardent leur comportement.
- Le journal occupe au plus `KEPT_GENERATIONS + 1` fois la borne ; un événement plus grand qu'elle est écrit seul
  plutôt que perdu. Le durcissement des permissions est celui de la base et de la configuration.
- Le lanceur décide du puits, dans la même direction que le runtime opérationnel qu'il transmet (ADR-0115), sans
  exiger davantage qu'une destination nommée une fois.
- Un répertoire inutilisable ne retombe ni sur la sortie d'erreur ni sur « aucun journal ». Une écriture perdue plus
  tard est seulement comptée : le journal ne peut pas rapporter sa propre panne et le produit ne s'arrête pas pour
  elle.
- Relire la taille et rouvrir le fichier à chaque événement laisse plusieurs processus MORPHEUS d'un même compte écrire
  dans le même journal. Deux processus qui franchissent la borne au même instant peuvent tourner deux fois ; le journal
  reste borné et aucune ligne n'est coupée, mais une génération peut être plus courte que la borne.
- Router `System.Logger` exigerait un `System.LoggerFinder`, qui capte aussi ce que le JDK journalise pour lui-même et
  remplace, journal coupé, un comportement que l'opérateur observe. Le mode `file` ne contient donc pas ces classes,
  et la documentation le dit ; les échecs qu'elles signalent et qui comptent pour l'exploitation passent par les
  compteurs `failure.suppressed.*`, pas par leur texte.

## Decision

1. **Binding** : `morpheus-cli` déclare `slf4j-api` (portée compilation, version du POM racine) et fournit
   `OperationalSlf4jServiceProvider` par `META-INF/services/org.slf4j.spi.SLF4JServiceProvider` ; aucun autre
   fournisseur SLF4J, `slf4j-nop` compris.
2. **Niveaux** : `WARN` et `ERROR` deviennent un événement `THIRD_PARTY_LOG` (logger, message formaté, **type** de
   l'échec joint). Les niveaux inférieurs sont refusés à la source (`isInfoEnabled()` faux) ; journal coupé, aucun.
3. **Puits** : le puits opérationnel, donc `SensitiveValueRedactor`, message borné à
   `OperationalSlf4jServiceProvider.MAX_MESSAGE_CHARACTERS`, rien sur la sortie standard.
4. **Mode `file`** : `MORPHEUS_OPERATIONAL_LOGS=file` (`OperationalLogMode.FILE`) écrit du JSON par ligne dans
   `morpheus-operational.jsonl` (`RotatingOperationalLogFile.FILE_NAME`) sous le répertoire de logs, et rien sur la
   sortie d'erreur.
5. **Rotation** : fichier courant borné par `RotatingOperationalLogFile.MAX_FILE_BYTES`, générations `.1` (la plus
   récente) à `.<KEPT_GENERATIONS>`, la plus ancienne supprimée ; un événement plus grand que la borne est écrit seul.
   Répertoire et fichiers passent par `LocalWritePermissionHardener`.
6. **Lanceur** : `MorpheusMain` résout le répertoire comme `paths` et le donne au runtime
   (`OperationalRuntime.writeLogFileIn`, ADR-0115) avant toute action ; `DeferredOperationalEventSink` refuse une
   seconde destination.
7. **Échecs** : répertoire non créable ou non privé, ou fichier non inscriptible : arrêt avant toute action par
   `OPERATIONAL_LOG_FILE_UNUSABLE` et `CliExitCode.IO_ERROR`, sans repli. Une écriture perdue ensuite fait monter
   `operational.log.write.failed`.
8. **Rien d'ouvert** : taille relue et fichier rouvert à chaque événement.
9. **`System.Logger`** : reste sur la sortie d'erreur, hors binding et hors fichier ; aucun `System.LoggerFinder`.

## Do's and Don'ts

### Do

- Déclarer `slf4j-api` sans version dans `morpheus-cli/pom.xml` et laisser le POM racine la gérer.
- Convertir un enregistrement SLF4J `WARN` ou `ERROR` en événement `THIRD_PARTY_LOG` passé par le puits opérationnel.
- Ne garder d'un échec joint que son type.
- N'écrire le fichier que si l'opérateur choisit `MORPHEUS_OPERATIONAL_LOGS=file`.
- Borner le fichier courant par `MAX_FILE_BYTES` et le nombre de générations par `KEPT_GENERATIONS`.
- Durcir le répertoire et chaque fichier par `LocalWritePermissionHardener`.
- Nommer le répertoire depuis `MorpheusMain`, une seule fois, avant toute autre action.
- Arrêter le processus sur `OPERATIONAL_LOG_FILE_UNUSABLE` quand le répertoire ou le fichier est inutilisable.
- Compter une écriture perdue dans `operational.log.write.failed`.
- Rouvrir le fichier et relire sa taille à chaque événement.

### Don't

- Ne pas embarquer un second fournisseur SLF4J (`slf4j-nop`, `slf4j-simple`, `slf4j-jdk14`, Logback, Log4j).
- Ne pas accepter les niveaux sous `WARN`, ni aucun niveau quand le journal opérationnel est coupé.
- Ne rien écrire sur la sortie standard depuis le binding.
- Ne pas résoudre le répertoire de logs dans `morpheus-application`.
- Ne pas retomber sur la sortie d'erreur ni sur l'absence de journal quand le répertoire est inutilisable.
- Ne pas accepter une seconde destination pour le puits différé.
- Ne pas tenir le fichier ouvert entre deux événements.
- Ne pas installer de `System.LoggerFinder` pour router `System.Logger` vers le fichier.

## Consequences

### Positives

- Ce que les bibliothèques embarquées signalent rejoint le journal opérationnel, rédigé et borné.
- Un opérateur qui n'a pas demandé de journal ne voit aucun changement.
- L'opérateur d'un service obtient un journal borné et privé sans capturer la sortie d'erreur.
- Plusieurs processus d'un même compte partagent le journal sans le corrompre.
- Un répertoire inutilisable est signalé au lancement au lieu d'être découvert par l'absence de journal.

### Négatives

- `slf4j-api` est une dépendance de compilation de `morpheus-cli`, et non seulement transitive.
- En mode `file`, tout lancement crée le répertoire de logs s'il manque, y compris une commande qui n'émet rien.
- Un événement émis avant que le lanceur ait nommé le répertoire n'a pas de destination et n'est pas gardé ; un code
  qui utiliserait le runtime hors du lanceur, en mode `file`, n'écrirait rien.
- La sortie d'erreur d'un processus en mode `file` peut encore porter le texte des utilisateurs de `System.Logger`.
- Rouvrir le fichier à chaque événement coûte plus qu'un fichier tenu ouvert.

### Risques

- Un second fournisseur sur le chemin de classes est une régression : SLF4J en choisirait un des deux, l'annoncerait
  sur la sortie d'erreur, et ce que les bibliothèques journalisent serait de nouveau perdu.
- Un message de bibliothèque peut nommer un chemin : le rédacteur remplace le répertoire personnel, et le journal
  opérationnel reste local ; aucun transport ne le sert à distance.
- **Rotation concurrente** : deux processus peuvent tourner deux fois ; le journal reste borné et aucune ligne n'est
  coupée.
- **Chemin divulgué par le refus** : le message du refus nomme le répertoire ; il sort sur la sortie d'erreur locale,
  comme la commande `paths` l'imprime.

## Compliance and Enforcement

- Règle Archgate `BE-0146-journal-operationnel-slf4j-et-fichier-borne.rules.ts` : `no-other-slf4j-provider`,
  `no-logger-finder`.
- `morpheus-cli/src/test/java/com/morpheus/cli/launch/OperationalSlf4jServiceProviderTest.java` : un avertissement et une
  erreur donnent chacun un événement ; rien sous l'avertissement ; rien quand le journal est coupé ; message borné ;
  secret rédigé ; un seul fournisseur sur le chemin de classes du produit, celui-ci ; dans un processus réel, une
  ligne JSON sur la sortie d'erreur et une sortie standard vide avec le journal actif, les deux sorties vides avec le
  journal coupé.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m20/ProductReleaseContractTest.java` : le POM
  de `morpheus-cli` déclare `slf4j-api` et ne nomme pas `slf4j-nop`.
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

- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP officiel.
- [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — opérabilité local-first.
- [ADR-0088](GEN-0088-product-release-installation-and-persistent-data-separation.md) — séparation programme /
  données persistantes.
- [ADR-0105](SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) — aucun secret réutilisable persisté.
- [ADR-0115](ARCH-0115-the-operational-runtime-is-a-value-the-launcher-hands-on.md) — runtime opérationnel transmis
  par le lanceur.

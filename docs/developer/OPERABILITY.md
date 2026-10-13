# Operability M19

Statut : **M19 livré et intégré** — ce document décrit le comportement actuel ; la preuve de qualification du jalon reste `docs/project/validation/VALIDATION_M19.md` (historique).

## Principes

MORPHEUS reste local-first :

```text
process-local counters
optional local JSON-lines logs on stderr
no mandatory network telemetry
no automatic SaaS exporter
redaction before structured log write
```

## Liveness et readiness

Les deux notions sont volontairement distinctes :

```text
health/liveness = le processus répond
readiness        = les dépendances locales nécessaires répondent réellement
```

`LocalOperabilityService.health()` renvoie `UP` sans prétendre que SQLite est prêt.

`LocalOperabilityService.readiness()` exécute une vraie opération sur le `SpecificationKnowledgeStore` :

```text
store accessible   -> READY
store unavailable  -> NOT_READY + DATABASE_NOT_READY
```

Les routes HTTP locales réellement enregistrées dans `MorpheusHttpServer` sont :

```text
GET /api/v1/health      liveness UP
GET /api/v1/readiness   vraie sonde SQLite READY / NOT_READY (HTTP 503)
GET /api/v1/metrics     compteurs et timings process-local
```

Le module API expose les mêmes projections transport-neutral :

```text
MorpheusOperabilityApiService.health()
MorpheusOperabilityApiService.readiness()
MorpheusOperabilityApiService.metrics()
```

## Une base qui fait attendre ne fait pas taire le serveur

Chaque requête HTTP arrive sur un thread virtuel. Le pilote SQLite attend une base occupée dans du code natif, où un
thread virtuel garde son porteur ; les porteurs sont aussi nombreux que les processeurs. Mesuré le 13/10/2026 : 64
lectures en attente sur 16 processeurs retardaient une sonde `health` de treize secondes (ADR-0114).

Le serveur local remet donc tout gestionnaire qui peut toucher la base à un pool borné de threads plateforme
(`DatabaseWorkOffloadingHttpServer`, `DATABASE_WORKERS` : le nombre de processeurs, deux au minimum), et le thread
virtuel de la requête se gare en attendant. La taille du pool est la concurrence de la base ; au-delà, les requêtes
font la queue.

La queue est bornée dans le temps : une requête attend un travailleur dix secondes au plus (`ADMISSION_WAIT`), dans
l'ordre d'arrivée, puis reçoit `429 LOCAL_SERVER_BUSY` avec `Retry-After: 1`. Les refus ne sont pas comptés.

`health`, `metrics` et `version` répondent sans la base et sont enregistrées hors du pool
(`MorpheusRootHttpRoutes.ANSWERED_WITHOUT_THE_DATABASE`) : elles restent servies quand tous les travailleurs
attendent. `readiness` interroge la base et passe par le pool.

Un nouveau contexte HTTP du serveur local s'enregistre à travers `databaseWork`, pas `httpServer` :
`LocalHttpServerBootstrapArchitectureTest` refuse un second enregistrement direct.

## Logs structurés locaux

Le runtime process-local partagé est `LocalOperationalRuntime`.

Par défaut :

```text
MORPHEUS_OPERATIONAL_LOGS absent -> aucun log opérationnel additionnel
compteurs/timings                 -> process-local
```

Activation explicite :

```text
MORPHEUS_OPERATIONAL_LOGS=json
```

Les événements sont alors écrits en JSON-lines sur `stderr` uniquement.

```text
MORPHEUS_OPERATIONAL_LOGS=file
```

Les mêmes lignes vont dans `morpheus-operational.jsonl` sous le répertoire de logs, et plus sur `stderr`
(`RotatingOperationalLogFile`, ADR-0112). Le fichier courant est borné à 4 Mio ; quatre générations sont gardées, de
`.1` à `.4`. Répertoire et fichiers sont privés au compte (`LocalWritePermissionHardener`). Rien n'est tenu ouvert
entre deux événements : plusieurs processus du même compte écrivent dans le même journal.

`morpheus-application` ne connaît pas la disposition du processus. `MorpheusMain` résout le répertoire - celui que
`paths` imprime pour les mêmes arguments - et le nomme une fois (`LocalOperationalRuntime.writeLogFileIn`), avant
toute autre action ; un événement émis plus tôt n'a pas de destination. Un répertoire inutilisable arrête le
processus : `OPERATIONAL_LOG_FILE_UNUSABLE`, code d'erreur d'entrée-sortie, jamais un repli sur `stderr`. Une écriture
qui échoue ensuite est perdue et comptée par `operational.log.write.failed`.

Le mode est une liste fermée, `OperationalLogMode` : `off`, `none` (aucun journal), `json`, `jsonl`, `structured`
(JSON-lines), `file`, comparés sans casse ni espaces. Une valeur hors liste est un refus nommé,
`OPERATIONAL_LOG_MODE_UNKNOWN`, jamais un repli : jusqu'au 13/10/2026 toute valeur inconnue sélectionnait le sink
no-op, et un opérateur qui avait écrit `jsno` exploitait un serveur muet sans le savoir.

Le refus vient du lanceur. `MorpheusMain` lit la variable avant toute autre classe du produit et sort en code d'usage ;
`LocalOperationalRuntime` choisit son sink dans un initialiseur statique, où le même refus sortirait comme une erreur
d'initialisation de la première classe qui l'utilise.

Aucun transport réseau n'est créé.

### Ce que journalisent les bibliothèques embarquées

Le SDK MCP et ses dépendances journalisent par SLF4J. Le produit embarque un seul binding, le sien
(`OperationalSlf4jServiceProvider`, dans `morpheus-cli`, ADR-0111) :

```text
journal coupé   -> aucun niveau accepté, rien n'est écrit
journal actif   -> WARN et ERROR deviennent un événement THIRD_PARTY_LOG ; INFO et en dessous sont refusés
```

L'événement porte `logger`, `message` (formaté, borné à 2 048 caractères) et, quand un échec est joint,
`failureType` - le type seul, comme `FAILURE_SUPPRESSED`. Il passe par le même puits et la même rédaction que les
événements du produit. Jusqu'au 13/10/2026 le produit embarquait `slf4j-nop` : une poignée de main MCP qui expirait
n'était dite nulle part, journal actif ou non.

Un second fournisseur SLF4J sur le chemin de classes est une régression : `OperationalSlf4jServiceProviderTest` le
refuse en énumérant les fournisseurs, `ProductReleaseContractTest` en lisant le POM.

Trois classes du produit journalisent par `System.Logger` (`BoundedStdioClientTransport`,
`BoundedStdioServerTransportProvider`, `SqliteTransactionRunner`). Elles ne passent pas par ce binding : leurs
enregistrements `INFO` et au-dessus sortent en texte sur la sortie d'erreur, dans tous les modes. Avec le journal
sur `stderr`, la sortie d'erreur mêle donc des lignes JSON et ce texte ; avec le journal en fichier, ce texte reste sur
la sortie d'erreur et n'est pas dans le fichier (ADR-0112, décision 8).

## Redaction

`SensitiveValueRedactor` masque avant écriture :

```text
password
secret
token
apiKey
authorization
credential
absolute path-like values
user home
recognized inline secret assignments
```

Le contenu métier complet n'est pas ajouté aux événements opérationnels.

## Timings et compteurs

`OperationalMetrics` est thread-safe et process-local.

Les opérations instrumentées ou instrumentables incluent :

```text
source.scan
snapshot.publish
snapshot.recovery
provider.read
external.<integration>
```

Les timings stockent :

```text
count
totalNanos
maxNanos
```

Les benchmarks M19 utilisent en plus un protocole p95 reproductible séparé ; les métriques runtime ne prétendent pas remplacer les gates de performance.

## Échecs écartés

Un échec que le produit choisit de ne pas propager laisse une trace. `OperationalRecorder.suppressed(famille, échec)`
incrémente le compteur `failure.suppressed.<famille>` - quel que soit le mode de journal - et émet un événement
`FAILURE_SUPPRESSED` portant la famille et le **type** de l'échec. Le message de l'échec n'y figure jamais : il peut
nommer un emplacement du serveur.

Les familles sont une liste fermée, `SuppressedFailureFamily`. Un compteur non nul veut dire que le produit a continué
sans quelque chose :

| Famille | Compteur | Ce qu'une valeur non nulle veut dire |
|---|---|---|
| `adapter-discovery` | `failure.suppressed.adapter-discovery` | un adaptateur de raisonnement présent sur le class path n'a pas pu être chargé ou décrit ; il est absent de la liste alors qu'il est installé |
| `http-internal-error` | `failure.suppressed.http-internal-error` | une requête HTTP est morte d'un échec que rien n'avait prévu et a reçu `500 INTERNAL_ERROR` ; c'est un défaut du produit à signaler, pas une erreur de l'appelant |
| `sync-recovery-read` | `failure.suppressed.sync-recovery-read` | pendant la reprise d'une synchronisation en échec, l'état qui dirait si le commit ou son marqueur est déjà visible n'a pas pu être lu ; il a été pris pour « non visible », ce qui est la lecture sûre, mais le stockage a un problème |
| `sqlite-conflict-read` | `failure.suppressed.sqlite-conflict-read` | en répondant à un conflit de cycle de vie, l'enregistrement d'audit ou l'état courant n'a pas pu être lu ; le conflit est rendu sans distinguer une mutation déjà appliquée d'une vraie collision |
| `mcp-peer` | `failure.suppressed.mcp-peer` | un pair MCP a échoué - il n'a pas démarré, a dépassé son délai, a répondu quelque chose d'inutilisable - et l'appelant a reçu un « indisponible » nu, la même réponse que pour un projet sans snapshot actif |
| `mcp-peer-shutdown` | `failure.suppressed.mcp-peer-shutdown` | un pair MCP n'a pas pu être fermé ou interrompu proprement ; son processus peut survivre à l'opération qui l'a lancé |
| `staged-artifact-cleanup` | `failure.suppressed.staged-artifact-cleanup` | la copie vérifiée d'un JAR épinglé n'a pas pu être supprimée après usage ; elle reste dans le répertoire temporaire |
| `sqlite-lease-release` | `failure.suppressed.sqlite-lease-release` | le verrou ou le canal d'un bail de base n'a pas pu être relâché ou fermé ; le bail est rendu alors que le système peut encore tenir le verrou, et la prochaine ouverture de cette base peut être refusée comme occupée |
| `source-watch` | `failure.suppressed.source-watch` | un changement sous une racine surveillée n'a pas pu devenir un signal - un répertoire impossible à enregistrer, un chemin hors du workspace - et a été rendu comme un débordement ; un nouveau répertoire peut rester non surveillé |

Ces compteurs sont servis avec les autres par `GET /api/v1/metrics`, en local comme à travers la façade remote.

Le module `morpheus-mcp-transport` ne peut dépendre d'aucun autre paquet MORPHEUS, donc il ne compte rien lui-même.
L'interruption d'une opération qui a dépassé son délai lui est remise par la passerelle (`MinosMcpCodeGateway`,
`NexusMcpContextGateway`) : c'est elle qui l'enveloppe (`countingAbort`) et compte son échec avant de le laisser
remonter.

Le cœur de raisonnement reste pur : `ReasoningAdapterRegistry` ne connaît aucun enregistreur. Il **signale** ce qu'il
écarte à un observateur (`Consumer<Throwable>`) que lui passent les trois adaptateurs qui le construisent - CLI, MCP,
HTTP -, et ce sont eux qui comptent.

`SilentCatchArchitectureTest` empêche le suivant d'arriver sans être vu : un `catch` dont le corps ne contient aucune
instruction, ou dont la variable s'appelle `ignored`, doit enregistrer l'échec ou figurer dans une liste fermée avec sa
raison - une sonde dont l'exception est la réponse, un nettoyage sur un chemin dont l'issue est déjà décidée. Une
seconde liste, qui ne fait que rétrécir, nomme ceux qui perdent encore un échec sans le compter.

Avant le 13/10/2026 aucun de ces échecs ne laissait quoi que ce soit : le `catch` portait un commentaire, et après la
fin du processus rien ne disait qu'un adaptateur avait été présent et cassé, ni que des requêtes mouraient.

## Recovery

`RuntimeSnapshotRecovery` est le hook explicite de composition root, exécuté au démarrage des runtimes CLI, API et MCP.

Il ne s'exécute pas implicitement lors d'une simple lecture SQLite.

Policy par défaut :

```text
staleAfter = 10 minutes
BUILDING/VALIDATING stale -> FAILED
READY/ACTIVE/RETIRED      -> untouched
```

Cette séparation évite qu'un lecteur ou un second processus invalide un candidat frais détenu par une commande concurrente.

## Scan local sûr

`SourceScanPolicy.safeDefaults()` :

```text
followSymbolicLinks = false
ignored = .git, .hg, .svn, .idea, .gradle, .morpheus,
          target, build, dist, node_modules
```

Une source root symbolique n'est pas suivie par défaut.

Une source root manquante produit un scan `incomplete` sans inventaire publiable.

## Liens externes

`ExternalLinkPolicy.safeDefaults()` ne suit pas implicitement les liens réseau :

```text
http/https -> denied
ftp/ftps   -> denied
relative   -> local policy may allow
file       -> local policy may allow
```

Une URL peut rester un fait ou une référence de spécification sans devenir une instruction de navigation réseau.

## Permissions d'écriture

`LocalWritePermissionHardener` applique, lorsque le filesystem le permet :

```text
POSIX directory rwx------
POSIX file      rw-------
Windows ACL     owner-only
```

Les chemins symboliques sont refusés.

Les neuf adapters SQLite publics passent par `SqliteDatabaseSecurity`. Le factory refuse les chemins symboliques/non réguliers, durcit le fichier principal et ne remplace pas les ACL d'un parent utilisateur préexistant. Les PRAGMA locales imposent notamment :

```text
foreign_keys = ON
busy_timeout = 5000
temp_store = MEMORY
locking_mode = NORMAL
journal_mode = PERSIST
```

Un probe sans donnée crée une fois le rollback journal `-journal`, puis ce fichier réutilisable est durci owner-only avant toute donnée métier. Les sidecars symboliques ou non réguliers sont refusés. Le contrat exécutable vérifie ses permissions pendant et après une transaction ainsi que l'absence de WAL/SHM. Les gates de taille incluent le fichier principal et les sidecars présents.

## Diagnostics SQLite

`SqliteFailureClassifier` fournit notamment :

```text
SQLITE_BUSY / SQLITE_LOCKED -> DATABASE_LOCKED
```

Le timeout de production du store principal reste :

```text
PRAGMA busy_timeout = 5000
```

Les tests peuvent utiliser une durée plus courte pour prouver la borne sans ralentir le reactor.

## Preuve

La preuve autoritative est enregistrée dans :

```text
docs/project/validation/VALIDATION_M19.md
```

```text
Code SHA      = dca27db969b426ad43941ccb8cee7e926efb931b
Windows proof = PASS
Linux proof   = PASS sur ext4 / WSL2
M19 result    = VALIDATED TECHNICALLY / MERGED
```

Les deux plateformes ont exécuté 449/449 tests, 178/178 tests d'architecture, 14/14 modules et les budgets gelés sans failure, error ou skipped. Ces chiffres restent la preuve historique de la qualification M19 au SHA ci-dessus ; ils ne sont pas recalculés ici. La PR #89 est **MERGED** (2026-07-27) ; le jalon M19 est intégré dans `develop` depuis lors.

# Operability M19

Statut : **M19 livré et intégré** — ce document décrit le comportement actuel ; la preuve de qualification du jalon reste `docs/validation/VALIDATION_M19.md` (historique).

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

Aucun transport réseau n'est créé.

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
docs/validation/VALIDATION_M19.md
```

```text
Code SHA      = dca27db969b426ad43941ccb8cee7e926efb931b
Windows proof = PASS
Linux proof   = PASS sur ext4 / WSL2
M19 result    = VALIDATED TECHNICALLY / MERGED
```

Les deux plateformes ont exécuté 449/449 tests, 178/178 tests d'architecture, 14/14 modules et les budgets gelés sans failure, error ou skipped. Ces chiffres restent la preuve historique de la qualification M19 au SHA ci-dessus ; ils ne sont pas recalculés ici. La PR #89 est **MERGED** (2026-07-27) ; le jalon M19 est intégré dans `develop` depuis lors.

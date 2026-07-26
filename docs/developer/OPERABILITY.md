# Operability M19

Statut : **M19 en cours — contrat implémenté, gate final non exécuté**

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

Le endpoint HTTP historique `/health` conserve sa sémantique de liveness. M19 n'annonce pas de nouvelle route HTTP readiness tant qu'une telle route n'est pas effectivement enregistrée dans `MorpheusHttpServer`.

Le module API expose déjà la projection transport-neutral :

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

`RuntimeSnapshotRecovery` est le hook explicite de composition root.

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

`SqliteSpecificationKnowledgeStore` applique le hardening au chemin SQLite principal. Les autres entry points SQLite restent sous audit M19 jusqu'au gate final ; aucun document ne doit annoncer un hardening global avant cette vérification.

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

La preuve autoritative sera enregistrée dans :

```text
docs/validation/VALIDATION_M19.md
```

Tant que le gate final n'a pas été exécuté :

```text
Windows proof = MISSING
Linux proof   = MISSING
M19 result    = NOT VALIDATED
```

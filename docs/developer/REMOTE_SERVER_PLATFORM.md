# M26 — Remote Server Platform

M26 ajoute une frontière réseau optionnelle sans modifier l’autorité du domaine MORPHEUS ni imposer un service distant au mode local.

## Architecture

```mermaid
flowchart LR
    C[Remote client] -->|HTTPS + Bearer| R[MorpheusRemoteHttpServer]
    R --> A[Auth file SHA-256 + live reload]
    R --> RBAC[READ / WRITE / ADMIN]
    R --> ROOTS[AllowedWorkspaceRoots]
    R --> LIMIT[Semaphore bounded concurrency]
    R -->|no Authorization forwarded| L[MorpheusHttpServer loopback : ephemeral port]
    L --> APP[Existing application services]
    APP --> DB[(SQLite)]
    R --> BK[SqliteServerMaintenance]
    BK --> DB
    BK --> B[(Verified backups)]
```

La façade remote est un adapter. Domain/application ne dépendent ni de TLS, ni des rôles réseau, ni des fichiers d’identités.

## Pourquoi une façade devant l’API locale

L’API locale M11-M25 possède déjà les routes métier, validation JSON, CAS, budgets et règles de mutation. M26 évite de dupliquer ou réécrire cette surface :

1. le frontal `HttpsServer` termine TLS ;
2. il authentifie le Bearer à partir du fichier d'identités courant ;
3. il calcule le rôle requis ;
4. il applique la limite de concurrence ;
5. il intercepte uniquement les endpoints serveur M26 ;
6. il proxy les autres requêtes vers un `MorpheusHttpServer` interne sur `127.0.0.1:0` ;
7. `Authorization` n’est jamais relayé vers l’inner server.

Cette composition préserve les contrats M11-M25 et rend la sécurité remote vérifiable à une frontière unique.

## Modes

### LOCAL

`LoopbackHostPolicy` est appliquée à la fois par `ApiLaunchOptions` et directement par
`MorpheusHttpServer.start()`. Chaque adresse résolue doit être loopback, puis le serveur lie le socket à
l’adresse déjà validée sans seconde résolution DNS. Un caller Java direct ne peut donc pas contourner
l’invariant. Seule la façade `MorpheusRemoteHttpServer`, avec TLS et authentification, peut écouter sur une
adresse non-loopback.

### REMOTE

`RemoteApiLaunchOptions` n’est sélectionné qu’en présence de `api --remote`.

Le démarrage exige :

- auth file valide et non symbolique ;
- au moins une identité ADMIN ;
- keystore PKCS12 valide et non symbolique ;
- mot de passe TLS via environnement/propriété ;
- limite de concurrence 1..512 ;
- au moins une racine workspace serveur existante et canonique.

## Authentication

`MorpheusRemoteIdentityFile` est le provider local de référence.

Format :

```text
principal|role|sha256(token)
```

Contraintes :

```text
identities        <= 256
auth file         <= 256 KiB
audit retained    <= 512 secret-free records
principal         [A-Za-z0-9._@-]{1,128}
token             32 random bytes / 256 bits
persisted token   never
comparison        MessageDigest.isEqual
```

Les mutations `create`, `revoke`, `rotate` et `role` utilisent :

```text
JVM mutation lock
    -> owner-hardened <auth-file>.lock
    -> FileChannel/FileLock inter-processus
    -> read current snapshot
    -> validate invariants, dont dernier ADMIN
    -> retain only the latest 512 audit records
    -> temp file owner-only
    -> atomic move/replace
```

Deux processus administratifs coopérants ne peuvent donc plus effectuer simultanément un read-modify-write qui perdrait une mutation. L'audit est secret-free et borné à une fenêtre roulante de 512 événements ; sa croissance ne peut pas remplir indéfiniment le fichier de 256 KiB et empêcher une rotation/révocation urgente.

Le serveur ne conserve pas un snapshot d'identités comme autorité d'authentification. Il recharge le fichier courant à chaque requête avant la comparaison du Bearer. Une rotation/révocation/changement de rôle est effective dès l'authentification suivante, sans redémarrage serveur. Une erreur de lecture/validation du store d'authentification produit un refus fail-closed plutôt qu'un fallback sur une ancienne copie en mémoire.

Les répertoires locaux sensibles nouvellement créés sont durcis. Sur POSIX, un répertoire préexistant modifiable par groupe/autres est refusé ; sur les filesystems ACL-only, les ACL natives du profil/administrateur restent l'autorité de plateforme et les fichiers créés par MORPHEUS sont durcis quand la vue ACL est disponible.

## Authorization

Ordre :

```text
READ < WRITE < ADMIN
```

Classification remote :

- `GET`/`HEAD` : READ, sauf métriques ADMIN ;
- POST read-only explicitement listés : READ ;
- POST/PUT/PATCH/DELETE restants : WRITE ;
- `/provider-plugins/probe` : ADMIN et POST uniquement ;
- `/server/backups` : ADMIN ;
- `/server/status` : READ ;
- méthode non supportée : 405.

Les POST read-only incluent Query DSL, exports, policy evaluate/dry-run, transition-check, augmented context, saved-view execute/export et external-reference resolve.

Une route inconnue n’obtient jamais ADMIN.

### Provider plugins en remote

La discovery reste une opération metadata-only. Elle refuse un répertoire de plugins symbolique et ignore/refuse les candidats JAR symboliques ; l'ouverture du candidat utilise `NOFOLLOW_LINKS`, de sorte qu'une discovery ne puisse pas suivre un lien `*.jar` hors du répertoire configuré.

Le probe exécutable remote :

- est réservé à ADMIN ;
- utilise uniquement le répertoire de plugins configuré côté serveur ;
- réapplique `AllowedWorkspaceRoots` au workspace demandé ;
- exige un paramètre `sha256` de 64 hex ;
- refuse un probe sans pin avec `PLUGIN_SHA256_REQUIRED`.

Pour un plugin épinglé, `ExternalJarIntegrity` copie d'abord le JAR dans un fichier de staging privé, vérifie le SHA-256 de **cette copie**, puis `URLClassLoader` charge exclusivement cette copie. Le chemin original peut changer après le staging sans modifier le code réellement exécuté. Le staging est supprimé après fermeture du classloader.

Le même principe de staging vérifié est utilisé pour les JARs MINOS/NEXUS lorsqu'un pin d'intégrité est configuré ; les clients MCP déjà démarrés sont fermés si `initialize()` ou `listTools()` échoue.

## Autorité filesystem des workspaces

`AllowedWorkspaceRoots` sépare le rôle métier `WRITE` des droits OS du compte
serveur. La configuration vient de `--workspace-root` (répétable),
`MORPHEUS_SERVER_WORKSPACE_ROOTS` ou `morpheus.server.workspaceRoots`; aucune
requête ne peut modifier cette allowlist.

L’enregistrement canonicalise et persiste le real path autorisé. Les opérations
ultérieures, notamment `sync`, réappliquent la politique au chemin persisté afin
qu’un ancien projet hors racine ou un répertoire remplacé par un lien soit
refusé. La décision exige à la fois confinement lexical, absence de composant
symbolique et confinement du real path. Le mode local conserve son comportement
historique sans politique remote.

Les lectures provider passent par `SafeWorkspaceFileResolver`, qui applique un décodage UTF-8 strict (`REPORT`) : un flux malformé est rejeté et n'est jamais normalisé silencieusement avec le caractère de remplacement Unicode.

### Budget du scan d'inventaire

Le scan source qui précède l'ingestion est lui aussi borné **avant** le fingerprint SHA-256 des fichiers. `SourceScanPolicy.safeDefaults()` limite :

```text
profondeur             128 segments
répertoires traversés  50 000
fichiers                50 000
taille d'un fichier     64 MiB
volume agrégé           2 GiB
```

Le dépassement produit un `SourceInventoryScanResult` incomplet ; la synchronisation ne publie pas cette observation comme nouvelle baseline. Le compteur de répertoires empêche également une arborescence composée de très nombreux répertoires vides de contourner le budget. Les budgets provider plus fins continuent ensuite de s'appliquer à l'ingestion métier.

## TLS

Implémentation JDK uniquement :

```text
HttpsServer
KeyStore PKCS12
KeyManagerFactory
SSLContext TLS
protocols TLSv1.3 + TLSv1.2
```

Le mot de passe du keystore est cloné pour l’initialisation puis le tableau temporaire est effacé.

## Concurrence et timeout proxy

Le frontal utilise un `Semaphore` équitable. `tryAcquire()` est non bloquant : lorsque le budget est atteint, la requête reçoit `429`.

Le modèle n’ajoute pas une queue applicative non bornée. Les invariants SQLite/CAS existants restent responsables des conflits métier.

Les opérations read-only dont l'exécution est entièrement contrôlée par MORPHEUS utilisent un timeout amont de 60 secondes et retournent `504 UPSTREAM_TIMEOUT` en cas de dépassement. Les mutations POST/PUT/PATCH/DELETE ne reçoivent pas cette deadline proxy arbitraire : le slot de concurrence reste détenu jusqu'à la réponse réelle de l'API loopback.

`provider-plugins/probe` constitue une exception explicite au timeout read-only. Le probe exécute du code tiers approuvé par ADMIN et le SDK ne garantit pas que ce code respecte une interruption/cancellation. Retourner `504` puis libérer le semaphore alors que le plugin continue serait donc une fausse garantie. Le frontal attend la fin réelle du probe et conserve le slot pendant toute son exécution.

Une coupure réseau côté client reste un résultat distribué potentiellement ambigu ; le client doit réconcilier l'état avant un retry d'une opération non idempotente.

### Sessions SQLite par opération

Chaque opération locale ou remote qui construit `ApiRuntime` possède un `SqliteConnectionScope` thread-confined. Les neuf stores du runtime empruntent neuf connexions logiques, mais partagent exactement une connexion physique. La vérification/migration du schéma est exécutée une seule fois dans ce scope ; la fermeture des stores ne ferme que leurs handles logiques, puis `ApiRuntime.close()` ferme la connexion physique propriétaire.

Le runtime Query HTTP utilise désormais le même principe : ses cinq stores (`snapshots`, `requirements`, `content`, `portfolios`, `saved`) partagent une seule connexion physique et le constructeur nettoie tous les handles déjà ouverts si une initialisation ultérieure échoue.

Une invocation CLI ouvre elle aussi un seul `SqliteConnectionScope` partagé par ses stores. La récupération des snapshots, les lectures et les mutations d'une commande restent donc dans une pression d'une seule connexion physique, avec fermeture du scope en dernier.

`SqliteConnectionScope.diagnostics()` expose les compteurs process `opened`, `closed`, `active` et `peak` sans chemin de base ni donnée métier. Un scope ne peut pas être imbriqué, changer de base/timeout, traverser un thread ou survivre à l’opération qui le possède. Un appel `close()` depuis le mauvais thread est rejeté **avant** de modifier l'état du scope afin que le thread propriétaire puisse encore effectuer le cleanup réel.

Les blocs transactionnels des stores et du gestionnaire de schéma délèguent à `SqliteTransactionRunner`. Le runner emprunte la connexion sans jamais la fermer, possède la transition d’auto-commit, le commit/rollback et la restauration du mode précédent. Il exige `autoCommit=true` à l'entrée : une transaction déjà ouverte appartient au caller et est refusée sans `commit()` ni `rollback()` afin d'interdire les transactions imbriquées implicites.

Une erreur métier, SQL **ou `Error`** quittant le work avant commit provoque un rollback best-effort. La défaillance primaire est réémise ; les échecs de rollback et de restauration sont rattachés comme exceptions `suppressed`.

Si le `commit()` réussit mais que la restauration du mode auto-commit échoue ensuite, le runner lève un `SqliteCommittedTransactionException`. Cette exception signifie explicitement que **la mutation est déjà commitée et ne doit pas être rejouée comme si elle avait rollbacké**.

### Réconciliation du sync après publication

La publication d'un snapshot et la persistance de la baseline de synchronisation restent deux transactions distinctes ; MORPHEUS ne prétend pas les transformer artificiellement en transaction distribuée atomique.

`IncrementalSyncService.complete()` applique une réconciliation bornée :

1. tente la persistance de la baseline ;
2. en cas d'exception, relit état + inventaire et accepte un commit déjà visible ;
3. sinon tente **un seul retry idempotent** ;
4. relit à nouveau après le retry ;
5. si une publication a pu avoir lieu mais que la baseline reste impossible à persister, enregistre `BASELINE_INCONSISTENT` plutôt que `EXECUTION_FAILED` ;
6. le plan suivant force alors un full rebuild conservateur.

`fail()` ne dégrade pas un commit déjà visible. Un scan incomplet conserve sa cause `SCAN_INCOMPLETE` et n'est plus reclassé comme échec d'exécution générique.

## Schéma SQLite

Le gestionnaire de migrations connaît la version maximale supportée par le runtime (`15` sur cette baseline). Après création/lecture du ledger et **avant toute migration connue**, une base dont `MAX(schema_migrations.version) > 15` est refusée. La règle de compatibilité utilisée à l'ouverture normale et celle du backup/restore sont ainsi cohérentes : aucun downgrade implicite d'une base future.

Le runtime utilise `journal_mode=PERSIST` avec busy timeout et refuse les sidecars WAL/SHM dans ce mode. La mitigation de concurrence ne repose donc pas sur WAL.

## Runtime status

`/api/v1/server/status` expose :

```text
mode
transport
host
port
startedAt
uptimeSeconds
activeRequests
maxConcurrentRequests
totalRequests
authenticationFailures
authorizationFailures
throttledRequests
```

Aucun header, token, token hash, keystore password ou payload métier.

## Backup

`SqliteServerMaintenance.createBackup` :

1. ouvre le store pour confirmer/migrer normalement le schéma courant ;
2. crée une destination sous le répertoire explicitement fourni ;
3. rejette symlink et path escape ;
4. exécute `VACUUM INTO` ;
5. durcit les permissions ;
6. exécute `integrity_check` ;
7. lit `schema_migrations` ;
8. calcule SHA-256.

M26 ne crée pas V016 : les données remote sont de la configuration externe/opérationnelle et non une nouvelle vérité métier en SQLite.

## Restore offline

`restoreOffline` :

```text
explicit --confirm
    -> verify backup
    -> acquire <db>.server.lock
    -> reject unsafe target/sidecars
    -> copy to staging file
    -> verify staging checksum
    -> replace target atomically when supported
    -> harden permissions
    -> verify final database
```

Le remote server garde le même file lock pendant toute sa durée de vie. Une restauration concurrente échoue donc avant remplacement.

`SUPPORTED_SCHEMA_VERSION = 15`. Un backup `> 15` est rejeté. Un backup `<= 15` est accepté ; les migrations normales restent l’unique mécanisme d’upgrade lors de l’ouverture suivante.

### Format des migrations SQLite

Les ressources `db/migration/VNNN__*.sql` peuvent contenir plusieurs instructions. Leur découpage reconnaît les
littéraux SQL, les identifiants quotés (`"..."`, `` `...` ``, `[...]`), les commentaires de ligne/bloc et les corps
`CREATE TRIGGER ... BEGIN ... END`. Un point-virgule dans l’une de ces constructions n’est donc jamais interprété
comme une fin d’instruction. Les expressions `CASE ... END` imbriquées dans un trigger sont également prises en
charge.

Une chaîne, un identifiant, un commentaire de bloc ou un corps de trigger non terminé est rejeté avec sa position
ligne/colonne. L’ensemble du script et l’écriture correspondante dans `schema_migrations` s’exécutent dans la même
transaction : toute erreur SQL annule les instructions précédentes et n’avance pas la version du schéma.

## Surface publique

M26 ajoute :

```text
server.status
server.identity.create
server.identity.list
server.identity.revoke
server.identity.rotate
server.identity.role
server.backup.create
server.backup.verify
server.restore
```

Expositions intentionnelles :

```text
server.status             HTTP remote
identity lifecycle        CLI local only
backup create             CLI local + HTTP ADMIN
backup verify             CLI local only
restore                   CLI offline only
provider plugin discover  HTTP READ
provider plugin probe     HTTP ADMIN + trusted SHA-256
MCP                       aucune surface control-plane M26
```

Voir `contracts/public-surfaces.tsv` et `docs/openapi/morpheus-v1-remote-m26.yaml`.

## Tests

- `MorpheusRemoteIdentityFileTest` / lifecycle tests : hash-only, auth, malformed/duplicates, mutations, concurrence et compaction de l'audit ;
- `MorpheusRemoteHttpServerTest` : PKCS12 réel, HTTPS, 401/403, rôles, live revoke, pin plugin, timeout classification, headers, backup ADMIN, 429, secret non-disclosure ;
- `ProviderPluginDiscoveryTest` : discovery metadata-only et refus des symlinks ;
- `RemoteApiLaunchOptionsTest` : local loopback et startup remote fail-closed ;
- `MorpheusServerCliTest` : provisioning/lifecycle + backup/verify/restore ;
- `ApiRuntimeSqliteSessionTest` / `CliRuntimeSqliteSessionTest` : une connexion physique par runtime ;
- `SqliteConnectionScopeTest` / `SqliteTransactionRunnerTest` : confinement thread, transaction ownership, `Error`, cleanup et résultat post-commit explicite ;
- `SyncReliabilityFallbackTest` : commit visible, retry borné, `BASELINE_INCONSISTENT`, `SCAN_INCOMPLETE` ;
- `SqliteFutureSchemaCompatibilityTest` / `SqliteServerMaintenanceTest` : integrity/schema/lease/future-schema ;
- `RemoteServerArchitectureTest` : boundaries et contrats source/manifest/OpenAPI.

## Gates

Le gate durable exact-head est M21 sur Linux et Windows, avec la baseline active `1.2.1`. Les gates milestone M26 restent des preuves historiques/spécialisées :

```text
Windows  .\validate-m26.cmd 1.0.0
Linux    bash ./scripts/validate-m26.sh 1.0.0
```

La CI canonique déclenche M21 sur les pull requests, `main` et `develop`. Le workflow `MORPHEUS Security` ajoute OWASP Dependency-Check à la frontière `main` et sur cadence hebdomadaire. Les actions GitHub sont épinglées par SHA immuable et le Maven Wrapper vérifie le SHA-256 de Maven 3.9.16.

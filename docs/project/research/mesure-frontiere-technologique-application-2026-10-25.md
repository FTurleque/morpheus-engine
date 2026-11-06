# Mesure de la technologie atteinte par le cœur applicatif — 25/10/2026

Relevé daté. Cette mesure a servi à décider [ADR-0150](../../architecture/adr/ARCH-0150-ce-que-le-coeur-applicatif-peut-toucher.md)
(option C) ; elle n'est pas tenue à jour. Les classes nommées en vigueur, leur famille et leur sort sont dans la table
« Classes nommées » de l'ADR, que le build compare à `ApplicationTechnologyBoundaryArchitectureTest`.

Le texte ci-dessous est la section « Mesure » de l'ADR telle qu'elle a été écrite, recopiée sans modification. Il
décrit l'état de `develop` à `b60a9286`, avant l'extraction du transport de la découverte de mise à jour : le réseau
sortant et les fils d'exécution de `product.UpdateDiscoveryService` sont sortis de l'application avec elle, et
`LocalWritePermissionHardener` vit dans `com.morpheus.application.files`, et non dans `security` comme l'écrit la
table.

---

Mesuré par une règle ArchUnit jetable sur `develop` à `b60a9286`, le 25/10/2026 : 349 sources, 36 paquets, 539 classes
importées (classes imbriquées comprises, rapportées à leur classe de premier niveau). Une famille est un ensemble de
types ou d'appels ; la colonne en donne les principaux, et `ApplicationTechnologyBoundaryArchitectureTest` la liste
complète :

| Famille | Types ou appels | Classes |
|---|---|---|
| Réseau sortant | `java.net.http..`, `URL`, `Socket`, `HttpURLConnection`, `javax.net..` | `product.UpdateDiscoveryService` (`:8-10`, `:44`) |
| Accès fichier | `Files`, `FileSystems`, `FileSystem`, `WatchService`, `DirectoryStream`, `java.nio.file.attribute..`, `java.nio.channels..`, flux fichier de `java.io` | 11, ci-dessous |
| Environnement du processus | `System.getenv`, `System.getProperty` | `operability.LocalOperationalRuntime` (`:20`), `operability.SensitiveValueRedactor` (`:43`), `product.ProductMetadata` (`:25`), `security.LocalWritePermissionHardener` (`:322`, `:356`) |
| Flux standard | `System.out`, `System.err`, `System.in` | `operability.OperationalRuntime` (`:34`), `operability.RotatingOperationalLogFile` (`:39`) |
| Fils d'exécution | `Executors`, `ExecutorService`, `CompletableFuture`, `ForkJoinPool`, `Thread.Builder` | `product.UpdateDiscoveryService` (`:139`) |
| Horloge murale | `*.now()` de `java.time`, `Clock.system*`, `System.currentTimeMillis` | `operability.LocalOperabilityService` (`:34`, `:39`), `operability.OperationalRecorder` (`:37`, `:52`, `:95`) ; `Clock.systemUTC()` comme valeur par défaut d'un constructeur : `lifecycle.mutation.ControlledChangeLifecycleMutationService`, `policy.PolicyPackService`, `portfolio.PortfolioRegistryService`, `query.saved.SavedViewService` |
| Réflexion, chargement de classes | `java.lang.reflect..`, `java.lang.invoke..`, `ServiceLoader`, `ClassLoader` | `query.compact.CanonicalJsonSerializer` (`:3-5`), `reasoning.ReasoningAdapterRegistry` (`:63`) |
| SQL | `java.sql..`, `javax.sql..` | aucune |
| Bibliothèque JSON | `tools.jackson..`, `com.fasterxml..` | aucune |
| Exécution de processus | `ProcessBuilder`, `Process`, `ProcessHandle`, `Runtime` | aucune |

`java.nio.file.Path` seul est utilisé par 26 classes, dont les signatures de ports (`SpecificationProvider`,
`ProviderReadRequest`, `ProviderProjectRoot`) : c'est le vocabulaire d'un produit local-first, pas un accès.

Les onze classes d'accès fichier (2 424 lignes) :

| Classe | Lignes | Appelants de production | Tests d'architecture qui la nomment |
|---|---|---|---|
| `files.SafeWorkspaceFileResolver` | 269 | 9 (providers, remote) | 4 |
| `sync.LocalSourceInventoryScanner` | 408 | `MorpheusProjectSyncApiService`, `MorpheusProjectCli` | 10 |
| `sync.ScanWorkspaceBoundary` | 134 | `LocalSourceInventoryScanner`, `WorkspacePathBoundary` | 0 |
| `sync.SourceFingerprint` | 235 | 5, dont `SqliteSyncStateStore` | 3 |
| `security.ExternalJarIntegrity` | 121 | 6 (plugins, pairs MCP) | 3 |
| `security.LocalWritePermissionHardener` | 413 | 8 (stores SQLite, remote, journal) | 2 |
| `operability.RotatingOperationalLogFile` | 149 | `OperationalRuntime`, `RemoteAccessJournal` | 0 |
| `product.UpdateDiscoveryService` | 313 | `MorpheusProductCli` | 4 |
| `sync.LocalSourceWatcher` | 237 | aucun | 1 |
| `discovery.ProjectDiscoveryService` | 102 | aucun | 0 |
| `discovery.WorkspaceRootResolver` | 43 | `ProjectDiscoveryService` seulement | 0 |

`java.time.Clock` est le port d'horloge, injecté par constructeur.

---
id: ARCH-0148
title: "Le cœur applicatif touche le disque local par des classes nommées, et jamais le réseau"
domain: architecture
rules: false
---

# Le cœur applicatif touche le disque local par des classes nommées, et jamais le réseau

- Statut : **Acceptée**
- Date : 2026-10-25

## Context

`morpheus-application` définit les ports, et aucun adaptateur ne lui est visible (`LayerDependencyTest`). Rien ne dit
en revanche quelle **technologie** il peut toucher lui-même : seuls `application.reasoning` et `application.policy`
portent un interdit, textuel (`m27/ReasoningPlatformArchitectureTest`, `m25/PolicyPlatformArchitectureTest`), et les
34 autres paquets peuvent acquérir `java.sql` ou un second client HTTP sans qu'aucun gate ne réagisse. Le constat
AUD-ARC-01 de l'audit du 09/10/2026 le relève ; AUD-ARC-02, AUD-ARC-03, AUD-ARC-06 et AUD-PRF-05 attendent la décision.

### Mesure

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
| `sync.LocalSourceInventoryScanner` | 408 | `MorpheusProjectSyncApiService`, `MorpheusCli` | 10 |
| `sync.ScanWorkspaceBoundary` | 134 | `LocalSourceInventoryScanner`, `WorkspacePathBoundary` | 0 |
| `sync.SourceFingerprint` | 235 | 5, dont `SqliteSyncStateStore` | 3 |
| `security.ExternalJarIntegrity` | 121 | 6 (plugins, pairs MCP) | 3 |
| `security.LocalWritePermissionHardener` | 413 | 8 (stores SQLite, remote, journal) | 2 |
| `operability.RotatingOperationalLogFile` | 149 | `OperationalRuntime`, `RemoteAccessJournal` | 0 |
| `product.UpdateDiscoveryService` | 313 | `MorpheusProductCli` | 4 |
| `sync.LocalSourceWatcher` | 237 | aucun | 1 |
| `discovery.ProjectDiscoveryService` | 102 | aucun | 0 |
| `discovery.WorkspaceRootResolver` | 43 | `ProjectDiscoveryService` seulement | 0 |

`java.time.Clock` est le port d'horloge, injecté par constructeur, et `UpdateDiscoveryService(HttpClient, Duration)`
permet de tester la découverte sans réseau.

### Options considérées

- **A — un cœur pur.** Toutes les familles en usage sortent de l'application : un port y est déclaré, un adaptateur
  l'implémente. Coût : les onze classes et les quatre lecteurs d'environnement, environ 2 400 lignes, dont cinq classes
  épinglées par des assertions de sécurité textuelles qui doivent les suivre ; un foyer à trouver (un nouveau module
  change la population de couverture d'ADR-0104, le classpath importé et la livraison ; un store ou un provider est le
  mauvais propriétaire d'une frontière d'espace de travail) ; et `Path` reste dans les ports, donc « pur » voudrait dire
  « sans accès », pas « sans `java.nio.file` ».
- **B — une couche de service autorisée à faire de l'I/O locale.** Toutes les familles en usage restent, confinées aux
  classes mesurées ; les familles inutilisées sont refusées partout. Coût minimal ; le réseau sortant reste dans la
  couche qui définit les ports, et le diagramme cesse de promettre un cœur sans technologie.
- **C — exceptions nommées, chacune avec son sort.** La même règle que B, et chaque classe nommée **reste** ou **sort**.
  Seul le transport réseau sort : c'est l'unique sortie réseau du cœur, une classe, un appelant (`MorpheusProductCli`),
  et le refus de `http:` avant toute I/O comme l'analyse du manifeste restent dans l'application. L'accès local reste :
  il est la matière d'un cas d'usage local-first, ces classes sont la frontière de sécurité de l'espace de travail et
  des fichiers sensibles, et aucun défaut observé ne se corrige en les déplaçant. Les trois classes sans appelant
  restent nommées tant qu'elles existent : leur retrait relève du code inatteignable. Un port d'horloge propre au
  projet serait un second nom pour `java.time.Clock`. La réflexion est nommée usage par usage par le changement
  `name-every-use-of-reflection`. Les interdits textuels de `reasoning` et `policy` ne migrent pas vers une règle de
  dépendance : ils refusent une mention, et interdisent `com.morpheus.application.store`,
  `com.morpheus.application.lifecycle.mutation`, `ScriptEngine`, `SELECT * FROM`, `executeQuery(`. SQL étant refusé,
  le runtime SQLite d'une opération ne peut pas entrer dans l'application ; le choix entre tenir son type dans les
  racines de composition des transports ou un port de l'application qui le masque reste celui d'ADR-0109. Coût de C :
  celui de B, plus une extraction dans un seul service. Retenu.

## Decision

### 1. SQL, JSON et processus sont refusés dans toute l'application

Aucune classe de `com.morpheus.application..` ne dépend de `java.sql`, de Jackson ni de l'exécution de processus.

### 2. Une famille en usage est confinée à des classes nommées, chacune avec son sort

| Classes nommées | Familles | Sort |
|---|---|---|
| `UpdateDiscoveryService` | réseau, fils, fichier | **sort** : le transport passe derrière un port de `application.product`, implémenté dans `morpheus-cli` |
| `SafeWorkspaceFileResolver`, `LocalSourceInventoryScanner`, `ScanWorkspaceBoundary`, `SourceFingerprint`, `ExternalJarIntegrity`, `RotatingOperationalLogFile` | fichier | reste |
| `LocalWritePermissionHardener` | fichier, environnement | reste |
| `LocalSourceWatcher`, `ProjectDiscoveryService`, `WorkspaceRootResolver` | fichier | reste tant que la classe existe |
| `LocalOperationalRuntime`, `SensitiveValueRedactor`, `ProductMetadata` | environnement | reste |
| `OperationalRuntime`, `RotatingOperationalLogFile` | flux standard | reste |
| les six classes de la mesure | horloge murale | reste ; `java.time.Clock` est le port |
| `CanonicalJsonSerializer`, `ReasoningAdapterRegistry` | réflexion | reste |

- Une liste est exactement l'ensemble des classes qui atteignent la famille : elle rétrécit, et un nom ajouté exige un
  ADR consolidé qui remplace celui-ci.
- `Path`, `LinkOption`, `IOException` et les flux en mémoire sont du vocabulaire, pas un accès.

### 3. Les interdits textuels de `reasoning` et `policy` restent

Ils refusent aussi une mention, et davantage qu'une famille.

### 4. Le runtime SQLite reste hors de l'application

Ce qu'un transport tient de `SqliteOperationRuntime` et `SqliteServerMaintenance` relève d'ADR-0109.

## Do's and Don'ts

### Do

- Faire passer par un port de l'application tout accès réseau dont un cas d'usage a besoin.
- Lire et écrire l'espace de travail local par les classes nommées, en premier lieu `SafeWorkspaceFileResolver`.
- Injecter l'horloge par `java.time.Clock`.
- Retirer de la liste d'une famille une classe qui ne l'atteint plus.

### Don't

- Ne pas faire dépendre une classe de l'application de `java.sql`, de Jackson ni de `ProcessBuilder`.
- Ne pas ajouter de classe à la liste d'une famille confinée sans un ADR consolidé qui remplace celui-ci.
- Ne pas présenter l'application comme une couche sans technologie : elle fait de l'accès local, par des classes
  nommées.
- Ne pas compter `Path` comme un accès fichier.

## Consequences

### Positives

- Toute famille de technologie de l'application est écrite, et l'état présent est gelé : la décision porte sur ce qui
  existe, pas sur ce qui s'ajoute pendant qu'elle est en attente.
- L'unique sortie réseau du cœur passe derrière un port, ce qui la rend testable sans client HTTP et la range dans le
  module qui possède le processus.
- Les listes nommées sont le compteur d'une éventuelle option A, comme `COMPOSITION_ROOTS` l'est pour ADR-0109.

### Négatives

- Le cœur n'est pas purement hexagonal : dix classes d'accès fichier, dont trois sans appelant de production, et quatre lecteurs d'environnement y restent.
- La règle ajoute des listes de noms, que l'audit voudrait moins nombreuses (AUD-ARC-06).

### Risques

- Les trois classes sans appelant de production restent nommées tant qu'aucune décision ne les retire.
- Le transport extrait emporte avec lui des invariants de sécurité épinglés textuellement : une extraction qui
  laisserait ces assertions sur un fichier vidé garderait les gates verts sans plus rien surveiller.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/ApplicationTechnologyBoundaryArchitectureTest.java`,
  règles ArchUnit sur le bytecode, dont les listes de types et d'appels sont la source vivante des familles :
  `aFamilyTheApplicationDoesNotUseIsRefusedInEveryPackage` (décision 1),
  `aConfinedFamilyIsReachedOnlyByTheClassesNamedForIt` et `everyNamedClassStillReachesItsFamily` (décision 2, les deux
  sens), `aPathUsedAsAValueIsNotAnAccess` (le vocabulaire).
- Décision 4 : la famille SQL de `aFamilyTheApplicationDoesNotUseIsRefusedInEveryPackage` interdit le runtime SQLite
  dans l'application ; ce que les transports en tiennent est tenu par `AdapterCompositionRootArchitectureTest` et
  `SqliteStorePackagesArchitectureTest` (ADR-0109).
- `m27/ReasoningPlatformArchitectureTest` et `m25/PolicyPlatformArchitectureTest` : interdits textuels inchangés.
- `LayerDependencyTest` : l'application ne dépend d'aucun adaptateur.
- Revue : l'extraction du transport de découverte de mise à jour (changement OpenSpec
  `move-marked-technology-access-behind-application-ports`) déplace avec lui `UpdateTrustBoundaryContractTest`,
  `AuditHardeningWorkflowContractTest`, `ProductionIntegrityContractTest` et `SilentCatchArchitectureTest`.

## References

- [ADR-0109](ARCH-0109-transport-adapters-are-named-composition-roots.md) — racines de composition nommées des
  transports.
- [ADR-0147](ARCH-0147-un-role-d-adaptateur-est-un-paquet.md) — rôles de l'adaptateur HTTP en paquets.
- [ADR-0104](GEN-0104-two-coverage-scales-share-one-population.md) — population de couverture.
- [ADR-0004](SEC-0004-local-first-no-llm-core.md) — cœur local-first.
- Changements OpenSpec `decide-what-technology-the-application-core-may-touch` et
  `move-marked-technology-access-behind-application-ports`.
- Audit du 09/10/2026, constat AUD-ARC-01 : `docs/project/audits/2026-10-09/findings.md`.
- `.claude/rules/architecture.md`

# MORPHEUS — constats d'audit
Dépôt `FTurleque/morpheus-engine`, révision `20cf2e0` (`main` == `develop`), version produit 1.2.1. Audit du 9 octobre 2026. Les constats sont triés par sévérité puis par axe.
**96 constats** : 11 Élevée, 40 Moyenne, 21 Faible, 24 Info.
Chaque constat porte une preuve `fichier:ligne` ouverte pendant l'audit, ou une commande exécutée. Les constats `AUD-TRV-*` sont des symptômes vus par plusieurs axes, fusionnés en un seul constat. Le champ **Sprint** donne l'ordre de correction : il est contraint par les dépendances, et `sprints.md` explique le découpage. Ce qui n'a pas pu être vérifié est dans `README.md`, section « Angles morts ».
---

## Sévérité : Élevée

### AUD-ARC-01 — morpheus-application, la couche qui définit les ports, fait de l'I/O fichier et du HTTP sortant en direct
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Architecture |
| **Effort** | L |
| **Sprint** | 11 — Trancher l'architecture |
| **Débloque** | AUD-ARC-02 (sprint 11), AUD-ARC-03 (sprint 11), AUD-ARC-06 (sprint 12), AUD-PRF-05 (sprint 13) |

**Preuve**

```
morpheus-application/src/main/java/com/morpheus/application/product/UpdateDiscoveryService.java:8-10 et :44 -- `import java.net.http.HttpClient; import java.net.http.HttpRequest; import java.net.http.HttpResponse;` puis `this(HttpClient.newBuilder()`. Et `grep -rln '^import java.nio.file.Files;' morpheus-application/src/main/java` renvoie 10 fichiers (sync x4, security x2, discovery x2, product, files), p.ex. morpheus-application/src/main/java/com/morpheus/application/sync/LocalSourceInventoryScanner.java et .../files/SafeWorkspaceFileResolver.java. Aucun port d'infrastructure : `grep -rln 'interface.*\(FileSystem\|Clock\|HttpPort\|Transport\)' morpheus-application/src/main/java` ne renvoie rien.
```

**Impact** — Le coeur de l'hexagone n'est technologiquement neutre qu'au niveau du nom de module : tester une regle metier de `sync`, `discovery`, `security` ou `product` exige un vrai systeme de fichiers et, pour la decouverte de mise a jour, un vrai client HTTP. L'interdiction textuelle de `java.net.http`/`java.sql`/`ProcessBuilder` n'existe que pour 2 des 36 paquets de l'application (`application.reasoning`, `application.policy`, cf. .claude/rules/architecture.md), donc 38 paquets peuvent acquerir n'importe quelle dependance JDK d'infrastructure sans qu'aucun gate ne reagisse.

**Action** — Decider explicitement (ADR) si `application` est un coeur pur ou une couche de service autorisee a faire de l'I/O. Si coeur pur : extraire des ports (`SourceInventoryPort`, `UpdateManifestPort`, `WorkspaceFilePort`) dans `application` et deplacer les implementations `java.nio.file` / `java.net.http` dans un adaptateur (store-fs, provider, ou un nouveau `morpheus-infra-local`). Si couche de service : etendre l'interdit textuel existant a tous les paquets qui doivent rester purs et le dire dans CLAUDE.md, plutot que de laisser le diagramme promettre un coeur sans technologie.

### AUD-ARC-02 — Le chemin d'ingestion principal n'a aucun port : api et cli instancient directement le lecteur OpenSpec concret
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Architecture |
| **Effort** | M |
| **Sprint** | 11 — Trancher l'architecture |
| **Après** | AUD-ARC-01 (sprint 11) |

**Preuve**

```
morpheus-provider-openspec/src/main/java/com/morpheus/provider/openspec/OpenSpecProjectContentReader.java:15 -- `public final class OpenSpecProjectContentReader {` (aucun `implements`). Consomme en dur par morpheus-api/src/main/java/com/morpheus/api/MorpheusProjectSyncApiService.java:67 (`var normalized = new OpenSpecProjectContentReader().read(`) et morpheus-cli/src/main/java/com/morpheus/cli/MorpheusCli.java:272 et :548. Le repertoire source est lui aussi code en dur : MorpheusProjectSyncApiService.java:55 `List.of(Path.of("openspec"))`. `grep -rn 'interface .*ContentReader' morpheus-application/src/main/java` ne renvoie qu'un port, SpecificationContentReader.java:7, qui couvre une autre capacite (lecture de specification, implemente par 4 providers).
```

**Impact** — La capacite d'ecriture centrale (sync -> snapshot publie) est verrouillee sur un seul provider, alors que .claude/rules/architecture.md exige qu'un consommateur de port fonctionne avec les trois providers. ProviderAntiLockInTest ne protege que `SpecificationContentReader` (3 methodes @Test, lignes 30 a 52) et ne voit pas ce chemin. Ajouter l'ingestion markdown ou synthetique demande de modifier api et cli, pas d'ajouter un adaptateur ; c'est aussi la seule raison pour laquelle morpheus-api declare `morpheus-provider-openspec` en scope compile pour un unique usage.

**Action** — Declarer un port `ProjectContentReader` dans `com.morpheus.application.read`, le faire implementer par OpenSpecProjectContentReader, injecter l'implementation depuis MorpheusMain / les racines de composition, et etendre ProviderAntiLockInTest a ce port. Externaliser le nom de repertoire `openspec` dans la description du provider plutot que dans le service HTTP.

### AUD-TRV-05 — La politique de connexion SQLite est une propriété de chaque site de composition, pas du module : 12 sites, 3 ouvertures de scope
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Architecture |
| **Effort** | M |
| **Sprint** | 5 — Écarter le risque d'indisponibilité |
| **Débloque** | AUD-PRF-03 (sprint 5) |
| **Constats fusionnés** | AUD-ARC-05, AUD-ARC-04 |

**Preuve**

```
grep -rln 'new Sqlite' morpheus-api/src/main/java morpheus-mcp/src/main/java -> 12 fichiers. Trois ouvertures de scope seulement dans tout src/main : morpheus-api/.../ApiRuntime.java:42, morpheus-api/.../MorpheusQueryApiService.java:265 (classe interne Runtime qui duplique ApiRuntime avec un autre jeu de stores) et morpheus-cli/.../CliRuntime.java:38. Neuf sites ouvrent des stores sans aucun scope, dont morpheus-mcp/.../MorpheusMcpRuntime.java:27-33 dont le javadoc l'ecrit : 'Each store opens its own SQLite connection, and the constructor opens seven of them.' - a comparer a ApiRuntime.java:20-21 'Nine logical stores share exactly one physical, thread-confined SQLite connection and one schema check.' Les huit autres : MorpheusPolicyManagementHttpRoutes.java:59,71, MorpheusPortfolioApiService.java:138, MorpheusOperabilityApiService.java:25, MorpheusLocalHttpServerBootstrap.java:150, MorpheusCompositionMcpTools.java:56-57, MorpheusPortfolioMcpTools.java:92, MorpheusPolicyMcpManagementTools.java:50, MorpheusMcpServer.java:112. Le scope est ambiant et non imbricable : SqliteConnectionScope.java:19 private static final ThreadLocal<State> ACTIVE, :38-40 'nested SQLite connection scopes are not supported', consomme par SqliteDatabaseSecurity.java:29 borrowIfActive et SqliteSchemaManager.java:48 schemaReadyIfActive. grep -rn 'SqliteConnectionScope' morpheus-architecture-tests -> vide.
```

**Impact** — Chaque appel d'outil MCP ouvre 7 connexions physiques SQLite et rejoue 7 fois la verification de schema, la ou la meme operation sous HTTP ou CLI en ouvre une seule : divergence de pression sur la base et de semantique transactionnelle entre deux surfaces que le manifeste de convergence declare equivalentes. Et les deux runtimes qui ouvrent un scope ne peuvent pas coexister sur le meme thread : une route qui aurait besoin a la fois d'ApiRuntime et du Runtime de MorpheusQueryApiService levait IllegalStateException a l'execution sans qu'aucun gate ne le voie. ADR-0109 nomme 32 racines de composition, ce qui donne l'illusion d'une racine unique par adaptateur. .claude/rules/security.md exige d'ouvrir les connexions via SqliteConnectionScope.open(databasePath) et aucun test, ArchUnit ou textuel, ne le verifie. Fusion de deux constats en relecture : l'audit livrait le cas restreint (MCP) en Elevee et le cas general en Moyenne, soit l'inversion exacte.

**Action** — Unifier la composition par adaptateur : un seul type runtime par transport, parametre par le sous-ensemble de stores necessaire, qui ouvre toujours le scope - en commencant par MorpheusMcpRuntime, le plus couteux et le plus simple a aligner (effort S a lui seul). Ajouter ensuite la regle manquante : une regle ArchUnit exigeant que toute classe de api/mcp/cli qui instancie un Sqlite*Store depende aussi de SqliteConnectionScope, a defaut une assertion textuelle sur les sites de runtime. Et transformer le refus d'imbrication en assertion testee plutot qu'en invariant implicite, par un test prouvant que deux runtimes du meme adaptateur se construisent en sequence sur un meme thread.

### AUD-TRV-04 — Le produit est muet : aucun journal applicatif, slf4j-nop comme seul binding, et un répertoire de logs publié sans aucun écrivain
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Qualité · Dépendances |
| **Effort** | M |
| **Sprint** | 4 — Rendre le produit observable |
| **Après** | AUD-DEP-10 (sprint 4) |
| **Débloque** | AUD-QUA-13 (sprint 4), AUD-SEC-04 (sprint 4) |
| **Constats fusionnés** | AUD-QUA-01, AUD-DEP-08, AUD-DEP-09 |

**Preuve**

```
Aucun emetteur cote MORPHEUS : grep -rln 'org.slf4j' --include=*.java sur les 659 fichiers de src/main -> 0 ; grep -rn 'LoggerFactory.getLogger' -> 0 ; printStackTrace -> 0. 21 suppressions d'exception deliberees, chacune avec un commentaire justificatif mais sans trace exploitable, p.ex. morpheus-application/.../reasoning/ReasoningAdapterRegistry.java:53 (catch ServiceConfigurationError | RuntimeException, 'Optional providers are fault-isolated from MORPHEUS facts-only operation') et morpheus-store-sqlite/.../SqliteChangeLifecycleMutationStore.java:380 (catch SQLException ignored). Seul binding package : morpheus-cli/pom.xml:73-78 org.slf4j:slf4j-nop en scope runtime ; grep -rn 'slf4j|logback|log4j' sur tous les POM -> slf4j-api en dependencyManagement et ce slf4j-nop, rien d'autre. Seul canal structure, opt-in : morpheus-application/.../operability/LocalOperationalRuntime.java:31-41, sortie System.err si MORPHEUS_OPERATIONAL_LOGS est pose, noop par defaut, 4 sites d'emission seulement. Repertoire de logs publie sans ecrivain : distribution/build-release.sh:61-66 publie 'logs' dans persistentLayout du release-manifest.json, morpheus-cli/.../CliLayout.java:14,20,54,59,67,79 le calcule et le durcit, MORPHEUS_LOGS_DIR le surcharge - et grep -rn 'logsDirectory()' sur src/main ne trouve que MorpheusCli.java:172 et :177, deux sites d'affichage.
```

**Impact** — Un echec avale sur un chemin SQLite, MCP ou de decouverte d'adaptateur ne produit ni log, ni compteur, ni evenement : un adaptateur de reasoning casse disparait de descriptors() et l'utilisateur lit 'unknown reasoning adapter' sans savoir qu'il etait present mais defectueux. Les echecs de la pile tierce - expiration de handshake MCP, flux rompu, erreur de deserialisation JSON-RPC face a MINOS ou NEXUS - n'apparaissent nulle part non plus, puisque slf4j-nop les jette. Et l'operateur a qui le produit annonce un repertoire de logs y trouve un repertoire vide, et conclut a une installation cassee ou a une perte de logs la ou il n'y en a jamais eu. En incident, aucune trace ne survit a la fin du processus. Contredit .claude/rules/code-style.md ('Jamais de degradation silencieuse') et rend tout diagnostic post-incident impossible sans reproduction. Fusion de trois constats en relecture : le meme symptome comptait 1 Elevee sur l'axe Qualite et 2 Moyennes sur l'axe Dependances, soit le principal artefact du bareme.

**Action** — Trancher et cabler, en trois gestes qui vont ensemble. (1) Router les 21 suppressions d'exception vers LocalOperationalRuntime.recorder() avec un code de rejet nomme - le mecanisme existe deja (SqliteContentionMetrics) - et faire remonter les compteurs meme quand le sink structure est noop, avec un compteur par famille d'echec avale expose sur /metrics. (2) Garder slf4j-nop par defaut et ajouter un mode diagnostic explicite : second binding selectionne par variable d'environnement, ou SLF4JServiceProvider maison routant vers StructuredOperationalEventSink, pour que MORPHEUS_OPERATIONAL_LOGS capture aussi la pile tierce. (3) Soit brancher StructuredOperationalEventSink sur un fichier rotatif sous logsDirectory quand MORPHEUS_OPERATIONAL_LOGS=file, soit retirer 'logs' de persistentLayout, de CliLayout et de MORPHEUS_LOGS_DIR et enoncer dans docs/developer/OPERABILITY.md que MORPHEUS n'ecrit aucun log sur disque. Ne pas laisser un chemin publie sans ecrivain.

### AUD-SEC-02 — Quatre routeurs HTTP qui enregistrent leur propre contexte renvoient le message d'exception brut, sans passer par le filtre de divulgation de chemins serveur
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | 3 — Fermer les écarts de sécurité |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/MorpheusQueryHttpRoutes.java:225 ; MorpheusPolicyHttpRoutes.java:197 ; MorpheusPolicyManagementHttpRoutes.java:148 ; MorpheusReasoningHttpRoutes.java:87 (safeMessage ne substitue que si le message est vide) ; a comparer a MorpheusHttpServer.java:210-212 et MorpheusRemoteHttpServer.java:255 qui appellent BoundaryFailureMessage.safe(...) ; BoundaryFailureMessage.java:13 affirme pourtant "Both servers apply this"
Chainage etabli en relecture : morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteProxyTransport.java:104-125 relaie le corps amont octet pour octet (copyBounded(upstream, exchange.getResponseBody(), ...)), et BoundaryFailureMessage.java:23-28 filtre par ServerLocationDisclosure.isSafeToRelay la ou les quatre safeMessage locaux ne substituent que sur message vide.
```

**Impact** — Les capacites query / exports / saved-views / policy / policy-management / reasoning representent une vingtaine de routes joignables a distance (role READ ou WRITE). Toute IllegalArgumentException, KnowledgeStoreException ou IllegalStateException remontant d'une couche inferieure est relayee verbatim au client distant, alors que le reste de l'API filtre les valeurs qui nomment un emplacement du systeme de fichiers. L'invariant annonce par la classe de filtrage n'est pas tenu sur ces routes. Severite montee de Moyenne a Elevee en relecture : le message brut d'une AccessDeniedException ou d'une KnowledgeStoreException levee sous ces routes atteint un appelant hors machine sans passer par ServerLocationDisclosure. C'est une divulgation de chemins serveur a distance, sur la surface meme que ServerLocationDisclosure existe pour fermer. C'est le premier constat de l'axe Securite.

**Action** — Remplacer les quatre safeMessage locaux par BoundaryFailureMessage.safe(...) et ajouter un test d'architecture qui interdit une methode safeMessage privee dans morpheus-api en dehors de BoundaryFailureMessage et du decodeur de requetes.

### AUD-TST-01 — Les cinq budgets de performance M19 ne sont exécutés par aucun pipeline ni par clean verify
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | 1 — Remettre le dispositif en marche |
| **Débloque** | AUD-DEP-12 (sprint 2), AUD-PRF-01 (sprint 6), AUD-PRF-02 (sprint 6), AUD-PRF-04 (sprint 6), AUD-PRF-05 (sprint 13), AUD-PRF-06 (sprint 6), AUD-TRV-02 (sprint 13) |

**Preuve**

```
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/M19PerformanceGate.java:24 — "The class name intentionally does not match the default Surefire *Test patterns; run it only through the M19 validator with -Dtest=M19PerformanceGate." ; aucun override <includes> dans morpheus-architecture-tests/pom.xml (surefire 3.6.0, pom.xml:146-154) ; `grep -rn "m19\|M19" .github/workflows/` => aucune sortie ; ci.yml:43 et :86 ne lancent que `bash ./scripts/validate-m21.sh 1.2.1` (Linux) et `.\scripts\validate-m28.ps1 -Version 1.2.1` (Windows) ; `grep -rn "m19" scripts/ | grep -v validate-m19` => aucune sortie (chaine reelle : m28 -> r2 -> m27).
```

**Impact** — Les cinq budgets pre-declares (scan d'inventaire, Query DSL, composition, tracabilite, publication complete) sont la preuve executable que docs/architecture/arc42/10-exigences-qualite.md:185-212 designe comme faisant autorite. Ils n'ont pas tourne depuis le SHA M19 (docs/validation/VALIDATION_M19.md:11 — dca27db96), soit neuf milestones de code plus tot. Une regression de performance ne casse donc aucun build, contrairement a ce qu'annonce .claude/rules/testing.md:179-183.

**Action** — Ajouter une etape dediee (nightly.yml, ou un job matrice distinct de ci.yml) qui lance `bash ./scripts/validate-m19.sh 1.2.1` et `scripts/validate-m19.ps1`, et publier sa preuve en artefact comme pour m21. Alternative : renommer les gates en `*Test` et les isoler derriere un profil Maven active en nightly, pour qu'ils soient comptes et executes au lieu d'etre invisibles.

### AUD-PRF-01 — N+1 SQL dans la lecture du contenu métier d'un snapshot : 7 requêtes filles par ligne parente
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Performance |
| **Effort** | M |
| **Sprint** | 6 — Borner les coûts qui croissent avec les données |
| **Après** | AUD-TST-01 (sprint 1) |
| **Débloque** | AUD-PRF-07 (sprint 7), AUD-PRF-10 (sprint 7) |

**Preuve**

```
morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSnapshotBusinessContentReader.java:190 (readChanges, 3 appels readOrderedValues dans le while), :211 et :218 (readConstraints, 2 appels), :162 (readScenarios), :304 (readAcceptanceVerificationEvidence) ; chaque appel refait un prepareStatement (ligne 333-347) a l'interieur du ResultSet parent encore ouvert
```

**Impact** — findSnapshotContent() execute 1*scenarios + 3*changes + 2*constraints + 1*criteres requetes supplementaires. Pour un snapshot de 500 scenarios / 200 changes / 300 contraintes / 400 criteres : ~2100 requetes et autant de prepareStatement par appel. L'appel est sur le chemin HTTP (ChangeAnalysisService.java:96, CompactQueryViewService.java:295, QueryExecutionService.java:169) : le cout croit lineairement avec la taille du snapshot a chaque requete. Cout non mesure, raisonnement sur la structure du code.

**Action** — Lire chaque table enfant en une seule requete par snapshot (WHERE snapshot_id = ? ORDER BY owner_id, ordinal), puis regrouper en memoire par owner_id dans une Map avant de construire les records. Le writer voisin (SqliteSnapshotBusinessContentWriter) montre deja le motif inverse avec addBatch/executeBatch.

### AUD-PRF-02 — ChangeAnalysisService refait un BFS complet par nœud déjà découvert par la traversée
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Performance |
| **Effort** | M |
| **Sprint** | 6 — Borner les coûts qui croissent avec les données |
| **Après** | AUD-TST-01 (sprint 1) |
| **Débloque** | AUD-PRF-12 (sprint 7) |

**Preuve**

```
morpheus-application/src/main/java/com/morpheus/application/analysis/ChangeAnalysisService.java:321-325 : `for (TraceabilityEntityRef target : subgraph.nodes()) { ... traversalService.findPath(snapshotId, root, target, ...)` ; findPath (traceability/TraceabilityTraversalService.java:119-170) relance un BFS entier dont chaque noeud appelle neighbors() -> store.outgoing/incoming (lignes 200 et 205)
```

**Impact** — La traversee a deja calcule l'arborescence des predecesseurs puis la jette (TraceabilitySubgraph ne porte que nodes+links, ligne 116). Avec MAX_NODES = 1_000 (ligne 30), N noeuds declenchent N BFS de N noeuds chacun, soit jusqu'a 10^6 requetes de store par direction, repete pour chaque delta de requirement (ligne 102-103) et pour les deux directions. Aucun gate M19 ne couvre findPath ni ChangeAnalysisService (verifie : `grep -rn findPath morpheus-architecture-tests/.../m19/` ne retourne rien, aucun fichier m19 ne mentionne ChangeAnalysisService). Cout non mesure, raisonnement sur la structure du code.

**Action** — Faire retourner par traverse() la carte des predecesseurs qu'il construit deja (predecessor/depthByNode), et reconstruire chaque chemin en memoire avec reconstruct() au lieu d'appeler findPath par noeud. Ajouter un budget M19 sur ChangeAnalysisService pour que la regression soit detectee.

### AUD-PRF-04 — Trois tables en croissance monotone lues sans LIMIT ni pagination, dont une exposée en HTTP et en MCP
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Performance |
| **Effort** | M |
| **Sprint** | 6 — Borner les coûts qui croissent avec les données |
| **Après** | AUD-TST-01 (sprint 1) |
| **Débloque** | AUD-PRF-13 (sprint 7) |

**Preuve**

```
morpheus-store-sqlite/.../SqlitePolicyPackStore.java:560-574 `SELECT ... FROM policy_audit WHERE pack_id = ?` puis `values.stream().sorted().toList()` ; SqliteChangeLifecycleMutationStore.java:77-90 (`change_lifecycle_mutation_audit`) ; SqliteSyncStateStore.java:93-116 (`sync_source_archives`). Aucun DELETE sur ces tables (`grep -rn "DELETE FROM policy_audit|DELETE FROM change_lifecycle_mutation_audit|DELETE FROM sync_source_archives"` ne retourne rien). Un seul `LIMIT` dans tout le module : SqliteVersionedRequirementStore.java:313. Surface publique : contracts/public-surfaces.tsv:44 `policy.audit READ policy audit get_policy_audit GET /api/v1/policy-packs/{id}/audit Append-only audit ...`
```

**Impact** — La reponse et le tas consommes par un GET /api/v1/policy-packs/{id}/audit croissent sans borne avec l'historique du pack : tout l'audit est charge en List puis trie en Java. Le meme motif vaut pour l'audit de cycle de vie et les archives de sources. Aucun des trois n'a de purge. Cout non mesure, raisonnement sur la structure du code.

**Action** — Ajouter une pagination (LIMIT/OFFSET ou curseur sur l'index existant idx_policy_audit_pack(pack_id, at, id)) a ces trois lectures, et declarer la borne maximale dans docs/openapi/*.yaml comme le font deja M24-M27. Prevoir une politique de retention pour les tables append-only.

### AUD-PRF-18 — Épinglage des threads virtuels : 94 méthodes public synchronized faisant du JDBC bloquant, atteintes depuis les serveurs HTTP, sur une baseline Java 21
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Performance · Architecture |
| **Effort** | M |
| **Sprint** | 5 — Écarter le risque d'indisponibilité |
| **Débloque** | AUD-PRF-03 (sprint 5) |
| **Constats fusionnés** | relecture independante |

**Preuve**

```
grep -rc 'public synchronized' morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/*.java -> 94 methodes reparties sur 13 fichiers (SqlitePolicyPackStore 16, SqlitePortfolioStore 15, SqliteVersionedRequirementStore 13, SqliteSpecificationKnowledgeStore 11, SqliteSavedViewStore 8, SqliteTraceabilityStore 6, SqliteSyncStateStore 6, SqliteChangeLifecycleMutationStore 5, SqliteExternalReferenceStore 4, SqliteSnapshotBusinessContentStore 3, SqliteEntityIdentityStore 3, SqliteCompositionStateStore 3, SqliteServerMaintenance 1) ; 176 au total dans src/main. Elles font toutes du JDBC bloquant, p.ex. SqlitePolicyPackStore.java:555 'public synchronized List<PolicyConfiguration.AuditRecord> listAudit(...)' qui ouvre un prepareStatement et un executeQuery. Les deux serveurs HTTP les executent sur des threads virtuels : morpheus-api/.../MorpheusLocalHttpServerBootstrap.java:165 et morpheus-api/.../MorpheusRemoteHttpServerBootstrap.java:109, tous deux Executors.newVirtualThreadPerTaskExecutor(). Baseline Java 21 : pom.xml:39 maven.compiler.release=21, .github/workflows/ci.yml:36 et :142 java-version: '21'. grep -rn 'virtualThreadScheduler' sur tout le depot (hors .git) -> aucun resultat : le parallelisme de l'ordonnanceur n'est configure nulle part, ni dans les workflows, ni dans les scripts, ni dans la distribution. Et SqliteDatabaseSecurity.java:118 pose busy_timeout = 5000 avec :137 PRAGMA journal_mode = PERSIST, un journal de rollback dont un ecrivain prend un verrou EXCLUSIVE sur toute la base.
```

**Impact** — Sur Java 21, un blocage a l'interieur d'un bloc synchronized epingle le thread porteur : JEP 491 ne leve cette restriction qu'en Java 24. Le pool de porteurs est dimensionne au nombre de processeurs. Quelques requetes concurrentes tenant un moniteur au-dessus d'un busy_timeout de 5 s suffisent donc a affamer l'ordonnanceur de threads virtuels - y compris les requetes qui n'auraient jamais touche SQLite, puisqu'elles attendent un porteur et non un verrou. C'est un chemin vers l'indisponibilite du serveur entier, pas une simple degradation de la couche de persistance. Ce constat n'a ete produit par aucun des six axes : il vient de la relecture independante, et AUD-PRF-03 passait a cote en prenant le probleme par l'autre bout ('threads virtuels illimites'). Le risque n'est pas qu'il y en ait trop, c'est qu'ils ne cedent pas la main. Cout non mesure : l'affirmation porte sur la semantique documentee de Java 21 et sur la structure du code, pas sur une execution observee.

**Action** — Trois options, a trancher par ADR. (a) Remplacer les moniteurs par des ReentrantLock sur les chemins JDBC des 13 stores : un thread virtuel bloque sur un ReentrantLock cede son porteur, la ou un synchronized ne le cede pas. (b) Fixer explicitement jdk.virtualThreadScheduler.maxPoolSize et documenter la decision, ce qui borne le degat sans le supprimer. (c) Passer la baseline a Java 24 ou plus, ou JEP 491 supprime l'epinglage. Dans les trois cas, ajouter un test de saturation : N requetes concurrentes sur une route de lecture pendant qu'une ecriture tient le verrou EXCLUSIVE, et asserter que les lectures aboutissent. Commencer par compter les methodes synchronized reellement atteignables depuis un handler de route, ce qui se fait sans rien executer.

### AUD-DEP-01 — La 1.2.1 non publiée ajoute 5 migrations SQLite (V016 à V020) et aucun document d'upgrade ou de retour arrière ne les couvre
| | |
|---|---|
| **Sévérité** | Élevée |
| **Axe** | Dépendances |
| **Effort** | M |
| **Sprint** | 2 — Débloquer la release 1.2.1 |
| **Débloque** | AUD-DEP-12 (sprint 2), AUD-DEP-16 (sprint 2), AUD-TST-10 (sprint 8) |

**Preuve**

```
morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java:45 `static final int SUPPORTED_SCHEMA_VERSION = 20;` et :86-89 `if (version > SUPPORTED_SCHEMA_VERSION) throw new KnowledgeStoreException("SQLite schema version " + version + " is newer than supported " ...)`. docs/user/UPGRADE_1_2.md:108 : « Le schema SQLite etant inchange a V015, les donnees restent structurellement compatibles. » `ls docs/user/` -> UPGRADE_1_1.md, UPGRADE_1_2.md, aucun UPGRADE_1_2_1.md. Dates d'ajout (git log --diff-filter=A) : V015 2026-07-29 (veille du tag v1.2.0, 2026-07-30), V016 2026-08-19, V017 2026-08-31, V018 2026-09-06, V019 2026-09-25 — toutes posterieures a la derniere release.
```

**Impact** — Un operateur qui installe 1.2.1 puis revient a 1.2.0 ou 1.1.0 avec une base passee en V020 declenche le refus explicite `schema version 20 is newer than supported` : le binaire precedent ne demarre plus sur ses donnees. La seule procedure de retour presente dans l'arbre affirme l'inverse (schema inchange), donc l'operateur decouvre le blocage apres avoir desinstalle la version qui fonctionnait.

**Action** — Livrer docs/user/UPGRADE_1_2_1.md avant de taguer v1.2.1 : enoncer la version de schema atteinte (lue dans SqliteSchemaManager, jamais recopiee), rendre la sauvegarde `server backup create` obligatoire avant installation, et dire explicitement que le rollback programme exige la restauration d'une sauvegarde pre-upgrade. Corriger ou borner la phrase V015 de UPGRADE_1_2.md au perimetre 1.2.0.

## Sévérité : Moyenne

### AUD-ARC-03 — morpheus-application contient une composante fortement connexe de 17 paquets sur 36
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Architecture |
| **Effort** | L |
| **Sprint** | 11 — Trancher l'architecture |
| **Après** | AUD-ARC-01 (sprint 11) |
| **Débloque** | AUD-ARC-13 (sprint 11) |

**Preuve**

```
Analyse des `import` par paquet (script Tarjan sur morpheus-application/src/main/java) : 36 paquets declares (grep -rh '^package ' | sort -u ; les 40 repertoires incluent 4 niveaux intermediaires sans .java), 87 aretes, 1 SCC de 17 paquets = composition, context, ingestion, operability, orchestration, policy, quality, query, query.dsl, query.saved, read, reference, security, snapshot, store, sync, traceability. Six paires mutuelles directes, p.ex. morpheus-application/src/main/java/com/morpheus/application/policy/PolicyPackService.java:3 `import com.morpheus.application.store.EntityNotFoundException` contre morpheus-application/src/main/java/com/morpheus/application/store/PolicyPackStore.java:3 `import com.morpheus.application.policy.PolicyConfiguration`. Egalement store<->sync (store/SyncStateStore.java:3 vs sync/SyncFreshnessService.java:3), store<->query.dsl, store<->query.saved, query.dsl<->query.saved, ingestion<->traceability. Chiffre coherent avec docs/adr/0109-transport-adapters-are-named-composition-roots.md:28 (167 cycles, 16 tranches) et :64 (regle reportee).
```

**Impact** — Aucune frontiere interne a l'application n'est tenable : tout decoupage futur (extraction d'un module query, policy ou sync) traverse la SCC. `application.store` est a la fois le paquet des ports (17 fichiers : 10 interfaces de store et 7 types/exceptions) et le vocabulaire d'exception de toute la couche, ce qui referme la plupart des cycles. La regle ArchUnit correspondante est explicitement reportee, donc rien n'empeche la SCC de grossir. Severite redescendue de Elevee a Moyenne en relecture : les 167 cycles sont mesures, chiffres et explicitement reportes par une decision datee (docs/adr/0109:64 'La meme regle sur com.morpheus.application est reportee. La mesure est consignee ici pour la prochaine decision'). Arbitrage contestable : on peut tenir qu'un ADR qui reporte ne reduit pas le risque, seulement sa surprise.

**Action** — Casser d'abord la cause unique : sortir EntityNotFoundException / EntityStateException / KnowledgeStoreException du paquet des ports vers un paquet de vocabulaire d'erreur sans dependance (p.ex. `application.failure`), et deplacer les types de donnees des ports (ProjectStoreEntry, RequirementVersionRecord, SnapshotBusinessContent, SnapshotSpecificationVersionBinding) vers le paquet de la capacite qui les possede. Puis activer `slices().matching("com.morpheus.application.(*)..").should().beFreeOfCycles()` avec la liste des cycles restants nommes, sur le modele deja en place pour le domaine, pour que le nombre ne puisse que descendre.

### AUD-ARC-06 — morpheus-api : 80 classes dans un unique paquet plat, aucune frontière intra-module exprimable
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Architecture |
| **Effort** | M |
| **Sprint** | 12 — Restructurer les adaptateurs |
| **Après** | AUD-ARC-01 (sprint 11) |
| **Débloque** | AUD-ARC-09 (sprint 12), AUD-TRV-03 (sprint 12) |

**Preuve**

```
`find morpheus-api/src/main/java -type d` s'arrete a morpheus-api/src/main/java/com/morpheus/api ; `ls morpheus-api/src/main/java/com/morpheus/api/*.java | wc -l` = 80 (dont 17 `*HttpRoutes` et 19 `*ApiService`). Meme situation pour morpheus-mcp (20 classes, un paquet), morpheus-cli (28, un paquet), morpheus-store-sqlite (29, un paquet). docs/adr/0109-...md:29 le reconnait : `cycles entre tranches de com.morpheus.api.(*).. : sans objet : api n'a pas de sous-paquet`. Consequence directe : AdapterCompositionRootArchitectureTest.java:38-70 enumere 32 noms de classes a la main, et HttpRoutesTransportBoundaryArchitectureTest classe les routeurs en trois groupes `ROUTERS_WITHOUT_A_BODY` / `ROUTERS_THROUGH_THE_SHARED_DECODER` / `ROUTERS_REGISTERING_THEIR_OWN_CONTEXT` listes par nom.
```

**Impact** — Toute frontiere interne a l'adaptateur HTTP doit etre maintenue comme une liste de noms de classes au lieu d'etre portee par la structure. La maintenance est proportionnelle au nombre de classes, pas au nombre de regles : chaque nouvelle classe exige une entree dans une liste, et une classe renommee casse une liste au lieu d'etre reclassee par son paquet. Les regles de tranches ArchUnit (slices) sont inutilisables sur api, mcp et cli.

**Action** — Introduire des sous-paquets par role dans morpheus-api (`api.routes`, `api.service`, `api.transport`, `api.remote`) et dans morpheus-mcp / morpheus-cli, puis remplacer les listes nommees par des regles de paquet (p.ex. seuls `api.service` et `api.remote` atteignent `store..`). Chaque liste de noms supprimee est une liste qui ne peut plus se perimer.

### AUD-ARC-07 — Le module morpheus-mcp-transport livre le paquet com.morpheus.integration.mcp, donc le transport partagé est réglé comme une intégration
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Architecture |
| **Effort** | M |
| **Sprint** | 10 — Dire la vérité dans les règles |

**Preuve**

```
`find morpheus-mcp-transport/src/main/java -name '*.java' | sed 's|.*/java/||'` donne `com/morpheus/integration/mcp` -- le meme prefixe que morpheus-integration-minos (`com/morpheus/integration/minos`) et morpheus-integration-nexus. Toutes les regles qui citent `com.morpheus.integration..` l'englobent donc : morpheus-architecture-tests/.../LayerDependencyTest.java:56 (interdit a `api`) et AdapterSiblingArchitectureTest.java:43 et :76,:93 (interdit aux providers et aux stores). La regle qui vise vraiment le transport le designe par son paquet, pas par son module : AdapterCompositionRootArchitectureTest.java:97 `.that().resideInAPackage("com.morpheus.integration.mcp..")`, avec un `because` qui parle du transport STDIO borne.
```

**Impact** — Le nom de module et le nom de paquet designent deux decoupages differents : .claude/rules/architecture.md intitule la regle `morpheus-mcp-transport ne depend d'aucun autre paquet MORPHEUS` alors que le test porte sur `com.morpheus.integration.mcp`. Un lecteur qui cherche le transport sous `com.morpheus.mcp..` ou sous un paquet `transport` ne le trouve pas, et un interdit ajoute sur `com.morpheus.integration..` pour viser MINOS/NEXUS frappe silencieusement le transport partage -- exactement le cas d'un futur besoin de transport MCP cote api.

**Action** — Renommer le paquet en `com.morpheus.transport.mcp` (ou renommer le module en morpheus-integration-mcp si c'est le decoupage voulu), et faire porter par un test la correspondance module/paquet pour les 18 modules, sur le modele d'ImportedClasspathCompletenessTest qui lit deja les `<module>` du POM racine.

### AUD-ARC-08 — com.morpheus.sdk n'est sujet d'aucune règle ArchUnit, contrairement à ce qu'annonce le javadoc du test qui le nomme
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Architecture |
| **Effort** | S |
| **Sprint** | 10 — Dire la vérité dans les règles |

**Preuve**

```
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterSiblingArchitectureTest.java:17-18 : `Dependency rules whose decision is written in an ADR or in the project rules, with the provider SDK, the providers, the stores, the MINOS and NEXUS integrations and the MCP adapter as subjects`. Or `grep -n 'that().resideIn' AdapterSiblingArchitectureTest.java` ne renvoie que les lignes 57 (DOMAIN, APPLICATION), 66 (OPENSPEC, MARKDOWN, SYNTHETIC), 75 (PROVIDERS), 92 (STORES), 109 (MINOS), 118 (NEXUS), 127 (MCP). `SDK` (ligne 37) n'apparait qu'en cible, lignes 58 et 67. Aucune autre regle du module ne prend `com.morpheus.sdk..` pour sujet.
```

**Impact** — Rien n'interdit a morpheus-provider-sdk de dependre de `store..`, `api..`, `cli..`, `mcp..` ou `integration..`. La frontiere tient aujourd'hui par le POM (morpheus-provider-sdk ne declare que domain et application, et ses imports se limitent a `com.morpheus.application` et `com.morpheus.domain`), mais la classe de test affirme qu'elle est enforcee -- or le but declare de ces regles, ligne 19, est justement de refuser `une future dependance ajoutee a un POM`.

**Action** — Ajouter la regle manquante `theProviderSdkDependsOnNoAdapter` avec `SDK` pour sujet et STORES, PROVIDERS, INTEGRATIONS, MCP, API, CLI pour cibles, et la casser une fois pour verifier qu'elle tient (procedure .claude/skills/enforcement-choice). Corriger le javadoc dans le meme changement.

### AUD-ARC-09 — Découpage incohérent d'un adaptateur à l'autre : api décomposé par capacité, mcp et cli gardent un monolithe historique à côté
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Architecture |
| **Effort** | L |
| **Sprint** | 12 — Restructurer les adaptateurs |
| **Après** | AUD-ARC-06 (sprint 12) |

**Preuve**

```
morpheus-api : 17 `*HttpRoutes` + 19 `*ApiService`, une classe par capacite. morpheus-cli/src/main/java/com/morpheus/cli/MorpheusCli.java : 908 lignes, 67 imports, 14 commandes de premier niveau dispatchees dans un seul `dispatch` (ligne 136 ; methodes `paths`:168, `projects`:182, `sync`:224, `requirements`:362, `changes`:392, `constraints`:426, `decisions`:445, `tasks`:464, `traceRequirement`:484, `changeContext`:509, `analyzeChange`:540, `quality`:590) plus 15 records de vue privee (lignes 869 a 907) -- a cote de 12 classes `Morpheus*Cli` par capacite. Symetriquement morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusMcpToolService.java : 412 lignes, 15 handlers d'outil (lignes 75 a 238) et 12 methodes de projection (lignes 299 a 385), a cote de 11 classes `*McpTools`.
```

**Impact** — Deux conventions coexistent dans le meme module : les capacites livrees depuis M22 ont leur classe, les capacites historiques restent dans le monolithe. Une evolution transverse (pagination, format d'erreur, projection d'un type) doit etre appliquee a deux endroits de forme differente par adaptateur, et le monolithe concentre le risque de conflit a chaque milestone. Aucune regle n'empeche une 15e commande d'etre ajoutee dans MorpheusCli plutot que dans une classe dediee.

**Action** — Terminer l'extraction commencee : une classe par capacite dans cli et mcp, MorpheusCli et MorpheusMcpToolService reduits au dispatch et au parsing. Puis fixer la convention par un test (p.ex. aucune classe de `com.morpheus.cli` ne depasse N methodes de commande, ou MorpheusCli ne depend que des classes `Morpheus*Cli`), faute de quoi la derive reprendra.

### AUD-ARC-10 — Projection domaine vers payload public dupliquée à l'identique entre api et mcp, sans gate sur les payloads
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Architecture |
| **Effort** | M |
| **Sprint** | 9 — Supprimer la duplication du socle |
| **Après** | AUD-QUA-07 (sprint 9) |

**Preuve**

```
`private Object requirement(Requirement item)` existe en quatre exemplaires au corps identique : morpheus-api/.../MorpheusRequirementQueryApiService.java:91, morpheus-api/.../MorpheusHistoryApiService.java:130, morpheus-api/.../MorpheusSpecificationQueryApiService.java:107 (seule difference : la constante FIELD_TITLE au lieu du litteral), morpheus-mcp/.../MorpheusMcpToolService.java:312 -- tous les quatre : `map("id", item.id().toString(), "specificationId", ..., "key", item.key().orElse(""), "title", item.title(), "statement", item.statement())`. Le helper `map(Object... entries)` est redefini dans 9 classes (7 dans api, 2 dans mcp). Meme schema pour `change` (3 exemplaires), `task`, `specification`, `scenario`, `decision`, `constraint`, `acceptanceCriterion` (2 chacun).
```

**Impact** — La convergence CLI/MCP/HTTP est verifiee sur la presence des routes et des outils (PublicHttpRouteConvergenceTest, PublicSurfaceManifestCoversEveryServedToolTest) mais pas sur la forme des payloads : un champ ajoute a l'une des quatre copies de `requirement` fait diverger silencieusement la reponse HTTP de la reponse MCP, sans casser aucun gate ni contredire contracts/public-surfaces.tsv.

**Action** — Remonter ces projections dans `application.query.compact` a cote de CompactQueryViewService, qui est deja partage par api, mcp et cli, et faire consommer une seule projection par les quatre appelants. Ajouter un test de convergence de payload sur au moins les types partages (requirement, change, specification) qui compare la sortie HTTP et la sortie MCP pour une meme fixture.

### AUD-ARC-11 — Interdiction absolue de la réflexion dans les règles du dépôt, contredite par quatre classes de production
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Architecture |
| **Effort** | S |
| **Sprint** | 10 — Dire la vérité dans les règles |

**Preuve**

```
.claude/rules/architecture.md, section `Pas de framework, pas de magie` : `Jamais Spring / Quarkus / Micronaut / Guice, jamais de reflexion, de classpath scanning ou d'annotations d'injection.` Or : morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteConnectionScope.java:114 `return (Connection) Proxy.newProxyInstance(` et :244 `public Object invoke(Object proxy, java.lang.reflect.Method method, Object[] args)` ; morpheus-store-sqlite/.../SqliteDatabaseLease.java:114 idem ; morpheus-application/src/main/java/com/morpheus/application/query/compact/CanonicalJsonSerializer.java:3-5 `import java.lang.reflect.Array; import java.lang.reflect.InvocationTargetException; import java.lang.reflect.RecordComponent;`. Aucune regle ArchUnit ni assertion textuelle ne vise `java.lang.reflect`.
```

**Impact** — .claude/rules/meta.md demande explicitement de signaler toute divergence entre une regle ecrite et le code. Ici la regle est fausse au sens absolu ou elle est ecrite, et la reflexion est structurante : la serialisation canonique (le produit vend du determinisme) repose sur RecordComponent, et le partage de connexion SQLite sur un proxy dynamique. Un contributeur ou un agent qui applique la regle refusera un correctif legitime, ou croira pouvoir supprimer ces usages.

**Action** — Reecrire la regle pour dire ce qui est vrai : pas de reflexion pour l'injection, le scan de classpath ou la construction d'objets depuis une configuration ; les trois usages existants (proxy de connexion, RecordComponent pour le JSON canonique) sont nommes et bornes. Puis l'enforcer : une regle ArchUnit interdisant `java.lang.reflect..` a tout `com.morpheus..` sauf les trois classes nommees, pour que la liste ne puisse que retrecir.

### AUD-QUA-02 — 73 copies du localisateur de racine de dépôt dans les tests, sous 3 noms et 7 implémentations divergentes
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité |
| **Effort** | M |
| **Sprint** | 9 — Supprimer la duplication du socle |

**Preuve**

```
grep -rn "Path repositoryRoot()\|Path repoRoot()" --include=*.java . | grep /src/test/ | wc -l -> 73 (44 `repositoryRoot()`, 29 `repoRoot()`, 1 `root()`), dont 72 dans morpheus-architecture-tests. Deux criteres de detection differents : morpheus-architecture-tests/.../LocalPortfolioHttpRoutesArchitectureTest.java:46 teste `contracts/public-surfaces.tsv` + `pom.xml` ; morpheus-architecture-tests/.../IntegrationStatusProjectionArchitectureTest.java:130 teste `pom.xml` + repertoire `distribution`. Hachage des corps de `repositoryRoot()` : 36 identiques, 3 d'une 2e variante, 5 variantes uniques (PartialRuntimeOwnershipContractTest.java:132 lit en plus le contenu du pom).
```

**Impact** — Plus gros foyer de duplication du depot (cluster de 36 occurrences en tete du scan de blocs normalises de 12 lignes). Deplacer un fichier d'ancrage casse une partie des suites et pas l'autre ; un correctif sur la variante majoritaire laisse 7 copies derriere lui. Aucun module de support de test n'existe pour l'accueillir.

**Action** — Creer une classe de support unique dans morpheus-architecture-tests (ex. `RepositoryRoot.locate()`), avec un critere d'ancrage unique et documente, et faire echouer un test de gouvernance sur toute nouvelle copie privee - exactement le patron deja applique a BoundedWait par BoundedWaitOwnershipTest:67.

### AUD-QUA-04 — Dérive documentation/code sur le nombre de modules : 18 déclarés, 17 annoncés par quatre pages vivantes
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | 8 — Fiabiliser les tests |

**Preuve**

```
grep -c "<module>" pom.xml -> 18 (le 18e est morpheus-coverage-report). docs/developer/BUILD_AND_TEST.md:17 "Le depot contient **17 modules enfants**, soit **18 projets Maven parent inclus**" et son bloc de code omet morpheus-coverage-report ; meme enonce a docs/developer/README.md:32, docs/architecture/arc42/05-vue-blocs.md:81, docs/architecture/arc42/04-strategie-solution.md:27. Le garde-fou existe mais ne couvre pas ces pages : morpheus-architecture-tests/.../RepositoryDocumentationCoherenceTest.java:38 cite litteralement "17 modules" comme exemple du total perissable interdit, et son pattern ADR_OR_MODULE_TOTAL n'est applique (ligne 662-668) qu'aux surfaces IA (GOVERNANCE_PAGES = .claude/CLAUDE.md, .github/AI_GOVERNANCE.md ; GOVERNANCE_DIRECTORIES = .claude/commands, .claude/agents, .claude/rules). L'ecart est deja nomme dans docs/audits/AUDIT_OUTILLE_2026-10-08.md:66 et subsiste a 20cf2e0.
```

**Impact** — Les deux pages d'entree du developpeur donnent un compte faux et une liste de modules incomplete ; le vrai total est 18 enfants plus le parent, soit 19 projets Maven. Un lecteur qui en deduit la population de couverture ou la liste des modules a construire se trompe, alors que le projet dispose du detecteur capable de l'empecher.

**Action** — Etendre `activeGovernanceSurfaces` du RepositoryDocumentationCoherenceTest aux pages developpeur et arc42 (ou ajouter un test dedie qui derive le compte des `<module>` du POM racine), puis remplacer les quatre enonces par une phrase sans chiffre ou par la liste derivee.

### AUD-QUA-05 — 16 méthodes publiques ne sont référencées nulle part, tests compris
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | 3 — Fermer les écarts de sécurité |

**Preuve**

```
Scan : pour chaque declaration `public ... nom(` hors @Override, comptage du nom sur les 1185 .java du depot ; 16 n'apparaissent que sur leur propre ligne de declaration. Concentration : morpheus-application/src/main/java/com/morpheus/application/query/BusinessContentQueryService.java:69,85,103,123,143,161 (`snapshotChange`, `listSnapshotChanges`, `snapshotConstraints`, `snapshotDesignDecisions`, `snapshotImplementationTasks`, `snapshotAcceptanceCriteria` - la classe a bien 8 consommateurs, mais aucun n'appelle cette famille). Autres : morpheus-application/.../query/ConstraintEvaluationQueryService.java:55, .../quality/ChangeLifecycleQualityService.java:77, .../reference/LiveExternalReferenceResolutionService.java:57, .../provider/SpecificationProviderRegistry.java:41, .../quality/QualityFactValue.java:15, .../query/export/QueryExport.java:22, .../composition/MultiProviderCompositionResult.java:26, .../lifecycle/mutation/ChangeLifecycleMutationPolicy.java:9, morpheus-api/.../MorpheusQueryApiService.java:47, morpheus-integration-minos/.../MinosIntegrationSettings.java:136.
```

**Impact** — Violation directe de rules/code-style.md ("Jamais de code mort"). Ces methodes sont comptees dans le denominateur de couverture et tirent le ratchet par module vers le bas sans rien proteger. `MinosIntegrationSettings.processEnvironment()` est trompeuse sur un chemin de securite : elle construit un `MINOS_HOME` pour le pair MCP alors que le lancement reel passe par `-Dminos.home=` (MinosMcpCodeGateway.java:208) et transmet un environnement explicite vide (MinosMcpCodeGateway.java:210 `Map.of()`) - NEXUS n'a pas d'equivalent, ce qui confirme le residu.

**Action** — Supprimer les 16 methodes, ou - pour la famille `snapshot*` de BusinessContentQueryService si elle est une surface voulue - lui donner son consommateur et sa ligne dans contracts/public-surfaces.tsv. Ajouter au passage un gate qui refuse une methode publique sans reference dans les modules domain et application.

### AUD-QUA-06 — Chaque sous-commande CLI reporte sa propre copie du même socle d'analyse d'arguments
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité |
| **Effort** | M |
| **Sprint** | 9 — Supprimer la duplication du socle |

**Preuve**

```
Sur 28 classes de morpheus-cli : `private static String safeMessage(Throwable failure)` x10, `private static String command(String[] args)` x12, `void error(PrintStream err, CliExitCode ...)` x6, classe interne `private static final class Options` x6 - et les 6 corps d'`Options` ont 6 empreintes md5 differentes. Exemple a l'identique sur 24 lignes : morpheus-cli/src/main/java/com/morpheus/cli/MorpheusCompositionCli.java:156-177 et morpheus-cli/src/main/java/com/morpheus/cli/MorpheusAcceptanceCriteriaCli.java:104-125. Le scan de blocs normalises de 12 lignes trouve 322 blocs dupliques sur >=2 fichiers dans tout src/main/java, dont les plus gros clusters (6 occurrences) sont tous des fichiers morpheus-cli.
```

**Impact** — 11 classes repetent le format de message d'erreur `MORPHEUS error [code]: message`. Un changement de format, une nouvelle option globale ou un correctif sur l'analyse d'arguments doit etre applique 6 a 12 fois ; les 6 variantes d'`Options` ayant deja divergé, le comportement d'une option n'est plus garanti identique d'une sous-commande a l'autre.

**Action** — Extraire `safeMessage`, `command(String[])`, `error(PrintStream, CliExitCode, String)` et `Options` dans des types partages du paquet com.morpheus.cli (a cote de GlobalArgs et CliExitCode, qui sont deja ce patron), puis interdire par assertion textuelle une copie privee de ces quatre membres - le meme mecanisme que BoundedWaitOwnershipTest.

### AUD-QUA-07 — Le constructeur de Map varargs est recopié 10 fois dans api et mcp, sous deux noms, avec un cast (String) non vérifié
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | 9 — Supprimer la duplication du socle |
| **Débloque** | AUD-ARC-10 (sprint 9), AUD-QUA-09 (sprint 9) |

**Preuve**

```
10 copies privees de `map(Object... entries)` : morpheus-api/src/main/java/com/morpheus/api/MorpheusApiService.java:203-213, MorpheusRequirementQueryApiService.java:113, MorpheusHistoryApiService.java:150, MorpheusProjectSyncApiService.java:157, MorpheusChangeQueryApiService.java:210, MorpheusSpecificationQueryApiService.java:158, MorpheusDiagnosticsApiService.java:144, morpheus-mcp/.../MorpheusMcpToolService.java:404, MorpheusMcpToolCatalog.java:105 sous le nom props(Object... entries), corps ligne 111, MorpheusCompositionMcpTools.java:100. Corps type : `result.put((String) entries[index], entries[index + 1]);` - la parite des cles est validee (`entries.length % 2 != 0`) mais le type de la cle ne l'est pas. 306 occurrences de `Map<String, Object>` en main, dont 205 dans morpheus-mcp et 45 dans morpheus-api.
Harmonise en relecture : 9 copies nommees map(Object... entries) (7 api, 2 mcp) + 1 copie nommee props(Object... entries) = 10 copies sous deux noms. AUD-ARC-10 compte 9 pour l'ensemble nomme map seul : les deux chiffres portent sur des ensembles differents.
```

**Impact** — Une cle non-String produit un ClassCastException brut au lieu d'un echec nomme, ce que rules/code-style.md interdit explicitement ("Rendre les echecs explicites : exception nommee"). Et la frontiere de serialisation de deux adaptateurs publics repose sur 10 helpers independants : une regle de serialisation ajoutee a l'un ne protege pas les neuf autres.

**Action** — Extraire un unique constructeur canonique (cote application, consommable par api et mcp) avec signature typee ou validation explicite de la cle et message nomme, et le faire exiger par une regle de dependance au lieu des 10 copies.

### AUD-QUA-08 — sha256, slug, readAllLines et joinContent sont recopiés dans les providers, avec deux messages d'erreur divergents
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | 9 — Supprimer la duplication du socle |

**Preuve**

```
`String sha256(String value)` x6 : morpheus-provider-markdown/.../StructuredMarkdownSpecificationContentReader.java:465, morpheus-provider-openspec/.../OpenSpecRequirementDeltaReader.java:557, OpenSpecCurrentSpecificationReader.java:518, OpenSpecChangeMetadataReader.java:478, morpheus-provider-synthetic/.../SyntheticConstraintSemanticsReader.java:168, SyntheticSpecificationContentReader.java:466 - 4 ecritures differentes du meme calcul et deux messages distincts : "SHA-256 is unavailable" (markdown, synthetic) contre "SHA-256 must be available" (openspec). Egalement `slug(String)` x4, `readAllLines(...)` x3 (OpenSpecRequirementDeltaReader.java:520, OpenSpecCurrentSpecificationReader.java:439, OpenSpecChangeMetadataReader.java:452) et `joinContent(List,int,int)` x3, tous identiques.
```

**Impact** — Le hachage d'extrait alimente les empreintes de provenance que le projet vend comme deterministes (ADR-0039/0043/0045). Trois ecritures du meme calcul dans trois modules sont trois endroits ou une evolution (encodage, normalisation) peut etre appliquee partiellement, sans qu'aucun test de parite ne compare les six. Les deux messages differents sur la meme condition rendent le diagnostic dependant du provider.

**Action** — Deplacer sha256, slug, readAllLines et joinContent dans un utilitaire de morpheus-application (les trois providers en dependent deja, le sens de dependance est respecte) et supprimer les 16 copies. Harmoniser le message sur une seule formulation.

### AUD-QUA-09 — MinosIntegrationSettings et NexusIntegrationSettings sont le même fichier à 90 %, avec une dérive déjà installée
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité |
| **Effort** | M |
| **Sprint** | 9 — Supprimer la duplication du socle |
| **Après** | AUD-QUA-07 (sprint 9) |

**Preuve**

```
diff apres normalisation du nom (s/Minos/XXX/ contre s/Nexus/XXX/) entre morpheus-integration-minos/.../MinosIntegrationSettings.java (171 l.) et morpheus-integration-nexus/.../NexusIntegrationSettings.java (154 l.) : 48 lignes de diff, toutes cosmetiques ou residuelles - helper nomme `requireNonBlank` cote MINOS (ligne 157) contre `requireText` cote NEXUS (ligne 144) pour un corps identique ; `enum State` sur 5 lignes contre 1 ; message "MINOS JAR is not a file" contre "NEXUS runner JAR is not a file" ; `processEnvironment()` present uniquement cote MINOS et mort (cf. AUD-QUA-05) ; affectation redondante `timeoutSeconds = DEFAULT_TIMEOUT_SECONDS;` au catch MINOS:117, absente cote NEXUS:112 (sans consequence, la variable etant initialisee a la valeur par defaut NEXUS:76). Memes constats triviaux sur les paires Exception (12/12 l., 2 lignes de diff) et GatewayFactory (6/6 l., 2 lignes). Les paires Runtime (70/39 l., 63 lignes) et Gateway (330/355 l., 203 lignes) divergent, elles, reellement. 45 occurrences de blocs dupliques detectees dans chacun des deux modules.
```

**Impact** — Deux resolveurs de configuration de code externe - donc deux chemins de confiance - evoluent par copie. Le durcissement du pin SHA-256 ou de la borne de timeout appliquee a l'un ne protege pas l'autre, et la divergence des noms de helper et des messages montre que la copie a deja commence a deriver.

**Action** — Extraire la resolution commune (chemin de JAR, pin SHA-256, commande java, home, borne de timeout) dans un type parametre par le prefixe de configuration et le nom du pair, place dans morpheus-application ou morpheus-mcp-transport, et ne laisser dans chaque module que ses constantes de propriete et d'environnement. Ajouter un test de parite entre les deux surfaces, comme les *PersistenceParityTest font entre les stores.

### AUD-TRV-01 — Les quatre validateurs M15 à M18 sont inexécutables, sans équivalent .sh, et les commandes documentées pour les rejouer n'existent pas
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Qualité · Tests · Dépendances |
| **Effort** | M |
| **Sprint** | 1 — Remettre le dispositif en marche |
| **Débloque** | AUD-TRV-07 (sprint 8) |
| **Constats fusionnés** | AUD-QUA-03, AUD-TST-05, AUD-DEP-11 |

**Preuve**

```
scripts/validate-m15.ps1:9 $Branch = 'm15/acceptance-verification-evidence' puis :112-114 git switch $Branch dans un bloc actif par defaut ; idem m16:9/104, m17:9/136, m18:9/134. git branch -a ne connait que main, origin/develop, origin/main : les quatre branches ont disparu. Aucun scripts/validate-m1[5-8].sh (m19->m28, d2, r2, r3 ont tous leur paire) ; aucun .cmd non plus, alors que docs/validation/VALIDATION_M15.md:199, M16:199, M17:187, M18:130 et docs/roadmap/M18_EXECUTION.md:122 donnent '.\validate-m15.cmd' comme commande canonique. Seule assertion de parite : morpheus-architecture-tests/.../m28/McpClientIntegrationArchitectureTest.java:219-231, qui enumere a la main les artefacts du seul M28.
```

**Impact** — Quatre scripts de 100 a 300 lignes ne peuvent plus demarrer, leur moitie Linux n'a jamais existe, et les documents qui portent la preuve de validation de ces milestones citent une commande qui ne resout sur aucune plateforme : la preuve referencee n'est reproductible nulle part. scripts/validate.ps1 les propose quand meme (enumeration dynamique de validate-*.ps1). .claude/rules/governance.md affirme que la parite Windows/Linux est assertee ; elle ne l'est que pour le dernier milestone, nomme a la main, donc rien n'empeche le prochain de livrer un .ps1 seul.

**Action** — Archiver validate-m15..m18.ps1 hors de scripts/ (leurs preuves restent dans docs/validation/) ou les reparer : remplacer le pinning de branche par un argument de SHA et livrer les .sh. Dans les deux cas, remplacer l'enumeration manuelle de McpClientIntegrationArchitectureTest par un test generique - pour tout scripts/validate-<t>.ps1 exiger scripts/validate-<t>.sh - et corriger les commandes citees dans VALIDATION_M15..M18 vers la forme reellement supportee (scripts\validate.cmd <t> -Version <v>).

### AUD-SEC-03 — Les segments de chemin HTTP sont décodés deux fois côté local, alors que l'autorisation remote les décode une seule fois
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Sécurité |
| **Effort** | M |
| **Sprint** | 3 — Fermer les écarts de sécurité |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java:223 passe exchange.getRequestURI().getPath() (deja percent-decode par java.net.URI) a MorpheusHttpPathParser.java:27 qui re-applique URLDecoder.decode ; meme double decodage duplique en MorpheusPolicyHttpRoutes.java:192 et MorpheusQueryHttpRoutes.java:212 ; l'autorisation remote decide sur getPath() (MorpheusRemoteHttpServer.java:219) et le proxy transmet getRawPath() (MorpheusRemoteProxyTargetResolver.java:39)
```

**Impact** — La vue du chemin qui sert a l'autorisation et la vue du chemin qui sert au routage ne sont pas identiques : %252e, %252f ou + sont interpretes differemment des deux cotes. Aucun contournement de privilege n'a pu etre demontre aujourd'hui, parce que tous les parametres de chemin declares sont types (UUID / identifiants parses) et echouent avant usage ; la protection vient donc du parsing des identifiants, pas du parseur de chemin. Un futur parametre de chemin en texte libre ouvre immediatement la classe de contournement.

**Action** — Decoder exactement une fois : supprimer le URLDecoder.decode des parseurs de segments (getPath() est deja decode), ou partir de getRawPath() et decoder une seule fois. Factoriser les trois parseurs dupliques sur MorpheusHttpPathParser, et ajouter un test qui prouve que le segment vu par l'autorisation remote est byte-pour-byte celui vu par le routeur local.

### AUD-SEC-04 — Aucune trace d'attribution : le principal authentifié d'une requête remote n'est enregistré nulle part
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Sécurité |
| **Effort** | M |
| **Sprint** | 4 — Rendre le produit observable |
| **Après** | AUD-TRV-04 (sprint 4) |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteHttpServer.java:212-220 (l'identite est une variable locale dont seul role() est consomme) ; MorpheusRemoteRuntimeState.java:94-159 ne tient que des compteurs anonymes ; l'enum MorpheusRemoteIdentityFile.Mutation (MorpheusRemoteIdentityFile.java:138-153) ne couvre que CREATE/REVOKE/ROTATE/ROLE_CHANGED/EXPIRY_MIGRATED/AUDIT_QUARANTINED, soit les mutations de credentials, jamais les operations ; aucun appel a un logger dans */src/main/java hors CLI
```

**Impact** — Apres usage d'un jeton WRITE ou ADMIN compromis (enregistrement de projet, sync, mutation de lifecycle, override de policy, creation de backup), rien ne permet de dire quel principal a agi ni quand. La rotation du jeton ne peut pas etre ciblee et l'incident ne peut pas etre reconstitue. SECURITY.md parle d'audit, mais l'audit existant est celui des credentials, pas celui des acces.

**Action** — Enregistrer, pour chaque requete remote autorisee, un evenement sans secret (horodatage, principal, role, methode, template de route issu de MorpheusHttpRouteTable, statut, X-Request-Id deja genere en MorpheusRemoteHttpServer.java:191) dans un journal borne distinct du fichier d'identites, et preciser dans SECURITY.md la difference entre audit de credentials et journal d'acces.

### AUD-TRV-06 — Trois branches inatteignables sur des chemins de sécurité, dont deux sur l'exécution de code tiers
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Sécurité · Qualité |
| **Effort** | S |
| **Sprint** | 3 — Fermer les écarts de sécurité |
| **Constats fusionnés** | AUD-SEC-08, AUD-SEC-01 |

**Preuve**

```
(1) morpheus-application/.../sync/LocalSourceInventoryScanner.java:118-120 construit des FileVisitOption selon policy.followSymbolicLinks(), alors que morpheus-application/.../sync/SourceScanPolicy.java:41-43 refuse la construction d'une politique avec cette valeur a vrai ('symbolic-link traversal is not supported by the source scan policy'). (2) morpheus-integration-minos/.../MinosMcpCodeGateway.java:206 staged = settings.jarSha256().map(...) puis Path launchJar = staged.orElse(jar) - mais :198 refuse avant tout lancement si !settings.enabled(), et MinosIntegrationSettings.java:132-133 definit enabled() comme state() == State.CONFIGURED && jarSha256.isPresent(). La branche orElse(jar) est donc inatteignable. (3) Identique cote NEXUS : NexusIntegrationSettings.java:126 et NexusMcpContextGateway.java:234.
```

**Impact** — Code mort sur trois chemins de securite, donc non teste et non couvert : un assouplissement futur du constructeur de SourceScanPolicy reactiverait silencieusement le parcours des liens symboliques dans un espace de travail fourni par l'utilisateur, et un assouplissement d'enabled() rendrait atteignable le lancement d'un JAR non verifie. Interdit par .claude/rules/code-style.md ('Jamais de code mort'). Note de relecture importante : un constat de l'audit initial (AUD-SEC-01) lisait ces branches comme un fail-open et classait l'integration MINOS/NEXUS comme lancant un JAR non pinne. C'est un FAUX POSITIF - l'integration est fail-closed, et MinosUnpinnedExecutionTest#existingUnpinnedJarIsNeverExecutable couvre deja exactement ce cas. La phrase de SECURITY.md:69 est correcte puisqu'elle se restreint d'elle-meme a 'A pinned JAR'. L'erreur vient de la lecture de launch() sans remonter a enabled() : c'est le piege a signaler pour les prochains audits.

**Action** — Supprimer les trois branches inatteignables : la conditionnelle FileVisitOption et le champ followSymbolicLinks du record SourceScanPolicy (ou faire echouer le scan explicitement si la valeur est vraie, avec un test de reproduction), et remplacer staged.orElse(jar) par staged.orElseThrow() dans les deux passerelles - ce qui transforme l'invariant fail-closed en invariant verifie par le compilateur plutot qu'en consequence d'une garde distante.

### AUD-TRV-02 — Écart de 20,9 points entre les deux échelles de couverture : morpheus-application n'est pas testable sans ses modules voisins
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Tests · Qualité |
| **Effort** | L |
| **Sprint** | 13 — Couverture et pagination |
| **Après** | AUD-TST-01 (sprint 1) |
| **Constats fusionnés** | AUD-TST-07, AUD-QUA-10 |

**Preuve**

```
Plafonds qualifies lus dans les gates : morpheus-architecture-tests/.../m21/CoverageQualityGateTest.java:92 PER_MODULE_QUALIFIED_LINE_RATIO = 0.694341d contre morpheus-coverage-report/.../AggregateCoverageGateTest.java:74 AGGREGATE_QUALIFIED_LINE_RATIO = 0.903026d, sur la meme population de modules -> 20,87 points gagnes uniquement par l'execution croisee. config/m21-quality-ratchets.properties enonce lui-meme que l'echelle par module 'measures what each module covers without help from the others'. Ratios test/main mesures : morpheus-application 11460/24819 = 0,46 et morpheus-domain 1010/2624 = 0,38, contre morpheus-api 1,31, morpheus-mcp 1,43, morpheus-mcp-transport 1,67. 31 classes de morpheus-application sur 341 (1456 lignes) ne sont nommees par aucun fichier de test du depot, dont des classes de comportement : CompactChangeAnalysisView (163 l.), PolicyPublicViews (138), CompactQualityReportView (118), CompactQualityReportService (99), QuerySchemaRegistry (93), ObservedProjectSnapshotPublisher (52), WorkspacePathBoundary (44) ; leurs seuls appelants sont dans morpheus-api, morpheus-cli et morpheus-mcp.
```

**Impact** — Le module qui porte 38 % du code de production, les ports et les cas d'usage a le plus faible rapport test/main des gros modules : une part de son comportement n'est exercee qu'a travers HTTP, MCP ou la CLI. Un changement de comportement n'est vu que si un test d'adaptateur passe par le bon chemin, et le diagnostic d'un echec part de la mauvaise couche. Environ un cinquieme de la couverture du depot est attribuee a un module de test transverse : un module extrait, scinde ou retire du reacteur emporterait cette attribution sans laisser de suite propre derriere lui. L'echelle par module nomme le phenomene et le mesure a 20,9 points ; elle ne le resorbe pas.

**Action** — Cibler d'abord les sept classes de comportement listees avec des tests unitaires dans morpheus-application/src/test (store memoire + provider synthetique, conformement aux regles du depot) : l'echelle par module monte sans toucher a l'agregee. Suivre la reduction de l'ecart entre les deux echelles comme indicateur, en relevant perModule* selon la procedure de la skill coverage-ratchet et sans jamais aligner les deux echelles. A terme, remplacer le seuil unique par un seuil de non-regression par module : a 69,1 % global, morpheus-application tire la moyenne sans jamais etre nomme.

### AUD-TRV-03 — L'enforcement repose majoritairement sur des assertions textuelles et non sur la structure du code
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Tests · Architecture |
| **Effort** | L |
| **Sprint** | 12 — Restructurer les adaptateurs |
| **Après** | AUD-ARC-06 (sprint 12) |
| **Constats fusionnés** | AUD-TST-04, AUD-ARC-12 |

**Preuve**

```
Sur morpheus-architecture-tests : 147 fichiers / 31 256 lignes / 605 methodes @Test. 15 fichiers seulement importent com.tngtech.archunit (3 707 lignes, 94 @Test) ; 68 fichiers (12 802 lignes, 275 @Test) assertent le contenu textuel d'artefacts non-Java (docs/, scripts/, .github/, contracts/, distribution/, config/, README). Comptage des regles de bytecode : 31 noClasses() + 5 classes() + 3 slices() + 1 fields() = 40 regles d'un cote (grep sur les constructeurs de regle), 49 de l'autre (grep incluant ArchRuleDefinition) ; en face, 1 668 assertions contains (498 assertFalse + 1 170 assertTrue) et 356 Files.readString. Le rapport reste du meme ordre : environ 1 regle de bytecode pour 35 assertions textuelles. find . -name 'archunit*.properties' -> aucun fichier, alors que docs/adr/0103-....md:156 s'appuie explicitement sur archRule.failOnEmptyShould.
Retire en relecture : l'absence d'un archunit.properties n'est pas une lacune - docs/adr/0103-....md:156-159 enonce que archRule.failOnEmptyShould vaut true par defaut et que rien dans ce depot ne le desactive. L'absence de fichier est la condition de l'invariant, pas sa negation.
```

**Impact** — Deux consequences mesurables. (a) Toute reformulation de prose dans docs/, scripts/ ou un workflow casse le build sans qu'aucun invariant de code n'ait change : c'est le gros du cout de maintenance des 31 256 lignes. (b) Un invariant ainsi enforce est satisfait en ecrivant la bonne chaine, pas en ayant le bon comportement - une dependance indirecte ou non epelee y echappe. Le depot assume le choix (ADR-0103 et son amendement du 11/09/2026) et .claude/rules/architecture.md avertit lui-meme que ces lignes 'ont menti d'une revision entiere' apres DT-16 ; le constat n'est pas que le choix est faux, c'est que sa proportion a franchi le point ou la machinerie coute plus que la propriete protegee. S'y ajoute que failOnEmptyShould, traite comme porteur par l'ADR, vient du defaut de la bibliotheque : un archunit.properties ajoute ailleurs ou un changement de defaut amont desactiverait le garde-fou en silence.

**Action** — Plafonner explicitement la part textuelle : exiger dans la description de PR la phrase d'intention qu'ADR-0103 reclame deja pour toute nouvelle assertion contains sur un artefact de documentation ; migrer en priorite vers une regle ArchUnit les assertions qui expriment une absence de dependance, en commencant par les classes sans constante inlinable ; deplacer les assertions de coherence documentaire pure vers un verificateur dedie hors du module de gates, pour qu'un echec dise 'documentation desynchronisee' et non 'architecture violee'.

### AUD-TRV-07 — Les 44 scripts de scripts/ sont le dispositif d'enforcement du dépôt et aucun n'est testé, sauf le gate de couverture différentielle
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Tests · Dépendances |
| **Effort** | M |
| **Sprint** | 8 — Fiabiliser les tests |
| **Après** | AUD-TRV-01 (sprint 1), AUD-DEP-06 (sprint 1) |
| **Constats fusionnés** | AUD-TST-17 |

**Preuve**

```
ls scripts/*.ps1 | wc -l -> 24 et ls scripts/*.sh | wc -l -> 20, soit 44 scripts dans scripts/ (37 .ps1 et 25 .sh dans tout le depot). Seul outillage teste : scripts/check-diff-coverage.py (329 lignes) couvert par scripts/tests/test_check_diff_coverage.py (142 lignes) - python3 -m unittest discover -s scripts/tests execute dans cette session : Ran 7 tests in 0.297s / OK. Aucun autre script n'a de test. Or ces scripts sont ce que le pipeline execute : .github/workflows/ci.yml:43 lance bash ./scripts/validate-m21.sh 1.2.1, :86 lance .\scripts\validate-m28.ps1, et .github/workflows/release.yml:134 lance validate-m28.ps1. Quatre d'entre eux sont deja demontres inexecutables (AUD-TRV-01).
```

**Impact** — Le depot tient ses invariants par des scripts qu'aucun test ne couvre, alors qu'il tient son code par 605 methodes de tests d'architecture et un gate de couverture a deux echelles. La defaillance d'un validateur est donc silencieuse : un script qui cesse d'asserter ce qu'il pretend asserter rend PASS, et c'est exactement ce que AUD-DEP-06 a trouve sur validate-d2 (plancher code en dur a 820/258 au lieu de la source vivante a 3820/585). Le raisonnement qui a produit AUD-TST-01 - 'ce gate est-il atteignable par une invocation reelle du depot ?' - n'a jamais ete applique aux validateurs eux-memes. Extrait en relecture de AUD-TST-17, ou ce point figurait en derniere phrase d'un constat classe Info, sur un comptage errone (36 scripts au lieu de 44).

**Action** — Reutiliser le patron scripts/tests/ sur les validateurs : couvrir au minimum leur analyse d'arguments, leur classification d'echec et le fait qu'ils lisent bien config/m21-quality-ratchets.properties quand ils pretendent le faire. Puis mesurer, comme pour AUD-TST-01, combien des 44 scripts sont atteints par une invocation reelle du depot (ci.yml, nightly.yml, release.yml, scripts/validate.ps1) et retirer ou reparer les orphelins.

### AUD-TST-02 — Le gate de couverture par module accepte un rapport JaCoCo périmé : aucun contrôle de fraîcheur
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | 1 — Remettre le dispositif en marche |

**Preuve**

```
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/CoverageQualityGateTest.java:270-288 — la population n'examine que l'existence du fichier :
```java
Path report = directory.resolve(PER_MODULE_REPORT);
if (Files.isRegularFile(report)) { reports.add(report); }
else if (Files.isDirectory(directory.resolve("target/classes"))) { builtWithoutReport.add(module); }
else { neverBuilt.add(module); }
```
Aucune comparaison de date avec `target/classes` ni marqueur d'invocation. La commande documentee `./mvnw test -pl morpheus-architecture-tests -Dtest=CoverageQualityGateTest` (.claude/rules/testing.md:214) atteint precisement cet etat sur un arbre deja construit.
```

**Impact** — Sur un poste de developpement deja construit une fois, modifier du code puis relancer le seul module de tests d'architecture rend un gate de couverture vert calcule sur les rapports d'avant la modification. Le refus ne distingue que « jamais construit » et « construit sans rapport » ; « construit avant la modification » passe. Le CI n'est pas affecte (scripts/validate-m21.sh:66 execute `./mvnw clean verify` lui-meme), donc le risque est une fausse assurance locale, pas une fuite en production.

**Action** — Dans `reportPopulation`, refuser un rapport plus ancien que le `target/classes` de son module (comparaison `Files.getLastModifiedTime`), et nommer cette troisieme cause dans le message de refus a cote de `never-built` et `built-without-report`. Ajouter le test de refus correspondant a cote de celui qui existe deja (lignes 184-204).

### AUD-TST-03 — 300 des 831 assertThrows n'assertent qu'un type d'exception JDK générique, sans message ni code de rejet
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Tests |
| **Effort** | M |
| **Sprint** | 8 — Fiabiliser les tests |

**Preuve**

```
Script execute sur les 526 fichiers de test (scratchpad/at2.py, regex `assertThrows\(\s*([A-Za-z0-9_.]+)\.class` sur le texte complet, imports retires) :
```
assertThrows(X.class) total: 831
captured in a variable or followed by a message/cause assertion within ~400 chars: 381
bare + generic JDK exception: 300
bare + project-specific exception: 150
```
Exemples : morpheus-api/src/test/java/com/morpheus/api/RemoteIdentityFileStoreTest.java:230-234 (quatre `assertThrows(NullPointerException.class, ...)` consecutifs sans assertion de message), morpheus-api/src/test/java/com/morpheus/api/TimedBoundedResponseWriterTest.java:143-147.
```

**Impact** — La regle du depot est explicite (.claude/rules/testing.md, section TOUJOURS) : « Tester un cas d'echec par assertThrows **et** une assertion sur le message ou le code de rejet — un echec pour une autre raison ne doit pas faire passer le test ». 36 % des sites de rejet ne la respectent pas, et sur `IllegalArgumentException` / `NullPointerException` n'importe quel autre chemin d'erreur (y compris un `Objects.requireNonNull` deplace, ou une validation retiree au profit d'une autre) satisfait encore l'assertion. Ces tests continueraient de passer apres la suppression de la validation qu'ils sont censes proteger.

**Action** — Traiter les 300 cas par lot, en priorite ceux des frontieres systeme (morpheus-api, morpheus-cli) : capturer l'exception et asserter le message ou le code (`assertEquals("--id requires a non-blank value", failure.getMessage())`). Puis ancrer la regle dans un test d'architecture textuel sur `src/test/java` au meme titre que BoundedWaitOwnershipTest ancre les attentes bornees, pour refuser la prochaine occurrence.

### AUD-TST-06 — La colonne cli du manifeste de convergence n'est vérifiée que pour sa non-vacuité, sans gate bidirectionnel, contrairement à http et mcp
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Tests |
| **Effort** | M |
| **Sprint** | 8 — Fiabiliser les tests |

**Preuve**

```
morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/ProductionIntegrityContractTest.java:79 `assertFalse(columns[2].isBlank(), "CLI shape must be explicit");` puis :86-88 trois lignes litterales choisies a la main. `grep -rln "cliColumn\|CLI_COLUMN\|columns\[2\]" morpheus-*/src/test` ne retourne que ce fichier. Les deux autres transports ont leur gate bidirectionnel : PublicHttpRouteConvergenceTest (paquet com.morpheus.api) et PublicSurfaceManifestCoversEveryServedToolTest (paquet com.morpheus.mcp). Volumetrie : `wc -l contracts/public-surfaces.tsv` -> 90 (89 lignes de capacite) ; `cut -f3 | grep -c '^EXPLICITLY'` -> 15, soit 74 surfaces CLI reelles declarees ; `grep -ohE 'case "[a-z0-9-]+"' morpheus-cli/src/main/java/com/morpheus/cli/*.java | sort -u | wc -l` -> 88 litteraux de commande distincts.
```

**Impact** — Une commande CLI peut etre ajoutee, renommee ou retiree sans qu'aucun test ne le remarque, et une ligne du manifeste peut designer une commande qui n'existe plus. Les 74 valeurs CLI du manifeste sont donc de la documentation non verifiee, alors que le manifeste est presente comme « la loi » de la convergence. .claude/rules/governance.md le reconnait (« Ni l'un ni l'autre ne verifie la colonne cli ») : c'est un trou connu, pas un trou cache, mais il porte sur le transport le plus directement expose a l'utilisateur.

**Action** — Ajouter un `PublicCliSurfaceConvergenceTest` sur le modele de PublicHttpRouteConvergenceTest : extraire la table des commandes et sous-actions depuis une source unique du module CLI (comme MorpheusHttpRouteTable le fait pour HTTP — a creer si elle n'existe pas), comparer dans les deux sens avec la colonne `cli`, et faire echouer sur une commande servie sans ligne ou une ligne sans commande.

### AUD-TST-10 — Aucun test d'upgrade depuis une base réelle aux paliers de schéma 13 à 19 ; le seul vrai test d'upgrade part du palier 12
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Tests |
| **Effort** | M |
| **Sprint** | 8 — Fiabiliser les tests |
| **Après** | AUD-DEP-01 (sprint 2) |

**Preuve**

```
morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java:45 `SUPPORTED_SCHEMA_VERSION = 20`. Le seul test qui construit une base ancienne puis la fait migrer est morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/R2UpgradeCompatibilityTest.java:24-36, dont la baseline `ONE_DOT_ZERO_MIGRATIONS` s'arrete a la migration 12. Les paliers 16, 17, 19 et 20 sont simules en supprimant une ligne du journal sur un schema deja courant (SqliteSchemaMigrationTest.java:170 `DELETE FROM schema_migrations WHERE version = 16`, SqliteSpecificationVersionSequenceMigrationTest.java:29 (17), SqliteSyncStateRevisionMigrationTest.java:34 (19), SqliteCompositionResolutionMigrationTest.java:24 (20)) ; SqliteMigrationAtomicityTest.java:45 utilise `applyThroughVersion12`. Aucun test ne part d'un palier 13, 14, 15 ou 18.
```

**Impact** — La technique de suppression de ligne de journal ne reproduit pas une base ancienne : les tables portent deja la forme finale, donc elle valide bien une migration de *valeurs* (V020 renomme une resolution) mais pas une migration de *forme*. Une base ecrite par MORPHEUS 1.1.x (palier 13 a 18 : portfolio, saved views, policy packs, integrite de sequence des policy packs) n'est couverte par aucun test d'upgrade avec donnees. Les migrations concernees sont majoritairement additives (`CREATE TABLE`) — V010 est la seule a faire des `ALTER TABLE ... ADD COLUMN` et elle est dans la baseline R2 — ce qui limite le risque de perte de donnees, mais le chemin d'upgrade le plus probable en production (1.1.x -> 1.2.1) n'est pas celui qui est teste. Severite montee de Faible a Moyenne en relecture : ce constat forme avec AUD-DEP-01 (5 migrations V016-V020 non publiees, aucun document d'upgrade) et AUD-DEP-16 (aucune sauvegarde automatique avant migration) un cluster unique dont la materialisation est irreversible pour un utilisateur. Classer le maillon le plus faible en Faible masquait le plus bloquant des trois.

**Action** — Generaliser R2UpgradeCompatibilityTest en un test parametre sur la baseline : pour chaque palier publie (12 pour 1.0.0, puis celui de chaque version livree), construire la base avec les seules migrations de cette baseline, y inserer un etat publie, migrer avec le runtime courant et asserter la preservation d'identite et d'historique. Les checksums golden deja pinnes (SqliteMigrationChecksumGoldenTest.java:23-44) fournissent les valeurs necessaires.

### AUD-PRF-03 — Serveur HTTP local sans contrôle d'admission : threads virtuels illimités, une connexion SQLite physique par requête, journal PERSIST
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Performance |
| **Effort** | M |
| **Sprint** | 5 — Écarter le risque d'indisponibilité |
| **Après** | AUD-PRF-18 (sprint 5), AUD-TRV-05 (sprint 5) |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/MorpheusLocalHttpServerBootstrap.java:165 `Executors.newVirtualThreadPerTaskExecutor()` (aucun Semaphore dans le fichier : `grep -c "Semaphore|maxConcurrent"` = 0) ; morpheus-api/src/main/java/com/morpheus/api/ApiRuntime.java:42 `SqliteConnectionScope.open(databasePath)` par operation (32 sites `new ApiRuntime(`) ; morpheus-store-sqlite/.../SqliteDatabaseSecurity.java:118-121 busy_timeout=5000 + journal_mode=PERSIST (ligne 137)
```

**Impact** — PERSIST est un journal de rollback : un ecrivain prend un verrou EXCLUSIVE sur toute la base et bloque les lecteurs. Sans plafond de concurrence, N requetes simultanees ouvrent N connexions physiques qui se disputent cette base et echouent en SQLITE_BUSY apres 5 s. Le serveur remote, lui, porte quatre semaphores (MorpheusRemoteHttpServer.java:54-57, 91-93) : l'asymetrie n'est pas documentee. Le risque de concurrence est enregistre en RT-01 (docs/architecture/arc42/11-risques-dette.md:14) avec observabilite (SqliteContentionMetrics) mais sans controle d'admission local. Cout non mesure, raisonnement sur la structure du code. Severite redescendue de Elevee a Moyenne en relecture : serveur de boucle locale mono-utilisateur, risque deja enregistre en RT-01 avec observabilite, et SqliteDatabaseSecurity.java:126-131 montre que la pression multi-ecrivain a ete raisonnee. L'asymetrie avec les quatre semaphores du serveur remote reste un ecart reel.

**Action** — Doter MorpheusLocalHttpServerBootstrap d'un semaphore de concurrence equivalent a celui du serveur remote, et refuser en 429 au-dela ; sinon un pic de clients locaux se traduit en echecs SQLITE_BUSY plutot qu'en file d'attente. Relire RT-01 et y consigner la decision.

### AUD-PRF-05 — Pagination de la recherche de requirements appliquée en mémoire après chargement intégral du snapshot
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Performance |
| **Effort** | L |
| **Sprint** | 13 — Couverture et pagination |
| **Après** | AUD-ARC-01 (sprint 11), AUD-TST-01 (sprint 1) |

**Preuve**

```
morpheus-application/src/main/java/com/morpheus/application/query/RequirementQueryService.java:63-73 : `requirementStore.listCurrentRequirementVersions(snapshot.id()).stream().filter(record -> matches(...)).sorted(REQUIREMENT_ORDER).toList()` puis `matches.subList(from, to)`
Contre-preuve relevee en relecture : morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m19/M19QueryPerformanceGate.java:42 QUERY_BUDGET_NANOS = 1_000_000_000L, :90 new RequirementQueryService, :93 .findActive( sur la fixture de 10 000 requirements (M19LargeFixtureSupport) - le cout de ce chemin est pre-declare et gele a 1 s p95.
```

**Impact** — Le filtre plein-texte, le tri et le decoupage de page se font apres avoir materialise tous les requirements CURRENT du snapshot. Le travail est identique quelle que soit la page demandee, et croit avec la taille du snapshot (la fixture M19 du projet en compte 10 000, M19LargeFixtureSupport.java:22). Huit autres services appellent la meme surcharge non bornee (DecisionReferenceQualityService.java:191, RequirementQualityService.java:57, ChangeCompletenessService.java:76, TaskQualityService.java:67, HistoricalRequirementQueryService.java:29, RequirementSnapshotComparisonService.java:79, SpecificationContextQueryService.java:74). Cout non mesure, raisonnement sur la structure du code. Severite redescendue de Elevee a Moyenne en relecture : le cout n'est pas un angle mort de conception, il est budgete. Le defaut reel est que ce budget ne tourne plus (voir AUD-TST-01).

**Action** — Pousser le predicat et la page dans le port : ajouter une methode de recherche paginee a VersionedRequirementStore et l'implementer en SQL (WHERE + ORDER BY + LIMIT/OFFSET), en gardant la surcharge non bornee pour les seuls appelants qui ont reellement besoin de l'ensemble. Prealable : remettre M19QueryPerformanceGate en execution (AUD-TST-01), sans quoi la correction ne sera pas mesurable.

### AUD-PRF-06 — Écriture N+1 des liens de traçabilité : trois à quatre prepareStatement par lien, sans batch, dans une transaction unique
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Performance |
| **Effort** | M |
| **Sprint** | 6 — Borner les coûts qui croissent avec les données |
| **Après** | AUD-TST-01 (sprint 1) |

**Preuve**

```
morpheus-store-sqlite/.../SqliteTraceabilityStore.java:68-72 `for (TraceabilityLink link : batch) { putLinkInternal(snapshotId, link); }` dans SqliteTransactionRunner.runVoid ; putLinkInternal (ligne 205-222) fait 1 SELECT (findDefinitionInternal), 1 INSERT definition (ligne 170), 1 batch evidence (ligne 195) et 1 INSERT d'appartenance (ligne 216), chacun avec son propre prepareStatement
Contre-preuve relevee en relecture : M19FullPublishPerformanceGate.java:40 PUBLISH_BUDGET_NANOS = 60_000_000_000L sur le chemin publishFull, avec M19LargeFixtureSupport.java:23 GATE_TRACEABILITY_LINKS = 25_000 - les ~100 000 prepareStatement tiennent dans un budget ecrit et assume.
```

**Impact** — La methode s'appelle putLinks et ne batche rien au niveau des liens. Appelee par le chemin de publication (morpheus-application/.../ingestion/ProjectSnapshotImportService.java:129) ; la fixture du projet lui-meme en insere 25 000 (M19LargeFixtureSupport.java:23), soit ~100 000 prepareStatement et executions dans une seule transaction qui tient le verrou EXCLUSIVE de la base en mode PERSIST. Toute requete concurrente epuise son busy_timeout de 5 s pendant ce temps. Cout non mesure, raisonnement sur la structure du code. Severite redescendue de Elevee a Moyenne en relecture : budget pre-declare et accepte a 60 s p95.

**Action** — Preparer les quatre instructions une fois hors de la boucle et utiliser addBatch/executeBatch par tranche, comme SqliteSnapshotBusinessContentWriter.java:73-75 et SqliteSyncStateStore.java:312-323. Remplacer le SELECT par lien par un unique SELECT des link_id deja presents. Prealable : remettre M19FullPublishPerformanceGate en execution (AUD-TST-01).

### AUD-PRF-07 — Six index redondants dupliquant l'index implicite d'une clé primaire
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | 7 — Le SQL de détail |
| **Après** | AUD-PRF-01 (sprint 6) |

**Preuve**

```
V014__saved_views.sql:23 `PRIMARY KEY (saved_view_id, revision)` vs :27 `CREATE INDEX idx_saved_view_versions_identity ON saved_view_versions(saved_view_id, revision)` (doublon exact) ; V005:24 vs V005:35 (snapshot_traceability_links, doublon exact) ; V015:31 vs V015:36 (policy_pack_activations, doublon exact) ; V015:49 vs V015:53 (policy_overrides, doublon exact) ; V008:19 vs V008:36 (sync_inventory_entries, prefixe gauche de la PK) ; V010:11 vs V010:30-33 (snapshot_constraint_blocking_targets et _supporting_evidence, prefixe gauche) ; V007:36/55/84 vs V007:174-176 (snapshot_specifications/scenarios/changes, prefixe gauche)
```

**Impact** — Une PRIMARY KEY non-INTEGER sur une table rowid cree un sqlite_autoindex ; ces index n'apportent aucun chemin d'acces nouveau mais sont maintenus a chaque INSERT, UPDATE et DELETE, et occupent de la place dans la base et les backups. Sur le chemin de publication, qui insere en masse dans snapshot_specifications, snapshot_scenarios et snapshot_changes, c'est une amplification d'ecriture pure. Cout non mesure, raisonnement sur la structure du schema.

**Action** — Ajouter une migration V021 qui DROP ces index, apres avoir confirme par EXPLAIN QUERY PLAN sur une base reelle qu'aucune requete ne les choisit plutot que l'autoindex. Conserver idx_snapshot_constraints_change, idx_snapshot_acceptance_* et idx_portfolio_memberships_project, qui portent des colonnes differentes ou un ordre inverse.

### AUD-PRF-08 — La vérification de frontière du workspace fait un toRealPath par segment, deux fois par fichier scanné
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Performance |
| **Effort** | M |
| **Sprint** | 7 — Le SQL de détail |

**Preuve**

```
morpheus-application/src/main/java/com/morpheus/application/sync/WorkspacePathBoundary.java:31-41 : boucle `for (Path segment : relative)` avec `Files.exists(current, NOFOLLOW_LINKS)`, `Files.isSymbolicLink(current)`, `current.toRealPath()` puis un second `Files.isSymbolicLink(current)` ligne 39 ; appelee en LocalSourceInventoryScanner.java:178 (par repertoire), :212 et :265 (deux fois par fichier)
```

**Impact** — Pour un fichier a la profondeur d, chaque appel fait ~3 appels systeme par segment, et toRealPath resout a son tour toute la chaine. Avec les defauts SourceScanPolicy.java:18-20 (maxDepth 128, maxFiles 50 000), le scan peut atteindre plusieurs millions d'appels systeme, deux fois par fichier. Le second `Files.isSymbolicLink(current)` de la ligne 39 est par ailleurs inatteignable : la ligne 35 a deja leve l'exception dans ce cas. Cout non mesure, raisonnement sur la structure du code.

**Action** — Memoiser les segments de repertoire deja valides pendant un scan (un Set<Path> de prefixes verifies) : le visiteur descend l'arborescence, donc le prefixe d'un fichier a deja ete valide par preVisitDirectory. Supprimer la condition morte de la ligne 39.

### AUD-PRF-09 — Le proxy remote n'impose ni timeout de lecture ni budget mémoire sur les routes WRITE et ADMIN
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | 5 — Écarter le risque d'indisponibilité |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteProxyTransport.java:75 `if (boundedUpstreamTimeout) request.timeout(READ_ONLY_UPSTREAM_TIMEOUT);` et :89 `if (boundedUpstreamTimeout && !responseSlots.tryAcquire())` ; MorpheusRemoteRoutePolicy.java:179-186 `usesBoundedUpstreamTimeout` ne retourne true que pour `MorpheusRemoteRole.READ`
```

**Impact** — Sur toute route WRITE ou ADMIN, l'appel amont n'a que le connectTimeout de 5 s (ligne 42) : une reponse amont qui ne vient jamais retient indefiniment le thread virtuel et son permis de concurrence remote, et aucun slot de reponse n'est reserve. Le serveur remote limite la concurrence mais pas la duree de ces requetes. Cout non mesure, raisonnement sur la structure du code.

**Action** — Appliquer un timeout de lecture amont a toutes les routes, plus genereux pour les ecritures que les 60 s des lectures, au lieu de n'en appliquer aucun. Documenter la valeur retenue a cote de READ_ONLY_UPSTREAM_TIMEOUT.

### AUD-PRF-10 — Le seul SELECT paginé du module trie sur une colonne que son index ne couvre pas
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | 7 — Le SQL de détail |
| **Après** | AUD-PRF-01 (sprint 6) |

**Preuve**

```
morpheus-store-sqlite/.../SqliteVersionedRequirementStore.java:308-314 : `SELECT * FROM requirement_versions WHERE snapshot_id = ? AND temporal_state = 'CURRENT' ORDER BY entity_version_id LIMIT ?` ; l'index partiel correspondant est V004__versioned_requirement_persistence.sql:45-47 `uq_requirement_versions_current_snapshot_identity ON requirement_versions(snapshot_id, entity_identity_id) WHERE temporal_state = 'CURRENT'`
```

**Impact** — L'index partiel satisfait exactement le WHERE mais ordonne par entity_identity_id, pas par entity_version_id : SQLite doit lire toutes les lignes CURRENT du snapshot et les trier dans un B-tree temporaire avant d'appliquer le LIMIT. La pagination ne reduit donc que la taille du resultat, pas le travail. Cout non mesure, raisonnement sur la structure du code et du schema.

**Action** — Soit ordonner par entity_identity_id pour que l'index partiel fournisse le LIMIT sans tri, soit ajouter un index partiel (snapshot_id, entity_version_id) WHERE temporal_state = 'CURRENT'. Verifier le choix par EXPLAIN QUERY PLAN avant de trancher, l'ordre observable faisant partie du contrat de determinisme.

### AUD-DEP-02 — reactor-bom figé à 2024.0.0 : 18 correctifs de retard dans la ligne même que le POM impose, et dependabot ne peut pas proposer ce correctif
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 3 — Fermer les écarts de sécurité |

**Preuve**

```
pom.xml:53 `<reactor-bom.version>2024.0.0</reactor-bom.version>` ; pom.xml:109 `<!-- MCP SDK 2.0.1 is built on the Reactor 2024.0.x/3.7.x line. Keep the transport on that compatible train. -->`. mvnrepository io.projectreactor:reactor-bom — 2024.0.0 = 12 nov. 2024, dernier de la meme ligne 2024.0.18 = 8 juin 2026 ; reactor-core 3.7.0 (12 nov. 2024) contre 3.7.19 (8 juin 2026). `git log -S"reactor-bom.version" -- pom.xml` ne renvoie qu'un seul commit (1c6d3550) : la valeur a ete posee une fois et jamais relevee. .github/dependabot.yml:3-11 declare l'ecosysteme maven sans aucune regle `ignore`, donc la seule montee proposable est la derniere stable (2025.0.7), celle que le commentaire du POM oblige a refuser.
```

**Impact** — reactor-core est une dependance d'execution embarquee dans l'uber-JAR distribue (morpheus-mcp-transport, pile du client MCP qui lance les processus MINOS/NEXUS). 19 mois de correctifs amont — y compris de fuites de ressources et de bugs de planification — ne sont pas appliques, et le mecanisme de veille automatique est structurellement incapable de les proposer : chaque PR dependabot sur reactor est une montee de train a refuser, ce qui entraine a refuser reactor sans distinguer les deux cas.

**Action** — Passer `reactor-bom.version` a 2024.0.18 (meme train, aucun changement d'API) et ajouter dans .github/dependabot.yml une regle `ignore` sur io.projectreactor:reactor-bom limitee a `version-update:semver-major`/`semver-minor`, afin que seuls les correctifs de la ligne compatible soient proposes. Aucune CVE verifiee sur reactor-core 3.7.x dans cette session : a confirmer au scanner OWASP.

### AUD-DEP-03 — La distribution d'un produit sous licence propriétaire n'embarque aucune attribution de licence tierce
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 2 — Débloquer la release 1.2.1 |
| **Débloque** | AUD-DEP-12 (sprint 2) |

**Preuve**

```
LICENSE:1 « MORPHEUS — PROPRIETARY SOURCE-AVAILABLE LICENSE », section 5 renvoie generiquement aux licences tierces sans les enumerer ni les joindre. `git ls-files | grep -iE 'notice|third.?party|attribution'` ne renvoie que LICENSE et une classe Java. distribution/build-portable.sh:91-93 et :137 : l'archive ne contient que l'app-image jpackage et le repertoire `integration/` — ni LICENSE, ni NOTICE, ni texte de licence tierce. Composants embarques et leurs licences : tools.jackson 3.2.3 (Apache-2.0), org.xerial:sqlite-jdbc 3.53.4.0 (Apache-2.0), io.projectreactor:reactor-core (Apache-2.0), io.modelcontextprotocol.sdk 2.0.1 (MIT), org.slf4j:slf4j-nop 2.0.20 (MIT), org.reactivestreams:reactive-streams 1.0.4 (MIT-0).
```

**Impact** — Apache-2.0 §4(a)/(d) exige qu'une redistribution joigne une copie de la licence et le contenu des fichiers NOTICE ; MIT exige la reproduction de l'avis de copyright. Le tar.gz Linux, le ZIP et le Setup.exe Windows redistribuent ces binaires sans ces textes : la distribution est non conforme aux licences qu'elle consomme, alors que le depot est par ailleurs tres strict sur sa propre protection.

**Action** — Generer un THIRD-PARTY-NOTICES a partir de la SBOM CycloneDX deja produite (target/m21-supply-chain/morpheus-sbom.json porte groupe/artefact/licence par composant), le copier dans l'app-image depuis build-portable.sh/.ps1 a cote du LICENSE du produit, et ajouter sa presence a la preuve de packaging deja assertee dans ces scripts.

### AUD-DEP-04 — La SBOM est produite à chaque verify et exigée par les validateurs, mais n'est jamais publiée avec la release
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 2 — Débloquer la release 1.2.1 |
| **Débloque** | AUD-DEP-12 (sprint 2) |

**Preuve**

```
pom.xml:237-253 cyclonedx-maven-plugin `makeAggregateBom` liee a `verify`, sortie `target/m21-supply-chain/morpheus-sbom.{json,xml}` ; exigee par scripts/validate-m21.sh:116-119, scripts/validate-d2.sh:135-138, scripts/validate-m26.sh:88, scripts/validate-m27.ps1:85-86 ; son empreinte est enregistree dans la provenance de build (scripts/write-build-provenance.ps1:56-57,72). Mais .github/workflows/release.yml:217-231 enumere exactement 10 assets attendus, sans SBOM, et scripts/verify-release-provenance.sh:61-72,93-95 echoue sur tout asset `unexpected`. Seule trace publiee : l'artefact CI `m21-integrity-<OS>` (ci.yml:95-101) avec `retention-days: 14`.
```

**Impact** — L'operateur qui installe MORPHEUS ne dispose d'aucun inventaire des composants tiers qu'il execute : il ne peut ni verifier son exposition a une CVE annoncee, ni alimenter son propre outillage de conformite. La preuve existe dans le pipeline, disparait au bout de 14 jours, et la verification de release refuse activement qu'on la publie. La SBOM qui prouve la chaine d'approvisionnement n'atteint donc jamais le consommateur de cette chaine.

**Action** — Publier morpheus-sbom.json (et .xml) comme asset de release avec son .sha256, l'inclure dans l'attestation `actions/attest`, l'ajouter aux listes `expected` de release.yml et `EXPECTED_ASSETS` de verify-release-provenance.{sh,ps1}, et le referencer dans le `assets[]` du release-manifest.json.

### AUD-DEP-05 — L'installeur Windows et les archives ne sont pas signés Authenticode, alors que le dépôt exige cette signature de son propre outillage
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | M |
| **Sprint** | 14 — Signer la distribution |
| **Après** | AUD-DEP-12 (sprint 2) |

**Preuve**

```
Aucune occurrence de signtool/osslsigncode/codesign dans distribution/, .github/workflows/ ou scripts/ hors verification de l'outillage. distribution/windows/MORPHEUS.iss:19-44 ne declare aucune directive `SignTool`. A l'inverse, distribution/ensure-inno-setup.ps1:34-38 et :120-124 refusent de poursuivre si la signature Authenticode du compilateur Inno Setup telecharge n'est pas valide. La provenance publiee se limite au SHA-256 (distribution/build-release.sh:37-44) et a l'attestation GitHub (release.yml:62-66, 149-156).
```

**Impact** — A l'installation, Windows presente Setup.exe comme d'editeur inconnu : SmartScreen/UAC avertit, et l'operateur n'a aucun moyen natif de distinguer l'installeur officiel d'un substitut. La seule preuve d'origine demande une commande hors produit (`gh attestation verify`, citee uniquement dans docs/validation/RELEASE_QUALIFICATION.md:50), que l'utilisateur final n'executera pas. Le depot applique donc a son fournisseur un controle qu'il n'applique pas a son propre livrable.

**Action** — Acquerir un certificat de signature de code (OV ou EV) et signer Setup.exe et le launcher dans build-installer.ps1 via la directive SignTool d'Inno Setup, puis asserter la presence et la validite de la signature dans verify-release-provenance.ps1 comme c'est deja fait pour le compilateur. Tant que le certificat n'existe pas, documenter l'avertissement attendu et la commande de verification dans docs/user/INSTALLATION.md.

### AUD-DEP-06 — validate-d2 ne lit pas le fichier de ratchets et code en dur un plancher de 820/258 tests, contrairement à ce que la règle du dépôt affirme
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 1 — Remettre le dispositif en marche |
| **Débloque** | AUD-TRV-07 (sprint 8) |

**Preuve**

```
scripts/validate-d2.sh:110-115 `if (( TESTS < 820 )); then ... if (( ARCH_TESTS < 258 ))` et scripts/validate-d2.ps1:132-133 `if ($tests -lt 820) ... if ($architectureTests -lt 258)`. `grep -c m21-quality-ratchets scripts/validate-d2.sh scripts/validate-d2.ps1 scripts/validate-m21.sh scripts/validate-m21.ps1` -> 0, 0, 1, 1. Valeurs vivantes : config/m21-quality-ratchets.properties `testsMinimum=3820`, `architectureTestsMinimum=585`. Les litteraux perimes sont verrouilles par morpheus-architecture-tests/src/test/java/com/morpheus/architecture/d2/D2RepositoryHardeningArchitectureTest.java:106-115 (`d2ScriptsKeepCurrentPresenceRatchets` asserte la presence de « 820 » et « 258 »). .claude/rules/governance.md affirme : « scripts/validate-m21.* et scripts/validate-d2.* lisent ce fichier et assertent le nombre de tests, le nombre de tests d'architecture... Valeurs constatees le 09/10/2026 : 3820 / 585 ».
Aggravation relevee en relecture : D2RepositoryHardeningArchitectureTest.java:110 asserte script.contains("820"), qui est satisfait par la sous-chaine de "3820" - le verrou ne detecterait donc meme pas une mise a jour correcte de testsMinimum, et n'epingle pas ce qu'il pretend epingler.
```

**Impact** — Le validateur D2 rend PASS sur un depot qui aurait perdu 3 000 tests et 327 tests d'architecture — 4,6x et 2,3x sous le ratchet reel. Il n'est pas lance par ci.yml, donc le risque porte sur une qualification manuelle : un operateur qui execute validate-d2 pour conclure a un depot sain conclut sur un plancher historique, et le nom de la methode de test (`...KeepCurrentPresenceRatchets`) lui dit que ce sont les valeurs courantes. C'est exactement la derive que .claude/rules/meta.md a ete ecrit pour empecher.

**Action** — Soit faire lire config/m21-quality-ratchets.properties par validate-d2.{sh,ps1} comme le font validate-m21.*, soit renommer les constantes et la methode de test en plancher explicite (`d2ScriptsKeepTheHistoricalPresenceFloor`) et corriger .claude/rules/governance.md, qui decrit aujourd'hui un comportement que le code n'a pas. Dans les deux cas, signaler la divergence regle/code dans la PR.

### AUD-DEP-07 — Le gate CVE a un point de défaillance unique : Dependency-Check 13.0.0 ne peut pas rafraîchir la base NVD sans NVD_API_KEY
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | M |
| **Sprint** | 1 — Remettre le dispositif en marche |

**Preuve**

```
.github/workflows/security.yml:179-184 : « Dependency-Check 13.0.0 upstream bug #8715 treats its built-in blank nvd.api.key as a real key. Until a release containing upstream fix #8716 exists, never attempt the broken anonymous update. » ; :75-86 le chemin `pull_request` echoue en `STALE_DATABASE` des que la sentinelle de rafraichissement de confiance manque ; :84 « on this branch exactly one thing can do that: NVD_API_KEY being configured. Waiting for a schedule will NOT clear it ». Budget de fraicheur : :17 `DEPENDENCY_CHECK_MAX_CACHE_AGE_HOURS: '72'`. Secret unique : :136 `NVD_API_KEY: ${{ secrets.NVD_API_KEY }}`. Risque suivi en RT-13 (docs/architecture/risks/register.md:27).
```

**Impact** — Expiration, revocation ou refus de la cle unique, ou tout changement d'espace de nommage du cache : 72 h plus tard, chaque pull request echoue en STALE_DATABASE et le gate CVE cesse de produire un verdict jusqu'a intervention humaine sur un secret. La conception est fail-closed — correcte — mais le depot n'a aucun second chemin de rafraichissement, et la sortie de cold start documentee passe par une branche encore epinglee a Dependency-Check 12.2.2 qui n'existe pas forcement.

**Action** — Surveiller la publication de Dependency-Check 13.0.1 (correctif amont #8716) et l'adopter des sa sortie pour retablir le rafraichissement anonyme en secours ; en attendant, poser une alerte sur l'annotation `MORPHEUS_DEPENDENCY_CHECK_REFRESH=REFRESH_FAILED` plutot que d'attendre l'echec a 72 h, et documenter la rotation de NVD_API_KEY (procedure, detenteur, echeance) dans docs/developer/COLD_START_RECOVERY.md.

### AUD-DEP-10 — MORPHEUS_OPERATIONAL_LOGS retombe silencieusement sur noop pour toute valeur non reconnue
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 4 — Rendre le produit observable |
| **Débloque** | AUD-TRV-04 (sprint 4) |

**Preuve**

```
morpheus-application/src/main/java/com/morpheus/application/operability/LocalOperationalRuntime.java:36-40 :
```java
return switch (mode.trim().toLowerCase(Locale.ROOT)) {
    case "off", "none" -> OperationalEventSink.noop();
    case "json", "jsonl", "structured" -> new StructuredOperationalEventSink(System.err);
    default -> OperationalEventSink.noop();
};
```
Le sink est construit une fois dans un initialiseur statique (:13), donc la decision n'est jamais rejouee.
```

**Impact** — `MORPHEUS_OPERATIONAL_LOGS=jsno`, `=true`, `=1` ou `=JSONL ` avec un caractere parasite desactive le seul canal d'observabilite du produit sans aucun diagnostic : l'operateur croit avoir active les logs structures et exploite un serveur muet. C'est la degradation silencieuse que .claude/rules/code-style.md interdit explicitement (« Jamais de degradation silencieuse — preferer l'echec explicite au fallback implicite »), sur la variable dont depend toute investigation d'incident.

**Action** — Remplacer la branche `default` par un echec nomme au demarrage (exception portant la valeur refusee et la liste des valeurs acceptees), ou a minima par une ligne sur System.err enoncant que la valeur est inconnue et que l'observabilite reste desactivee. Ajouter un test de reproduction sur une valeur inconnue avant la correction.

### AUD-DEP-12 — pom.xml à 1.2.1 sans tag v1.2.1 : la chaîne de release signée n'a pas tourné depuis 1 588 commits et 2,3 mois
| | |
|---|---|
| **Sévérité** | Moyenne |
| **Axe** | Dépendances |
| **Effort** | M |
| **Sprint** | 2 — Débloquer la release 1.2.1 |
| **Après** | AUD-DEP-01 (sprint 2), AUD-DEP-03 (sprint 2), AUD-DEP-04 (sprint 2), AUD-TST-01 (sprint 1) |
| **Débloque** | AUD-DEP-05 (sprint 14) |

**Preuve**

```
pom.xml:9 `<version>1.2.1</version>`. `git tag -l` -> v1.0.0, v1.1.0, v1.2.0. `git log -1 --format='%ci %h' v1.2.0` -> 2026-07-30 19:12:43 3ad9ebf0 ; HEAD -> 2026-10-09 21:09:30 20cf2e0d ; `git rev-list --count v1.2.0..HEAD` -> 1588. docs/release/RELEASE_NOTES_1.2.1.md existe et pese 50 313 octets. .github/workflows/release.yml:3-6 ne se declenche que sur `push: tags: ['v*']`.
```

**Impact** — Tout le chemin de publication — build jpackage double plateforme, attestation de provenance, checksums, release immuable, verify-release-provenance — n'a pas ete exerce de bout en bout depuis 2,3 mois et 1588 commits, dont 5 migrations de schema et le durcissement complet de 1.2.1. Une regression dans release.yml ou dans build-release.* ne se decouvrira qu'au moment du tag, c'est-a-dire au pire moment. Les notes de release de 50 Kio indiquent une release prete mais non coupee, non un simple decalage de numerotation.

**Action** — Couper v1.2.1 des que les bloqueurs de l'issue #185 sont leves, ou a defaut exercer la chaine complete sur un tag de pre-release jetable (schema de tag a autoriser dans release.yml, ou execution manuelle de distribution/build-release.sh + scripts/verify-release-provenance.sh sur un depot miroir). Ne pas laisser la version du POM avancer sans qu'aucun tag n'ait valide le pipeline.

## Sévérité : Faible

### AUD-ARC-13 — Domaine sans aucun type polymorphe : 59 records et 25 enums, zéro interface, pour 9,4 fois moins de lignes que l'application
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Architecture |
| **Effort** | L |
| **Sprint** | 11 — Trancher l'architecture |
| **Après** | AUD-ARC-03 (sprint 11) |

**Preuve**

```
`grep -rhoP '\b(interface|class|record|enum)\s+\w+' morpheus-domain/src/main/java | awk '{print $1}' | sort | uniq -c` : 59 record, 25 enum, 0 interface, 0 class. 2 624 lignes dans 84 fichiers (plus gros fichier : morpheus-domain/.../constraint/Constraint.java, 93 lignes) contre 24 819 lignes et 341 fichiers dans morpheus-application, dont 55 `*Service.java`. Les invariants sont bien dans le domaine (constructeurs compacts, p.ex. morpheus-domain/.../constraint/ConstraintBlockingPolicy.java:15-36) et quelques predicats existent (ligne 50 `targets(ChangeLifecycleState)`), mais la decision metier est ailleurs : seul morpheus-application/.../orchestration/ChangeOrchestrationStateService.java consomme ConstraintBlockingPolicy pour decider d'un blocage.
```

**Impact** — Ce n'est pas une anemie au sens strict -- les invariants de construction sont dans le domaine -- mais le domaine ne peut porter aucune strategie ni aucune variation de comportement : toute regle metier devient une methode d'un service d'application, ce qui alimente la SCC de 17 paquets (AUD-ARC-03). Le ratio 1 pour 9,4 signifie que le modele est un vocabulaire, pas un modele de comportement.

**Action** — Aucune refonte globale. Au cas par cas, quand un service d'application decide a partir d'un seul type du domaine (le blocage de contrainte en est l'exemple le plus net), descendre la decision sur le type et laisser le service orchestrer. Mesurer le resultat par la reduction des aretes de la SCC, pas par le nombre de lignes deplacees.

### AUD-ARC-14 — Huit constructeurs de compatibilité rétroactive alors que les règles du dépôt les interdisent
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Architecture |
| **Effort** | M |
| **Sprint** | 10 — Dire la vérité dans les règles |

**Preuve**

```
.claude/rules/code-style.md, section JAMAIS : `Jamais de feature flag ni de shim de compatibilite retroactive -- modifier le code directement`. Or : morpheus-domain/.../constraint/Constraint.java:24 `/** Compatibility constructor for pre-M16 providers and callers. */`, morpheus-domain/.../provider/ProviderProbeResult.java:33, morpheus-application/.../orchestration/ChangeOrchestrationState.java:86 (`pre-M16 tests/callers`), .../orchestration/ChangeTransitionEvaluation.java:22, .../query/RequirementSearchPage.java:35, .../store/SnapshotBusinessContent.java:35 (`pre-M15 callers`), .../ingestion/NormalizedProjectContent.java:93, .../discovery/ProjectDiscoveryResult.java:31, .../sync/SourceScanPolicy.java:66.
```

**Impact** — Surface de construction doublee sur neuf types, dont deux du domaine, avec des valeurs par defaut (UNKNOWN, listes vides) qu'un appelant obtient sans les avoir choisies -- ce qui contredit aussi la regle `Jamais de degradation silencieuse` de la meme page. Plusieurs de ces constructeurs sont annonces pour des appelants anterieurs a M15 ou M16, alors que le dernier milestone livre est M28.

**Action** — Verifier pour chacun s'il reste un appelant de production (plusieurs sont declares `for tests/callers`) ; supprimer ceux qui ne servent que les tests en adaptant les tests, et pour les autres remplacer le constructeur par une fabrique nommee qui dit ce qu'elle suppose. Si la pratique doit rester, amender .claude/rules/code-style.md pour la borner, plutot que de laisser la regle etre fausse.

### AUD-QUA-11 — Deux shims de compatibilité @Deprecated(forRemoval = true) vivent depuis deux mois sans plan de retrait
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | 10 — Dire la vérité dans les règles |

**Preuve**

```
morpheus-provider-sdk/src/main/java/com/morpheus/sdk/provider/ProviderPluginActivator.java:22-27 : `/** Unpinned executable activation is retained only for compatibility and always fails closed. */ @Deprecated(forRemoval = true) public ProviderPluginActivation activate(ProviderPluginCandidate candidate) { throw new IllegalArgumentException("provider plugin activation requires a trusted SHA-256 pin"); }` ; meme forme a ProviderPluginService.java:43. Introduits le 2026-08-13 (git log -S "@Deprecated(forRemoval = true)" -- morpheus-provider-sdk/ : 6163788c, cbd2ffc9). Aucun plan de retrait : grep -rn "forRemoval\|until removal" docs/ .claude/ ne retourne rien. Un seul test les epingle : morpheus-provider-sdk/src/test/.../ProviderPluginActivatorTest.java:54.
```

**Impact** — rules/code-style.md interdit explicitement "tout shim de compatibilite retroactive - modifier le code directement". Ces deux methodes sont nommees shim par leur propre Javadoc, forRemoval sans echeance ni ticket, et elargissent la surface publique du SDK plugin de deux entrees qui ne font que lever une exception.

**Action** — Supprimer les deux surcharges non epinglees et le test qui les couvre (la variante a pin obligatoire reste la seule entree), ou - si une compatibilite binaire externe est reellement due - inscrire l'echeance de retrait dans un ADR et la faire asserter par un gate.

### AUD-QUA-12 — 47 @SuppressWarnings dont environ cinq portent une justification écrite, alors que les exclusions SpotBugs en exigent une
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | 4 — Rendre le produit observable |

**Preuve**

```
grep -rn '@SuppressWarnings' --include=*.java . -> 47 sites ; par motif : 26 "unchecked", 9 "java:S1181" (capture de Throwable), 8 "java:S2925" (Thread.sleep), 3 "java:S106", 2 "java:S107", 1 "removal". Cinq seulement portent un commentaire : morpheus-cli/.../MorpheusMain.java ("System.out is the actual MCP wire-protocol stream, not a log write"), ProviderPluginActivatorTest.java:54, et un "java:S1181" commente. Les 26 "unchecked" sont tous des casts de Map non verifies, ex. morpheus-mcp/src/main/java/com/morpheus/mcp/MorpheusPolicyMcpTools.java:305-308 `@SuppressWarnings("unchecked") private static Map<String, Object> castMap(Object value) { return (Map<String, Object>) value; }`. En regard, config/spotbugs-exclude.xml impose par commentaire qu'une entree "carries a comment saying why the finding is wrong for that code" et reste vide a ce titre.
```

**Impact** — Deux poids deux mesures sur la meme decision : supprimer une alerte dans le fichier d'exclusion exige une justification ecrite, la supprimer en ligne n'exige rien. Une revue ne peut pas distinguer un cast sur, prouve par le site d'appel, d'un cast suppose.

**Action** — Exiger par assertion textuelle qu'un @SuppressWarnings dans src/main/java soit suivi d'un commentaire sur la meme ligne ou la ligne precedente, en appliquant la regle deja ecrite pour config/spotbugs-exclude.xml. Remplacer les castMap par une validation instanceof avec rejet nomme aux frontieres MCP et HTTP.

### AUD-QUA-13 — L'observabilité repose sur un singleton statique mutable qui interdit d'activer le parallélisme JUnit
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Qualité |
| **Effort** | M |
| **Sprint** | 4 — Rendre le produit observable |
| **Après** | AUD-TRV-04 (sprint 4) |

**Preuve**

```
morpheus-application/src/main/java/com/morpheus/application/operability/LocalOperationalRuntime.java:12-14 : `private static final OperationalMetrics METRICS = new OperationalMetrics();` - accumulateurs ConcurrentHashMap/LongAdder sans methode reset ni clear (grep "reset\|clear" OperationalMetrics.java -> aucun resultat). Les tests lisent cet etat global : morpheus-store-sqlite/src/test/.../SqliteContentionObservabilityTest.java:139 `return LocalOperationalRuntime.metrics().snapshot().counters();` et assertent une egalite stricte a 0 sur un delta avant/apres (ligne 50 : `assertEquals(0L, delta(before, after, SqliteContentionMetrics.CONTENDED_TRANSACTIONS))`). Aucune configuration de parallelisme n'existe : find . -name junit-platform.properties -> aucun fichier, et le bloc maven-surefire-plugin de pom.xml:146-154 ne declare ni forkCount ni parallel.
```

**Impact** — La mesure delta avant/apres protege correctement l'execution sequentielle actuelle, mais toute activation du parallelisme JUnit - le levier evident sur un reacteur de 3 820+ tests et 86 803 lignes de test - rendrait ces assertions non deterministes. rules/testing.md interdit par ailleurs "tout champ static mutable partage entre tests" ; ce compteur de processus en est un, par construction.

**Action** — Injecter OperationalMetrics par constructeur la ou les tests l'observent (SqliteContentionMetrics prend deja un chemin statique explicite), en gardant LocalOperationalRuntime comme cablage par defaut du processus. Documenter dans un ADR que le parallelisme JUnit est conditionne a cette injection.

### AUD-QUA-14 — SpotBugs et PIT sont épinglés, configurés et documentés mais lancés par aucun workflow
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | 1 — Remettre le dispositif en marche |

**Preuve**

```
pom.xml:87-90 epingle spotbugs.maven.plugin.version=4.10.4.1, spotbugs.version=4.10.4, pitest.maven.plugin.version=1.30.0, pitest.junit5.plugin.version=1.2.3, avec le commentaire pom.xml:80-81 "Audit tooling, active only through the audit-spotbugs and audit-mutation profiles. None of these plugins is bound to a default build". grep -rn "audit-spotbugs\|audit-mutation\|spotbugs\|pitest" .github/workflows/ -> aucun resultat sur les 5 workflows (ci.yml, codeql.yml, nightly.yml, release.yml, security.yml). config/spotbugs-exclude.xml est vide. La couverture analytique automatisee repose donc sur SonarCloud (ci.yml:151-178, sonar.qualitygate.wait=true) et CodeQL seuls.
```

**Impact** — Deux outils d'analyse epingles a une version precise ne tournent que si quelqu'un y pense. Le dernier passage connu (docs/audits/AUDIT_OUTILLE_2026-10-08.md) a releve 195 alertes production SpotBugs et 1 586 mutants survivants PIT : sans execution periodique, ce diagnostic perime et les versions epinglees derivent sans que rien ne le signale. SonarCloud couvre le role de detection de bugs, ce qui limite le risque.

**Action** — Ajouter les deux profils au workflow nightly.yml, en mode rapport non bloquant d'abord, et publier les deux rapports en artefacts pour que la comparaison d'une revision a l'autre soit possible sans relancer l'audit a la main.

### AUD-SEC-05 — La commande Java des pairs MCP est résolue via le PATH plutôt que par un chemin absolu
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | 3 — Fermer les écarts de sécurité |

**Preuve**

```
morpheus-integration-minos/src/main/java/com/morpheus/integration/minos/MinosIntegrationSettings.java:68 et morpheus-integration-nexus/.../NexusIntegrationSettings.java:67 : value(...).orElse("java") ; PATH est explicitement conserve dans l'allowlist d'heritage (morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/BoundedStdioClientTransport.java, SAFE_ENVIRONMENT_KEYS) et le lancement se fait par ProcessBuilder sur cette commande (BoundedStdioClientTransport.java:206-209)
```

**Impact** — Chemin de recherche non fiable (CWE-426) : un PATH manipule dans l'environnement du processus MORPHEUS fait executer un faux binaire 'java' comme pair MCP. Le risque reste borne au compte qui execute MORPHEUS, mais il est evitable.

**Action** — Resoudre par defaut sur le JVM courant (java.home + /bin/java) au lieu du litteral "java", et n'accepter une valeur configuree que si elle designe un fichier regulier executable, chemin absolu, non symbolique.

### AUD-SEC-06 — En-têtes de sécurité incomplets : pas de X-Frame-Options ni de CSP sur les réponses de succès locales, pas de HSTS sur la façade remote
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | 3 — Fermer les écarts de sécurité |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpResponseWriter.java:33-35 (Content-Type, Cache-Control, X-Content-Type-Options seulement) alors que le refus d'origine en pose cinq (LoopbackRequestProtectedHttpServer.java:36-40) ; morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteResponseWriter.java:32-39 pose Cache-Control, X-Content-Type-Options, X-Frame-Options, Referrer-Policy, Content-Security-Policy, X-Request-Id mais pas Strict-Transport-Security
```

**Impact** — Defense en profondeur manquante. Le risque reel est faible (API JSON, nosniff present, serveur local protege par la politique Host/Origin), mais l'asymetrie entre la reponse de refus et la reponse de succes du meme serveur est une incoherence qui finira par etre lue comme une regle.

**Action** — Poser le meme jeu d'en-tetes sur toute reponse locale (ajouter X-Frame-Options: DENY et Content-Security-Policy: default-src 'none'; frame-ancestors 'none' dans MorpheusHttpResponseWriter.sendRaw) et ajouter Strict-Transport-Security sur la facade remote, qui est HTTPS-only par construction.

### AUD-SEC-07 — Aucune règle .gitignore ne protège le fichier d'identités remote ni le keystore TLS, que SECURITY.md interdit de committer
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | 3 — Fermer les écarts de sécurité |

**Preuve**

```
.gitignore (fichier complet : aucune entree *.p12, *.jks, *.pem, *.key, remote-auth*) ; SECURITY.md (section "Remote server security") : "Never commit generated tokens, keystore passwords, or authentication files containing operational credential hashes" ; le nom par defaut est produit en morpheus-cli/src/main/java/com/morpheus/cli/RemoteApiLaunchOptions.java:135 (configDirectory + remote-auth.txt) mais --auth-file et --tls-keystore acceptent n'importe quel chemin, y compris dans l'arbre de travail
```

**Impact** — Une regle textuelle sans garde-fou : un operateur qui lance le serveur avec --auth-file ./remote-auth.txt ou --tls-keystore ./server.p12 depuis le depot peut committer des empreintes de credentials ou une cle privee TLS sans aucun avertissement.

**Action** — Ajouter a .gitignore : remote-auth*.txt, *.p12, *.jks, *.pfx, *.pem, *.key, et asserter la presence de ces lignes dans le gate de durcissement du depot, comme le sont deja les versions pinnees.

### AUD-TST-08 — Le ratchet testsMinimum est dominé par la combinatoire : au moins 40 % des exécutions comptées viennent de 13 méthodes dans 3 fichiers
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | 8 — Fiabiliser les tests |

**Preuve**

```
Comptage sur les fichiers dont le nom satisfait les includes par defaut de Surefire (Test*/*Test/*Tests/*TestCase) : `@Test` en declaration = 2286, `@TestFactory` = 13. config/m21-quality-ratchets.properties:2 `testsMinimum=3820`, donc au minimum 1534 executions (40,2 %) sont des DynamicTest. Les 13 fabriques vivent dans trois fichiers : morpheus-cli/src/test/java/com/morpheus/cli/BlankOptionValueRefusalTest.java:57,93,158, NothingIsCreatedBeforeTheOptionsAreAcceptedTest.java:106,128,148,178, DuplicateOptionRefusalTest.java:65,101,123,141 ; elles sont combinatoires par construction (`invocations().stream().flatMap(... BLANKS.stream().map(...))`, BlankOptionValueRefusalTest.java:59).
```

**Impact** — Le ratchet de presence mesure un nombre d'executions, pas un effort de test. Ajouter une option a une famille CLI peut ajouter des dizaines d'executions et faire croire a une hausse de couverture de test ; a l'inverse, retirer une option fait descendre le compte sous 3820 et casse le build alors que rien n'a regresse, ce qui pousse a relever le ratchet ou a conserver une option morte. Les fabriques elles-memes sont de bonne qualite (matrice d'options reelle, refus avant toute ecriture) — le probleme est l'usage du compte comme ratchet.

**Action** — Declarer le compte dynamique separement du compte statique dans config/m21-quality-ratchets.properties (deux cles, comme pour les deux echelles de couverture) et faire lire les deux par validate-m21, afin qu'une variation de combinatoire CLI ne se confonde plus avec une variation de nombre de tests ecrits.

### AUD-TST-09 — Les quatre ratchets de couverture sont à moins de 0,41 point de leur plafond qualifié : plus de marge sans requalification CI
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | 8 — Fiabiliser les tests |

**Preuve**

```
config/m21-quality-ratchets.properties:7-8 et :14-15 (perModule 0.691 / 0.612, aggregate 0.900 / 0.757) compares aux plafonds lus dans les gates : CoverageQualityGateTest.java:92-93 (0.694341 / 0.615727), AggregateCoverageGateTest.java:74-75 (0.903026 / 0.761089). Ecarts : 0,334 pt (ligne par module), 0,373 pt (branche par module), 0,303 pt (ligne agregee), 0,409 pt (branche agregee). La fenetre est assertee : CoverageQualityGateTest.java:239-241 `assertTrue(ratchet <= cap, ...)`.
```

**Impact** — Les quatre plafonds ont ete requalifies le 09/10/2026 et les quatre ratchets ont ete poses juste en dessous le meme jour. Le mecanisme a donc epuise sa marge : aucune session ne peut plus relever un ratchet sans produire une nouvelle preuve Windows ET Linux exact-head, ce que seul le CI sait faire. C'est le fonctionnement attendu, mais cela veut dire que la prochaine hausse de couverture est bloquee par une procedure de requalification, pas par l'ecriture de tests — a anticiper avant d'annoncer un objectif de couverture pour un prochain milestone.

**Action** — Documenter dans docs/developer/BUILD_AND_TEST.md que la marge residuelle est nulle et que toute hausse passe par une requalification de plafond ; prevoir l'archivage systematique des artefacts m21-integrity-<OS> des deux plateformes a chaque promotion, pour que la preuve soit disponible sans relancer le CI exprès.

### AUD-TST-11 — Nom de test périmé et version de schéma recopiée en dur trois fois dans le test de rejeu des migrations
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | 1 — Remettre le dispositif en marche |

**Preuve**

```
morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteSchemaMigrationTest.java:140 `void migrationReplayIsIdempotentAndLedgerContainsEighteenImmutableEntries()` alors que :154 asserte `assertEquals(20, result.getInt("count"))` ; le meme fichier recopie la version de schema en dur trois fois (:39, :154, :194). Le test voisin fait l'inverse correctement : SqliteMigrationChecksumGoldenTest.java:50 `assertEquals(SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION, CANONICAL_CHECKSUMS.size(), ...)`.
```

**Impact** — Le nom annonce dix-huit entrees la ou le test en exige vingt : un lecteur qui se fie au nom conclura faux, et le nom est la seule documentation d'un test. La valeur 20 recopiee en dur oblige a editer trois endroits a chaque nouvelle migration, alors que .claude/rules/meta.md traite tout chiffre periable comme devant venir de sa source vivante — ici la constante `SUPPORTED_SCHEMA_VERSION`, deja accessible depuis le meme paquet.

**Action** — Renommer la methode en `...ContainsOneLedgerEntryPerSupportedMigration` et remplacer les trois litteraux 20 (:39, :154, :194) par `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`.

### AUD-TST-12 — La documentation M19 renvoie à un point d'entrée validate-m19.cmd qui n'existe pas
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | 1 — Remettre le dispositif en marche |

**Preuve**

```
docs/validation/VALIDATION_M19.md:89 `.\validate-m19.cmd` et docs/roadmap/M19_EXECUTION.md:210 `validate-m19.cmd` ; `ls scripts/*.cmd` ne retourne que `scripts/validate.cmd` (le dispatcher generique).
```

**Impact** — Les deux documents qui expliquent comment rejouer les budgets de performance donnent une commande qui echoue. Combine a AUD-TST-01, cela rend le seul moyen declare de verifier la performance inutilisable tel quel : il faut deviner `scripts\validate.cmd m19` ou appeler le .ps1 directement.

**Action** — Corriger les deux references en `scripts\validate.cmd m19 -Version 1.2.1` (forme reellement supportee par scripts/validate.ps1) et verifier la meme formulation dans les autres documents de milestone.

### AUD-PRF-11 — ORDER BY name COLLATE NOCASE sur saved_views ne peut pas utiliser l'index BINARY qui porte la colonne
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | 7 — Le SQL de détail |

**Preuve**

```
morpheus-store-sqlite/.../SqliteSavedViewStore.java:110-116 `... FROM saved_views WHERE scope_kind = ? AND scope_id = ? ORDER BY name COLLATE NOCASE, id` ; V014__saved_views.sql:13-14 `CREATE INDEX idx_saved_views_scope ON saved_views(scope_kind, scope_id, name, id)` (collation BINARY par defaut)
```

**Impact** — L'index sert le WHERE mais pas le tri : SQLite materialise les lignes du scope et les trie. L'effet reste proportionnel au nombre de vues d'un scope, qui est deja plafonne par le COUNT(*) de la ligne 60. Cout non mesure, raisonnement sur la structure du code et du schema.

**Action** — Declarer la colonne ou l'index en `name COLLATE NOCASE` dans une migration pour que le tri soit servi par l'index, ou accepter le tri et le noter a cote de la requete.

### AUD-PRF-12 — Le filtre de type de relation des liens de traçabilité est appliqué en Java alors que l'index le porte
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | 7 — Le SQL de détail |
| **Après** | AUD-PRF-02 (sprint 6) |

**Preuve**

```
morpheus-store-sqlite/.../SqliteTraceabilityStore.java:145-148 (la clause WHERE ne mentionne pas relation_type) puis :156-157 `if (filter.isEmpty() || filter.contains(link.relationType()))` ; V005__snapshot_traceability_persistence.sql:29-33 `idx_traceability_links_source ON traceability_links(source_kind, source_identity_id, relation_type, link_id)`
```

**Impact** — Tous les liens d'un point d'accroche sont lus et materialises avant d'etre jetes cote Java, alors que la troisieme colonne de l'index existe precisement pour cela. Le surcout est multiplie par chaque pas de BFS de TraceabilityTraversalService (lignes 200 et 205), donc par le defaut signale en AUD-PRF-02. Cout non mesure, raisonnement sur la structure du code et du schema.

**Action** — Ajouter `AND l.relation_type IN (...)` genere a partir du filtre quand il n'est pas vide, en gardant le chemin sans clause pour le filtre vide.

### AUD-PRF-13 — Deux lectures triées en SQL puis retriées en Java
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | 7 — Le SQL de détail |
| **Après** | AUD-PRF-04 (sprint 6) |

**Preuve**

```
morpheus-store-sqlite/.../SqliteSyncStateStore.java:94-98 `ORDER BY archived_at, source_path, reason` puis :116 `return records.stream().sorted().toList();` ; SqlitePolicyPackStore.java:574 `values.stream().sorted().toList()` (sans ORDER BY SQL, avec une raison documentee lignes 555-558 : `at` est un Instant.toString dont la partie fractionnaire ne trie pas lexicographiquement)
```

**Impact** — Pour sync_source_archives le tri SQL est paye puis integralement refait en memoire, et il s'appuie sur l'index idx_sync_source_archives_project_time(project_id, archived_at). Le cas policy_audit est justifie par un commentaire, pas celui des archives. Cout non mesure, raisonnement sur la structure du code.

**Action** — Retirer l'ORDER BY SQL de listArchives si le tri Java est la verite, ou retirer le tri Java si l'ORDER BY suffit ; si `archived_at` souffre du meme probleme de format que `at`, le noter comme dans SqlitePolicyPackStore.

### AUD-PRF-14 — Aucun cache de lecture de document : chaque lecture reparcourt le texte deux fois de plus et consomme le budget
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | 7 — Le SQL de détail |

**Preuve**

```
morpheus-application/src/main/java/com/morpheus/application/read/ProviderIngestionBudget.java:106-191 : `read()` ne memoise rien, puis `long bytes = utf8Bytes(text); long lines = text.lines().count();` (lignes 177-178) et `fileCount++` (ligne 185) ; utf8Bytes est une boucle caractere par caractere (lignes 224-239). Enumeration dupliquee du meme repertoire : OpenSpecChangeMetadataReader.java:126 et OpenSpecRequirementDeltaReader.java:93 appellent chacun listChangeRoots(root.resolve("openspec/changes")), qui fait un Files.list (respectivement lignes 328 et 446) et facture requireAdditionalFiles
```

**Impact** — Chaque document lu subit trois parcours complets (decodage, utf8Bytes, lines().count()). Les trois lecteurs OpenSpec partagent bien une session (OpenSpecProjectContentReader.java:47-50) et lisent des fichiers disjoints, donc aucun fichier n'est relu, mais le repertoire openspec/changes est enumere deux fois et son compte impute deux fois au budget. Cout non mesure, raisonnement sur la structure du code.

**Action** — Deriver le nombre de lignes et d'octets du meme parcours que le decodage, et passer la liste des racines de changes une seule fois depuis OpenSpecProjectContentReader aux deux lecteurs qui en ont besoin.

### AUD-DEP-13 — Version littérale 1.0.4 en dur dans un POM de module, contre la règle du dépôt
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 10 — Dire la vérité dans les règles |

**Preuve**

```
morpheus-mcp-transport/pom.xml:25-29 :
```xml
<dependency>
    <groupId>org.reactivestreams</groupId>
    <artifactId>reactive-streams</artifactId>
    <version>1.0.4</version>
</dependency>
```
Aucune entree correspondante dans le `<dependencyManagement>` du POM racine (pom.xml:93-131). .claude/rules/build.md : « Jamais de version de dependance en dur dans un POM de module ». 1.0.4 est bien la derniere version publiee (mvnrepository, 26 mai 2022), donc il n'y a pas de retard — seulement une entorse au mecanisme.
```

**Impact** — La version de reactive-streams echappe au point de controle unique du depot : une montee de reactor qui changerait l'API reactive-streams attendue ne serait pas vue, et `dependencyConvergence` de l'Enforcer doit arbitrer entre ce litteral et ce que reactor-bom apporte transitivement. Le risque est faible aujourd'hui (artefact gele depuis 2022) mais la regle existe pour que le prochain ajout n'ait pas ce precedent a invoquer.

**Action** — Deplacer org.reactivestreams:reactive-streams dans le `<dependencyManagement>` du POM racine sous une propriete `reactive-streams.version`, et retirer le `<version>` du module — ou laisser reactor-bom le gerer si sa resolution suffit, ce que `./mvnw dependency:tree` tranchera.

### AUD-DEP-14 — sqlite-jdbc et slf4j-nop sont versionnés par propriété dans le module au lieu du dependencyManagement racine
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 10 — Dire la vérité dans les règles |

**Preuve**

```
morpheus-store-sqlite/pom.xml:27-31 `<artifactId>sqlite-jdbc</artifactId><version>${sqlite-jdbc.version}</version>` et morpheus-cli/pom.xml:73-78 `<artifactId>slf4j-nop</artifactId><version>${slf4j.version}</version>`. Le `<dependencyManagement>` racine (pom.xml:93-131) ne gere que les quatre BOM importes et slf4j-api (pom.xml:125-129) — ni sqlite-jdbc, ni slf4j-nop. .claude/rules/build.md : « Declarer toute dependance dans le `<dependencyManagement>` du POM racine, puis la referencer sans version dans le module ».
```

**Impact** — Deux conventions coexistent pour la meme intention. slf4j-api est gere au centre tandis que son binding d'execution slf4j-nop est versionne dans le module : rien ne garantit structurellement que les deux restent sur la meme version, et c'est precisement l'alignement que le commentaire du POM racine (pom.xml:124) dit vouloir tenir. Le risque est contenu par la propriete partagee, pas par le mecanisme.

**Action** — Declarer org.xerial:sqlite-jdbc et org.slf4j:slf4j-nop dans le `<dependencyManagement>` racine, puis retirer les `<version>` des deux modules. Verifier ensuite `./mvnw dependency:analyze` (failOnWarning actif) et le gate textuel D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned, qui exige la presence litterale de `<sqlite-jdbc.version>3.53.4.0</sqlite-jdbc.version>` dans le POM racine.

### AUD-DEP-15 — MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR, qui désigne le répertoire de plugins exécutables du serveur remote, n'est documentée nulle part
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | 2 — Débloquer la release 1.2.1 |

**Preuve**

```
morpheus-cli/src/main/java/com/morpheus/cli/RemoteApiLaunchOptions.java:144 `.or(() -> envPath(environment, "MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR"))`. `git grep -l MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR -- docs` -> aucun resultat ; seule la forme en option est documentee (docs/user/TEAM_REMOTE_SERVER.md:200,380). Les autres variables de la meme famille le sont : MORPHEUS_SERVER_MAX_CONCURRENT et MORPHEUS_SERVER_WORKSPACE_ROOTS dans docs/user/TEAM_REMOTE_SERVER.md, MORPHEUS_BACKUPS_DIR dans docs/user/INSTALLATION.md, MORPHEUS_OPERATIONAL_LOGS dans docs/developer/OPERABILITY.md.
```

**Impact** — La variable qui decide ou le serveur remote cherche des JAR de plugins — code tiers execute sous le compte MORPHEUS apres verification de pin SHA-256 — est configurable sans etre documentee. Un exploitant qui durcit son deploiement en inventoriant la documentation ne sait pas que ce chemin peut etre impose par l'environnement, donc ne l'inclut pas dans sa surface a controler.

**Action** — Ajouter MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR au tableau des variables d'environnement de docs/user/TEAM_REMOTE_SERVER.md a cote de son equivalent `--provider-plugin-dir`, en precisant la precedence entre option et variable telle que RemoteApiLaunchOptions:144 l'implemente.

### AUD-DEP-16 — Aucune sauvegarde automatique avant migration de schéma : la procédure repose sur la discipline de l'opérateur
| | |
|---|---|
| **Sévérité** | Faible |
| **Axe** | Dépendances |
| **Effort** | M |
| **Sprint** | 2 — Débloquer la release 1.2.1 |
| **Après** | AUD-DEP-01 (sprint 2) |

**Preuve**

```
morpheus-store-sqlite/.../SqliteSchemaManager.java:47-57 : `migrate` enveloppe la creation du ledger, le refus d'un schema futur et les 20 scripts dans un seul `SqliteTransactionRunner.runVoid`, sans appel de sauvegarde prealable. Les capacites existent mais sont manuelles : contracts/public-surfaces.tsv:54-56 `server.backup.create`, `server.backup.verify`, `server.restore --confirm`. docs/user/UPGRADE_1_2.md:24 : « Conserver une copie de la base et de la configuration si une sauvegarde supplementaire est souhaitee » — formulation optionnelle.
```

**Impact** — La migration elle-meme est sure (atomique, ledger a checksum, refus d'un historique divergent), donc il n'y a pas de risque de corruption partielle. Le trou est le retour arriere : une fois le schema avance, seule une sauvegarde prise avant l'upgrade permet de revenir au binaire precedent, et rien dans le produit ne garantit qu'elle existe. La formulation « si souhaitee » invite a s'en passer.

**Action** — Prendre automatiquement une sauvegarde (`VACUUM INTO` deja implemente) avant toute migration qui ferait avancer la version de schema, nommee par la version d'origine, et echouer explicitement si elle ne peut pas etre ecrite. A defaut, rendre la sauvegarde imperative dans la procedure d'upgrade et la faire verifier par l'installeur Windows comme etape de son upgrade transactionnel.

## Sévérité : Info

### AUD-QUA-15 — Info : 72 715 lignes de Markdown pour 65 181 lignes de Java main, dont 18 937 d'historique de milestones figé
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
find . -name "*.md" -not -path "./.git/*" | wc -l -> 378 fichiers, 72 715 lignes, contre 65 181 lignes de src/main/java (ratio 1,12). Repartition : docs/ 63 841 l., dont docs/adr/ 23 608 l. pour 109 ADR numerotes (ls docs/adr/0*.md | wc -l -> 109, soit 217 l. par ADR en moyenne), docs/roadmap/ 9 771 l. (39 fichiers) et docs/validation/ 9 166 l. (38 fichiers). Ces deux derniers repertoires sont des releves par milestone qui ne sont plus touches : git log -1 --date=short sur docs/roadmap/M10_EXECUTION.md -> 2026-07-24, M15 et M18 -> 2026-07-26.
```

**Impact** — Observation, pas defaut : le volume est explique par un historique append-only et des ADR longs, et les chiffres perissables des pages actives sont coherents entre eux (README.md:211, docs/README.md:95-99, docs/developer/BUILD_AND_TEST.md:62-66 et docs/governance/ROADMAP.md:87-90 citent tous 3820 / 585 / 90,0 % / 75,7 % / 69,1 % / 61,2 %, identiques a config/m21-quality-ratchets.properties). Le risque reel est la surface a relire : 18 937 lignes d'historique melees aux pages contractuelles dans le meme arbre, ce qui a deja laisse passer l'ecart du constat AUD-QUA-04.

**Action** — Aucune action requise sur le volume. Marquer explicitement docs/roadmap/ et docs/validation/ comme historique non maintenu (un en-tete ou un deplacement sous docs/history/), de sorte que les scans de coherence et les relectures humaines distinguent une page contractuelle d'un releve date.

### AUD-QUA-16 — Info : hygiène de base sans défaut mesurable — ni marqueur de dette, ni trace avalée classique, ni fonction démesurée
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Qualité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
grep -rn "TODO\|FIXME\|HACK\|XXX" --include=*.java . | wc -l -> 1, et l'unique occurrence est un echappement Unicode en Javadoc (morpheus-provider-synthetic/.../SyntheticJsonParser.java:182, "{@code \\uXXXX} escape") : zero marqueur de dette reel. grep "printStackTrace" -> 0. catch a corps vide detecte par regex multiligne sur tout le depot -> 0 (les 21 du constat AUD-QUA-01 portent un commentaire). System.out/System.err en main -> 12 sites sur 4 fichiers, tous justifies (protocole MCP dans MorpheusMcpServer et BoundedStdioServerTransportProvider, sortie CLI dans MorpheusMain, cible du sink structure dans LocalOperationalRuntime.java:38). JUnit 4 (org.junit.Test, @RunWith, @Rule) -> 0. @Disabled -> 0. Methodes de >= 60 lignes en main -> 24 sur 659 fichiers, la plus longue etant un tokeniseur SQL de 134 lignes (morpheus-store-sqlite/.../SqliteSqlStatements.java:17). Plus gros fichier main : 908 lignes (MorpheusCli.java). Profondeur d'imbrication maximale : 8, atteinte une seule fois (morpheus-application/.../sync/LocalSourceInventoryScanner.java:256). Blocs de code commente en main -> 0. Aucun type de src/main/java n'est non reference. Aucun module du reacteur n'est sans tests propres. Suffixes de nommage homogenes (77 *Service, 35 *Store, 25 *Exception, 24 *Result, 17 *Routes, 15 *Policy). Tous les .ps1 du depot sont en ASCII pur, conformement a rules/tooling.md.
```

**Impact** — Aucun. Ce constat delimite le perimetre : les defauts classiques recherches par cet axe sont absents, ce qui situe la dette reelle du depot dans la duplication de socle (AUD-QUA-02, 06, 07, 08, 09), l'absence de journal (AUD-QUA-01) et la gouvernance d'outillage (AUD-QUA-03, 04, 14) plutot que dans la complexite ou l'hygiene du code.

**Action** — Aucune action. Conserver ces mesures comme baseline pour detecter une regression lors du prochain audit.

### AUD-SEC-09 — Info : aucun secret en clair dans le code, les tests, les workflows, les scripts ou l'historique git
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
grep -rniE '(password|passwd|secret|api[_-]?key|token|credential|private[_-]?key)[[:space:]]*=[[:space:]]*"[^"]{6,}"' sur *.java/*.yml/*.yaml/*.properties/*.ps1/*.sh/*.xml/*.json : seules des fixtures de test (RemoteHttpTestSupport.java:23 "changeit", MorpheusRemoteIdentityFileTest.java:48, McpDiagnosticRedactorTest.java:43-44) ; grep -rniE '(ghp_|github_pat_|AKIA[0-9A-Z]{16}|sk-[A-Za-z0-9]{20,}|xox[baprs]-|-----BEGIN)' : aucun resultat ; git log --all -S 'BEGIN PRIVATE KEY' et -S 'BEGIN RSA PRIVATE KEY' : aucun resultat ; find -name '*.p12' -o -name '*.jks' -o -name '*.pem' -o -name '*.key' : aucun fichier
```

**Impact** — Aucun. Aucune rotation de secret n'est requise au titre de cet axe.

**Action** — Rien a corriger. Conserver la discipline : le mot de passe TLS reste lu depuis MORPHEUS_SERVER_TLS_PASSWORD uniquement (TlsKeystorePassword.java:41-46), materialise en char[] et efface par MorpheusMain.java:299 et MorpheusRemoteHttpServerBootstrap.java:160-163.

### AUD-SEC-10 — Info : aucune injection SQL — tout le SQL est paramétré et les rares identifiants dynamiques sont validés par expression régulière
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
182 litteraux SQL dans morpheus-store-sqlite, aucun String.format sur du SQL ; les seules concatenations sont des fragments constants (SqlitePortfolioStore.java:216-254 : referenceSelect() + " WHERE id = ?") ou des noms de colonnes derives d'un booleen interne (SqliteTraceabilityStore.java:140-147 : kindColumn/identityColumn choisis par le drapeau outgoing) ; les noms de table et de colonne reellement variables passent par SqliteSnapshotBusinessContentStore.requireSqlIdentifier (lignes 92-99), appele en SqliteSnapshotBusinessContentWriter.java:295-296 et SqliteSnapshotBusinessContentReader.java:338-339 ; le DSL de requete publique (POST /api/v1/queries/execute) ne genere aucun SQL : grep 'SELECT|executeQuery|java.sql' sur morpheus-application/.../query/ ne retourne rien
```

**Impact** — Aucun.

**Action** — Rien a corriger.

### AUD-SEC-11 — Info : autorisation remote fail-closed à couverture exacte, comparaison de jeton à temps constant et révocation immédiate
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/MorpheusRemoteRoutePolicy.java:120-121 et 146-176 : les 74 entrees de roles doivent nommer exactement les 74 templates et methodes de MorpheusHttpRouteTable, sinon la classe refuse de se charger ; chemin inconnu -> 404, methode non declaree -> 405, donc aucune route GET future n'herite de READ par son verbe ; RemoteIdentityCredentialService.java:70-84 compare chaque identite par MessageDigest.isEqual sans court-circuit, borne le jeton presente a 1024 caracteres et evalue l'expiration a l'instant de la requete ; MorpheusRemoteIdentitySnapshotCache.java:56-83 invalide le cache sur les metadonnees du fichier (fileKey, taille, mtime, creation), et les mutations publient par ATOMIC_MOVE (RemoteIdentityFileStore.java:132), donc une revocation est effective a la requete suivante ; aucune route serveur/identity n'existe dans MorpheusHttpRouteTable, ce qui rend le sentinelle EXPLICITLY_LOCAL_ONLY des 7 capacites server.identity.* vrai par absence et non par convention
```

**Impact** — Aucun. Les sentinelles EXPLICITLY_LOCAL_ONLY et EXPLICITLY_OFFLINE_ONLY de contracts/public-surfaces.tsv sont tenues ; aucune surface declaree non exposee n'est joignable a distance.

**Action** — Rien a corriger.

### AUD-SEC-12 — Info : le serveur local est protégé contre le DNS rebinding et le CSRF navigateur, et l'enveloppe de protection est fail-closed
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/LoopbackRequestPolicy.java:27-45 exige un Host unique de boucle locale, refuse toute Origin non loopback et tout Sec-Fetch-Site cross-site ; LoopbackHostPolicy.requireLoopbackAddress est applique au bind (MorpheusLocalHttpServerBootstrap.java:152) ; RequestProtectedHttpServer.java:53-63 et 98-101 enveloppent les deux surcharges de createContext ET setHandler, et rendent la liste de filtres immuable (ligne 123), donc aucun contexte ne peut etre enregistre sans la protection ; le hop prive du mode remote exige en plus une capability par processus comparee par MessageDigest.isEqual (MorpheusInternalCapability.java:57-65) et le proxy ne transmet a l'amont que Content-Type et Accept, jamais Authorization (MorpheusRemoteProxyTransport.java:74-78)
```

**Impact** — Aucun.

**Action** — Rien a corriger.

### AUD-SEC-13 — Info : aucune injection de commande — lancement par liste d'arguments sans shell, aucun interpréteur dynamique dans les scripts
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
Deux seuls sites de lancement de processus en code de production : BoundedStdioClientTransport.java:206-209 (ProcessBuilder sur une List<String> construite depuis McpPeerLaunch) et ProviderPluginProbeProcess.java:81-83 (ProcessBuilder sur command(...), sortie DISCARD, environnement reduit a une allowlist) ; aucun Runtime.exec hors du shutdown hook de ProviderPluginProbeWorker.java:25 ; grep 'Invoke-Expression|iex |&(' sur *.ps1, 'eval |curl ... | sh' sur *.sh, 'shell=True|os.system|subprocess.call|eval(|exec(' sur *.py : aucun resultat
```

**Impact** — Aucun.

**Action** — Rien a corriger.

### AUD-SEC-14 — Info : confinement des chemins fournis par l'utilisateur — allowlist canonicalisée, refus des liens symboliques, ré-vérification de la racine à chaque requête
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
morpheus-api/src/main/java/com/morpheus/api/AllowedWorkspaceRoots.java:49-78 refuse tout composant '..', refuse une racine ou une cible symbolique, compare le chemin reel (toRealPath) a la racine reelle, rejette tout ancetre symbolique entre la racine et la cible (lignes 95-105) et re-verifie fileKey/owner/creationTime de la racine avant et apres (lignes 117-124) ; le resync re-autorise le workspace stocke a chaque appel (MorpheusProjectSyncApiService.java:132-134) ; le parcours OpenSpec refuse tout lien symbolique et borne profondeur et nombre d'entrees (OpenSpecBoundedTraversal.java:31-52) ; les chemins serveur sont retires des reponses remote (MorpheusProjectRegistryApiService.java:88-102, ServerLocationDisclosure.java:30-50, ProviderPluginViews.java:52-65 allowlist de 13 cles de detail)
```

**Impact** — Aucun. Aucun path traversal ni ecriture hors perimetre n'a ete trouve sur les chemins traces.

**Action** — Rien a corriger.

### AUD-SEC-15 — Info : pas de SSRF — deux clients HTTP sortants seulement, sans suivi de redirection, et la découverte de mise à jour n'est pas exposée au modèle
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
grep 'java.net.http' sur */src/main/java : trois fichiers, dont deux clients construits avec followRedirects(HttpClient.Redirect.NEVER) (MorpheusRemoteProxyTransport.java:41-45, UpdateDiscoveryService.java:44-47) ; le proxy remote ne peut viser que http://127.0.0.1:<port local> (MorpheusRemoteProxyTargetResolver.java:69) ; UpdateDiscoveryService.java:79-85 refuse http: avant tout I/O et n'accepte que file: ou https:, avec corps borne a 64 Kio et deadline sur la lecture ; l'outil MCP check_product_update est un stub qui refuse (MorpheusProductMcpTools.java:46-49), donc l'URI de manifeste n'est choisissable que par l'operateur en CLI ; les integrations MINOS/NEXUS ne parlent que MCP STDIO, sans socket
```

**Impact** — Aucun.

**Action** — Rien a corriger. Si la decouverte de mise a jour devient un jour model-facing ou remote, elle demandera une allowlist d'hotes et un refus des adresses privees.

### AUD-SEC-16 — Info : chaîne d'approvisionnement CI verrouillée, et suppressions de sécurité minimales et justifiées
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Sécurité |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
Les 9 actions utilisees par les 5 workflows sont toutes pinnees par SHA 40 caracteres (grep 'uses:' sur .github/workflows/) ; aucun pull_request_target ni issue_comment ; aucune interpolation de github.event.* dans un bloc run: hors pull_request.head.sha ; permissions contents: read partout (security-events: write uniquement pour CodeQL) ; la cle NVD passe par -DnvdApiKeyEnvironmentVariable=NVD_API_KEY et le chemin pull_request n'y a pas acces (.github/workflows/security.yml) ; CodeQL security-extended tourne sur push et pull_request (.github/workflows/codeql.yml:1-56) ; config/spotbugs-exclude.xml est vide et config/dependency-check-suppressions.xml ne contient que deux suppressions d'association CPE faussement positive, chacune limitee a un packageUrl interne exact et a un seul CPE, avec justification ecrite
```

**Impact** — Aucun. Les deux suppressions sont epinglees sur la version 1.2.1, donc un bump de version les invalide et fait reapparaitre le faux positif plutot que de masquer silencieusement une vraie CVE : le defaut est du bon cote.

**Action** — Rien a corriger. Noter que le profil audit-spotbugs n'est declenche par aucun workflow (verifie sur les 5 fichiers de .github/workflows/) : c'est un choix assume d'audit manuel, couvert sur les PR par CodeQL et SonarQube Cloud.

### AUD-TST-13 — Info : hygiène de test remarquable — aucun test désactivé, aucun JUnit 4, aucun état statique mutable, aucun port fixe
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`grep -rn "@Disabled\|@Ignore" --include=*.java .` (hors target/) => aucune occurrence. `grep -rn "org.junit.Test\|@RunWith\|@Rule\b\|junit.framework" --include=*.java morpheus-*/src` => aucune occurrence. `grep -rnE "^\s+(private |protected |public )?static [A-Za-z<>,.\[\] ]+ [a-zA-Z_][A-Za-z0-9_]*\s*(=|;)" --include=*.java morpheus-*/src/test/java | grep -v "static final"` => aucune occurrence. `find . -name junit-platform.properties -not -path '*/target/*'` => aucun fichier (pas d'execution parallele, donc pas de risque d'ordre). Tous les serveurs de test se lient sur un port ephemere : `MorpheusHttpServer.start(database, "127.0.0.1", 0)` (ex. morpheus-api/src/test/java/com/morpheus/api/MorpheusPortfolioApiContractTest.java:22) ; les `InetSocketAddress("127.0.0.1", 12345)` trouves sont des doublures sans liaison (MorpheusHttpResponseWriterTest.java:118). Les seules suspensions d'execution sont encadrees par un test dedie : morpheus-architecture-tests/src/test/java/com/morpheus/architecture/BoundedWaitOwnershipTest.java:37-53, qui impose un unique helper d'attente bornee par module et refuse qu'un helper roule son propre deadline. Les compteurs globaux de SqliteConnectionScope sont consommes en delta et non en valeur absolue (morpheus-api/src/test/java/com/morpheus/api/ApiRuntimeSqliteSessionTest.java:32-37).
```

**Impact** — Aucune action requise. C'est le socle qui rend les 3820 executions reproductibles : les `assumeTrue` recenses (22 sites) portent tous sur la creation de liens symboliques ou sur une ACL Windows, c'est-a-dire sur une capacite de plateforme reellement absente, jamais sur un test que l'on renonce a faire passer.

**Action** — Rien a corriger. A preserver : la regle « jamais @Disabled sans commentaire + ticket » n'a jamais eu a s'appliquer, et BoundedWaitOwnershipTest est le patron a reutiliser pour ancrer d'autres regles de test (cf. AUD-TST-03).

### AUD-TST-14 — Info : les six chiffres normatifs sont identiques dans les onze destinations documentaires vérifiées
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`grep -rnoE "3820|585|69[.,]1|61[.,]2|90[.,]0|75[.,]7"` sur README.md:211, docs/README.md:95-100, docs/developer/BUILD_AND_TEST.md:62-67 et :95,:99, docs/developer/PRODUCTION_INTEGRITY.md:26-34, docs/developer/README.md:71-76, docs/governance/DOCUMENTATION_STATUS.md:129-134, docs/governance/ROADMAP.md:85-88, docs/architecture/arc42/11-risques-dette.md:33 et :67-72, docs/architecture/risks/register.md:185 et :459-464, scripts/README.md:38-43, distribution/README.md:153-158 : les six valeurs correspondent exactement a config/m21-quality-ratchets.properties (3820, 585, 0.900, 0.757, 0.691, 0.612).
```

**Impact** — La derive de 2026-08-31 qui a motive .claude/rules/meta.md (trois chiffres differents pour le meme seuil) n'est pas reapparue. RepositoryDocumentationCoherenceTest et ProductionIntegrityContractTest tiennent effectivement les onze destinations.

**Action** — Rien a corriger.

### AUD-TST-15 — Info : les vingt migrations SQLite sont épinglées par checksum golden, et le compte est lié à la constante de schéma
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
morpheus-store-sqlite/src/test/java/com/morpheus/store/sqlite/SqliteMigrationChecksumGoldenTest.java:23-44 epingle les vingt SHA-256 en litteraux (et explique en javadoc pourquoi ils ne sont pas recalcules depuis les fichiers), :49-52 `everyMigrationIsPinned` compare la taille de la table a `SqliteSchemaManager.SUPPORTED_SCHEMA_VERSION`. `ls morpheus-store-sqlite/src/main/resources/db/migration/ | wc -l` -> 20. Complements : SqliteFutureSchemaCompatibilityTest (refus d'une base plus recente avant tout I/O), SqliteMigrationAtomicityTest (echec a mi-parcours = version precedente conservee), SqliteMigrationLineEndingCompatibilityTest (LF/CRLF), SqliteSchemaMigrationTest.java:199 (historique modifie rejete).
```

**Impact** — Aucune action requise. C'est la partie la plus solide du dispositif de test sur le chemin a risque de perte de donnees : ajouter une migration sans epingler son checksum casse le build, et modifier une migration deja appliquee est detecte a l'ouverture de la base.

**Action** — Rien a corriger ; etendre ce niveau d'exigence au chemin d'upgrade lui-meme (cf. AUD-TST-10).

### AUD-TST-16 — Info : les chemins d'erreur HTTP sont assertés par code de statut sur plus de la moitié des assertions
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`grep -rhoE "assertEquals\(([0-9]{3})," --include=*.java morpheus-api/src/test | sort | uniq -c | sort -rn` -> 331 assertions au total, dont 153 sur un 2xx (200:128, 201:22, 204:3) et 176 sur un code d'erreur (400:71, 404:44, 405:27, 409:10, 403:7, 415:5, 401:4, 502:3, 503:2, 429:2, 413:1). Le routage est de plus verifie contre un serveur reellement demarre, en-tete `Allow` comprise, par PublicHttpRouteConvergenceTest (morpheus-architecture-tests/src/test/java/com/morpheus/api/).
```

**Impact** — Aucune action requise. Les rejets de la frontiere HTTP (corps trop gros, methode non permise, conflit CAS, rate limit, authentification) sont couverts explicitement, pas seulement les chemins heureux.

**Action** — Rien a corriger.

### AUD-TST-17 — Info : les tests du gate de couverture différentielle sont le seul outillage exécutable sans Maven, et ils passent
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Tests |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`python3 -m unittest discover -s scripts/tests -v` execute dans cette session : 7 tests, `Ran 7 tests in 0.297s / OK`, couvrant notamment `test_an_unsafe_path_in_the_diff_is_refused`, `test_a_changed_source_absent_from_every_report_fails` et `test_the_aggregate_report_is_preferred_and_mapped_back_to_its_module`. `wc -l` : scripts/check-diff-coverage.py = 329 lignes, scripts/tests/test_check_diff_coverage.py = 142 lignes.
```

**Impact** — Aucune action requise au titre de ce constat. L'absence de test sur les 43 autres scripts est sortie en constat propre : AUD-TRV-07.

**Action** — Rien a corriger ; le patron scripts/tests/ est reutilisable pour couvrir les validateurs .sh (au moins leur analyse d'arguments et leur classification d'echec).

### AUD-PRF-15 — Info : les bornes annoncées par le transport MCP sont effectivement appliquées
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
morpheus-mcp-transport/src/main/java/com/morpheus/integration/mcp/BoundedStdioServerTransportProvider.java:55-56 `DEFAULT_MAX_FRAME_BYTES = 1024 * 1024`, `DEFAULT_MAX_PENDING_MESSAGES = 64`, :248 `new ArrayBlockingQueue<>(maxPendingMessages)`, :271 refus explicite sur `offer` ; BoundedStdioClientTransport.java:135-137 `onBackpressureBuffer(new ArrayBlockingQueue<>(maxPendingMessages))`, :338 et :343 fail-closed sur depassement ; PeerOperationDeadline.java:24-35 MAX_REQUEST_TIMEOUT 120 s, MAX_SEQUENTIAL_REQUESTS 4, START_UP_ALLOWANCE et CLOSE_ALLOWANCE 15 s
```

**Impact** — Aucun. Le mot Bounded correspond a des bornes reelles : taille de trame, capacite de file et deadlines sont toutes verifiees a l'execution avec un refus explicite, pas un buffer illimite.

**Action** — Rien a corriger. A conserver comme reference pour les autres surfaces : c'est le seul endroit audite ou une borne annoncee est systematiquement appliquee et testable.

### AUD-PRF-16 — Info : hygiène des ressources JDBC sans faille détectée, et écritures du projecteur correctement batchées
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`grep -rn "executeQuery()" --include=*.java */src/main | grep -v "try (|ResultSet result = statement.executeQuery|; ResultSet"` retourne zero ligne ; aucun `.close()` manuel sur Connection, Statement ou ResultSet hors AutoCloseable ; morpheus-store-sqlite/.../SqliteSnapshotBusinessContentWriter.java:73-75, 94-96, 115-117, 144-146, 188-190, 222-224, 243-245, 266-268, 282-285, 304-306 (dix paires addBatch/executeBatch) ; SqliteSyncStateStore.java:312-323 et 327-344
```

**Impact** — Aucun. Toutes les ressources JDBC sont en try-with-resources, et le chemin d'ecriture du projecteur de contenu metier batche ses insertions.

**Action** — Rien a corriger. L'ecart a souligner est interne au meme module : le writer batche, le reader (AUD-PRF-01) et SqliteTraceabilityStore.putLinks (AUD-PRF-06) ne le font pas.

### AUD-PRF-17 — Info : les parcours d'arborescence fournis par l'utilisateur sont bornés explicitement
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Performance |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
morpheus-application/.../sync/LocalSourceInventoryScanner.java:169 utilise Integer.MAX_VALUE comme profondeur walkFileTree mais verifie `depth > policy.maxDepth()` aux lignes 189 et 224, et reserve chaque repertoire et chaque fichier aupres de ScanBudget (lignes 217, 232, 361, 374) ; SourceScanPolicy.java:18-22 maxDepth 128, maxDirectories 50 000, maxFiles 50 000, maxFileBytes 64 MiB, maxAggregateBytes 2 GiB ; OpenSpecBoundedTraversal.java:37 `Files.walk(normalizedRoot, maxDepth + 1)` dans un try-with-resources, et les enumerations OpenSpec appliquent `.limit(budget.remainingFiles() + 1)` (OpenSpecRequirementDeltaReader.java:450 et 469, OpenSpecChangeMetadataReader.java:332)
```

**Impact** — Aucun. Un depot arbitrairement profond ou volumineux fait echouer le scan avec un code de rejet nomme au lieu de consommer la memoire ou le temps sans limite.

**Action** — Rien a corriger. Le `Integer.MAX_VALUE` de la ligne 169 merite neanmoins un commentaire : il se lit comme une absence de borne alors que la borne est deux lignes plus bas.

### AUD-DEP-17 — Info : toutes les dépendances et plugins sauf reactor sont à la dernière version publiée à ce jour
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
Versions lues dans pom.xml:49-65,87-89 confrontees a mvnrepository (consulte le 09/10/2026) : jackson-databind 3.2.3 = dernier (23 sept. 2026) ; sqlite-jdbc 3.53.4.0 = dernier (27 aout 2026) ; mcp-core 2.0.1 = dernier (20 aout 2026) ; slf4j 2.0.20 = dernier (23 sept. 2026) ; junit-bom 6.1.3 = dernier (8 aout 2026) ; archunit 1.5.1 = dernier (26 sept. 2026) ; jacoco 0.8.15 = dernier (6 juin 2026) ; dependency-check-maven 13.0.0 = dernier (3 aout 2026) ; cyclonedx 2.9.3 = dernier (31 juil. 2026) ; maven-compiler 3.16.0, surefire 3.6.0, enforcer 3.6.3, jar 3.5.1, shade 3.6.2, dependency 3.11.0, spotbugs-maven 4.10.4.1 = tous derniers ; reactive-streams 1.0.4 = dernier (2022) ; Maven 3.10.0 = dernier (2 oct. 2026). Seule exception : reactor-bom (voir AUD-DEP-02).
Reserve de relecture : la confrontation a mvnrepository n'est pas rejouable dans cet environnement (Maven Central refuse par le proxy). La fraicheur des versions lues dans pom.xml est verifiable ; leur statut de derniere version publiee ne l'est pas ici.
```

**Impact** — Aucun. Constat positif : la surface de dependances tierces d'execution est minimale (6 artefacts) et tenue a jour, ce qui reduit mecaniquement l'exposition CVE. Aucun scan NVD n'a ete execute dans cette session, donc l'absence de CVE n'est pas prouvee — seule la fraicheur l'est.

**Action** — Rien a corriger. Conserver la cadence dependabot hebdomadaire et faire tourner `./mvnw -Pd2-security ... dependency-check-maven:aggregate` sur une machine ayant acces a la NVD pour convertir cette fraicheur en absence de CVE prouvee.

### AUD-DEP-18 — Info : le build est entièrement épinglé et reproductible — aucune version flottante, aucun SNAPSHOT, aucun dépôt tiers, wrapper vérifié par empreinte
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`grep -rnE '<version>\s*(LATEST|RELEASE|\[|\()' pom.xml morpheus-*/pom.xml` ne renvoie que les deux regles Enforcer (pom.xml:185 `[3.9.16,4.0.0)`, :188 `[21,22)`), usage correct d'un intervalle. Aucun SNAPSHOT, aucun `<repositories>`/`<pluginRepositories>`. pom.xml:38 `<project.build.outputTimestamp>2026-01-01T00:00:00Z</project.build.outputTimestamp>` ; pom.xml:39 `<maven.compiler.release>21</maven.compiler.release>`, coherent avec `java-version: '21'` dans les cinq workflows et avec `--add-modules jdk.httpserver,java.sql,java.net.http` de build-portable.sh:104. .mvn/wrapper/maven-wrapper.properties epingle 3.10.0 avec `distributionSha256Sum=1f6d9909...` et mvnw:219-236 echoue si aucun outil de verification n'est disponible. Heritage propre : un seul module surcharge le compilateur (morpheus-api/pom.xml:61-70, pour jdk.httpserver) et un seul surefire (morpheus-coverage-report/pom.xml:83) ; 18 `<module>` declares pour 18 repertoires.
```

**Impact** — Aucun. Constat positif : le build est rejouable a l'identique, la chaine d'outils ne peut pas deriver silencieusement, et les 18 modules n'ont pas de configuration dupliquee a maintenir en parallele.

**Action** — Rien a corriger.

### AUD-DEP-19 — Info : chaîne CI/CD verrouillée — actions toutes épinglées par SHA 40, permissions minimales, secrets jamais exposés à du code non fusionné
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`grep -rn 'uses:' .github/workflows/ | grep -v '@[0-9a-f]\{40\}'` ne renvoie qu'une ligne de commentaire (security.yml:106) : les 18 `uses:` reels sont tous epingles par SHA avec le tag en commentaire. `permissions: contents: read` au niveau workflow dans les cinq fichiers (ci.yml:9-10, security.yml:13-14, release.yml:8-9, nightly.yml:23-24, codeql.yml:12-14), elargi par job uniquement la ou c'est necessaire (release.yml:20-23 id-token/attestations, :185-186 contents:write pour la publication). Aucun `pull_request_target`, aucun `workflow_run`, aucun `|| true`. SONAR_TOKEN n'est atteignable que sur `push` vers main (ci.yml:127) et dans un workflow sans declencheur pull_request (nightly.yml:16-21,41), raisonnement ecrit en ci.yml:109-125. Les trois `continue-on-error` sont suivis d'une etape de classification qui echoue sur un vrai echec (ci.yml:152 + :180-185 -> classify-sonar-quality-gate.sh:93-98 `exit 1`) ou documentes comme advisory (nightly.yml:116,159). scripts/classify-dependency-check-failure.sh:54 `exit 1` sur tous les chemins. Aucun litteral de type secret trouve dans .github/, scripts/, distribution/, integration/, config/.
```

**Impact** — Aucun. Constat positif : la chaine d'approvisionnement du build resiste a la compromission d'un tag d'action amont, et le token durable SONAR_TOKEN ne partage jamais son environnement avec du code d'une PR — y compris depuis une branche interne, cas que la plupart des pipelines laissent ouvert.

**Action** — Rien a corriger. Verifier que la regle `codeql-action` de .github/dependabot.yml:24-27 reste le seul groupe necessaire quand d'autres actions acquierent des contraintes de version croisees.

### AUD-DEP-20 — Info : aucun Dockerfile ni compose dans le dépôt, conforme à la revendication « sans Docker »
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`find . -iname 'Dockerfile*' -o -iname '*compose*.y*ml' -o -iname '*.dockerfile' | grep -v '/\.git/'` -> aucun resultat. La distribution est un app-image jpackage autoportant (distribution/build-portable.sh:96-107, runtime Java embarque, `--jlink-options --strip-debug --no-man-pages --no-header-files`) plus un installeur Inno Setup (distribution/windows/MORPHEUS.iss:35,39 `DefaultDirName={localappdata}\Programs\MORPHEUS`, `PrivilegesRequired=lowest`). Conforme a .claude/rules/build.md (ADR-0027, ADR-0061) et a l'interdit textuel M28.
```

**Impact** — Aucun. Constat positif : pas de conteneur signifie pas de base image a patcher, et l'installation sans elevation de privileges limite la surface d'un installeur compromis au profil de l'utilisateur. Le revers assume est qu'il n'existe aucun chemin de deploiement orchestre.

**Action** — Rien a corriger.

### AUD-DEP-21 — Info : hygiène du dépôt — aucun artefact de build suivi par git, suppressions CVE et dérogations dependency:analyze toutes justifiées
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`git ls-files | grep -Ei '^(dist|validation-output|Claude outputs)/'` -> aucun resultat ; .gitignore couvre `**/target/`, `dist/`, `validation-output/`, `*.db`, `*.sqlite*`, `*.log`, `.claude/settings.local.json`. config/dependency-check-suppressions.xml ne porte que deux regles, chacune limitee a un `packageUrl` interne exact et a un seul CPE faux positif (sqlite:sqlite sur morpheus-store-sqlite, github:cli sur morpheus-cli), avec `failBuildOnUnusedSuppressionRule=true` (pom.xml:263). config/spotbugs-exclude.xml est vide avec un commentaire expliquant la politique d'entree. Les six derogations `<usedDependency>` (morpheus-architecture-tests/pom.xml:48, morpheus-cli/pom.xml:94, morpheus-integration-minos/pom.xml:59, morpheus-integration-nexus/pom.xml:54, morpheus-mcp/pom.xml:65, morpheus-store-sqlite/pom.xml:47) portent chacune un commentaire nommant le mecanisme SPI concerne.
```

**Impact** — Aucun. Constat positif : `failOnWarning=true` sur dependency:analyze reste credible parce qu'aucune derogation n'est large, et `failBuildOnUnusedSuppressionRule=true` garantit qu'une suppression devenue inutile casse le build au lieu de masquer une CVE future. A noter : les deux suppressions sont indexees sur `@1.2.1`, donc un bump de version les rendra inutilisees et fera echouer le scan — couplage fail-closed, mais couplage.

**Action** — Rien a corriger. Lors du bump vers 1.2.2, mettre a jour les deux `packageUrl` de config/dependency-check-suppressions.xml dans le meme commit, sinon le scan OWASP echoue sur une suppression inutilisee.

### AUD-DEP-22 — Info : mesure de stabilité de ci.yml — 29 commits sur tout l'historique disponible, un seul sur les 200 derniers
| | |
|---|---|
| **Sévérité** | Info |
| **Axe** | Dépendances |
| **Effort** | S |
| **Sprint** | aucun — constat *Info*, aucune action requise |

**Preuve**

```
`git rev-list --count HEAD` -> 3970 (depot shallow). `git rev-list --count HEAD -- .github/workflows/ci.yml` -> 29 ; security.yml 29 ; codeql.yml 10 ; release.yml 6 ; nightly.yml 2 ; pom.xml 24. Intersection des 200 derniers commits (fenetre 2026-09-25 -> 2026-10-09) avec l'historique de ci.yml -> 1 commit. `git log --format='%s' -- .github/workflows/ci.yml | grep -ciE 'revert|fix|hotfix|repair'` -> 3. Les dix derniers changements portent des intitules de decision explicite (`ci: spend the per-pull-request budget where it informs`, `ci: simplify to a single full M28 gate after measuring its real cost`), pas de reparation.
```

**Impact** — Aucun. Le chiffre de 43 modifications sur 200 commits annonce dans le contexte partage n'est pas reproductible sur ce clone : ci.yml a ete touche 29 fois sur 3 970 commits et une seule fois dans les deux dernieres semaines. Le pipeline s'est stabilise, et l'essentiel de son historique est de l'ajustement motive (deplacement de lanes vers nightly.yml, separation des echelles de couverture), non de la correction en urgence.

**Action** — Rien a corriger. Si un suivi de churn est voulu, le mesurer sur une fenetre datee plutot que sur un nombre de commits : le depot est largement constitue de commits de merge, ce qui rend un compte en commits peu comparable d'une mesure a l'autre.

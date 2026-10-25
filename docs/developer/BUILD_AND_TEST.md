# Build, tests et validation

Ce guide décrit l’environnement de développement et les gates actifs sur la baseline corrective **MORPHEUS 1.2.1**. La dernière release publiée est `v1.2.1` (16 octobre 2026), qualifiée par les scripts de relecture publique.

## Toolchain

```text
Java 21 uniquement (>= 21 et < 22)
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
Maven Wrapper = 3.10.0 + distribution SHA-256
Maven Enforcer = dependency convergence obligatoire
```

## Reactor Maven

Le reactor déclare les modules ci-dessous dans le bloc `<modules>` du `pom.xml` racine, dans son ordre. Le nombre ne s'écrit pas ici : il se compte dans le POM.

```text
morpheus-domain
morpheus-application
morpheus-provider-sdk
morpheus-provider-testkit
morpheus-provider-reference
morpheus-provider-openspec
morpheus-provider-markdown
morpheus-provider-synthetic
morpheus-store-memory
morpheus-store-sqlite
morpheus-mcp-transport
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
morpheus-coverage-report
```

Les POMs actifs du reactor, parent compris, portent tous la même version MORPHEUS `1.2.1`. Les preuves de release historiques `1.2.0` ne sont pas réécrites.

## Gate Maven canonique

Windows :

```powershell
.\mvnw.cmd clean verify
```

Linux :

```bash
./mvnw clean verify
```

`clean test` est utile pour le diagnostic mais n’est pas la qualification finale : les tests d’architecture dépendent des JARs et rapports produits à `package`/`verify`.

## Gate durable M21

Le workflow `MORPHEUS CI` exécute le même gate exact-head sur Windows et Ubuntu pour les pull requests ainsi que sur les pushes `main` et `develop`.

```text
baseline Surefire écrits       >= 2600
baseline Surefire générés     >= 1574
baseline architecture          >= 762
JaCoCo aggregate line ratchet     >= 90.0%
JaCoCo aggregate branch ratchet   >= 75.7%
JaCoCo per-module line ratchet    >= 69.1%
JaCoCo per-module branch ratchet  >= 61.2%
D2 absolute line floor         40%
D2 absolute branch floor       35%
maven dependency analyze       failOnWarning=true
maven dependency convergence   required
CycloneDX SBOM                  JSON + XML
product/package version         1.2.1
```

La source normative des six ratchets M21 est `config/m21-quality-ratchets.properties`. Les deux échelles de couverture y sont déclarées séparément : `aggregate*` pour la mesure canonique (`morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml`, qui fusionne aussi l'exécution cross-module des tests d'architecture) et `perModule*` pour la somme des rapports JaCoCo de chaque module pris isolément. `AggregateCoverageGateTest` et les deux validateurs `validate-m21.*` consomment la première, `CoverageQualityGateTest` la seconde ; aucun gate ne lit les clés de l'autre échelle, et `CoverageScaleSeparationTest` fait échouer le build si l'un d'eux s'y essaie. Les deux échelles portent sur la même population de modules — tout module du réacteur qui porte une classe sous `src/main/java`, outillage de vérification compris (`morpheus-store-memory`, `morpheus-provider-synthetic`, `morpheus-provider-testkit`, `morpheus-provider-reference`) — et ne diffèrent que par les exécutions autorisées à créditer une ligne : `AggregateCoverageGateTest` dérive cette population du POM racine et refuse un rapport agrégé qui en mesure une autre.

Les floors sont des ratchets de présence : ils ne sont pas abaissés automatiquement, et toute hausse ultérieure doit être fondée sur une qualification exacte du même SHA sous Windows et Linux.

## Couverture différentielle des pull requests

Sur les PR, Linux exécute `scripts/check-diff-coverage.py` sur les lignes Java de production ajoutées ou modifiées. Le gate exige simultanément :

```text
changed executable line coverage   >= 80%
changed branch coverage             >= 70%
```

La couverture de branches ne porte que sur les branches JaCoCo situées sur des lignes exécutables changées. Une PR sans branche modifiée n'est donc pas pénalisée artificiellement. Le résultat est écrit dans `validation-output/m21/diff-coverage.txt` et archivé avec les preuves CI.

Cette garde différentielle complète le ratchet global : elle évite qu'une nouvelle logique conditionnelle critique soit ajoutée avec une simple couverture de ligne nominale.

## Qualité et ratchet JaCoCo

La baseline courante est verrouillée à **90,0% lignes / 75,7% branches** sur l'échelle agrégée et **69,1% lignes / 61,2% branches** par module. Les deux chiffres mesurent des populations de lignes différentes : les comparer entre eux n'a pas de sens, et comparer l'un au seuil de l'autre est précisément le défaut que la séparation des clés supprime.

Règle d’évolution :

1. une baisse sous 90,0% lignes ou 75,7% branches agrégées, ou sous 69,1% lignes ou 61,2% branches par module, fait échouer le gate M21 ;
2. les floors D2 40% / 35% restent des minima absolus et ne peuvent jamais affaiblir le ratchet ;
3. une amélioration de couverture ne relève le ratchet qu’après qualification du même SHA exact sur Windows et Linux ;
4. le ratchet n’est jamais abaissé automatiquement ; une baisse nécessite une décision d’audit explicite et motivée ;
5. les compteurs de tests sont eux aussi des ratchets de présence, pas une mesure de qualité autonome ;
6. la couverture ne justifie pas des tests artificiels : les tests doivent conserver une valeur fonctionnelle, de contrat, de sécurité ou d’architecture indépendante du chiffre.

### Relever un ratchet, ou relever un plafond

Ce sont deux gestes différents, et le premier ne donne aucun droit sur le second.

- Un **ratchet** (`config/m21-quality-ratchets.properties`) se relève par décision, tant qu'il reste sous le plafond qualifié **de sa propre échelle** et garde la distance que la règle exige : une dispersion maximale observée entre plateformes, multipliée par trois, comptée en lignes et en branches — jamais en points de pourcentage. Une session qui ne dispose que d'une plateforme peut le faire.
- Un **plafond** (`PER_MODULE_QUALIFIED_*_RATIO` dans `CoverageQualityGateTest`, `AGGREGATE_QUALIFIED_*_RATIO` dans `AggregateCoverageGateTest`) ne se relève que par une requalification : une mesure exact-head de l'échelle sur Windows **et** sur Linux, au moins deux runs par plateforme, qualifiée sur la plus basse. Les résumés de ces runs remplacent ceux de `config/coverage-qualification/` dans le même changement, et `CoverageQualificationEvidenceTest` exige que chaque plafond soit égal au plus bas ratio de son échelle dans ces fichiers.

Où lire ce qu'il reste : la preuve de chaque gate (`m21-per-module-coverage-summary.txt`, `m21-aggregate-coverage-summary.txt`, décrites ci-dessous) affiche côte à côte la mesure (`lineRatio`, `branchRatio`), le plafond (`qualifiedLineBaseline`, `qualifiedBranchBaseline`) et le ratchet actif (`lineRatchet`, `branchRatchet`). La distance se calcule sur ces valeurs et sur les compteurs de lignes et de branches du même fichier ; elle ne s'écrit pas dans cette page.

Chaque gate écrit sa propre preuve, dont la première ligne déclare l'échelle mesurée : `CoverageQualityGateTest` écrit `morpheus-architecture-tests/target/m21-per-module-coverage-summary.txt` (`coverageScope=per-module`) et `AggregateCoverageGateTest` écrit `morpheus-architecture-tests/target/m21-aggregate-coverage-summary.txt` (`coverageScope=aggregate`). Les deux fichiers portent la couverture observée, la baseline qualifiée, le ratchet actif et les minima D2 ; la preuve agrégée nomme en outre les modules qu'elle a mesurés (`population=`). Les validateurs refusent de conclure sur une preuve dont l'échelle n'est pas celle qu'ils attendent.

## Frontière HTTP des corps de requête

Toutes les routes HTTP locales lisent leur corps par `MorpheusHttpRequestDecoder`, celui du serveur : les treize routeurs construits par `MorpheusHttpServer` le reçoivent à la construction, les contextes Query/Saved Views/Export, Policy, Policy Management et Reasoning le reçoivent à `register(...)`. Le décodeur lit par la primitive partagée `HttpRequestBodyReader`, qui délègue à `TimedBoundedInputReader`. La politique active est :

```text
request body max size     65 536 bytes
request body read timeout 15 seconds
```

Aucun routeur `*HttpRoutes` ne lit `getRequestBody()`, n'utilise `HttpRequestBodyReader` ni ne consulte le `Content-Type` lui-même. `RepositoryDocumentationCoherenceTest`, `D2RepositoryHardeningArchitectureTest` et `HttpRoutesTransportBoundaryArchitectureTest` verrouillent cette règle ; `HttpRequestBodyReaderTest` couvre succès, dépassement de taille, timeout et erreur I/O, et `ExtensionRoutesRequestBoundaryParityTest` fige octet pour octet les réponses d'échec des quatre contextes d'extension.

## SCA / dépendances

OWASP Dependency-Check est épinglé à `13.0.0` dans les profils Maven `d2-security` et
`d2-security-tests`. La version exacte est assertée par
`D2RepositoryHardeningArchitectureTest#dependencyAndQualityBaselineIsPinned` — la relire dans
`pom.xml` avant de la citer.

Deux scans, deux périmètres différents :

```text
d2-security          ce que MORPHEUS distribue        test scope ignoré
d2-security-tests    ce que le build exécute          test scope inclus
```

Le SBOM produit (`cyclonedx`, `includeTestScope=false`) et `d2-security` excluent délibérément
le scope `test` : un artefact JUnit ou ArchUnit n'est jamais installé chez un opérateur, et le
faire figurer comme composant du produit décrirait mal ce qui est livré. Mais ces artefacts
s'exécutent sur chaque machine qui construit MORPHEUS, donc `d2-security-tests` les regarde,
avec le même seuil bloquant. Il réutilise la base Dependency-Check déjà préparée par le scan
produit, donc ce second regard ne coûte aucun téléchargement NVD supplémentaire.

Commandes :

```text
./mvnw -Pd2-security       -DautoUpdate=false org.owasp:dependency-check-maven:13.0.0:aggregate
./mvnw -Pd2-security-tests -DautoUpdate=false org.owasp:dependency-check-maven:13.0.0:aggregate
```

Politique :

```text
CVSS >= 7.0      FAIL (les deux scans)
scan error       FAIL
report format    ALL
output           target/d2-security · target/d2-security-tests
```

Les suppressions versionnées dans `config/dependency-check-suppressions.xml` retirent uniquement
deux associations CPE erronées sur des modules internes : `io.github.fturleque:morpheus-store-sqlite`
n'est pas SQLite, `io.github.fturleque:morpheus-cli` n'est pas GitHub CLI. Le vrai driver
`org.xerial:sqlite-jdbc` et toutes les dépendances tierces restent analysés. Le scan produit échoue
si une de ces règles devient inutilisée, afin d'empêcher une suppression obsolète ou trop large.

Le workflow **MORPHEUS Security** utilise une base Dependency-Check produite uniquement par des événements de confiance. Sa politique est :

```text
pull_request -> main, develop        cache trusted uniquement, autoUpdate=false
push         -> main, develop        refresh trusted + scan
schedule     -> tous les jours 04:17 UTC
manual       -> workflow_dispatch
cache max age on PR                  72 h
```

Le refresh quotidien est volontairement plus fréquent que le TTL de 72 h afin qu'un dépôt calme ne puisse pas entrer dans une fenêtre déterministe où toutes les PR échoueraient faute de base OWASP suffisamment fraîche. `AuditHardeningWorkflowContractTest` verrouille cette invariant.

Un `.github/dependabot.yml` maintient en parallèle des PRs hebdomadaires Maven et GitHub Actions vers `develop`. L'activation des alertes de vulnérabilité Dependabot reste un réglage administrateur du dépôt et n'est pas supposée par ce fichier.

## Gate D2 spécialisé

D2 reste un gate local spécialisé avec interdiction de modifier `.github/workflows` dans son périmètre. Il ne remplace pas M21 et n'est donc pas le validateur approprié d'une PR dont l'objet est précisément de modifier les workflows de sécurité.

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.1 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.1
```

D2 conserve ses minima absolus de couverture `40% / 35%`, exécute Dependency-Check et exige le portable de la plateforme pour une qualification finale sans skip.

## Packaging et release

Windows portable :

```powershell
.\distribution\build-portable.ps1 -Version 1.2.1
```

Windows setup :

```powershell
.\distribution\build-installer.ps1 -Version 1.2.1
```

Linux portable :

```bash
bash distribution/build-portable.sh 1.2.1
```

Les builders de release exigent un workspace propre et que le tag attendu pointe exactement sur HEAD avant de produire l'artefact et son checksum SHA-256.

Le workflow **MORPHEUS Release** complète désormais ce contrôle pour les tags `vX.Y.Z` :

- checkout du SHA exact du tag ;
- refus si le commit tagué n'est pas atteignable depuis `main` ;
- build via `distribution/build-release.sh` ;
- attestation GitHub de provenance avec OIDC (`actions/attest` pinné par SHA) ;
- conservation du bundle d'attestation avec les assets ;
- création de la GitHub Release sans `--clobber` ;
- refus d'écraser une release déjà publiée.

Le `.sha256` reste utile pour l'intégrité locale, tandis que l'attestation fournit la preuve d'origine liée au workflow et au commit GitHub.

La release stable publiée est `v1.2.1` jusqu'à publication explicite d'une version suivante ; les corrections de développement ne déplacent jamais un tag existant.

## Tests ciblés

Exemples :

```powershell
.\mvnw.cmd -pl morpheus-domain test
.\mvnw.cmd -pl morpheus-api -am test
.\mvnw.cmd -pl morpheus-architecture-tests -am verify
```

Contrats de durcissement particulièrement importants :

```text
morpheus-store-sqlite/.../SqliteTransactionRunnerTest.java
morpheus-store-sqlite/.../ReleasedSchemaUpgradeTest.java
morpheus-application/.../SyncReliabilityFallbackTest.java
morpheus-api/.../MorpheusRemoteIdentityLifecycleTest.java
morpheus-api/.../ApiRuntimeSqliteSessionTest.java
morpheus-api/.../HttpRequestBodyReaderTest.java
morpheus-provider-sdk/.../ProviderPluginDiscoveryTest.java
morpheus-mcp-transport/.../McpDiagnosticRedactorTest.java
morpheus-architecture-tests/.../ProductReleaseContractTest.java
morpheus-architecture-tests/.../AuditHardeningWorkflowContractTest.java
```

`ReleasedSchemaUpgradeTest` ouvre avec le store courant une base construite par les seules migrations de chaque niveau de schéma publié, garnie de lignes dans toutes les tables qu'une migration ultérieure lit ou réécrit, puis relit ces lignes par les opérations des stores. Les niveaux sont déclarés une fois dans `morpheus-store-sqlite/src/test/resources/released-schema-levels.properties` ; une release y déclare son niveau dans la version qui la suit, et `ReleasedSchemaLevelsDeclarationTest` fait échouer le build tant qu'une version publiée dont les notes existent n'a pas sa ligne. Les lignes sont écrites par le test, d'après les requêtes d'insertion des stores aux tags, et non par le programme publié.

Windows et Linux doivent qualifier exactement le même SHA pour considérer une baseline M21 comme durable.

## Parallélisme JUnit

La règle est décidée par [ADR-0115](../architecture/adr/ARCH-0115-the-operational-runtime-is-a-value-the-launcher-hands-on.md) : le parallélisme JUnit n'est activé que dans un module dont aucun test ne partage un état de la JVM.

`morpheus-store-sqlite` est ce module. Son `src/test/resources/junit-platform.properties` versionné rend concurrentes les méthodes et les classes, sur un pool fixe (`config.strategy=fixed`), pour qu'une course ne dépende pas du nombre de cœurs de la machine. Dans ce module :

- `NoSqliteScopeLeftBehindExtension`, enregistrée par `META-INF/services` et `junit.jupiter.extensions.autodetection.enabled`, lit après chaque test la portée encore attachée au thread (`SqliteConnectionScope.attachedToThisThread()`, visible du paquet seulement), la ferme, et fait échouer le test fautif par son nom : une portée laissée ouverte ferait sinon échouer, par le refus `SQLITE_CONNECTION_SCOPE_NESTED`, le test suivant du même thread du pool ;
- une portée se ferme dans un `finally`, jamais seulement après des assertions ;
- un test qui sature SQLite exprès (`SqliteLocalSecurityContractTest`) ou qui compte une ressource de toute la JVM (`SqliteDatabaseLeaseTest`) est `@Isolated` : il ne rivalise pas avec d'autres tests.

Les autres modules restent séquentiels. État de la JVM encore partagé entre tests, hors `morpheus-store-sqlite` :

| État partagé | Qui l'écrit ou le lit | Effet attendu en parallèle |
|---|---|---|
| Propriété système `java.home` | posée puis rétablie par `MinosPeerLauncherResolutionTest` et `NexusPeerLauncherResolutionTest` | tout test qui résout un lanceur Java pendant la fenêtre lit un répertoire temporaire |
| `Locale.setDefault` | `PolicyApiEnumVocabularyLocaleTest`, `McpEnumVocabularyLocaleTest` | tout formatage sensible à la locale dans un autre test |
| Fichier hors répertoire temporaire | `MorpheusRemoteLoadProfileTest` écrit `target/remote-load-profile.txt`, que la lane nocturne publie | aucun tant qu'il reste le seul écrivain |
| Champs statiques d'une classe de test | `MorpheusRemoteAdversarialClientTest` (serveur, client et répertoire partagés entre ses méthodes, posés en `@BeforeAll`) | ses méthodes ne peuvent pas s'exécuter en concurrence |

Aucun test n'écoute sur un port fixe. Les baux (`SqliteDatabaseLease`) et la quarantaine de l'exécuteur de transactions sont statiques mais indexés par base ou par connexion : deux tests sur deux bases temporaires n'y partagent rien.

Activer le parallélisme dans un autre module est un changement à part, précédé d'exécutions parallèles propres répétées dans ce module.

## Preuves historiques

- R3 / release 1.2.0 : [`../project/validation/VALIDATION_R3.md`](../project/validation/VALIDATION_R3.md)
- D2 historique : [`../project/validation/VALIDATION_D2.md`](../project/validation/VALIDATION_D2.md)
- plan D2 : [`../project/roadmap/D2_EXECUTION.md`](../project/roadmap/D2_EXECUTION.md)

Ces documents historiques conservent les commandes et versions réellement utilisées lors de leur qualification ; ils ne doivent pas être réécrits pour faire croire qu'ils validaient `1.2.1`.

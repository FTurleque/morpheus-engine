# Build, tests et validation

Ce guide décrit l’environnement de développement et les gates actifs sur la baseline corrective **MORPHEUS 1.2.1**. La dernière release effectivement publiée reste `v1.2.0` tant qu'une release `v1.2.1` n'a pas été créée et qualifiée.

## Toolchain

```text
Java 21 uniquement (>= 21 et < 22)
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
Maven Wrapper = 3.9.16 + distribution SHA-256
Maven Enforcer = dependency convergence obligatoire
```

## Reactor Maven

Le dépôt contient **17 modules enfants**, soit **18 projets Maven parent inclus** :

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
```

Les 18 POMs actifs portent la même version MORPHEUS `1.2.1`. Les preuves de release historiques `1.2.0` ne sont pas réécrites.

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
baseline Surefire totale       >= 1300
baseline architecture          >= 335
JaCoCo line ratchet            >= 54.5%
JaCoCo branch ratchet          >= 47.7%
D2 absolute line floor         40%
D2 absolute branch floor       35%
maven dependency analyze       failOnWarning=true
maven dependency convergence   required
CycloneDX SBOM                  JSON + XML
product/package version         1.2.1
```

La source normative des quatre ratchets M21 est `config/m21-quality-ratchets.properties`. Les scripts Windows/Linux et `CoverageQualityGateTest` consomment ce même fichier afin d'empêcher toute divergence entre gate shell, gate PowerShell et gate Java.

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

La baseline globale courante est verrouillée à **54,5% lignes / 47,7% branches**.

Règle d’évolution :

1. une baisse sous 54,5% lignes ou 47,7% branches fait échouer le gate M21 ;
2. les floors D2 40% / 35% restent des minima absolus et ne peuvent jamais affaiblir le ratchet ;
3. une amélioration de couverture ne relève le ratchet qu’après qualification du même SHA exact sur Windows et Linux ;
4. le ratchet n’est jamais abaissé automatiquement ; une baisse nécessite une décision d’audit explicite et motivée ;
5. les compteurs de tests sont eux aussi des ratchets de présence, pas une mesure de qualité autonome ;
6. la couverture ne justifie pas des tests artificiels : les tests doivent conserver une valeur fonctionnelle, de contrat, de sécurité ou d’architecture indépendante du chiffre.

`CoverageQualityGateTest` écrit dans `morpheus-architecture-tests/target/m21-coverage-summary.txt` la couverture observée, la baseline qualifiée, le ratchet actif et les minima D2.

## Frontière HTTP des corps de requête

Toutes les routes HTTP doivent utiliser la primitive partagée `HttpRequestBodyReader`, qui délègue à `TimedBoundedInputReader`. La politique active est :

```text
request body max size     65 536 bytes
request body read timeout 15 seconds
```

Il est interdit aux contextes Query, Saved Views, Export, Policy, Policy Management ou Reasoning de revenir à un `exchange.getRequestBody().readNBytes(...)` direct sans deadline. `RepositoryDocumentationCoherenceTest` verrouille cette règle de repository et `HttpRequestBodyReaderTest` couvre succès, dépassement de taille, timeout et erreur I/O.

## SCA / dépendances

OWASP Dependency-Check est épinglé à `12.2.2` dans le profil Maven `d2-security`.

Commande :

```text
./mvnw -Pd2-security org.owasp:dependency-check-maven:12.2.2:aggregate
```

Politique :

```text
CVSS >= 7.0     FAIL
scan error       FAIL
test scope       skipped
report format    ALL
output            target/d2-security
```

La suppression versionnée dans `config/dependency-check-suppressions.xml` retire uniquement l'association CPE erronée entre le module interne `io.github.fturleque:morpheus-store-sqlite:1.2.1` et SQLite 1.2.1 ; le véritable driver `org.xerial:sqlite-jdbc:3.53.2.0` reste analysé. Le scan échoue si cette règle devient inutilisée afin d'empêcher une suppression obsolète ou trop large.

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

La release stable publiée reste `v1.2.0` jusqu'à publication explicite d'une version suivante ; les corrections de développement ne déplacent jamais le tag existant.

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
morpheus-application/.../SyncReliabilityFallbackTest.java
morpheus-api/.../MorpheusRemoteIdentityLifecycleTest.java
morpheus-api/.../ApiRuntimeSqliteSessionTest.java
morpheus-api/.../HttpRequestBodyReaderTest.java
morpheus-provider-sdk/.../ProviderPluginDiscoveryTest.java
morpheus-mcp-transport/.../McpDiagnosticRedactorTest.java
morpheus-architecture-tests/.../ProductReleaseContractTest.java
morpheus-architecture-tests/.../AuditHardeningWorkflowContractTest.java
```

Windows et Linux doivent qualifier exactement le même SHA pour considérer une baseline M21 comme durable.

## Preuves historiques

- R3 / release 1.2.0 : [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- D2 historique : [`../validation/VALIDATION_D2.md`](../validation/VALIDATION_D2.md)
- plan D2 : [`../roadmap/D2_EXECUTION.md`](../roadmap/D2_EXECUTION.md)

Ces documents historiques conservent les commandes et versions réellement utilisées lors de leur qualification ; ils ne doivent pas être réécrits pour faire croire qu'ils validaient `1.2.1`.

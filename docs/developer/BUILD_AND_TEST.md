# Build, tests et validation

Ce guide décrit l’environnement de développement et les gates actifs sur la baseline corrective **MORPHEUS 1.2.1**. La dernière release effectivement publiée reste `v1.2.0` tant qu'une release `v1.2.1` n'a pas été créée et qualifiée.

## Toolchain

```text
Java >= 21
Maven >= 3.9.16 et < 4.0.0
compiler release = 21
Maven Wrapper = 3.9.16 + distribution SHA-256
```

## Reactor Maven

Le dépôt contient 16 modules enfants, soit 17 projets Maven parent inclus :

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
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
```

Les 17 POMs actifs portent la même version MORPHEUS `1.2.1`. Les preuves de release historiques `1.2.0` ne sont pas réécrites.

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
baseline Surefire totale       >= 711
baseline architecture          >= 253
JaCoCo line ratchet            >= 47%
JaCoCo branch ratchet          >= 40%
D2 absolute line floor         40%
D2 absolute branch floor       35%
maven dependency analyze       failOnWarning=true
CycloneDX SBOM                  JSON + XML
product/package version         1.2.1
```

Les seuils `711 / 253` correspondent à la baseline de remédiation post-audit qualifiée sur le même SHA exact sous Windows et Linux. Les floors sont des ratchets de présence : ils ne sont pas abaissés automatiquement, et toute hausse ultérieure doit elle aussi être fondée sur une qualification exacte des deux plateformes.

Les scripts `validate-m21.sh` et `validate-m21.ps1` appliquent eux-mêmes les ratchets `47% / 40%`, en plus du contrat `CoverageQualityGateTest`, afin d'éviter qu'un changement de wiring Maven transforme silencieusement un ancien floor `25% / 20%` en garde principale.

## Qualité et ratchet JaCoCo

MRA-12 a remplacé le simple floor D2 par un ratchet anti-régression. La baseline historique de référence MRA était **47,2781% lignes / 40,4547% branches** ; le merge post-audit `54c9d01c…` a ensuite qualifié **47,4534% / 40,7212%** sous Linux et **47,4739% / 40,6867%** sous Windows. La remédiation post-audit suivante a qualifié **47,6094% lignes** sur les deux plateformes, avec **40,8521% branches** sous Linux et **40,7610%** sous Windows. Le ratchet exécutable reste volontairement arrondi vers le bas au point de pourcentage entier : **47% / 40%**.

Règle d’évolution :

1. une baisse sous 47% lignes ou 40% branches fait échouer le gate ;
2. les floors D2 40% / 35% restent des minima absolus et ne peuvent jamais affaiblir le ratchet ;
3. une amélioration de couverture ne relève le ratchet qu’après qualification du même SHA exact sur Windows et Linux ;
4. le ratchet n’est jamais abaissé automatiquement ; une baisse nécessite une décision d’audit explicite et motivée ;
5. les compteurs de tests sont eux aussi des ratchets de présence, pas une mesure de qualité autonome ;
6. la couverture ne justifie pas des tests artificiels : les tests doivent conserver une valeur fonctionnelle, de contrat, de sécurité ou d’architecture indépendante du chiffre.

`CoverageQualityGateTest` écrit dans `morpheus-architecture-tests/target/m21-coverage-summary.txt` la couverture observée, la baseline qualifiée, le ratchet actif et les minima D2.

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

Le scan réseau n'est pas intégré au `clean verify` développeur ordinaire. Le workflow **MORPHEUS Security** l'exécute :

```text
pull_request -> main
push         -> main
schedule     -> chaque lundi
manual       -> workflow_dispatch
```

Il constitue le gate SCA de la frontière stable `main`. Un `.github/dependabot.yml` maintient en parallèle des PRs hebdomadaires Maven et GitHub Actions vers `develop`. L'activation des alertes de vulnérabilité Dependabot reste un réglage administrateur du dépôt et n'est pas supposée par ce fichier.

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

D2 applique les mêmes floors de présence `711 / 253`, conserve les minima absolus de couverture `40% / 35%`, exécute Dependency-Check et exige le portable de la plateforme pour une qualification finale sans skip.

## Packaging actif

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

Les builders actifs utilisent `1.2.1` par défaut. Les builders de release exigent en plus un workspace propre et que le tag `v1.2.1` pointe exactement sur HEAD avant de produire une release `1.2.1`.

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
morpheus-provider-sdk/.../ProviderPluginDiscoveryTest.java
morpheus-architecture-tests/.../ProductReleaseContractTest.java
```

Windows et Linux doivent qualifier exactement le même SHA pour considérer une baseline M21 comme durable.

## Preuves historiques

- R3 / release 1.2.0 : [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- D2 historique : [`../validation/VALIDATION_D2.md`](../validation/VALIDATION_D2.md)
- plan D2 : [`../roadmap/D2_EXECUTION.md`](../roadmap/D2_EXECUTION.md)

Ces documents historiques conservent les commandes et versions réellement utilisées lors de leur qualification ; ils ne doivent pas être réécrits pour faire croire qu'ils validaient `1.2.1`.

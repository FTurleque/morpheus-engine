---
id: GEN-0133
title: "Toolchain de build : Java 21 et Maven par Wrapper épinglé"
domain: general
rules: true
files: ["**/pom.xml", ".github/workflows/*.yml", ".mvn/wrapper/maven-wrapper.properties", "pom.xml"]
---

# Toolchain de build : Java 21 et Maven par Wrapper épinglé

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0016, ADR-0017

## Context

MORPHEUS a besoin d'un langage et d'un runtime maintenables pour le domaine riche, les providers, le stockage local, la
CLI, les surfaces MCP et HTTP, Windows et Linux, sur une durée de vie longue. La technologie des spikes de recherche
ne devient pas la fondation de production par inertie.

Le domaine manipule de nombreux concepts orthogonaux : identités, versions, états temporels, lifecycle, relations,
evidence et diagnostics. Un langage fortement typé limite la dérive vers des dictionnaires ou des chaînes
interchangeables. Java apporte des types explicites, les records et les sealed types dans la baseline, JDBC, un
outillage de tests mature, des bibliothèques Markdown, YAML et JSON, un packaging multiplateforme et un écosystème
adapté à la CLI, à MCP et à HTTP. NEXUS utilise une baseline Java 21.

Le build doit être reproductible sur Windows et Linux, et gérer la compilation en `--release 21`, les dépendances, les
tests, le multi-module, le packaging, la génération des distributions, les vérifications d'architecture et la CI.
Maven apporte un modèle de build déclaratif stable, largement supporté par IntelliJ et l'écosystème Java ; le Maven
Wrapper ne dépend pas de la version de Maven installée sur le poste ; les bibliothèques nécessaires (provider
OpenSpec, SQLite JDBC, tests, couches d'exposition) sont distribuées par Maven Central ; le multi-module sépare
domaine, application et adaptateurs sans changer d'outil.

Java 21 est une LTS qui couvre les fonctionnalités nécessaires au domaine, aligne MORPHEUS sur la baseline Java de
NEXUS et n'impose pas un runtime plus récent pour exécuter MORPHEUS : une LTS suffisamment moderne et déjà commune à
l'écosystème, plutôt que la LTS la plus récente sans besoin concret.

Options écartées :

- **Java 25 comme baseline** : LTS plus récente, mais aucune de ses fonctionnalités n'est nécessaire, et la cible
  créerait une divergence gratuite avec la baseline Java de NEXUS.
- **Go** : excellent pour la CLI et la distribution binaire, mais aucun besoin de concurrence ou de performance ne
  compense un second écosystème de production.
- **Python** : utile pour prototyper, mais ne justifie pas un runtime et un gestionnaire de paquets supplémentaires
  pour un domaine fortement structuré et distribué sur Windows. Conservé pour les spikes, non retenu comme runtime
  produit.
- **Gradle** : techniquement capable, mais aucun avantage MORPHEUS concret ne justifie un second modèle de build.
- **Maven 4 avant sa GA** : la fondation ne dépend pas d'une release candidate uniquement pour être plus récente ;
  la série Maven 3.9 est GA et maintenue.
- **Build manuel ou par scripts** : insuffisant pour la gestion des dépendances, des tests et du packaging.

## Decision

### 1. Java 21 est la baseline du code produit

```text
Language                 = Java
Source / bytecode        = Java 21
JDK de build             = JDK 21
Maven compiler release   = 21
```

Le code produit n'utilise que les API et fonctionnalités Java SE 21, sans preview feature. Le build compile avec
`javac --release 21` : le POM racine déclare `maven.compiler.release` à `21` et le `maven-compiler-plugin` le passe en
`<release>`. Activer une preview feature ou migrer la baseline exige une nouvelle décision explicite.

Les outils et spikes de recherche peuvent utiliser d'autres langages tant qu'ils restent hors du runtime produit ; les
spikes Python restent des preuves et ne deviennent pas une seconde implémentation produit.

Java 21 ne signifie pas Spring ni Quarkus : le domaine ne dépend d'aucun framework Java, les providers et les stores
restent des adaptateurs, et les contrats restent sérialisables indépendamment de la JVM. Cette décision ne choisit ni
framework web, ni injection de dépendances, ni bibliothèque CLI.

### 2. Maven, par le Wrapper versionné

```text
Build tool       = Apache Maven, par le Maven Wrapper versionné
Version Maven    = celle de .mvn/wrapper/maven-wrapper.properties, dans [3.9.16,4.0.0) (Enforcer)
JDK de build     = [21,22) (Enforcer)
Gate local       = ./mvnw clean verify
CI               = requise, exact-head Linux et Windows (.github/workflows/ci.yml)
```

Le dépôt porte `mvnw` / `mvnw.cmd` et `.mvn/wrapper/maven-wrapper.properties` (Wrapper `only-script`, distribution
épinglée par somme SHA-256) : la version de Maven est celle que ce fichier désigne. Le POM racine borne le build par
`maven-enforcer-plugin`, exécution `enforce-foundation` : `requireMavenVersion` `[3.9.16,4.0.0)`,
`requireJavaVersion` `[21,22)` (un JDK plus récent est refusé), `dependencyConvergence`.

Maven 4 n'est adopté qu'après sa GA et une vérification de compatibilité du build MORPHEUS, par une modification de
l'Enforcer et une nouvelle décision.

### 3. Un seul chemin de build, local et CI

Le gate local est `./mvnw clean verify` sous Unix et `.\mvnw.cmd clean verify` sous Windows. La CI est requise : le
workflow `.github/workflows/ci.yml` exécute le gate sur la tête exacte de la PR, sous Linux et sous Windows, pour
`develop` et `main`, avec un JDK 21. Elle appelle le même Wrapper et ne crée pas de second chemin de build divergent.

### 4. Invariants de build

1. Les tests unitaires sont exécutés par défaut.
2. Les tests d'architecture sont exécutés par le build standard.
3. Les versions de dépendances sont explicites ou centralisées.
4. Aucun dépôt Maven privé n'est requis pour compiler le cœur local : le build ne dépend que de Maven Central.
5. Aucun secret n'est requis pour les tests.
6. Le build principal fonctionne hors réseau une fois les dépendances en cache.

La liste des modules est celle des `<module>` du `pom.xml` racine. L'invariant important est la direction des
dépendances, pas le nombre de POMs.

## Do's and Don'ts

### Do

- Compiler avec `--release 21`, par la propriété `maven.compiler.release` du POM racine.
- Construire avec un JDK de la ligne 21, admis par l'Enforcer `[21,22)`.
- Lancer le build par `./mvnw` (ou `.\mvnw.cmd`), et lire la version de Maven dans
  `.mvn/wrapper/maven-wrapper.properties`.
- Exécuter `./mvnw clean verify` avant de déclarer une PR prête.
- Faire appeler le même Wrapper par la CI, avec un JDK 21.
- Réévaluer la baseline seulement quand une fonctionnalité, une dépendance ou une politique de support apporte un
  bénéfice concret, et par une nouvelle décision.

### Don't

- Ne pas compiler avec un JDK hors de `[21,22)` ni un Maven hors de `[3.9.16,4.0.0)`.
- Ne pas lancer le build par un `mvn` installé sur le poste, ni créer dans la CI un chemin de build qui contourne le
  Wrapper.
- Ne pas utiliser d'API ni de fonctionnalité Java postérieure à 21, ni de preview feature, dans le code produit.
- Ne pas introduire Spring, Quarkus ni un autre framework dans le domaine ou la fondation.
- Ne pas embarquer de dépendance Python dans le runtime produit.
- Ne pas déclarer de dépôt Maven dans un POM, ni exiger de secret pour compiler ou tester.
- Ne pas rendre advisory le job CI exact-head.

## Consequences

### Positives

- Domaine fortement typé, sur une LTS moderne, aligné sur la baseline de NEXUS.
- JDBC mature, outillage Maven et IntelliJ robuste, environnement Windows réaliste.
- Build familier et déclaratif, multi-module, avec des plugins de tests et de packaging matures.
- Dépendances Maven Central et Wrapper Windows/Linux ; version de Maven identique sur tous les postes.
- Séparation claire entre baseline Java et JDK de build.

### Négatives

- Les fonctionnalités Java postérieures à 21 ne peuvent pas être utilisées dans le code produit.
- Un runtime plus lourd qu'un binaire Go.
- Un développeur qui n'a qu'un JDK plus récent ne peut pas construire le projet sans installer un JDK 21.
- XML plus verbeux ; configuration multi-module à maintenir.
- Maven 3 n'expose pas les nouveautés de Maven 4.
- Le packaging Windows nécessite des outils complémentaires.

### Risques

- **Baseline trop conservatrice à terme** : réévaluée uniquement sur un bénéfice concret.
- **Migration Maven 4** : mitigée par un POM Maven 3 classique, sans hacks.
- **Divergence de version Maven locale** : mitigée par le Wrapper et l'Enforcer.
- **API Java postérieure à 21** : mitigée par `maven.compiler.release=21` et l'Enforcer `[21,22)`.
- **Indisponibilité d'un runner CI** : un échec du runner avant l'exécution du build n'est pas un défaut du code ; il
  se relance, il ne se contourne pas.

## Compliance and Enforcement

- Règle Archgate `GEN-0133-toolchain-java-21-et-maven-wrapper.rules.ts` : `compiler-release-is-21`,
  `workflows-install-java-21`, `no-preview-features`, `maven-version-is-enforced`, `wrapper-distribution-is-pinned`,
  `no-extra-maven-repository`, `workflows-run-the-wrapper`.
- `pom.xml` (racine) : Enforcer `enforce-foundation` (`requireMavenVersion`, `requireJavaVersion`,
  `dependencyConvergence`) fait échouer le build hors des bornes ; `maven.compiler.release` à `21` passé au
  `maven-compiler-plugin`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RepositoryDocumentationCoherenceTest.java`
  (`rootBuildEnforcerPinsQualifiedJavaAndDependencyConvergence`) : `requireJavaVersion` `[21,22)` et
  `dependencyConvergence` dans le POM racine.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/AuditHardeningWorkflowContractTest.java`
  (`sonarAnalysisNeverRunsOnPullRequestsAndStaysOutOfTheRequiredVerifyJob`) : refuse un job exact-head rendu advisory.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/GateReachabilityArchitectureTest.java`
  (`theScriptTestsRunOnEachLegOfTheContinuousIntegrationWorkflow`) : le job de vérification de `ci.yml` tourne sous
  Linux et sous Windows.
- Revue : l'absence de framework dans le domaine, et le contenu des règles Archgate, qui ne s'exécutent pas en CI.

## References

- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite par JDBC.
- [ADR-0019](GEN-0019-maven-coordinates-java-namespace.md) — coordonnées Maven et espace de noms Java.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native autonome, avec runtime embarqué.
- `.mvn/wrapper/maven-wrapper.properties`, `pom.xml`, `.github/workflows/ci.yml`.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md), [VALIDATION_M1](../../project/validation/VALIDATION_M1.md).

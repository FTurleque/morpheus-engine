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
- **Un workflow par jalon** : produit de la dette temporaire et rend la CI non durable.

Principes de la toolchain :

```text
Language                 = Java
Source / bytecode        = Java 21
JDK de build             = JDK 21
Maven compiler release   = 21
Build tool               = Apache Maven, par le Maven Wrapper versionné
Version Maven            = celle de .mvn/wrapper/maven-wrapper.properties, dans [3.9.16,4.0.0) (Enforcer)
JDK admis                = [21,22) (Enforcer)
Gate local               = ./mvnw clean verify
CI                       = requise, exact-head Linux et Windows (.github/workflows/ci.yml)
```

Le build compile ainsi avec `javac --release 21`. L'exécution `enforce-foundation` appartient au
`maven-enforcer-plugin`. Le workflow de CI est générique ; les validateurs locaux restent supportés, et le workflow est
une preuve reproductible supplémentaire, pas un remplacement des invariants produit. Pour les modules, l'invariant
important est la direction des dépendances, pas le nombre de POMs.

## Decision

1. **Java 21** : le code produit n'utilise que Java SE 21, sans preview feature ; le POM racine déclare
   `maven.compiler.release` à `21`, passé en `<release>` au `maven-compiler-plugin`. Une preview ou une autre baseline
   exige une nouvelle décision. D'autres langages ne servent qu'aux outils et spikes hors runtime produit ; les spikes
   Python restent des preuves, jamais une seconde implémentation produit.
2. Aucun framework Java (Spring, Quarkus…) dans le domaine ou la fondation ; providers et stores restent des
   adaptateurs, les contrats sérialisables indépendamment de la JVM. Ce choix ne fixe ni framework web, ni injection,
   ni bibliothèque CLI.
3. **Maven par le Wrapper** : `mvnw` / `mvnw.cmd` et `.mvn/wrapper/maven-wrapper.properties` (`only-script`,
   distribution épinglée par SHA-256) fixent la version. L'Enforcer `enforce-foundation` du POM racine impose
   `requireMavenVersion` `[3.9.16,4.0.0)`, `requireJavaVersion` `[21,22)` (un JDK plus récent est refusé) et
   `dependencyConvergence`. Maven 4 attend sa GA, une vérification de compatibilité, une modification de l'Enforcer et
   une nouvelle décision.
4. **Un seul chemin de build** : `./mvnw clean verify` (`.\mvnw.cmd` sous Windows). La CI requise,
   `.github/workflows/ci.yml`, le lance par le même Wrapper avec un JDK 21 sur la tête exacte, Linux et Windows, à
   chaque pull request et push sur `main`, sans second chemin de build. Aucun workflow propre à un jalon.
5. **Invariants** : tests unitaires et d'architecture dans le build standard ; versions explicites ou centralisées ;
   Maven Central seul, sans dépôt privé ; aucun secret pour tester ; build hors réseau une fois le cache rempli. Les
   modules sont les `<module>` du POM racine.

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
- Ne pas créer de workflow propre à un jalon.

## Consequences

### Positives

- Domaine fortement typé, sur une LTS moderne, aligné sur la baseline de NEXUS.
- JDBC mature, outillage Maven et IntelliJ robuste, environnement Windows réaliste.
- Build familier et déclaratif, multi-module, avec des plugins de tests et de packaging matures.
- Dépendances Maven Central et Wrapper Windows/Linux ; version de Maven identique sur tous les postes.
- Séparation claire entre baseline Java et JDK de build.
- Le workflow de CI n'a pas à être recréé à chaque jalon.

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
- Revue : l'absence de framework dans le domaine, l'absence de workflow propre à un jalon, et le contenu des règles
  Archgate, qui ne s'exécutent pas en CI.

## References

- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite par JDBC.
- [ADR-0019](GEN-0019-maven-coordinates-java-namespace.md) — coordonnées Maven et espace de noms Java.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native autonome, avec runtime embarqué.
- `.mvn/wrapper/maven-wrapper.properties`, `pom.xml`, `.github/workflows/ci.yml`.
- [VALIDATION_M0](../../project/validation/VALIDATION_M0.md), [VALIDATION_M1](../../project/validation/VALIDATION_M1.md).

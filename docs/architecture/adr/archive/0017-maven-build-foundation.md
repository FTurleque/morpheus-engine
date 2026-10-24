# ADR-0017 — Utiliser Maven avec le Maven Wrapper comme fondation de build

> **Archivée.** Cette décision est consolidée dans [ADR-0133](../GEN-0133-toolchain-java-21-et-maven-wrapper.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0133**
- Date : 2026-07-22

## Context

Java est retenu avec une baseline Java 21 (ADR-0016). MORPHEUS doit disposer d'un build reproductible sur Windows et
Linux, qui gère la compilation en `--release 21`, les dépendances, les tests, le multi-module, le packaging, la
génération des distributions, les vérifications d'architecture et la CI.

Maven 4 n'était pas GA à la date de la décision ; la série Maven 3.9 l'était et restait maintenue. La fondation
MORPHEUS ne dépend pas d'une release candidate uniquement pour être plus récente.

Raisons du choix de Maven :

- **cohérence** : modèle de build déclaratif stable, largement supporté par IntelliJ et l'écosystème Java ;
- **reproductibilité** : le Maven Wrapper ne dépend pas de la version de Maven installée sur le poste ;
- **dépendances** : les bibliothèques nécessaires (provider OpenSpec, SQLite JDBC, tests, couches d'exposition) sont
  distribuées par Maven Central ;
- **multi-module** : séparer domaine, application et adaptateurs sans changer d'outil ;
- **baseline Java indépendante du JDK** : `maven.compiler.release = 21` empêche l'usage accidentel d'une API Java
  postérieure à 21.

Options considérées :

- **Gradle** : non retenu ; techniquement capable, mais aucun avantage MORPHEUS concret ne justifie un second modèle
  de build.
- **Maven 4 RC** : différé ; le projet préfère une GA stable pour son socle.
- **Build manuel ou par scripts** : rejeté ; insuffisant pour la gestion des dépendances, des tests et du packaging.

## Decision

```text
Build tool       = Apache Maven, par le Maven Wrapper versionné
Version Maven    = celle de .mvn/wrapper/maven-wrapper.properties, dans [3.9.16,4.0.0) (Enforcer)
JDK de build     = [21,22) (Enforcer)
Compiler release = 21
Gate local       = ./mvnw clean verify
CI               = requise, exact-head Linux et Windows (.github/workflows/ci.yml)
```

Le dépôt porte `mvnw` / `mvnw.cmd` et `.mvn/wrapper/maven-wrapper.properties` (Wrapper `only-script`, distribution
épinglée par somme SHA-256) : la version de Maven est celle que ce fichier désigne, et le POM racine la borne par
`maven-enforcer-plugin` (`requireMavenVersion` `[3.9.16,4.0.0)`, `requireJavaVersion` `[21,22)`,
`dependencyConvergence`).

Le gate local est :

```text
Windows : .\mvnw.cmd clean verify
Unix    : ./mvnw clean verify
```

La CI est requise : le workflow `.github/workflows/ci.yml` exécute le gate sur la tête exacte de la PR, sous Linux et
sous Windows, pour `develop` et `main`. Elle appelle le même Wrapper et ne crée pas de second chemin de build
divergent.

Maven 4 n'est adopté qu'après sa GA et une vérification de compatibilité du build MORPHEUS, par une modification de
l'Enforcer et de cet ADR.

### Invariants de build

1. Java `release = 21`.
2. Pas de preview feature sans ADR.
3. Tests unitaires exécutés par défaut.
4. Tests d'architecture exécutés par le build standard.
5. Versions de dépendances explicites ou centralisées.
6. Build reproductible via le Wrapper.
7. Aucun dépôt Maven privé obligatoire pour compiler le cœur local.
8. Aucun secret requis pour les tests.
9. Le build principal fonctionne hors réseau une fois les dépendances en cache.
10. Le JDK de build est un JDK 21 ; un JDK plus récent est refusé par l'Enforcer.
11. Le gate CI exact-head Linux et Windows est requis.

### Structure multi-module

La liste des modules est celle des `<module>` du `pom.xml` racine. L'invariant important est la direction des
dépendances, pas le nombre de POMs.

## Do's and Don'ts

### Do

- Lancer le build par `./mvnw` (ou `.\mvnw.cmd`), jamais par un `mvn` installé sur le poste.
- Lire la version de Maven dans `.mvn/wrapper/maven-wrapper.properties`.
- Compiler en `maven.compiler.release = 21` avec un JDK 21.
- Exécuter `./mvnw clean verify` avant de déclarer une PR prête.
- Faire appeler le même Wrapper par la CI.

### Don't

- Ne pas compiler avec un JDK hors de `[21,22)` ni un Maven hors de `[3.9.16,4.0.0)`.
- Ne pas activer de preview feature sans ADR.
- Ne pas exiger de dépôt Maven privé ni de secret pour compiler ou tester.
- Ne pas rendre advisory le job CI exact-head.
- Ne pas créer dans la CI un chemin de build qui contourne le Wrapper.

## Consequences

### Positives

- Excellente intégration IntelliJ ; build familier et déclaratif.
- Dépendances Maven Central et Wrapper Windows/Linux.
- Support multi-module.
- Séparation claire entre baseline Java et JDK de build.
- Plugins de tests et de packaging matures.
- Migration possible vers Maven 4.

### Négatives

- XML plus verbeux.
- Maven 3 n'expose pas les nouveautés de Maven 4.
- Configuration multi-module à maintenir.
- Le packaging Windows nécessite des outils complémentaires.

### Risques

- **Migration Maven 4** : mitigée par un POM Maven 3 classique, sans hacks.
- **Divergence de version Maven locale** : mitigée par le Wrapper et l'Enforcer.
- **API Java postérieure à 21** : mitigée par `maven.compiler.release=21` et l'Enforcer `[21,22)`.
- **Indisponibilité d'un runner CI** : un échec du runner avant l'exécution du build n'est pas un défaut du code ; il
  se relance, il ne se contourne pas.

## Compliance and Enforcement

- Règle Archgate `GEN-0017-maven-build-foundation.rules.ts` : `maven-version-is-enforced`, `wrapper-distribution-is-pinned`, `no-extra-maven-repository`, `workflows-run-the-wrapper`.
- `pom.xml` : `maven-enforcer-plugin` (`requireMavenVersion`, `requireJavaVersion`, `dependencyConvergence`) fait
  échouer le build hors des bornes.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RepositoryDocumentationCoherenceTest.java`
  (`rootBuildEnforcerPinsQualifiedJavaAndDependencyConvergence`) exige `requireJavaVersion` `[21,22)` et
  `dependencyConvergence` dans le POM racine.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m21/AuditHardeningWorkflowContractTest.java`
  (`sonarAnalysisNeverRunsOnPullRequestsAndStaysOutOfTheRequiredVerifyJob`) refuse un job exact-head rendu advisory.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/GateReachabilityArchitectureTest.java`
  (`theScriptTestsRunOnEachLegOfTheContinuousIntegrationWorkflow`) exige que le job de vérification de `ci.yml` tourne
  sous Linux et sous Windows.
- Revue : la borne `requireMavenVersion`, l'usage du Wrapper dans les workflows et l'absence de preview feature ne sont
  tenus par aucun test.

## References

- [ADR-0016](0016-java-21-production-baseline.md) — baseline Java 21.
- `.mvn/wrapper/maven-wrapper.properties`, `pom.xml`, `.github/workflows/ci.yml`
- [VALIDATION_M1](../../../project/validation/VALIDATION_M1.md)

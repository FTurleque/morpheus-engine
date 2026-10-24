---
id: GEN-0016
title: "Utiliser Java 21 comme baseline de compatibilité MORPHEUS"
domain: general
rules: true
files: ["**/pom.xml", ".github/workflows/*.yml", "pom.xml"]
---

# Utiliser Java 21 comme baseline de compatibilité MORPHEUS

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

MORPHEUS a besoin d'un langage et d'un runtime maintenables pour le domaine riche, les providers, le stockage local, la
CLI, les surfaces MCP et HTTP, Windows et Linux, sur une durée de vie longue. La technologie des spikes de recherche
ne devient pas la fondation de production par inertie.

Le domaine manipule de nombreux concepts orthogonaux : identités, versions, états temporels, lifecycle, relations,
evidence et diagnostics. Un langage fortement typé limite la dérive vers des dictionnaires ou des chaînes
interchangeables. Java apporte des types explicites, les records et les sealed types dans la baseline, JDBC, un
outillage de tests mature, des bibliothèques Markdown, YAML et JSON, un packaging multiplateforme et un écosystème
adapté à la CLI, à MCP et à HTTP. NEXUS utilise déjà une baseline Java 21.

Options considérées :

- **Java 25 comme baseline** : LTS plus récente, mais aucune de ses fonctionnalités n'est nécessaire, et la cible
  créerait une divergence gratuite avec la baseline Java de NEXUS.
- **Go** : excellent pour la CLI et la distribution binaire, mais aucun besoin de concurrence ou de performance ne
  compense un second écosystème de production.
- **Python** : utile pour prototyper, mais ne justifie pas un runtime et un gestionnaire de paquets supplémentaires
  pour un domaine fortement structuré et distribué sur Windows. Conservé pour les spikes, non retenu comme runtime
  produit.

Java 21 est retenu parce qu'il s'agit d'une LTS, qu'il couvre les fonctionnalités nécessaires au domaine, qu'il aligne
MORPHEUS sur la baseline Java de NEXUS, et qu'il n'impose pas un runtime plus récent pour exécuter MORPHEUS : une LTS
suffisamment moderne et déjà commune à l'écosystème, plutôt que la LTS la plus récente sans besoin concret.

## Decision

```text
Language                 = Java
Source / bytecode        = Java 21
JDK de build             = JDK 21
Maven compiler release   = 21
```

Le code produit n'utilise que les API et fonctionnalités Java SE 21.

Le build compile avec `javac --release 21` : le POM racine déclare `maven.compiler.release` à `21` et le
`maven-compiler-plugin` le passe en `<release>`. Le bytecode cible Java 21 et la compilation n'utilise que l'API
documentée de Java 21.

Le JDK de build est un JDK 21 : l'Enforcer du POM racine (exécution `enforce-foundation`) exige
`<requireJavaVersion><version>[21,22)</version></requireJavaVersion>` et refuse un JDK plus récent. La CI construit
avec un JDK 21 (`.github/workflows/ci.yml`).

Les outils et spikes de recherche peuvent utiliser d'autres langages tant qu'ils restent hors du runtime produit.

Invariants :

1. le domaine ne dépend d'aucun framework Java ;
2. Java 21 ne signifie pas Spring ni Quarkus ;
3. aucune preview feature dans le code produit ;
4. `maven.compiler.release = 21` ;
5. les contrats restent sérialisables indépendamment de la JVM ;
6. les providers et les stores restent des adaptateurs ;
7. les fixtures de `experiments/m0/fixtures/` servent de tests de non-régression Java ;
8. les spikes Python restent des preuves et ne deviennent pas une seconde implémentation produit.

Cette décision ne choisit ni framework web, ni injection de dépendances, ni bibliothèque CLI. Une migration de la
baseline exige une nouvelle décision explicite.

## Do's and Don'ts

### Do

- Compiler avec `--release 21`, par la propriété `maven.compiler.release` du POM racine.
- Construire avec un JDK de la ligne 21, admis par l'Enforcer `[21,22)`.
- Réévaluer la baseline seulement quand une fonctionnalité, une dépendance ou une politique de support apporte un
  bénéfice concret, et par une nouvelle décision.

### Don't

- Ne pas construire avec un JDK plus récent que la ligne 21.
- Ne pas utiliser d'API ni de fonctionnalité Java postérieure à 21, ni de preview feature, dans le code produit.
- Ne pas introduire Spring, Quarkus ni un autre framework dans le domaine ou la fondation.
- Ne pas embarquer de dépendance Python dans le runtime produit.

## Consequences

### Positives

- Domaine fortement typé, sur une LTS moderne.
- Alignement de MORPHEUS et de NEXUS sur la même baseline.
- JDBC mature, outillage Maven et IntelliJ robuste, environnement Windows réaliste.

### Négatives

- Les fonctionnalités Java postérieures à 21 ne peuvent pas être utilisées dans le code produit.
- Un runtime plus lourd qu'un binaire Go.
- Un développeur qui n'a qu'un JDK plus récent ne peut pas construire le projet sans installer un JDK 21.

### Risques

- Une baseline trop conservatrice à terme : réévaluée uniquement sur un bénéfice concret.

## Compliance and Enforcement

- Règle Archgate `GEN-0016-java-21-production-baseline.rules.ts` : `compiler-release-is-21`, `workflows-install-java-21`, `no-preview-features`.
- `pom.xml` (racine) : Enforcer `enforce-foundation` avec `requireJavaVersion` `[21,22)`, et
  `maven.compiler.release` à `21` passé au `maven-compiler-plugin`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RepositoryDocumentationCoherenceTest.java` :
  `rootBuildEnforcerPinsQualifiedJavaAndDependencyConvergence` exige la contrainte `[21,22)` dans le POM racine.
- Revue : la valeur de `maven.compiler.release`, l'absence de preview feature, l'absence de framework et le JDK 21 de
  la CI.

## References

- [ADR-0017](GEN-0017-maven-build-foundation.md) — fondation de build Maven.
- [ADR-0018](DATA-0018-sqlite-initial-persistent-store.md) — SQLite par JDBC.
- [ADR-0019](GEN-0019-maven-coordinates-java-namespace.md) — coordonnées Maven et espace de noms Java.
- [ADR-0061](GEN-0061-self-contained-jpackage-portable-distribution.md) — distribution portable avec runtime embarqué.
- [Validation M0](../../project/validation/VALIDATION_M0.md)

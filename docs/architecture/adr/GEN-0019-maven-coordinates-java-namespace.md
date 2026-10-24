---
id: GEN-0019
title: "Stabiliser les coordonnées Maven et le namespace Java de MORPHEUS"
domain: general
rules: false
---

# Stabiliser les coordonnées Maven et le namespace Java de MORPHEUS

- Statut : **Acceptée**
- Date : 2026-07-22

## Context

Plusieurs identités du produit ne répondent pas au même besoin : le nom du produit (MORPHEUS), le nom du dépôt
(`morpheus-engine`), le `groupId` Maven, les `artifactId`, le package Java et la commande CLI (`morpheus`). Les
confondre rendrait les renommages et les publications plus coûteux.

L'écosystème utilise une convention où le `groupId` Maven représente l'identité de publication, le package Java le
domaine du produit, et le nom du dépôt peut rester plus descriptif.

Le `groupId` répond à la publication, aux coordonnées d'artefacts, à l'unicité dans un dépôt Maven et à
l'appartenance technique au propriétaire. Le package Java répond à l'organisation du code, à la lisibilité du domaine,
à la stabilité des imports et à la cohérence du produit. Rien n'impose donc que le package reprenne le `groupId`.

`io.github.fturleque` représente explicitement le propriétaire GitHub, ne prétend posséder aucun domaine DNS tiers,
reste stable si le dépôt change de nom, peut regrouper plusieurs composants de l'écosystème et suit la convention
Maven déjà utilisée. `com.morpheus` porte l'identité fonctionnelle stable du produit et donne des imports lisibles sans
exposer GitHub, le nom du dépôt, le backend, OpenSpec ni le propriétaire humain.

Options considérées :

- **`io.github.fturleque.morpheus.*` comme package Java** : non retenue. Correspondance stricte avec le `groupId`,
  mais package plus verbeux et identité du domaine liée à l'hébergement, sans bénéfice fonctionnel.
- **`com.morpheus.engine.*`** : non retenue. `engine` décrit le composant technique et n'apporte aucune distinction
  utile aux concepts du domaine.
- **Package par nom de module seul** (`domain.*`, `application.*`) : rejetée. Trop générique, propice aux collisions.
- **`com.morpheus.*`** : retenue. Simple, stable, alignée sur l'identité produit.

## Decision

| Identité | Valeur |
|---|---|
| Produit | MORPHEUS |
| Dépôt | `morpheus-engine` |
| `groupId` Maven | `io.github.fturleque` |
| Artefact racine / distribution | `morpheus-engine` |
| Préfixe des modules Maven | `morpheus-` (`morpheus-domain`, `morpheus-application`, `morpheus-store-sqlite`…) |
| Namespace Java racine | `com.morpheus` (`com.morpheus.domain`, `com.morpheus.application`, `com.morpheus.store.sqlite`…) |

La liste des modules est celle des `<module>` du `pom.xml` racine.

**Frontières de packages.** Le namespace rend visibles les dépendances architecturales : `com.morpheus.domain` ne
dépend d'aucun adaptateur ; `com.morpheus.application` dépend du domaine et définit les ports des cas d'usage ; les
adaptateurs (`com.morpheus.provider..`, `com.morpheus.store..`, `com.morpheus.cli..` et les autres transports)
dépendent vers l'intérieur, jamais l'inverse.

**Package interne et contrat public.** Le package Java n'est pas un contrat réseau. Les surfaces HTTP et MCP exposent
des DTO et contrats explicitement versionnés ; elles ne sérialisent pas les noms de classes Java comme type public.
L'organisation interne peut donc être refactorée sans casser un protocole externe.

Invariants :

1. `groupId = io.github.fturleque` ;
2. les `artifactId` MORPHEUS utilisent le préfixe `morpheus-` ;
3. le namespace Java racine est `com.morpheus` ;
4. aucun package `com.morpheus.domain` ne dépend d'un adaptateur ;
5. le nom du dépôt `morpheus-engine` n'est pas encodé dans les packages métier ;
6. aucune classe OpenSpec n'apparaît dans `com.morpheus.domain` ;
7. aucun type SQLite ou JDBC n'apparaît dans `com.morpheus.domain` ;
8. les DTO réseau ne dépendent pas implicitement du nom pleinement qualifié des classes du domaine.

## Do's and Don'ts

### Do

- Déclarer tout nouveau module avec le `groupId` `io.github.fturleque` et un `artifactId` préfixé par `morpheus-`.
- Placer toute classe de production sous `com.morpheus`.
- Exposer sur HTTP et MCP des DTO explicitement versionnés.

### Don't

- Ne pas utiliser `io.github.fturleque.morpheus` ni `com.morpheus.engine` comme package Java.
- Ne pas faire dépendre `com.morpheus.domain` d'un adaptateur, d'une classe OpenSpec, ni d'un type SQLite ou JDBC.
- Ne pas sérialiser un nom de classe Java comme type public d'une surface réseau.

## Consequences

### Positives

- Conventions immédiatement claires et imports lisibles.
- Séparation entre produit, dépôt et publication ; aucun couplage GitHub dans les packages métier.
- Modules Maven nommés de manière homogène ; tests d'architecture simples à exprimer.

### Négatives

- Le `groupId` et le package Java ne sont pas identiques ; certains outils ou développeurs peuvent s'attendre à une
  correspondance stricte.

### Risques

- `com.morpheus` reste un nom générique, que seule la structure Maven protège des collisions dans le workspace.

## Compliance and Enforcement

- `LayerDependencyTest#domainMustNotDependOnAdapters` et `LayerDependencyTest#applicationMustNotDependOnAdapters`
  (`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`) tiennent les
  frontières de packages, OpenSpec et stores compris.
- Revue : `groupId`, préfixe `morpheus-` des `artifactId`, namespace racine `com.morpheus`, absence de type
  `java.sql` ou `org.sqlite` dans `morpheus-domain`, absence de nom de classe Java dans les DTO publics.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine possédé par MORPHEUS.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — baseline Java 21.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — fondation Maven.
- [Validation M1](../../project/validation/VALIDATION_M1.md)

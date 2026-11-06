---
id: ARCH-0158
title: "Le runtime du store SQLite est public pour son adaptateur seulement, et sa surface ne fait que rétrécir"
domain: architecture
rules: false
---

# Le runtime du store SQLite est public pour son adaptateur seulement, et sa surface ne fait que rétrécir

- Statut : **Acceptée**
- Date : 2026-11-03

## Context

`morpheus-store-sqlite` porte deux rôles : les implémentations des ports de l'application, et la machinerie sur
laquelle elles tournent (connexion, portée, bail, schéma, transactions, maintenance hors ligne). Le commit `8ffbd41f`
(24/10/2026) les range dans deux paquets, `com.morpheus.store.sqlite.adapter` (14 classes) et
`com.morpheus.store.sqlite.runtime` (13 classes), pour qu'une règle puisse nommer chaque rôle par son paquet, comme
ADR-0155 le fait pour `morpheus-api`.

Les stores appellent le runtime d'un paquet à l'autre : ce que la visibilité paquet cachait doit devenir public. Le
constat AUD-ARC-16 de l'audit du 09/10/2026 mesure l'élargissement : trois types et onze membres passés de la
visibilité paquet à `public`, et aucune classe de production d'un autre module qui les emploie. La frontière avec les
autres modules, que le compilateur tenait sans rien coûter, n'est plus tenue que par une règle.

Options considérées :

- **Revenir à un seul paquet.** Le compilateur tient de nouveau la frontière ; une règle ne peut plus distinguer
  l'adaptateur de son runtime que par une liste de noms, ce qu'ADR-0155 refuse. Rejeté.
- **Un module nommé** (`module-info.java`) qui n'exporte que l'adaptateur et les deux points d'entrée. Le dépôt n'a
  aucun module nommé : les modules voisins, les tests, `jpackage` et le classpath de livraison (ADR-0134) passeraient au
  chemin de modules pour un seul adaptateur. Rejeté tant qu'aucun autre module n'en a besoin.
- **Une règle, et deux listes exactes qui ne font que rétrécir.** Retenu : la frontière est écrite, chaque élargissement
  futur est refusé sous son nom, et ce qui reste public se lit en un endroit.

Les transports tiennent deux classes du runtime : `SqliteOperationRuntime`, qui construit les stores d'une opération
(ADR-0162, décision 2), et `SqliteServerMaintenance`, qui sauvegarde, restaure et prend le bail exclusif de la base
pour le serveur remote et la CLI.

## Decision

1. **Deux paquets.** `com.morpheus.store.sqlite.adapter` ne contient que des implémentations de ports de l'application,
   plus les deux collaborateurs nommés du store de contenu de snapshot ; `com.morpheus.store.sqlite.runtime` contient
   tout le reste. Aucune classe ne réside dans `com.morpheus.store.sqlite`.
2. **Le runtime est public pour l'adaptateur, pas pour les autres modules.** Hors du store, une classe de production ne
   dépend que des classes du runtime de `SqliteStorePackagesArchitectureTest.RUNTIME_ENTRY_POINTS`
   (`SqliteOperationRuntime`, `SqliteServerMaintenance`). C'est un compteur de dette : exact, il ne fait que rétrécir.
3. **La surface publique du runtime ne fait que rétrécir.** Les types publics de `com.morpheus.store.sqlite.runtime`,
   imbriqués compris, sont exactement `SqliteStorePackagesArchitectureTest.RUNTIME_PUBLIC_TYPES`. Un type qui doit
   devenir public, ou une entrée ajoutée à l'une des deux listes, exige un ADR consolidé qui remplace celui-ci.
4. **Aucun module nommé.** L'élargissement de visibilité est le prix du découpage en paquets ; la frontière est tenue
   par les règles de la section de conformité, pas par le compilateur.

## Do's and Don'ts

### Do

- Ranger une nouvelle implémentation de port dans `adapter`, toute autre classe du store dans `runtime`.
- Garder package-private ce que l'adaptateur n'appelle pas.
- Retirer de `RUNTIME_PUBLIC_TYPES` un type qui cesse d'être public, et baisser sa clé dans
  `config/debt-counter-ratchets.properties` dans le même changement.

### Don't

- Ne pas faire dépendre une classe d'un autre module d'une classe du runtime hors des deux points d'entrée, même pour
  une constante.
- Ne pas rendre public un type du runtime sans un ADR consolidé qui remplace celui-ci.
- Ne pas ajouter de `module-info.java` à un seul module.

## Consequences

### Positives

- Chaque rôle du store est un paquet : une règle sur l'adaptateur ou sur le runtime voit la classe ajoutée demain.
- Ce qui est public dans le runtime se lit dans une liste, et un élargissement fait échouer le build en se nommant.

### Négatives

- La frontière avec les autres modules est une règle et non le compilateur : trois types et onze membres sont publics
  pour être appelés d'un paquet à l'autre.
- La liste des types publics ne voit pas les membres : un membre devenu public dans un type déjà public lui échappe, et
  seule la règle de dépendance l'empêche d'être employé hors du store.

### Risques

- Une règle peut être affaiblie dans un diff là où un modificateur ne pouvait pas l'être ; chaque règle a sa preuve
  d'échec, et la taille des deux listes est inscrite dans `config/debt-counter-ratchets.properties`.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/SqliteStorePackagesArchitectureTest.java` :
  `onlyTheStoreDependsOnItsRuntimeBesideTheTwoNamedEntryPoints` (règle sur le bytecode, vue en échec sur une classe de
  `morpheus-cli` qui nomme `SqliteTransactionRunner`), `everyNamedEntryPointIsStillReachedFromOutsideTheStore` (chaque
  entrée de `RUNTIME_ENTRY_POINTS` est encore atteinte), `noMainSourceOutsideTheStoreNamesARuntimeClassBesideTheTwoNamedEntryPoints`
  (texte, qui voit une constante inlinée), `theRuntimeDeclaresExactlyTheNamedPublicTypes` (décision 3, dans les deux
  sens, vue en échec sur `SqliteSchemaManager` rendu public), `everyTopLevelClassOfTheAdapterPackageImplementsAnApplicationPortBesideTheTwoNamedCollaborators` et
  `noClassOfTheStoreResidesDirectlyInItsParentPackage` (décision 1).
- `ProductionClassListsArchitectureTest` : les deux listes sont des compteurs de dette déclarés sous cet ADR ; il exige
  que cet ADR les nomme et que leur taille égale leur clé de `config/debt-counter-ratchets.properties`.
- Revue : une hausse d'une clé de `config/debt-counter-ratchets.properties` est justifiée dans la description de la
  pull request.

## References

- [ADR-0155](ARCH-0155-un-role-est-un-paquet-et-une-liste-de-classes-declare-sa-sorte.md) — un rôle est un paquet ;
  sortes des listes de classes.
- [ADR-0162](ARCH-0162-racines-de-composition-nommees-paquets-sans-cycle-hors-une-paire-bornee-et-decisions-dans-le-domaine.md) —
  `SqliteOperationRuntime` construit les stores d'une opération.
- [ADR-0150](ARCH-0150-ce-que-le-coeur-applicatif-peut-toucher.md) — le runtime SQLite reste hors de l'application.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native.
- Audit du 09/10/2026, constats AUD-ARC-09 et AUD-ARC-16 : `docs/project/audits/2026-10-09/findings.md`.
- `.claude/rules/architecture.md`

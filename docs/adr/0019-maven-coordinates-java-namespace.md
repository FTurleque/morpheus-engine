# ADR-0019 — Stabiliser les coordonnées Maven et le namespace Java de MORPHEUS

- Statut : **Proposée — à accepter au bootstrap M1**
- Date : 22 juillet 2026
- Dépend de : ADR-0001, ADR-0016, ADR-0017
- Portée : coordonnées Maven, packages Java, noms de modules et compatibilité future

---

## 1. Contexte

M1 va créer la première fondation Java durable de MORPHEUS.

Avant le premier `pom.xml` et les premières classes, il faut distinguer plusieurs identités qui ne répondent pas au même besoin :

```text
nom produit       MORPHEUS
nom repository    morpheus-engine
Maven groupId     ?
Maven artifactId  ?
package Java      ?
commande CLI      morpheus
```

Les confondre rendrait les renommages et publications futures plus coûteux.

L'écosystème existant utilise déjà une convention où :

- le `groupId` Maven représente l'identité de publication ;
- le package Java représente le domaine du produit ;
- le nom GitHub/repository peut rester plus descriptif.

---

## 2. Décision proposée

### Produit

```text
MORPHEUS
```

### Repository

```text
morpheus-engine
```

### Maven groupId

```text
io.github.fturleque
```

### Artifact racine / distribution

```text
morpheus-engine
```

### Préfixe des modules Maven

```text
morpheus-
```

Exemples :

```text
morpheus-domain
morpheus-application
morpheus-provider-openspec
morpheus-store-memory
morpheus-store-sqlite
morpheus-cli
```

### Namespace Java racine

```text
com.morpheus
```

Packages candidats :

```text
com.morpheus.domain
com.morpheus.application
com.morpheus.provider.openspec
com.morpheus.store.memory
com.morpheus.store.sqlite
com.morpheus.cli
```

---

## 3. Pourquoi séparer `groupId` et package Java

Le `groupId` Maven répond principalement à :

- publication ;
- coordonnées d'artefacts ;
- unicité dans un repository Maven ;
- appartenance technique au propriétaire du projet.

Le package Java répond principalement à :

- organisation du code ;
- lisibilité du domaine ;
- stabilité des imports ;
- cohérence du produit.

Il n'est donc pas nécessaire d'imposer :

```text
io.github.fturleque.morpheus....
```

comme package Java uniquement parce que Maven utilise ce `groupId`.

---

## 4. Pourquoi `io.github.fturleque`

Cette coordonnée :

- représente explicitement le propriétaire GitHub ;
- évite de prétendre posséder un domaine DNS tiers ;
- reste stable si le repository technique change de nom ;
- peut regrouper plusieurs composants du même écosystème ;
- est déjà cohérente avec la convention Maven utilisée dans l'écosystème.

---

## 5. Pourquoi `com.morpheus`

MORPHEUS est l'identité fonctionnelle stable du produit.

Le package :

```text
com.morpheus
```

permet des imports lisibles :

```java
com.morpheus.domain.Requirement
com.morpheus.domain.ChangeProposal
com.morpheus.application.SpecificationService
```

sans exposer :

- GitHub ;
- le nom du repository ;
- le backend ;
- OpenSpec ;
- le propriétaire humain dans le domaine.

Le package `com.morpheus.domain` ne dépend donc pas de l'endroit où le code est hébergé.

---

## 6. Frontières de packages

Le namespace doit rendre les dépendances architecturales visibles.

### Domaine

```text
com.morpheus.domain
```

Ne dépend d'aucun package :

```text
com.morpheus.provider.*
com.morpheus.store.*
com.morpheus.cli
```

### Application

```text
com.morpheus.application
```

Peut dépendre du domaine et définir les ports nécessaires aux cas d'usage.

### Adapters

```text
com.morpheus.provider.*
com.morpheus.store.*
com.morpheus.cli
```

Dépendent vers l'intérieur, jamais l'inverse.

---

## 7. Package interne vs contrat public

Le package Java ne doit pas être considéré comme un contrat réseau.

Les futures API/MCP exposeront des DTO/contracts explicitement versionnés ; elles ne sérialiseront pas automatiquement les noms de classes Java comme type public.

Cela permet de refactorer l'organisation interne sans casser un protocole externe.

---

## 8. Alternatives étudiées

### A. `io.github.fturleque.morpheus.*` comme package Java

**Non retenue comme préférence initiale.**

Avantage : correspondance stricte avec le `groupId`.

Inconvénient : package plus verbeux et identité du domaine liée au propriétaire/hébergement technique sans bénéfice fonctionnel.

### B. `com.morpheus.engine.*`

**Non retenue.**

`engine` décrit le repository/composant technique mais n'apporte pas de distinction utile aux concepts du domaine.

### C. package par nom de module uniquement

Exemple :

```text
domain.*
application.*
```

**Rejeté.**

Namespace trop générique et propice aux collisions.

### D. `com.morpheus.*`

**Retenue.**

Simple, stable et alignée sur l'identité produit.

---

## 9. Invariants

1. `groupId = io.github.fturleque` ;
2. les artifactIds MORPHEUS utilisent le préfixe `morpheus-` ;
3. namespace Java racine = `com.morpheus` ;
4. aucun package `com.morpheus.domain` ne dépend d'un adapter ;
5. le repository `morpheus-engine` n'est pas encodé dans les packages métier ;
6. aucune classe OpenSpec n'apparaît dans `com.morpheus.domain` ;
7. aucun type SQLite/JDBC n'apparaît dans `com.morpheus.domain` ;
8. les DTO réseau futurs ne dépendent pas implicitement du fully-qualified name des classes du domaine.

---

## 10. Conséquences positives

- conventions immédiatement claires ;
- imports lisibles ;
- alignement avec la séparation produit / repository / publication ;
- aucun couplage GitHub dans les packages métier ;
- modules Maven nommés de manière homogène ;
- tests d'architecture simples à exprimer.

---

## 11. Conséquences négatives

- `groupId` et package Java ne sont pas identiques ;
- certains outils ou développeurs peuvent s'attendre à une correspondance stricte ;
- `com.morpheus` reste un nom générique qui doit être protégé contre les collisions à l'intérieur du workspace par la structure Maven.

Ces coûts sont essentiellement organisationnels.

---

## 12. Validation M1

Le bootstrap doit démontrer :

```text
io.github.fturleque:morpheus-domain
    -> package com.morpheus.domain

io.github.fturleque:morpheus-provider-openspec
    -> package com.morpheus.provider.openspec

io.github.fturleque:morpheus-store-sqlite
    -> package com.morpheus.store.sqlite
```

Un test d'architecture doit empêcher les dépendances :

```text
com.morpheus.domain -> com.morpheus.provider..
com.morpheus.domain -> com.morpheus.store..
com.morpheus.domain -> com.morpheus.cli..
```

---

## 13. Critère d'acceptation

Cette ADR peut passer à **Acceptée** dès que le premier bootstrap Maven M1 utilise ces coordonnées et que les tests d'architecture démontrent les frontières attendues.

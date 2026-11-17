---
id: BE-0145
title: "Une ressource d'un pair MCP se désigne explicitement, sans heuristique ni mutation du pair"
domain: backend
rules: false
---

# Une ressource d'un pair MCP se désigne explicitement, sans heuristique ni mutation du pair

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0070, ADR-0074

## Context

MORPHEUS joint deux pairs par MCP STDIO (ADR-0128) : MINOS, qui indexe des symboles de code, et NEXUS, qui construit
un contexte technique pour un projet (ADR-0075).

Une référence externe (ADR-0142) peut désigner un symbole de code indexé par MINOS. Une identité externe
doit rester distincte d'une identité MORPHEUS, d'un locator et d'une version (ADR-0168). La recherche MINOS est
lexicale : un nom simple, un `qualifiedName`, un fichier ou un score ne suffit pas à désigner un symbole sans
ambiguïté, et l'index MINOS évolue d'un snapshot à l'autre.

Un projet MORPHEUS et un projet NEXUS n'ont pas la même identité, et rien ne permet de déduire sûrement l'un de
l'autre. Le registre et l'index de NEXUS ont leur propre cycle de vie, coûteux à reconstruire.

Forme d'une cible de code MINOS, et table de résolution qui en découle :

```text
system       = MINOS
resourceType = SYMBOL
project      = identifiant ou nom unique MINOS
externalId   = symbolKey MINOS exact
revision     = activeSnapshotId MINOS attendu, optionnel

0 exact  -> NOT_FOUND, ou UNAVAILABLE si la recherche bornée a pu être tronquée
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
```

Une recherche qui a rendu une page pleine ne prouve rien sur ce qui se trouve au-delà. MINOS ne fournit ni total, ni
drapeau, ni curseur : seule une page qui atteint la limite demandée peut signaler une troncature possible.

## Decision

1. **Cible MINOS** : une cible `MINOS`/`SYMBOL` porte `project` (identifiant ou nom unique MINOS) et `externalId`
   (`symbolKey` MINOS exact), obligatoires, et une `revision` optionnelle (`activeSnapshotId` attendu). Une cible
   sans projet n'est pas résolue.
2. **Promotion exacte** : la recherche lexicale MINOS peut servir de récupération, mais la promotion en `FOUND` est
   exclusivement `result.symbolKey.equals(target.externalId)`. Zéro exact donne `NOT_FOUND`, ou `UNAVAILABLE` si la
   recherche bornée a pu être tronquée ; un exact donne `FOUND` ; plusieurs donnent `AMBIGUOUS`. Aucun nom simple,
   `qualifiedName`, fichier, module ou score lexical ne constitue seul une identité suffisante.
3. **Troncature** : `MinosCodeGateway.findSymbols` rend un `SymbolSearch(symbols, possiblyTruncated)`, vrai pour une
   page qui atteint la limite demandée ; une troncature n'est jamais présentée comme un symbole supprimé.
4. **Révision** : une `target.revision` présente est comparée à `minos_index_status.activeSnapshotId` avant la
   résolution ; un écart rend `REVISION_MISMATCH`, jamais masqué. La cible persistée reste telle quelle : la révision
   et l'identité observées (`activeSnapshotId`, `symbolKey`, projet) vont dans les attributs du résultat résolu.
5. **Projet NEXUS explicite** : chaque demande de contexte technique transporte `nexusProject` (UUID ou nom unique),
   transmis tel quel, jamais déduit du chemin du workspace, du nom du projet MORPHEUS ou d'une heuristique. Un projet
   NEXUS absent ou non indexé est une indisponibilité ou une erreur externe explicite.
6. **NEXUS non muté** : aucun workflow NEXUS de mutation (`project add`, `index`, `rebuild`, `remove`) n'est appelé,
   et `NexusMcpContextGateway` n'appelle aucun outil hors des outils requis qu'énumère ADR-0128 pour NEXUS : registre
   et index restent administrés par NEXUS ou un orchestrateur externe.

## Do's and Don'ts

### Do

- Exiger `project` et `externalId` pour une cible `MINOS`/`SYMBOL`.
- Ne promouvoir en `FOUND` qu'un résultat dont le `symbolKey` est exactement égal à `externalId`.
- Rendre `AMBIGUOUS` dès que plusieurs résultats ont le `symbolKey` exact.
- Rendre `UNAVAILABLE`, et non `NOT_FOUND`, quand aucune correspondance exacte n'est trouvée dans une recherche qui a
  pu être tronquée.
- Comparer la révision attendue à `activeSnapshotId` avant toute recherche, et rendre `REVISION_MISMATCH` en cas
  d'écart.
- Conserver la provenance MINOS (snapshot actif, `symbolKey`, projet) dans les attributs du résultat résolu.
- Exiger `nexusProject`, non vide, sur toute construction de contexte augmenté, et le transmettre tel quel à NEXUS.
- Rendre un projet NEXUS absent comme une indisponibilité ou une erreur externe explicite.

### Don't

- Ne pas promouvoir un résultat sur un nom simple, un `qualifiedName`, un fichier, un module ou un score lexical.
- Ne pas faire de correspondance floue.
- Ne pas lire une recherche possiblement tronquée comme une cible supprimée.
- Ne pas réécrire la cible persistée avec l'identité ou la révision canonique de MINOS.
- Ne jamais déduire le projet NEXUS d'un chemin, d'un nom de projet MORPHEUS ou d'une heuristique.
- N'appeler aucun outil NEXUS de mutation ou d'indexation (`project add`, `index`, `rebuild`, `remove`).
- N'appeler aucun outil NEXUS hors des outils requis qu'énumère ADR-0128.

## Consequences

### Positives

- Une référence de code résolue désigne un seul symbole, dans une révision connue.
- Un changement d'index MINOS est visible (`REVISION_MISMATCH`) au lieu d'être absorbé silencieusement.
- Aucune collision de nom silencieuse entre projets MORPHEUS et NEXUS.
- Aucune indexation coûteuse déclenchée par une lecture MORPHEUS.
- Le même projet MORPHEUS peut viser différents projets NEXUS selon l'appel.
- L'absence ou l'état non indexé du projet NEXUS est un fait externe explicite.

### Négatives

- Un renommage de symbole côté MINOS rend la référence `NOT_FOUND` : il n'y a pas de rattrapage approximatif.
- Une recherche tronquée rend `UNAVAILABLE` même quand le symbole n'existe réellement pas.
- Une page qui contient exactement autant de symboles que la limite est déclarée possiblement tronquée.
- L'appelant doit connaître et fournir l'identifiant du projet NEXUS à chaque appel.

### Risques

- La stabilité du `symbolKey` dépend de MINOS ; un changement de son format casserait toutes les références persistées.
- Une correspondance exacte unique trouvée dans une page tronquée reste `FOUND` : un second exact au-delà de la page
  ne serait pas vu.

## Compliance and Enforcement

- `morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/MinosMcpExternalReferenceResolverTest.java` :
  `resolvesOnlyOneExactSymbolKeyPreservesCoordinatesAndExportsDeterministicFacts`,
  `lexicalButNonExactResultIsNotPromotedAndDuplicateExactKeysAreAmbiguous`,
  `aFullPageWithoutTheExactSymbolIsUnavailableNeverNotFound`,
  `anExhaustiveSearchWithoutTheExactSymbolStaysNotFoundAndATruncatedOneStillFindsItsMatch`,
  `matchingRevisionIsPreservedAndMismatchOrUnsupportedTypeAreExplicit`.
- `morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/MinosBoundedSearchResolutionTest.java` :
  une recherche possiblement tronquée sans correspondance exacte n'est jamais lue comme une cible supprimée.
- `morpheus-application/src/test/java/com/morpheus/application/context/TechnicalContextOptionsTest.java` : projet
  NEXUS implicite refusé.
- `morpheus-integration-nexus/src/test/java/com/morpheus/integration/nexus/NexusMcpTechnicalContextProviderTest.java` :
  projet, budget, sources, contraintes et mode explication transmis inchangés ; échec de la passerelle rendu comme
  observation d'indisponibilité.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusAugmentedContextApiContractTest.java` : contrat HTTP du
  contexte augmenté.
- Revue : la passerelle NEXUS ne nomme aucun outil hors des trois constantes `TOOL_*` de `NexusMcpContextGateway`
  (aucune règle ne la tient).

## References

- [ADR-0007](ARCH-0007-cross-engine-integration.md) — intégration entre moteurs.
- [ADR-0168](DATA-0168-identite-de-domaine-generee-dans-l-ordre.md) — identité, version, locator et référence externe distincts.
- [ADR-0142](DATA-0142-references-externes.md) — résolution optionnelle des références externes.
- [ADR-0142](DATA-0142-references-externes.md) — références externes du snapshot.
- [ADR-0075](ARCH-0075-morpheus-intent-vs-nexus-technical-context.md) — intention MORPHEUS et contexte technique
  NEXUS.
- [ADR-0124](ARCH-0124-qualite-findings-et-faits-tri-etat.md) — ce qui n'est pas observé est dit.
- [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO, outils requis.
- [VALIDATION_M12](../../project/validation/VALIDATION_M12.md), [VALIDATION_M13](../../project/validation/VALIDATION_M13.md).

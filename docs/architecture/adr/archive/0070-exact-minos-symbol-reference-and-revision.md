# ADR-0070 — Référence MINOS par `symbolKey` exact et révision explicite

> **Archivée.** Cette décision est consolidée dans [ADR-0145](../BE-0145-ressource-de-pair-mcp-designee-explicitement.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0145**
- Date : 2026-07-24

## Context

Une référence externe (ADR-0026, ADR-0041) peut désigner un symbole de code indexé par MINOS, joint par MCP STDIO
(ADR-0128). Une identité externe doit rester distincte d'une identité MORPHEUS, d'un locator et d'une version
(ADR-0009). La recherche MINOS est lexicale : un nom simple, un `qualifiedName`, un fichier ou un score ne suffit
pas à désigner un symbole sans ambiguïté, et l'index MINOS évolue d'un snapshot à l'autre.

## Decision

Une cible de code MINOS utilise :

```text
system       = MINOS
resourceType = SYMBOL
project      = identifiant ou nom unique MINOS
externalId   = symbolKey MINOS exact
revision     = activeSnapshotId MINOS attendu, optionnel
```

`project` et `externalId` sont obligatoires.

### Résolution

Le resolver peut utiliser la recherche lexicale MINOS comme mécanisme de récupération, mais la promotion en `FOUND`
est exclusivement :

```text
result.symbolKey.equals(target.externalId)
```

Puis :

```text
0 exact  -> NOT_FOUND, ou UNAVAILABLE si la recherche bornée a pu être tronquée (ADR-0108)
1 exact  -> FOUND
>1 exact -> AMBIGUOUS
```

Une recherche qui a rendu une page pleine ne prouve rien sur ce qui se trouve au-delà : l'absence de correspondance
exacte n'y vaut `NOT_FOUND` que si la recherche était exhaustive.

Aucun nom simple, `qualifiedName`, fichier, module ou score lexical ne constitue seul une identité suffisante.

### Révision

Si `target.revision` est présente :

```text
minos_index_status.activeSnapshotId == target.revision
```

est obligatoire avant la résolution. Sinon : `REVISION_MISMATCH`. Une révision différente n'est jamais masquée.

### Coordonnées et provenance

La cible persistée est conservée telle quelle : la révision et l'identité canonique observées chez MINOS
(`activeSnapshotId`, `symbolKey`, projet) sont portées par les attributs du résultat résolu, jamais réécrites dans
la cible.

## Do's and Don'ts

### Do

- Exiger `project` et `externalId` pour une cible `MINOS`/`SYMBOL` ; une cible sans projet n'est pas résolue.
- Ne promouvoir en `FOUND` qu'un résultat dont le `symbolKey` est exactement égal à `externalId`.
- Rendre `AMBIGUOUS` dès que plusieurs résultats ont le `symbolKey` exact.
- Rendre `UNAVAILABLE`, et non `NOT_FOUND`, quand aucune correspondance exacte n'est trouvée dans une recherche qui a
  pu être tronquée.
- Comparer la révision attendue à `activeSnapshotId` avant toute recherche, et rendre `REVISION_MISMATCH` en cas
  d'écart.
- Conserver la provenance MINOS (snapshot actif, `symbolKey`, projet) dans les attributs du résultat résolu.

### Don't

- Ne pas promouvoir un résultat sur un nom simple, un `qualifiedName`, un fichier, un module ou un score lexical.
- Ne pas faire de correspondance floue.
- Ne pas réécrire la cible persistée avec l'identité ou la révision canonique de MINOS.

## Consequences

### Positives

- Une référence de code résolue désigne un seul symbole, dans une révision connue.
- Un changement d'index MINOS est visible (`REVISION_MISMATCH`) au lieu d'être absorbé silencieusement.

### Négatives

- Un renommage de symbole côté MINOS rend la référence `NOT_FOUND` : il n'y a pas de rattrapage approximatif.
- Une recherche tronquée rend `UNAVAILABLE` même quand le symbole n'existe réellement pas.

### Risques

- La stabilité du `symbolKey` dépend de MINOS ; un changement de son format casserait toutes les références persistées.
- Une correspondance exacte unique trouvée dans une page tronquée reste `FOUND` : un second exact au-delà de la page
  ne serait pas vu (ADR-0108).

## Compliance and Enforcement

- `morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/MinosMcpExternalReferenceResolverTest.java` :
  `resolvesOnlyOneExactSymbolKeyPreservesCoordinatesAndExportsDeterministicFacts`,
  `lexicalButNonExactResultIsNotPromotedAndDuplicateExactKeysAreAmbiguous`,
  `aFullPageWithoutTheExactSymbolIsUnavailableNeverNotFound`,
  `anExhaustiveSearchWithoutTheExactSymbolStaysNotFoundAndATruncatedOneStillFindsItsMatch`,
  `matchingRevisionIsPreservedAndMismatchOrUnsupportedTypeAreExplicit`.
- `morpheus-integration-minos/src/test/java/com/morpheus/integration/minos/MinosBoundedSearchResolutionTest.java` :
  une recherche possiblement tronquée sans correspondance exacte n'est jamais lue comme une cible supprimée.

## References

- [ADR-0009](0009-stable-domain-identity.md) — identité, version, locator et référence externe distincts.
- [ADR-0026](0026-optional-external-reference-resolution.md) — résolution optionnelle des références externes.
- [ADR-0041](0041-snapshot-external-reference-traceability.md) — références externes du snapshot.
- [ADR-0108](0108-a-response-says-what-it-could-not-observe.md) — une recherche bornée rend une indisponibilité.
- [ADR-0128](../ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [VALIDATION_M12](../../../project/validation/VALIDATION_M12.md)

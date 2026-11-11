---
id: ARCH-0075
title: "Séparer l'intention MORPHEUS du contexte technique NEXUS"
domain: architecture
rules: false
---

# Séparer l'intention MORPHEUS du contexte technique NEXUS

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

MORPHEUS possède les exigences, changements, contraintes, décisions et tâches. NEXUS possède la sélection, le ranking,
la fusion et la compression du contexte technique.

Copier le ranking NEXUS dans MORPHEUS créerait deux sources de vérité et rendrait les résultats impossibles à
attribuer.

## Decision

Le contexte augmenté sépare strictement :

```text
MORPHEUS intent context
!=
NEXUS technical ContextBundle
```

MORPHEUS compose uniquement une requête d'intention déterministe depuis le snapshot ACTIVE du projet
(`AugmentedContextService`).

Pour une exigence, l'intention porte :

```text
key
title
statement
```

Pour un changement, elle porte :

```text
key
title
intent
scope
affected requirements
constraints
design decisions
implementation tasks
```

Cette requête est transmise à NEXUS avec le projet, le budget, les sources, les contraintes et `explain`.

NEXUS retourne un `ContextBundle` déjà sélectionné. MORPHEUS le projette sans reranking, sans fusion supplémentaire et
sans recalcul de budget : les scores, raisons et exclusions du bundle sont conservés tels quels.

Le contexte NEXUS est une observation live :

```text
ACTIVE snapshot
 -> intent seed
 -> NEXUS context
 -> response
 -X-> KnowledgeSnapshot mutation
 -X-> persisted ContextBundle
```

La réponse expose `persisted=false` ; `AugmentedContextResult` refuse toute autre valeur. Quand NEXUS est absent ou
indisponible, la réponse le dit explicitement et garde l'intention MORPHEUS, sans inventer de contexte technique.

## Do's and Don'ts

### Do

- Composer l'intention d'une exigence ou d'un changement de façon déterministe, depuis le snapshot ACTIVE.
- Transmettre à NEXUS le projet, le budget, les sources, les contraintes et `explain` demandés.
- Conserver les scores, raisons et exclusions du bundle NEXUS tels que NEXUS les rend.
- Représenter explicitement l'absence ou l'indisponibilité de NEXUS.

### Don't

- Ne pas reclasser, fusionner ni recompresser le bundle NEXUS dans MORPHEUS.
- Ne pas appliquer un second budget technique au bundle NEXUS.
- Ne pas muter le snapshot ni persister le `ContextBundle`.
- Ne pas inventer de contexte technique quand NEXUS ne répond pas.

## Consequences

### Positives

- Chaque élément du contexte technique reste attribuable à NEXUS ; l'intention reste attribuable à MORPHEUS.
- Une seule source de vérité pour le ranking technique.

### Négatives

- La qualité du contexte technique dépend entièrement de NEXUS ; MORPHEUS ne la corrige pas.

### Risques

- Une évolution du contrat `ContextBundle` de NEXUS peut changer ce que MORPHEUS projette sans que MORPHEUS en décide.

## Compliance and Enforcement

- `morpheus-application/src/main/java/com/morpheus/application/context/AugmentedContextResult.java` : le constructeur
  compact refuse `persisted = true`.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusAugmentedContextApiContractTest.java` :
  `disabledNexusStillReturnsDeterministicRequirementAndChangeIntentWithoutMutatingActiveSnapshot` et
  `availableProviderReceivesBudgetSourcesConstraintsAndExplainWithoutMorpheusReranking`.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusM13McpStdioIntegrationTest.java` : les outils de contexte
  augmenté sont servis par STDIO sans NEXUS installé.
- Revue : l'absence de reranking, de fusion et de second budget dans tout nouveau code qui touche au bundle.

## References

- [ADR-0135](ARCH-0135-domaine-propre-et-neutralite-multi-provider.md) — domaine possédé par MORPHEUS.
- [ADR-0007](ARCH-0007-cross-engine-integration.md) — intégrations cross-engine par contrats explicites.
- [ADR-0145](BE-0145-ressource-de-pair-mcp-designee-explicitement.md) — correspondance explicite des projets NEXUS.
- [ADR-0123](ARCH-0123-vues-compactes-et-json-canonique.md) — vues compactes et JSON canonique.
- [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — projection du statut d'intégration sur les frontières
  distantes.
- [Validation M13](../../project/validation/VALIDATION_M13.md)

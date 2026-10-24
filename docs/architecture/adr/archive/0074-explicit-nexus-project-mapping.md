# ADR-0074 — Mapping projet NEXUS explicite sans ownership de lifecycle

> **Archivée.** Cette décision est consolidée dans [ADR-0145](../BE-0145-ressource-de-pair-mcp-designee-explicitement.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

- Statut : **Remplacée par ADR-0145**
- Date : 2026-07-24

## Context

MORPHEUS demande à NEXUS un contexte technique pour un projet (ADR-0075, ADR-0128). Un projet MORPHEUS et un projet
NEXUS n'ont pas la même identité, et rien ne permet de déduire sûrement l'un de l'autre. Le registre et l'index de
NEXUS ont leur propre cycle de vie, coûteux à reconstruire.

## Decision

Chaque demande de contexte technique transporte explicitement :

```text
nexusProject = UUID ou nom unique du projet NEXUS
```

MORPHEUS ne déduit pas ce mapping depuis le chemin du workspace, le nom du projet MORPHEUS ou une heuristique.

MORPHEUS n'appelle aucun workflow NEXUS de mutation :

```text
project add
index
rebuild
remove
```

La passerelle NEXUS n'utilise que les outils `list_projects`, `build_context` et `explain_context`
(`NexusMcpContextGateway`). Le registre et l'index NEXUS restent administrés par NEXUS ou par un orchestrateur
externe. Un projet NEXUS absent ou non indexé est rendu comme une indisponibilité ou une erreur externe explicite.

## Do's and Don'ts

### Do

- Exiger `nexusProject`, non vide, sur toute construction de contexte augmenté.
- Transmettre `nexusProject` tel quel à NEXUS.
- Rendre un projet NEXUS absent comme une indisponibilité ou une erreur externe explicite.

### Don't

- Ne jamais déduire le projet NEXUS d'un chemin, d'un nom de projet MORPHEUS ou d'une heuristique.
- N'appeler aucun outil NEXUS de mutation ou d'indexation (`project add`, `index`, `rebuild`, `remove`).
- N'appeler aucun outil NEXUS hors de `list_projects`, `build_context` et `explain_context`.

## Consequences

### Positives

- Aucune collision de nom silencieuse.
- Aucune indexation coûteuse déclenchée par une lecture MORPHEUS.
- Le même projet MORPHEUS peut viser différents projets NEXUS selon l'appel.
- L'absence ou l'état non indexé du projet NEXUS est un fait externe explicite.

### Négatives

- L'appelant doit connaître et fournir l'identifiant du projet NEXUS à chaque appel.

### Risques

- Aucun risque propre n'est consigné par cette décision au-delà des conséquences négatives.

## Compliance and Enforcement

- `morpheus-application/src/test/java/com/morpheus/application/context/TechnicalContextOptionsTest.java` : projet
  NEXUS implicite refusé.
- `morpheus-integration-nexus/src/test/java/com/morpheus/integration/nexus/NexusMcpTechnicalContextProviderTest.java` :
  projet, budget, sources, contraintes et mode explication transmis inchangés ; échec de la passerelle rendu comme
  observation d'indisponibilité.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusAugmentedContextApiContractTest.java` : contrat HTTP du
  contexte augmenté.
- Revue : la passerelle ne nomme aucun outil NEXUS hors des trois constantes `TOOL_*` de `NexusMcpContextGateway`
  (aucune règle ne la tient).

## References

- [ADR-0007](../ARCH-0007-cross-engine-integration.md) — intégration entre moteurs.
- [ADR-0075](../ARCH-0075-morpheus-intent-vs-nexus-technical-context.md) — intention MORPHEUS et contexte technique
  NEXUS.
- [ADR-0128](../ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [VALIDATION_M13](../../../project/validation/VALIDATION_M13.md)

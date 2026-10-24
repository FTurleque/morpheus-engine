---
id: ARCH-0077
title: "Frontière d'orchestration read-only MORPHEUS / JARVIS"
domain: architecture
rules: true
files: ["**/pom.xml"]
---

# Frontière d'orchestration read-only MORPHEUS / JARVIS

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

MORPHEUS connaît l'intention, les règles de cycle de vie, les contraintes et la qualité observable. JARVIS est
l'orchestrateur de l'écosystème.

Copier la machine d'état MORPHEUS dans JARVIS créerait deux sources de vérité. Faire appeler JARVIS depuis MORPHEUS
inverserait les responsabilités.

## Decision

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

La frontière cross-engine est un contrat machine read-only exposé par MORPHEUS et consommé par JARVIS. L'évaluation
d'une transition (`transition-check`) n'effectue aucune mutation.

MORPHEUS ne dépend d'aucun type, artefact Maven ou runtime `com.jarvis.*`.

JARVIS ne dépend d'aucun type `com.morpheus.*` ; il consomme du JSON via l'API HTTP locale. Son client MORPHEUS est
optionnel et fail-open.

Les mutations de cycle de vie contrôlées (ADR-0083) passent par des surfaces distinctes des surfaces d'évaluation ;
le contrat consommé par JARVIS reste read-only.

### Non-objectifs

```text
MORPHEUS choisit la prochaine action
MORPHEUS déclenche JARVIS
MORPHEUS écrit le lifecycle provider
JARVIS recode la machine d'état MORPHEUS
```

## Do's and Don'ts

### Do

- Exposer à JARVIS des faits, des règles et des décisions de transition, en lecture seule.
- Garder l'évaluation de transition sans effet de bord.
- Garder le client JARVIS optionnel et fail-open quand MORPHEUS est désactivé ou indisponible.

### Don't

- Ne pas faire dépendre une classe `com.morpheus..` d'une classe `com.jarvis..`.
- Ne pas déclarer d'artefact JARVIS dans un POM MORPHEUS.
- Ne pas faire choisir la prochaine action par MORPHEUS, ni lui faire déclencher JARVIS.
- Ne pas muter un état depuis une surface d'évaluation de transition.

## Consequences

### Positives

- Une seule source de vérité pour les règles de cycle de vie.
- Les deux moteurs évoluent sans dépendance de compilation entre eux.

### Négatives

- JARVIS ne réutilise aucun type MORPHEUS : son client porte ses propres DTO (ADR-0080).

### Risques

- L'absence de dépendance JARVIS -> MORPHEUS se vérifie dans le dépôt JARVIS, pas dans celui-ci.

## Compliance and Enforcement

- Règle Archgate `ARCH-0077-morpheus-jarvis-read-only-orchestration-boundary.rules.ts` : `no-jarvis-dependency`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`morpheusMustNotDependOnJarvisImplementation`) interdit toute dépendance de `com.morpheus..` vers `com.jarvis..`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/JarvisOrchestrationContractTest.java` : état
  d'orchestration sans cycle de vie inféré, `UNKNOWN` jamais converti en `BLOCKED`, abandon sans raison qui demande
  une entrée plutôt que de muter.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusJarvisOrchestrationApiContractTest.java`
  (`transitionCheckKeepsUnknownDistinctFromBlockedAndRequiresInputWithoutMutation`).
- Revue : absence d'artefact JARVIS dans les POMs.

## References

- [ADR-0007](ARCH-0007-cross-engine-integration.md) — intégration cross-engine.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie des changements.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) — catalogue MCP read-only.
- [ADR-0066](ARCH-0066-versioned-http-api-contract.md) — contrat HTTP versionné.
- [ADR-0080](ARCH-0080-jarvis-orchestration-surfaces-and-optional-client.md) — surfaces d'orchestration.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — mutations de cycle de vie contrôlées.
- [VALIDATION_M14](../../project/validation/VALIDATION_M14.md)

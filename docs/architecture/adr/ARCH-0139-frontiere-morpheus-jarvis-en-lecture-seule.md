---
id: ARCH-0139
title: "Frontière MORPHEUS / JARVIS : un contrat machine en lecture seule"
domain: architecture
rules: true
files: ["**/pom.xml"]
---

# Frontière MORPHEUS / JARVIS : un contrat machine en lecture seule

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0077, ADR-0080

## Context

MORPHEUS connaît l'intention, les règles de cycle de vie, les contraintes et la qualité observable ; il porte les
faits de cycle de vie et l'évaluation des transitions (ADR-0119). JARVIS est l'orchestrateur de
l'écosystème : il orchestre le travail autour d'un changement.

JARVIS doit pouvoir lire ces faits et demander une évaluation sans recoder les règles de cycle de vie, sans dépendance
binaire entre les deux dépôts, et sans que l'indisponibilité de MORPHEUS ne le bloque. Copier la machine d'état
MORPHEUS dans JARVIS créerait deux sources de vérité. Faire appeler JARVIS depuis MORPHEUS inverserait les
responsabilités.

## Decision

### 1. Partage des responsabilités

```text
MORPHEUS = facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

```text
MORPHEUS API          = source des règles et des faits
JARVIS client         = transport + DTO local
JARVIS orchestration  = choix de la prochaine action
```

Non-objectifs :

```text
MORPHEUS choisit la prochaine action
MORPHEUS déclenche JARVIS
MORPHEUS écrit le lifecycle provider
JARVIS recode la machine d'état MORPHEUS
```

### 2. Un contrat machine en lecture seule, un use case, trois adaptateurs

La frontière entre les deux moteurs est un contrat machine en lecture seule exposé par MORPHEUS et consommé par
JARVIS. MORPHEUS expose le même use case d'orchestration par la CLI, MCP et HTTP : lire l'état d'orchestration d'un
changement, et évaluer une transition. Les surfaces exactes sont les lignes `orchestration.state` et
`orchestration.transition` de `contracts/public-surfaces.tsv`, toutes deux d'intention `READ`.

Les outils MCP d'orchestration sont en lecture seule. L'évaluation d'une transition (`transition-check`) n'effectue
aucune mutation : la route HTTP `POST .../transition-check` est une requête de calcul, et un verdict `ALLOWED` n'est
pas appliqué. Les mutations de cycle de vie contrôlées relèvent d'ADR-0083 et passent par des surfaces distinctes des
surfaces d'orchestration.

### 3. Client JARVIS optionnel

Le dépôt JARVIS porte un client HTTP optionnel qui :

- ne connaît que l'URL de MORPHEUS et un `projectId` configurable ;
- lit l'état d'orchestration ;
- demande une évaluation de transition ;
- ne recode aucune règle de cycle de vie ;
- échoue ouvert lorsque MORPHEUS est désactivé ou indisponible.

JARVIS ne dépend d'aucun type `com.morpheus.*` : il consomme du JSON via l'API HTTP locale, avec ses propres DTO. Les
raisons d'abandon (observation, source, cible) sont transmises explicitement et séparément.

### 4. Aucune dépendance entre les dépôts

MORPHEUS ne dépend d'aucun type, artefact Maven ou runtime `com.jarvis.*`. Aucune dépendance Maven n'existe entre les
deux dépôts. Aucun runtime JARVIS n'est embarqué dans MORPHEUS ; aucun runtime MORPHEUS n'est embarqué dans JARVIS.

## Do's and Don'ts

### Do

- Exposer à JARVIS des faits, des règles et des décisions de transition, en lecture seule.
- Exposer l'état d'orchestration et l'évaluation de transition de façon cohérente sur la CLI, MCP et HTTP, et les
  déclarer dans `contracts/public-surfaces.tsv`.
- Garder l'évaluation de transition sans effet : elle rend un verdict, elle ne l'applique pas.
- Transmettre séparément les raisons d'abandon d'observation, de source et de cible.
- Garder le client JARVIS optionnel et fail-open quand MORPHEUS est désactivé ou indisponible.

### Don't

- Ne pas faire dépendre une classe `com.morpheus..` d'une classe `com.jarvis..`.
- Ne pas déclarer d'artefact JARVIS dans un POM MORPHEUS, ni embarquer le runtime de l'un dans l'autre.
- Ne pas faire choisir la prochaine action par MORPHEUS, ni lui faire déclencher JARVIS.
- Ne pas muter un état depuis une surface d'évaluation de transition, ni faire porter une écriture du cycle de vie
  par les surfaces d'orchestration.

## Consequences

### Positives

- Une seule source de vérité pour les règles de cycle de vie : JARVIS consomme les règles et les faits de MORPHEUS
  sans les dupliquer.
- Les deux moteurs évoluent sans dépendance de compilation entre eux, restent livrables et déployables séparément ;
  JARVIS fonctionne sans MORPHEUS.

### Négatives

- JARVIS ne réutilise aucun type MORPHEUS : son client maintient ses propres DTO, alignés à la main sur le contrat
  HTTP de MORPHEUS.

### Risques

- L'absence de dépendance JARVIS -> MORPHEUS se vérifie dans le dépôt JARVIS, pas dans celui-ci.
- Le client JARVIS vit hors de ce dépôt : aucun contrôle de MORPHEUS ne vérifie qu'il reste optionnel et fail-open.

## Compliance and Enforcement

- Règle Archgate `ARCH-0139-frontiere-morpheus-jarvis-en-lecture-seule.rules.ts` : `no-jarvis-dependency`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LayerDependencyTest.java`
  (`morpheusMustNotDependOnJarvisImplementation`) interdit toute dépendance de `com.morpheus..` vers `com.jarvis..`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/JarvisOrchestrationContractTest.java` : état
  d'orchestration sans cycle de vie inféré, `UNKNOWN` jamais converti en `BLOCKED`, abandon sans raison qui demande
  une entrée plutôt que de muter.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusJarvisOrchestrationApiContractTest.java`
  (`transitionCheckKeepsUnknownDistinctFromBlockedAndRequiresInputWithoutMutation`) et
  `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusJarvisOrchestrationCliTest.java` exercent les surfaces HTTP et
  CLI.
- `PublicHttpRouteConvergenceTest` (`morpheus-architecture-tests/src/test/java/com/morpheus/api/`),
  `PublicSurfaceManifestCoversEveryServedToolTest` (`.../com/morpheus/mcp/`) et
  `PublicSurfaceManifestCoversEveryServedCliCommandTest` (`.../com/morpheus/cli/`) comparent les surfaces servies au
  manifeste.
- Revue : absence de runtime JARVIS embarqué ; comportement du client JARVIS.

## References

- [ADR-0007](ARCH-0007-cross-engine-integration.md) — intégration cross-engine.
- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat CLI local stable.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) — contrat des outils MCP.
- [ADR-0066](ARCH-0066-versioned-http-api-contract.md) — contrat HTTP `/api/v1`.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — opérations d'écriture contrôlées du cycle de vie.
- [ADR-0119](BE-0119-cycle-de-vie-des-changements-et-transition-tri-etat.md) — cycle de vie et transition tri-état.
- Preuve : [VALIDATION_M14](../../project/validation/VALIDATION_M14.md).

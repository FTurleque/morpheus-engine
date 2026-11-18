# Proposition : dessiner la vue en blocs depuis les dépendances de compilation des POM (AUD-ARC-24)

## Why

Constat `AUD-ARC-24` (Moyenne), `docs/project/audits/2026-11-17/findings.md`, revérifié le 17/11/2026 sur `1a539c22`.
`docs/architecture/arc42/05-vue-blocs.md` s'annonce construit depuis le POM racine et les POM des modules, mais :

- §5.1 dessine `POS --> PSDk`, `PMD --> PSDk`, `PSYN --> PSDk` (et §5.4 `O --> SDK`, `M --> SDK`), alors qu'aucun POM de
  provider intégré ne déclare `morpheus-provider-sdk` et qu'`AdapterSiblingArchitectureTest.BUILT_IN_PROVIDERS_IGNORE_THE_SDK`
  l'interdit (« ADR-0028: the built-in providers do not depend on the SDK ») ;
- §5.1 omet treize arêtes de compilation réelles : `api` → openspec, SDK, store-sqlite ; `mcp` → SDK, store-sqlite ;
  `cli` → api, mcp, intégration MINOS, intégration NEXUS, openspec, markdown, SDK, store-sqlite. Ces arêtes comprennent
  les exceptions nommées d'`ARCH-0162` (`api`/`mcp` → `store-sqlite`, `api` → openspec) et tout le câblage du processus.

Seul `RepositoryDocumentationCoherenceTest` lit la page, et seulement pour la pile technique et la liste des modules :
rien ne compare ses arêtes aux POM. Le seul schéma de dépendances de la documentation d'architecture affirme le
contraire d'une règle exécutable sur la frontière SDK / providers.

## What Changes

- §5.1 et §5.4 de `05-vue-blocs.md` redessinés depuis les POM : providers intégrés → application, SDK → application,
  plugin de référence → SDK ; `api`/`mcp` → `store-sqlite` et `api` → openspec annotées `ARCH-0162` ; `cli` vers chaque
  module qu'il compile.
- Nouveau contrôle de cohérence (`@CoherenceCheck`) : chaque arête Mermaid entre deux modules du réacteur de la page est
  une dépendance de compilation de leurs POM, et chaque module du diagramme atteint, par ses arêtes, tout ce que ses
  POM lui font atteindre.
- Aucune surface publique, aucun code de production ne change.

## Capabilities

### New Capabilities

### Modified Capabilities
- `reactor-module-documentation` : les diagrammes de dépendances de la vue en blocs égalent les dépendances de
  compilation des POM.

## Périmètre et exclusions

**Inclus :** `docs/architecture/arc42/05-vue-blocs.md` §5.1 et §5.4, un test de cohérence du module
`morpheus-architecture-tests`, la page durable de la capacité.

**Exclus :** le diagramme de `.claude/CLAUDE.md` (surface IA, hors `docs/`) ; les autres pages d'architecture, sans
diagramme de dépendances entre modules ; les arêtes que changeront `AUD-ARC-27` (`mcp` → SDK) et `AUD-ARC-30`
(module hôte éventuel) : le contrôle les fera suivre au change qui les modifie.

## Acteurs et surfaces concernés

Lecteurs et agents qui raisonnent sur la vue en blocs ; mainteneur ; CI.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Trois arêtes provider → SDK dessinées sont fausses | existant vérifié | `05-vue-blocs.md:50-52`, `:155-156` ; POM des trois providers ; `AdapterSiblingArchitectureTest.java:73` |
| Treize arêtes de compilation manquent | existant vérifié | relevé POM du 17/11/2026, identique à l'audit (comparaison des atteignabilités) |
| Aucun test ne compare les arêtes de la page aux POM | existant vérifié | `RepositoryDocumentationCoherenceTest` |
| Règle de comparaison : arêtes directes réelles, atteignabilité égale | proposition à valider | design.md D2 |

## Impact

`docs/architecture/arc42/05-vue-blocs.md` ; `morpheus-architecture-tests` (test ajouté, marqué `@CoherenceCheck`,
justification de PR) ; page durable `reactor-module-documentation`. Change à reprendre si `AUD-ARC-27` ou `AUD-ARC-30`
s'appliquent avant lui : le contrôle dira quelles arêtes suivre.

## Questions ouvertes

Aucune.

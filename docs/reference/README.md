# Références MORPHEUS

Cette section indexe les contrats machine et les références stables de la baseline **M20 / MORPHEUS 1.0.0 publiée**, complétées par les contrats du candidat **M21 en qualification**.

Les fichiers d’index expliquent et orientent ; ils ne remplacent pas les sources machine normatives.

## Contrat de convergence public M21

- [Public surfaces — explication](PUBLIC_SURFACES.md)
- [Manifeste machine `contracts/public-surfaces.tsv`](../../contracts/public-surfaces.tsv)
- [Production Integrity](../developer/PRODUCTION_INTEGRITY.md)

Le manifeste TSV est la source machine M21 pour les capabilities critiques, leur intention `READ`/`WRITE`, leurs formes CLI/MCP/HTTP et les asymétries explicitement déclarées.

## API HTTP

- [Guide développeur API](../developer/API.md)
- [OpenAPI 3.1 — `morpheus-v1.yaml`](../openapi/morpheus-v1.yaml)

Base stable : `/api/v1`.

Surfaces de production acquises jusqu’à M20 incluent notamment :

```text
GET /api/v1/version
GET /api/v1/health
GET /api/v1/readiness
GET /api/v1/metrics
GET /api/v1/projects/{projectId}/composition
GET /api/v1/projects/{projectId}/composition/conflicts
```

M21 n’expose volontairement pas un endpoint HTTP générique de découverte d’update à URI arbitraire. Cette asymétrie est enregistrée `EXPLICITLY_NOT_EXPOSED` dans le manifeste public.

## MCP

- [Guide développeur MCP](../developer/MCP.md)
- M21 ajoute `get_product_info` ;
- M21 ajoute `check_product_update` en lecture explicite ;
- `apply_change_lifecycle_transition` reste une capability write séparée ;
- `evaluate_change_transition` reste read-only : `ALLOWED != applied`.

## CLI

- [Référence CLI utilisateur](../user/CLI.md)

Surfaces produit M21 :

```text
morpheus version
morpheus product-info
morpheus update-check --manifest URI_OR_PATH
```

`update-check` ne télécharge, n’installe et ne remplace jamais MORPHEUS.

## Providers et composition

Providers réels de la baseline 1.0 :

```text
OpenSpec
Structured Markdown
```

La composition conserve identité provider-scoped, provenance, priorité et conflits explicites. Elle n’utilise pas de last-write-wins silencieux.

## Persistance

Baseline SQLite 1.0 : **V012**, couvrant notamment l’état de composition M18 en plus des contrats persistants antérieurs.

M21 n’ajoute pas de migration métier : les métadonnées de build/release et la découverte d’update restent distinctes de l’état métier persistant.

## Architecture et décisions

- [Architecture développeur](../developer/ARCHITECTURE.md)
- [Index ADR](../adr/README.md)
- [ADR-0088 — release/installation/data separation](../adr/0088-product-release-installation-and-persistent-data-separation.md)
- [ADR-0089 — production integrity & surface convergence](../adr/0089-production-integrity-surface-convergence.md) — **Proposée tant que M21-S8 n’est pas verte**

## Intégrations

- [Guide utilisateur](../user/INTEGRATIONS.md)
- [Contrats développeur](../developer/INTEGRATIONS.md)

MINOS, NEXUS et JARVIS restent optionnels et conservent leurs frontières de responsabilité.

## Baseline publiée M20 / 1.0.0

```text
M20 code qualifié   9199ed43c4bd8596a97db055eeff17ae31399eb8
M20 merge           75d0b82ab0c960692db2fee1ced146fa6547fd4a
release 1.0.0 SHA   51f6a120f3461c8d8c24323f3db8211d28d6cb42
tests               454/454 PASS Windows + Linux
architecture         182/182 PASS Windows + Linux
reactor              14/14 SUCCESS
portable Windows     PASS
portable Linux       PASS
checksums            PASS
```

## Preuves

- [Index des validations](../validation/README.md)
- [Validation M20](../validation/VALIDATION_M20.md)
- [Validation M21](../validation/VALIDATION_M21.md) — **actuellement bloquée, pas PASS**
- [Plan M21](../roadmap/M21_EXECUTION.md)
- [Roadmap 1.x active](../roadmap/POST_M20_EVOLUTION.md)
- [Gouvernance](../governance/ROADMAP.md)

Les preuves historiques restent immuables. Une configuration présente dans le dépôt n’est jamais comptée comme PASS sans exécution réelle du gate correspondant.

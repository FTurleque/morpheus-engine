# Références MORPHEUS

Cette section indexe les contrats machine et les références stables de la baseline **M18 intégrée**.

## API HTTP

- [Guide développeur API](../developer/API.md)
- [OpenAPI 3.1 — `morpheus-v1.yaml`](../openapi/morpheus-v1.yaml)

Version du contrat OpenAPI actuel : **`1.7.0`**.

M18 ajoute au contrat machine les projections provider-neutral de composition :

```text
GET /api/v1/projects/{projectId}/composition
GET /api/v1/projects/{projectId}/composition/conflicts
```

Les évolutions précédentes restent additives : acceptance/verification/evidence M15, constraint policy M16 et controlled lifecycle write M17.

## MCP

- [Guide développeur MCP](../developer/MCP.md)
- catalogue actuel : **22 tools read-only + 1 tool write explicite** ;
- M18 ajoute `get_composition_status` et `list_composition_conflicts` ;
- le seul tool write reste `apply_change_lifecycle_transition` ;
- `evaluate_change_transition` reste read-only : `ALLOWED != applied`.

## CLI

- [Référence CLI utilisateur](../user/CLI.md)

Surfaces M18 :

```text
composition sync
composition status
composition conflicts
```

## Providers et composition

Providers réels validés ensemble :

```text
OpenSpec
Structured Markdown
```

La composition conserve identité provider-scoped, provenance, priorité et conflits explicites. Elle n’utilise pas de last-write-wins silencieux.

## Persistance

Baseline SQLite courante : **V012** pour l’état de composition M18, en plus des contrats persistants antérieurs.

## Architecture et décisions

- [Architecture développeur](../developer/ARCHITECTURE.md)
- [Index ADR](../adr/README.md)
- [ADR-0084 — composition multi-provider provider-neutral](../adr/0084-provider-neutral-multi-provider-composition.md)

## Intégrations

- [Guide utilisateur](../user/INTEGRATIONS.md)
- [Contrats développeur](../developer/INTEGRATIONS.md)

MINOS, NEXUS et JARVIS restent optionnels et conservent leurs frontières de responsabilité.

## Preuves historiques

- [Index des validations](../validation/README.md)
- [Validation M18](../validation/VALIDATION_M18.md)
- [Plan M18](../roadmap/M18_EXECUTION.md)
- [Roadmaps d’exécution](../roadmap/)
- [Gouvernance](../governance/README.md)

Baseline M18 :

```text
code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
merge           30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
tests           418/418 PASS
architecture    170/170 PASS
packaging       PASS
```

Les preuves détaillées par jalon sont regroupées sous `docs/validation/` et conservent le contexte exact du gate exécuté.
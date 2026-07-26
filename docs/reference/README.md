# Références MORPHEUS

Cette section indexe les contrats machine et les références stables de la baseline **M18 validée et intégrée**.

## API HTTP

- [Guide développeur API](../developer/API.md)
- [OpenAPI 3.1 — `morpheus-v1.yaml`](../openapi/morpheus-v1.yaml)

Version actuelle du contrat : **1.7.0**.

M18 ajoute au contrat machine :

```text
GET /api/v1/projects/{projectId}/composition
GET /api/v1/projects/{projectId}/composition/conflicts
```

Les projections restent provider-neutral et conservent provenance, priorité et conflits explicites.

## MCP

- [Guide développeur MCP](../developer/MCP.md)
- catalogue actuel : **22 tools read-only + 1 tool write M17 explicite**.

M18 ajoute :

```text
get_composition_status
list_composition_conflicts
```

Le tool write reste uniquement :

```text
apply_change_lifecycle_transition
```

`READ_CHANGES != WRITE_CHANGE` et `ALLOWED != applied`.

## CLI

- [Référence CLI utilisateur](../user/CLI.md)

Surfaces M18 :

```text
composition sync
composition status
composition conflicts
```

## Persistance

SQLite M18 : **V012 — multi-provider composition**.

```text
composition state is snapshot-scoped
precedence != provenance erasure
conflict != silent last-write-wins
```

## Architecture et décisions

- [Architecture développeur](../developer/ARCHITECTURE.md)
- [Index ADR](../adr/README.md)
- [ADR-0084](../adr/0084-provider-neutral-multi-provider-composition.md) — composition multi-provider provider-neutral, déterministe et explicable.

## Intégrations

- [Guide utilisateur](../user/INTEGRATIONS.md)
- [Contrats développeur](../developer/INTEGRATIONS.md)

## Baseline de preuve

```text
M18             ✅ VALIDÉ / INTÉGRÉ — PR #86
Code validé     7e8caacff567f51354fcb88bd7505a6d135071c0
Merge           30f11ac3ffc522bcc0c71e31216a3fb70f0631d7
Tests           418/418 PASS
Architecture    170/170 PASS
Packaging       Windows + smokes + API health PASS
```

## Preuves historiques

- [Index des validations](../validation/README.md)
- [Roadmaps d’exécution](../roadmap/)
- [Gouvernance](../governance/README.md)

Les preuves détaillées par jalon sont regroupées sous `docs/validation/`. Elles conservent le SHA et le gate réellement exécutés, indépendamment de l’état GitHub postérieur.
# Morpheus Engine

## Règles à respecter (chargées automatiquement)

@.claude/rules/architecture.md
@.claude/rules/testing.md
@.claude/rules/security.md
@.claude/rules/governance.md
@.claude/rules/build.md
@.claude/rules/code-style.md

---

## Identité

`io.github.fturleque:morpheus-engine` **1.2.1** — Specification & Intent Intelligence Engine
Java 21 · Maven 3.9.16 · 16 modules · local-first · **sans framework** · **sans Docker**

## Principe directeur

**Les règles de ce projet sont exécutables.** 70 tests d'architecture dans `morpheus-architecture-tests`
assertent la structure, les contrats, la sécurité et jusqu'au contenu textuel de fichiers précis.
96 ADRs dans `docs/adr/` documentent le *pourquoi*.

> Avant de décider quoi que ce soit de structurel : **lire le test ArchUnit concerné**, puis l'ADR.
> Ne jamais deviner une règle — elle est écrite quelque part et vérifiable.

## Architecture : ports & adapters

`application` définit les **ports** et ne connaît **aucun** adaptateur.
Les adaptateurs dépendent vers l'intérieur. Les adaptateurs sont **frères** — ils ne s'appellent pas entre eux.

```
                  ┌──────────────────────────────┐
                  │      morpheus-domain         │  ← modèle pur, 22 packages
                  └──────────────▲───────────────┘
                  ┌──────────────┴───────────────┐
                  │   morpheus-application       │  ← ports + use cases, 29 packages
                  └──▲────▲────▲────▲────▲────▲──┘
      ┌──────────────┘    │    │    │    │    └──────────────┐
┌─────┴─────┐ ┌───────────┴┐ ┌─┴────┴─┐ ┌┴──────────┐ ┌──────┴──────┐
│ provider-*│ │  store-*   │ │api│mcp │ │integration│ │provider-sdk │
└───────────┘ └────────────┘ └───▲────┘ └───────────┘ └─────────────┘
                                 │
                          ┌──────┴──────┐
                          │ morpheus-cli│  ← câblage explicite uniquement
                          └─────────────┘
```

## Commandes

```bash
./mvnw clean verify                                       # reactor complet
./mvnw test -pl morpheus-architecture-tests               # tous les gates
./mvnw test -pl morpheus-architecture-tests -Dtest=*M28*  # gate courant
./mvnw dependency:analyze                                 # 0 warning exigé
```

Validation milestone : `scripts/validate-m<N>.ps1` (Windows) · `scripts/validate-m<N>.sh` (Linux)
Dispatcher : `scripts/validate.cmd` → `scripts/validate.ps1`

## Milestones

Milestone courant : **M28** (intégration client MCP native).
Gates actifs : **M19** (perf) · **M20** (release) · **M21** (coverage + intégrité) · **M22** (plugins)
· **M23** (portfolio) · **M24** (query DSL) · **M25** (policy) · **M26** (remote) · **M27** (reasoning) · **M28** (MCP clients) · **D2** (hardening repo)

## Points d'entrée clés

| Fichier | Rôle |
|---|---|
| `morpheus-cli/.../MorpheusMain.java` | Câblage complet de l'application |
| `morpheus-api/.../MorpheusHttpServer.java` | Serveur local (loopback obligatoire) |
| `morpheus-api/.../MorpheusRemoteHttpServer.java` | Serveur remote (TLS + RBAC) |
| `morpheus-application/.../product/ProductMetadata.java` | Source unique de la version |
| `contracts/public-surfaces.tsv` | Manifeste de convergence CLI/MCP/HTTP |
| `morpheus-architecture-tests/.../LayerDependencyTest.java` | Règles de couches |

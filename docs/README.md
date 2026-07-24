# Documentation MORPHEUS

Cette page est le point d’entrée de la documentation active de MORPHEUS.

MORPHEUS est un **Specification & Intent Intelligence Engine** local-first. Il normalise des spécifications, publie des snapshots versionnés, expose des requêtes et de la traçabilité, produit des diagnostics qualité, analyse les changements et fournit des contrats d’intégration optionnels pour MINOS, NEXUS et JARVIS.

## Je veux utiliser MORPHEUS

Commencer ici :

- [Guide utilisateur](user/README.md)
- [Démarrage rapide](user/QUICKSTART.md)
- [Référence CLI](user/CLI.md)
- [Intégrations optionnelles](user/INTEGRATIONS.md)

Les distributions portables Windows/Linux embarquent leur runtime Java. L’utilisateur final n’a pas besoin d’installer un JDK pour exécuter l’archive portable.

## Je veux développer MORPHEUS

Commencer ici :

- [Guide développeur](developer/README.md)
- [Architecture](developer/ARCHITECTURE.md)
- [Build, tests et validation](developer/BUILD_AND_TEST.md)
- [API HTTP](developer/API.md)
- [Serveur MCP](developer/MCP.md)
- [Intégrations cross-engine](developer/INTEGRATIONS.md)

Baseline technique actuelle : Java 21, Maven Wrapper, SQLite, Java MCP SDK 2.0.0 et API HTTP basée sur `jdk.httpserver`.

## Produit et spécification

- [`product/CAHIER_DES_CHARGES.md`](product/CAHIER_DES_CHARGES.md) — cadrage fonctionnel et technique de haut niveau ;
- [`product/USE_CASES.md`](product/USE_CASES.md) — cas d’usage ;
- [`product/MVP.md`](product/MVP.md) — périmètre MVP historique.

## Gouvernance et preuves

- [`governance/README.md`](governance/README.md) — index de gouvernance ;
- [`governance/ROADMAP.md`](governance/ROADMAP.md) — état global des jalons ;
- [`governance/PLAN.md`](governance/PLAN.md) — plan de travail de cadrage ;
- [`governance/AUDIT_COHERENCE_C0.md`](governance/AUDIT_COHERENCE_C0.md) — audit C0 ;
- [`validation/`](validation/) — preuves de validation C0 et M0 à M14 ;
- [`roadmap/`](roadmap/) — plans d’exécution historiques par jalon ;
- [`adr/`](adr/) — Architecture Decision Records.

## Références machine

- [`reference/`](reference/) — index des contrats ;
- [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml) — contrat OpenAPI machine-readable ;
- [`../distribution/README.md`](../distribution/README.md) — construction et packaging des distributions.

## État livré

```text
C0 → M14       ✅ validés
M3 → M14       ✅ intégrés
M14            ✅ 357/357 PASS
Architecture   ✅ 160/160 PASS
Packaging Win  ✅ PASS
JARVIS         ✅ 536 tests BUILD SUCCESS
```

M14 maintient la frontière suivante :

```text
MORPHEUS = specification facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

## Règle de rangement

La racine `docs/` ne contient que ce portail. Les documents sont classés par usage (`user`, `developer`, `product`, `reference`) ou par gouvernance (`governance`, `validation`, `roadmap`, `adr`).
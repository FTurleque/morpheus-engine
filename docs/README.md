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

## Références et gouvernance

Ces documents sont importants, mais ne constituent pas le parcours principal utilisateur/développeur :

- [Roadmap](ROADMAP.md) — état des jalons et preuves de validation ;
- [`roadmap/`](roadmap/) — plans d’exécution historiques par jalon ;
- [`adr/`](adr/) — Architecture Decision Records ;
- `VALIDATION_M*.md` — preuves de gate reproductibles ;
- [`openapi/morpheus-v1.yaml`](openapi/morpheus-v1.yaml) — contrat OpenAPI machine-readable ;
- [`../distribution/README.md`](../distribution/README.md) — construction et packaging des distributions.

## État livré

```text
C0 → M14  ✅ validés
M3 → M14  ✅ intégrés
M14       ✅ 357/357 PASS
Architecture 160/160 PASS
Packaging Windows PASS
JARVIS cross-repo 536 tests BUILD SUCCESS
```

M14 maintient la frontière suivante :

```text
MORPHEUS = specification facts + lifecycle rules + transition decisions
JARVIS   = sequencing + orchestration + action choice
```

## Compatibilité des anciens liens

Les anciens chemins `docs/CLI.md`, `docs/API.md`, `docs/MCP.md`, `docs/MINOS.md`, `docs/NEXUS.md` et `docs/JARVIS.md` sont conservés comme pages de redirection documentaire. Les nouveaux documents actifs vivent sous `docs/user/` et `docs/developer/`.

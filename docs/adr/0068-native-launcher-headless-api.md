# ADR-0068 — Launcher/distribution headless avec `jdk.httpserver` embarqué

- Statut : **Acceptée — M11**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0061, ADR-0064, ADR-0065
- Portée : M11 — lancement et packaging API

## Décision

Le launcher portable reste unique :

```text
morpheus <CLI command>
morpheus mcp --stdio
morpheus api --host 127.0.0.1 --port 8765
```

L'API réutilise le layout M9 :

```text
--data-dir
--config-dir
--db
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

## Runtime

Le module `morpheus-api` devient une dépendance de `morpheus-cli` afin d'être présent dans le shaded JAR.

Le runtime jpackage M11 ajoute explicitement :

```text
--add-modules jdk.httpserver
```

## Packaging proof

Le build portable vérifie au minimum :

```text
com/morpheus/api/MorpheusHttpServer.class
com/morpheus/api/MorpheusApiService.class
```

et réalise un smoke HTTP réel sur le launcher packagé :

```text
GET /api/v1/health -> 200
```

## Process lifecycle

Le mode API :

- démarre le serveur ;
- écrit les diagnostics de démarrage sur stderr, pas dans les réponses ;
- bloque le process jusqu'à interruption ;
- ferme proprement le serveur à l'arrêt du launcher.

## Critères d'acceptation

1. routage launcher `api` ;
2. `--host` / `--port` validés ;
3. CLI et MCP inchangés ;
4. shaded JAR contient l'API ;
5. runtime embarqué contient `jdk.httpserver` ;
6. app-image démarre l'API ;
7. health smoke packagé vert ;
8. aucune installation JDK requise côté utilisateur final.

## Preuve d'acceptation — 24 juillet 2026

Sur le head `a7daa9bb7eef1799926ea20b9e96606a388a301f` :

```text
MorpheusMainTest 7/7 PASS
TOTAL           314/314 PASS
BUILD SUCCESS
```

Puis `distribution/build-portable.ps1` :

```text
uber-JAR BUILD SUCCESS
MCP/API packaging proof: PASS
jpackage app-image + jdk.httpserver: PASS
MORPHEUS 0.1.0-SNAPSHOT
{"version":"0.1.0-SNAPSHOT"}
Packaged API health smoke: PASS
Portable archive creation: PASS (attempt 1/8, 33533017 bytes)
```

Artefact :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
```

Le ZIP contient son runtime Java, MCP STDIO et l'API HTTP ; aucun JDK séparé n'est nécessaire côté utilisateur final.

Décision : **ADR-0068 ACCEPTÉE — M11**. Voir `docs/VALIDATION_M11.md`.
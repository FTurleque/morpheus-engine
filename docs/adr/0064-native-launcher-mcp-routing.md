# ADR-0064 — Routage MCP dans le launcher natif M9

- Statut : **Acceptée — M10**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0061, ADR-0062
- Portée : M10 — lancement et distribution

## Contexte

M9 produit un launcher portable `morpheus`/`morpheus.exe` contenant son runtime Java. Créer un second mécanisme d'installation pour MCP compliquerait inutilement la distribution locale.

## Décision

Étendre le launcher officiel avec :

```text
morpheus mcp --stdio
```

Les options globales M9 de localisation des données restent applicables :

```text
--data-dir
--config-dir
--db
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Le serveur MCP ouvre la même base SQLite que la CLI et reste read-only au niveau des tools exposés.

## Discipline des flux

En mode MCP :

```text
stdout = MCP JSON-RPC uniquement
stderr = diagnostics runtime uniquement
```

Aucun help, banner, log ou message de démarrage ne doit polluer stdout.

## Packaging

Le module MCP est une dépendance du module CLI afin que le shaded JAR M9 et les app-images Windows/Linux embarquent le serveur et le SDK sans nouveau runtime externe.

Le build portable vérifie explicitement les classes suivantes dans l'uber-JAR avant `jpackage` :

```text
com/morpheus/mcp/MorpheusMcpServer.class
io/modelcontextprotocol/server/McpServer.class
io/modelcontextprotocol/server/transport/StdioServerTransportProvider.class
```

## Preuve M10

```text
MorpheusMainTest                5/5 PASS
MorpheusMcpStdioIntegrationTest 1/1 PASS
TOTAL                          307/307 PASS
Architecture                   149/149 PASS
MCP packaging proof: PASS
jpackage app-image: PASS
morpheus.exe --version: PASS
morpheus.exe --json version: PASS
Portable archive creation: PASS
Windows ZIP: PASS (77275075 bytes)
```

Le ZIP validé est :

```text
N:\workspace-dev\morpheus-engine\dist\morpheus-0.1.0-windows-x64.zip
```

Validation détaillée : `docs/VALIDATION_M10.md`.

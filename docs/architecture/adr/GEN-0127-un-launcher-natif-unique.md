---
id: GEN-0127
title: "Un launcher natif unique pour la CLI, le serveur MCP STDIO et l'API HTTP"
domain: general
rules: true
files: ["distribution/build-portable.ps1", "distribution/build-portable.sh"]
---

# Un launcher natif unique pour la CLI, le serveur MCP STDIO et l'API HTTP

- Statut : **Acceptée**
- Date : 2026-10-24
- Remplace : ADR-0064, ADR-0068

## Context

La distribution portable (ADR-0134) produit un launcher natif `morpheus` / `morpheus.exe` qui embarque son runtime
Java et lance `com.morpheus.cli.MorpheusMain`. MORPHEUS s'utilise par trois transports : la CLI, un serveur MCP en
STDIO (ADR-0062) et une API HTTP sur `jdk.httpserver` (ADR-0065). Livrer un second mécanisme d'installation ou un
second runtime pour MCP ou pour l'API compliquerait la distribution locale et ferait diverger la localisation des
données entre transports.

En mode MCP STDIO, la sortie standard porte le protocole JSON-RPC : un octet qui n'est pas une trame (aide, bannière,
log, message de démarrage) casse la session du client.

Les trois formes d'appel du launcher :

```text
morpheus <commande CLI>
morpheus mcp --stdio
morpheus api --host <hôte> --port <port>
```

La liste et le nombre d'outils MCP se lisent dans `contracts/public-surfaces.tsv`. Les dépendances de `morpheus-cli`
vers `morpheus-mcp` et `morpheus-api` font que le JAR ombré et l'app-image embarquent le serveur MCP, son SDK et l'API
sans runtime externe.

## Decision

1. **Un seul launcher, trois modes** : `MorpheusMain` route la CLI, `mcp --stdio` et `api`. Hôte et port par défaut
   de l'API : `MorpheusHttpServer.DEFAULT_HOST` et `MorpheusHttpServer.DEFAULT_PORT`, sous la politique loopback. Le
   mode serveur distant passe par le même launcher et relève d'ADR-0094.
2. **Un seul layout** : les trois modes acceptent `--data-dir`, `--config-dir`, `--db` et `MORPHEUS_DATA_DIR`,
   `MORPHEUS_CONFIG_DIR`, `MORPHEUS_DB`, résolus par `CliLayout` (ADR-0059) ; MCP et API ouvrent la base SQLite de la
   CLI.
3. **Flux en MCP** : en `mcp --stdio`, la sortie standard est réservée aux trames MCP JSON-RPC, la sortie d'erreur aux
   diagnostics ; ni aide, ni bannière, ni log, ni message de démarrage sur la sortie standard ; `--json` est refusé.
4. **Outils MCP** : un outil `READ` de `contracts/public-surfaces.tsv` est en lecture seule (ADR-0063), un outil
   `WRITE` relève d'ADR-0083 (confirmation explicite, révision attendue).
5. **Cycle de vie de l'API** : `api` démarre le serveur, écrit ses diagnostics de démarrage sur la sortie d'erreur,
   jamais dans les réponses, bloque jusqu'à interruption et ferme le serveur à l'arrêt.
6. **Packaging** : `morpheus-mcp` et `morpheus-api` sont des dépendances de `morpheus-cli` ; le runtime embarqué
   ajoute `jdk.httpserver` aux modules passés à `jlink`. Avant `jpackage`, le build portable exige dans le JAR ombré
   les classes de `$requiredEntries` (`distribution/build-portable.ps1`, équivalent dans `build-portable.sh`), dont
   `com/morpheus/mcp/server/MorpheusMcpServer.class`, `io/modelcontextprotocol/server/McpServer.class` et
   `com/morpheus/api/local/MorpheusHttpServer.class`, puis lance `--version`, `--json version` et l'API packagée
   (`GET /api/v1/health` → `200`). Aucun JDK séparé n'est requis chez l'utilisateur final.

## Do's and Don'ts

### Do

- Ajouter un mode d'exécution au launcher `MorpheusMain` plutôt que livrer un second exécutable.
- Résoudre la base, les données et la configuration de chaque mode par `CliLayout`.
- Écrire tout diagnostic du mode MCP et du mode API sur la sortie d'erreur.
- Classer chaque outil MCP `READ` ou `WRITE` dans `contracts/public-surfaces.tsv` et appliquer ADR-0083 aux outils
  `WRITE`.
- Garder dans la liste de contrôle du build portable les classes d'entrée du serveur MCP et de l'API.

### Don't

- Ne rien écrire sur la sortie standard en mode `mcp --stdio` qui ne soit une trame MCP.
- Ne pas accepter `--json` en mode MCP.
- Ne pas donner au serveur MCP ou à l'API une base ou un layout distincts de ceux de la CLI.
- Ne pas exiger un JDK installé chez l'utilisateur final pour lancer l'un des trois modes.
- Ne pas écrire un diagnostic de démarrage de l'API dans une réponse HTTP.

## Consequences

### Positives

- Une seule installation sert la CLI, les clients MCP et les clients HTTP, sur les mêmes données.
- Un client MCP peut lancer `morpheus mcp --stdio` sans filtrer la sortie standard.

### Négatives

- Le JAR ombré embarque le serveur MCP, son SDK et l'API même pour un usage CLI seul.
- Le runtime embarqué porte `jdk.httpserver` même quand l'API n'est pas utilisée.

### Risques

- Une écriture sur la sortie standard ajoutée dans un chemin atteignable en mode MCP casse les sessions des clients
  sans échec de compilation.
- La liste de classes vérifiées au packaging se tient à la main et peut ne plus couvrir une nouvelle classe d'entrée.

## Compliance and Enforcement

- Règle Archgate `GEN-0127-un-launcher-natif-unique.rules.ts` : `portable-image-checks-launcher-classes`.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusMainTest.java` : détection et analyse de `mcp --stdio` sans
  sortie de protocole parasite (`detectsAndParsesNativeMcpStdioCommandWithoutProtocolOutput`), de `api` avec les
  défauts loopback, refus des modes MCP non pris en charge et de `--json`, aide qui documente les modes.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusMcpStdioIntegrationTest.java` : vrai échange MCP sur STDIO.
- `distribution/build-portable.ps1` et `distribution/build-portable.sh` : refus de l'image si une classe de
  `$requiredEntries` manque, smokes `--version`, `--json version` et santé de l'API packagée.
- Revue : absence d'écriture sur la sortie standard hors du transport dans les chemins du mode MCP, et classement
  `READ` / `WRITE` des outils.

## References

- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution portable autonome.
- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat de la CLI locale et layout.
- [ADR-0062](ARCH-0062-official-java-mcp-sdk-and-native-stdio.md) — SDK MCP Java et STDIO natif.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) — contrat des outils MCP en lecture.
- [ADR-0083](BE-0083-controlled-lifecycle-write-operations.md) — écritures contrôlées.
- [ADR-0065](ARCH-0065-jdk-httpserver-local-api.md) — API locale sur `jdk.httpserver`.
- [ADR-0094](SEC-0094-optional-team-remote-server-mode.md) — mode serveur distant.
- `contracts/public-surfaces.tsv`.
- [VALIDATION_M10](../../project/validation/VALIDATION_M10.md),
  [VALIDATION_M11](../../project/validation/VALIDATION_M11.md).

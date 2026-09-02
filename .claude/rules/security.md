# Règles — Sécurité (D2 + M22 + M26 + audit 2026-08-26)

Toutes ces invariants sont **assertés textuellement** dans les sources par les tests d'architecture.
Supprimer une de ces chaînes casse le build.

## Désérialisation JSON — interdits absolus

- **Aucun** fichier sous `src/main/java/` ne doit contenir `activateDefaultTyping(` ou `enableDefaultTyping(`
  → scanné sur **tout le repo** par `D2RepositoryHardeningArchitectureTest`
- `MorpheusHttpServer` doit conserver :
  - `MAX_REQUEST_BODY_BYTES = 65_536`
  - `FAIL_ON_UNKNOWN_PROPERTIES`
  - `FAIL_ON_TRAILING_TOKENS`
- `morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java` doit exister

## Serveur local — loopback obligatoire

- `MorpheusHttpServer` → `LoopbackHostPolicy.requireLoopbackAddress`
- `ApiLaunchOptions` → `LoopbackHostPolicy.requireLoopback`
- Toute exposition non-loopback **exige le mode remote explicite**

## Serveur remote (M26) — surface durcie

| Invariant | Exigence |
|---|---|
| Transport | `HttpsServer`, `PKCS12`, `TLSv1.3` + `TLSv1.2` |
| Mot de passe TLS | **variable d'env `MORPHEUS_SERVER_TLS_PASSWORD`** — jamais un flag `--tls-password` |
| Tokens | `SecureRandom`, `TOKEN_BYTES = 32`, stockés en `sha256`, comparés par `MessageDigest.isEqual` |
| Audit | `MAX_AUDIT_RECORDS = 512`, écriture via `FileChannel` + `FileLock` |
| RBAC | `MorpheusRemoteRole.ADMIN` pour les opérations sensibles |
| Rate limit | `TOO_MANY_REQUESTS` (429) |
| Headers | `X-Frame-Options`, `Content-Security-Policy` |

### Interdits stricts sur le proxy remote
- **Jamais** de `Access-Control-Allow-Origin` (pas de CORS)
- **Jamais** de forward du header `Authorization` vers l'amont (`request.header("Authorization"` interdit)
- **Jamais** de concaténation de token en clair (`token + "|"` interdit)
- La méthode HTTP amont doit être **préservée** : `String upstreamMethod = exchange.getRequestMethod();`
  — jamais de réécriture conditionnelle type `providerProbe ? "GET"`

## Processus MCP externes — environnement et cycle de vie bornés

- `BoundedStdioClientTransport` ne doit **jamais** transmettre implicitement tout l'environnement MORPHEUS à MINOS/NEXUS.
- L'environnement hérité est réduit à une allowlist de lancement (`PATH`, variables Windows d'exécution, temp et locale), puis les variables explicitement configurées pour le peer sont appliquées.
- Les variables sensibles comme `MORPHEUS_SERVER_TLS_PASSWORD` et les variables d'injection JVM ne sont jamais héritées implicitement.
- Les descendants observés d'un peer MCP sont retenus par PID/`ProcessHandle` pendant toute la vie du parent afin de pouvoir les terminer même si le parent sort avant `closeGracefully()`.
- `ProviderPluginProbeWorker` (code MORPHEUS) termine **son propre sous-arbre avant de sortir** : c'est le seul endroit où ce sous-arbre est encore énumérable. Ne jamais revenir à un nettoyage assuré uniquement par le parent — l'observation périodique ne garantit rien pour un descendant créé puis orphelin dans le même intervalle.
- Côté MCP le pair n'est pas du code MORPHEUS : la terminaison des descendants reste **best-effort** et ce trou est documenté dans `SECURITY.md`. Ne pas le présenter comme une garantie.
- Cette frontière fournit une isolation de **lifecycle/environnement**, pas une sandbox OS : un peer explicitement configuré reste du code de confiance exécuté sous le compte MORPHEUS.

## Plugins providers (M22) — fail-closed

- L'activation d'un plugin **exige un pin SHA-256 de confiance**
  → `ProviderPluginActivator` : *"provider plugin activation requires a trusted SHA-256 pin"*
- La découverte est **métadonnées uniquement** : pas de classloading, pas de scan au démarrage
- `ProviderPluginDiscovery` doit utiliser `LinkOption.NOFOLLOW_LINKS` + `Files.isSymbolicLink`
- Le probe (exécution de code tiers) est :
  - **remote-only** + **ADMIN** (`PLUGIN_SHA256_REQUIRED`, `usesBoundedUpstreamTimeout`)
  - **jamais model-facing** → `EXPLICITLY_NOT_EXPOSED` côté MCP
  - refusé en local : *"provider-plugin probe is remote-only"*
- `morpheus-cli/pom.xml` ne doit **jamais** embarquer `morpheus-provider-reference`

## Update discovery

- Un manifeste local utilise `file:`.
- Un manifeste distant utilise **uniquement `https:`** ; `http:` est refusé avant tout I/O réseau.
- La découverte reste read-only : aucun téléchargement, installation ou exécution automatique de l'artefact annoncé.

## Persistance SQLite

- Schéma supporté : `SUPPORTED_SCHEMA_VERSION` (constaté à 17 le 01/09/2026 dans `SqliteSchemaManager` — revérifier avant de citer, cf. `rules/meta.md`)
- Backup : `VACUUM INTO` + `PRAGMA integrity_check` + `tryLock` + `ATOMIC_MOVE`
- Restore : exige une **`explicit confirmation`**, et reste `EXPLICITLY_OFFLINE_ONLY`
- `SqliteTransactionRunner` doit gérer `catch (Error failure)` + `rollbackSuppressing(connection, failure)`
- Ouvrir les connexions via `SqliteConnectionScope.open(databasePath)`

## Chaîne d'approvisionnement CI

- Toutes les actions GitHub sont **pinnées par SHA 40 caractères**, génération Node 24+
  (`actions/checkout` ≥ v6, `actions/setup-java` ≥ v5, `actions/upload-artifact` ≥ v6, `actions/cache/{restore,save}` ≥ v6)
- **Jamais** de tag mutable `uses: actions/<x>@v...`.
- CodeQL est versionné dans `.github/workflows/codeql.yml`; `init` et `analyze` sont pinnés par SHA et exécutent les requêtes `security-extended` Java.
- OWASP : `failBuildOnCVSS = 7.0`, `-DautoUpdate=false`, clé NVD via
  `-DnvdApiKeyEnvironmentVariable=NVD_API_KEY` — **jamais** `-DnvdApiKey=${NVD_API_KEY}` (fuite en ligne de commande).
- La clé `NVD_API_KEY` est réservée aux événements de confiance ; le chemin `pull_request` exécute l'update Dependency-Check **sans secret repository**.
- Ordre imposé dans `security.yml` : restore cache → remove stale lock (`odc.update.lock`) → update-only → scan agrégé.

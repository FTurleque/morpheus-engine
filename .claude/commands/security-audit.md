# Commande /security-audit

Audit des invariants de sécurité. Tous sont assertés textuellement dans les sources —
une chaîne manquante = un gate cassé.

## 1. Désérialisation JSON
```bash
grep -rn "activateDefaultTyping(\|enableDefaultTyping(" --include="*.java" .
```
→ doit être **vide** sous `src/main/java/`.

Vérifier `morpheus-api/src/main/java/com/morpheus/api/MorpheusHttpServer.java` :
- `MAX_REQUEST_BODY_BYTES = 65_536`
- `FAIL_ON_UNKNOWN_PROPERTIES`
- `FAIL_ON_TRAILING_TOKENS`

Vérifier l'existence de `morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java`.

## 2. Frontière loopback (serveur local)
- `MorpheusHttpServer` → `LoopbackHostPolicy.requireLoopbackAddress`
- `ApiLaunchOptions` → `LoopbackHostPolicy.requireLoopback`
- `LoopbackHostPolicy` → `isLoopbackAddress` + `"requires explicit remote mode"`

## 3. Serveur remote (`MorpheusRemoteHttpServer.java`)

Doit contenir : `HttpsServer` · `PKCS12` · `TLSv1.3` · `TLSv1.2` · `MorpheusRemoteRole.ADMIN`
· `TOO_MANY_REQUESTS` · `X-Frame-Options` · `Content-Security-Policy` · `PLUGIN_SHA256_REQUIRED`
· `usesBoundedUpstreamTimeout` · `String upstreamMethod = exchange.getRequestMethod();`

Ne doit **PAS** contenir : `Access-Control-Allow-Origin` · `request.header("Authorization"` · `providerProbe ? "GET"`

`MorpheusRemoteIdentityFile.java` doit contenir : `MessageDigest.isEqual` · `SecureRandom`
· `TOKEN_BYTES = 32` · `MAX_AUDIT_RECORDS = 512` · `sha256` · `FileChannel` · `FileLock`
Ne doit **PAS** contenir : `token + "|"`

`RemoteApiLaunchOptions.java` : `MORPHEUS_SERVER_TLS_PASSWORD` présent, `--tls-password` **absent**.

## 4. Plugins providers (fail-closed)
- `ProviderPluginActivator` → `"provider plugin activation requires a trusted SHA-256 pin"`
- `ProviderPluginDiscovery` → `LinkOption.NOFOLLOW_LINKS` + `Files.isSymbolicLink`
- `MorpheusHttpServer` → `providerPluginProbeEnabled` + `"provider-plugin probe is remote-only"`
- `morpheus-cli/pom.xml` ne contient **pas** `morpheus-provider-reference`
- Le probe reste `EXPLICITLY_NOT_EXPOSED` côté MCP dans le TSV

## 5. SQLite
Récupérer d'abord la valeur normative — ne jamais la citer de mémoire ni la recopier ici :

```bash
grep -n "SUPPORTED_SCHEMA_VERSION = " morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/SqliteSchemaManager.java
```

`SqliteSchemaManager.java` **déclare** la version supportée ; `SqliteServerMaintenance.java` doit
**consommer cette constante**, jamais un littéral. Reporter dans le rapport la valeur réellement détectée.

`SqliteServerMaintenance.java` : `VACUUM INTO` · `PRAGMA integrity_check`
· `tryLock` · `ATOMIC_MOVE` · `"explicit confirmation"`
`SqliteTransactionRunner.java` : `catch (Error failure)` · `rollbackSuppressing(connection, failure)`

## 6. Chaîne CI
```bash
grep -n "uses: actions/" .github/workflows/ci.yml .github/workflows/security.yml
```
Toutes les actions doivent être pinnées par SHA 40 caractères (génération Node 24+).
Aucun tag mutable `@v...`.

`security.yml` : `failBuildOnCVSS 7.0` · `-DautoUpdate=false`
· `-DnvdApiKeyEnvironmentVariable=NVD_API_KEY` (jamais `-DnvdApiKey=${NVD_API_KEY}`)
Ordre imposé : restore cache → remove stale lock → update-only.

## 7. Scan CVE complet
```bash
./mvnw verify -P d2-security
```

## Rapport

```
═══════════════════════════════════════════
  AUDIT SÉCURITÉ — MORPHEUS ENGINE
═══════════════════════════════════════════

JSON            ✅ typing désactivé, limites strictes
LOOPBACK        ✅ local confiné
REMOTE TLS      ✅ TLS 1.2/1.3, tokens hashés, pas de CORS
PLUGINS         ✅ pin SHA-256 requis, probe remote-only
SQLITE          ✅ schéma <valeur lue dans SqliteSchemaManager>, backup atomique
CI SUPPLY CHAIN ✅ actions pinnées par SHA
CVE             ✅ 0 ≥ 7.0
```

Le gabarit ci-dessus contient un emplacement, pas une valeur : substituer le numéro de schéma
réellement lu à l’étape 5. Ne jamais recopier un numéro depuis une exécution antérieure de cette
commande — il devient faux dès la migration suivante.

Pour chaque ❌ : fichier, chaîne manquante ou interdite, test d'architecture qui l'exige.

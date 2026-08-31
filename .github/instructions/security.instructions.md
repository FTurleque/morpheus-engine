---
applyTo: "morpheus-api/**,morpheus-provider-sdk/**,morpheus-provider-reference/**,morpheus-store-sqlite/**,morpheus-mcp-transport/**,.github/workflows/**,config/**"
---

# Sécurité — invariants non négociables

Toutes ces invariants sont **assertées textuellement** dans les sources par des tests
d'architecture (notamment `D2RepositoryHardeningArchitectureTest`). Une chaîne manquante
ou affaiblie casse le build, pas seulement une revue humaine. Détail complet, exemples et
procédures : `.claude/rules/security.md` (source partagée avec Claude Code — la relire
avant toute revue de sécurité).

## Désérialisation JSON

- Aucun fichier sous `src/main/java/` ne doit contenir `activateDefaultTyping(` ni
  `enableDefaultTyping(` — recherche sur tout le repo, pas seulement le diff
- `MorpheusHttpServer` doit conserver `MAX_REQUEST_BODY_BYTES = 65_536`,
  `FAIL_ON_UNKNOWN_PROPERTIES`, `FAIL_ON_TRAILING_TOKENS`
- `morpheus-api/src/test/java/com/morpheus/api/JacksonSecurityRegressionTest.java` doit
  exister et rester vert

## Frontières loopback / remote

- Serveur local : `LoopbackHostPolicy.requireLoopbackAddress` / `requireLoopback` —
  toute exposition non-loopback exige le mode remote explicite, jamais un contournement
- Serveur remote (M26) : `HttpsServer` + `PKCS12` + `TLSv1.3`/`TLSv1.2`, mot de passe TLS
  **uniquement** via la variable d'environnement `MORPHEUS_SERVER_TLS_PASSWORD` (jamais un
  flag CLI), tokens `SecureRandom` 32 octets stockés en `sha256`, comparaison par
  `MessageDigest.isEqual`, audit `MAX_AUDIT_RECORDS = 512` via `FileChannel` + `FileLock`
- **Jamais** de CORS (`Access-Control-Allow-Origin`), **jamais** de forward du header
  `Authorization` vers l'amont, **jamais** de concaténation de token en clair, la méthode
  HTTP amont doit toujours être préservée telle quelle

## Plugins providers (fail-closed)

- Activation exige un pin SHA-256 de confiance — découverte en métadonnées uniquement,
  jamais de classloading au scan, jamais de suivi de lien symbolique
  (`LinkOption.NOFOLLOW_LINKS`)
- Le probe (exécution de code tiers) est **remote-only** + **ADMIN**, jamais exposé côté
  MCP (`EXPLICITLY_NOT_EXPOSED` dans `contracts/public-surfaces.tsv`)
- `morpheus-cli/pom.xml` ne doit jamais embarquer `morpheus-provider-reference`

## Persistance SQLite

- Backup : `VACUUM INTO` + `PRAGMA integrity_check` + `tryLock` + `ATOMIC_MOVE`
- Restore : exige une confirmation explicite, reste `EXPLICITLY_OFFLINE_ONLY`
- Gestion d'erreur : `catch (Error failure)` + `rollbackSuppressing`

## Chaîne d'approvisionnement CI

- Toutes les actions GitHub **pinnées par SHA 40 caractères** — jamais de tag mutable
  `@v...` — génération Node 24+ (`actions/checkout` ≥ v6, `actions/setup-java` ≥ v5,
  `actions/upload-artifact` ≥ v6, `actions/cache/{restore,save}` ≥ v6)
- OWASP Dependency-Check : `-DautoUpdate=false`, clé NVD via
  `-DnvdApiKeyEnvironmentVariable=NVD_API_KEY` (jamais `-DnvdApiKey=${NVD_API_KEY}` en clair
  sur la ligne de commande), seuil CVSS et lecture des versions pinnées dans `pom.xml`,
  jamais recopiés de mémoire

## Secrets — règle absolue

`NVD_API_KEY` et `MORPHEUS_SERVER_TLS_PASSWORD` sont des variables d'environnement,
jamais des flags CLI, jamais des littéraux dans le code, les logs ou les messages de
commit.


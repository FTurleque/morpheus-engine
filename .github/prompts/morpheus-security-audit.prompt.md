---
mode: agent
description: "Audit des invariants de sécurité Morpheus Engine — JSON, loopback, remote TLS, plugins, SQLite, chaîne CI."
---

# Audit sécurité Morpheus Engine

Tous les invariants sont assertés textuellement dans les sources — une chaîne manquante ou
affaiblie casse un gate ArchUnit. Miroir du prompt Claude `.claude/commands/security-audit.md`
et détail complet `.github/instructions/security.instructions.md` / `.claude/rules/security.md`.

## 1. Désérialisation JSON
```bash
grep -rn "activateDefaultTyping(\|enableDefaultTyping(" --include="*.java" .
```
Doit être **vide** sous `src/main/java/`. Vérifier dans `MorpheusHttpServer.java` :
`MAX_REQUEST_BODY_BYTES = 65_536`, `FAIL_ON_UNKNOWN_PROPERTIES`, `FAIL_ON_TRAILING_TOKENS`.
Vérifier l'existence de `JacksonSecurityRegressionTest.java`.

## 2. Frontière loopback
`MorpheusHttpServer` → `LoopbackHostPolicy.requireLoopbackAddress`. `ApiLaunchOptions` →
`LoopbackHostPolicy.requireLoopback`. Toute exposition non-loopback exige le mode remote
explicite.

## 3. Serveur remote (`MorpheusRemoteHttpServer.java`)
Doit contenir : `HttpsServer` · `PKCS12` · `TLSv1.3` · `TLSv1.2` ·
`MorpheusRemoteRole.ADMIN` · `TOO_MANY_REQUESTS` · `X-Frame-Options` ·
`Content-Security-Policy` · `PLUGIN_SHA256_REQUIRED` · `usesBoundedUpstreamTimeout` ·
préservation de la méthode HTTP amont.
Ne doit **PAS** contenir : `Access-Control-Allow-Origin` ·
`request.header("Authorization"` · réécriture conditionnelle de méthode.
`RemoteApiLaunchOptions.java` : `MORPHEUS_SERVER_TLS_PASSWORD` présent,
`--tls-password` **absent**.

## 4. Plugins providers (fail-closed)
- `ProviderPluginActivator` exige un pin SHA-256 de confiance
- `ProviderPluginDiscovery` → `LinkOption.NOFOLLOW_LINKS` + `Files.isSymbolicLink`
- Probe : remote-only + ADMIN, `EXPLICITLY_NOT_EXPOSED` côté MCP dans le TSV
- `morpheus-cli/pom.xml` ne contient pas `morpheus-provider-reference`

## 5. SQLite
Backup : `VACUUM INTO` + `PRAGMA integrity_check` + `tryLock` + `ATOMIC_MOVE`. Restore :
confirmation explicite + `EXPLICITLY_OFFLINE_ONLY`. `SqliteTransactionRunner` :
`catch (Error failure)` + `rollbackSuppressing`.

## 6. Chaîne CI
```bash
grep -n "uses: actions/" .github/workflows/ci.yml .github/workflows/security.yml
```
Toutes les actions pinnées par SHA 40 caractères, aucun tag mutable `@v...`.

## 7. Scan CVE complet
```bash
./mvnw -Pd2-security -DautoUpdate=false org.owasp:dependency-check-maven:aggregate
```

## Rapport

```
═══════════════════════════════════════════
  AUDIT SÉCURITÉ — MORPHEUS ENGINE
═══════════════════════════════════════════

JSON            ✅/❌ typing désactivé, limites strictes
LOOPBACK        ✅/❌ local confiné
REMOTE TLS      ✅/❌ TLS 1.2/1.3, tokens hashés, pas de CORS
PLUGINS         ✅/❌ pin SHA-256 requis, probe remote-only
SQLITE          ✅/❌ backup atomique, schéma vérifié en live
CI SUPPLY CHAIN ✅/❌ actions pinnées par SHA
CVE             ✅/❌ seuil CVSS lu dans pom.xml, pas mémorisé
```

Pour chaque ❌ : fichier, chaîne manquante ou interdite, test d'architecture qui l'exige.

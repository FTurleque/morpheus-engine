# VALIDATION M26 — Optional Team / Remote Server Mode

Statut : **PASS — qualification Windows + Linux/WSL exact-head acquise sur le même SHA**

Date : 29 juillet 2026

Issue : #109  
PR : #110  
Branche : `m26/optional-team-remote-server-mode`  
Target : `develop`

Baseline M26 :

```text
develop@619237f5273d83ed70728c58e0b97f85803cb167
```

Head exact qualifié Windows + Linux/WSL :

```text
bf481b24054c4577144b4cb2ede2bdbc4d9974a2
```

Dernier changement runtime avant qualification finale :

```text
bf481b24054c4577144b4cb2ede2bdbc4d9974a2
fix(m26): decouple HTTPS backlog from request concurrency
```

Les deux gates ont été rejoués intégralement sur ce SHA exact.

## Question de sortie

> MORPHEUS peut-il être utilisé par une équipe via un mode serveur optionnel sans casser le fonctionnement local-first ?

**Réponse : oui.** Le mode local reste first-class et loopback-only via le launcher officiel. Le mode remote est explicitement opt-in, HTTPS obligatoire, authentifié par Bearer token, autorisé par rôles READ/WRITE/ADMIN, borné en concurrence et accompagné d'opérations de backup/restore explicites sans transformer l'état serveur en source de vérité métier.

## Contrats prouvés

```text
local mode remains first-class                  PASS
remote mode is opt-in                           PASS
non-loopback local bind rejected                PASS
remote without TLS/auth fails closed            PASS
TLS 1.3 / 1.2 + PKCS12                          PASS
Bearer authentication                           PASS
token plaintext != persisted credential         PASS
constant-time hash comparison                    PASS
READ != WRITE != ADMIN                          PASS
Authorization never forwarded to loopback       PASS
security headers / no implicit CORS              PASS
bounded application concurrency                 PASS
HTTP 429 on saturation                          PASS
TCP backlog != application concurrency          PASS
secret non-disclosure                           PASS
backup via SQLite VACUUM INTO                    PASS
PRAGMA integrity_check                           PASS
schema compatibility <= V015                    PASS
future schema rejection                         PASS
o V016 for remote configuration                 PASS
o live restore                                  PASS
o implicit migration during restore             PASS
restore confirmation + server lease              PASS
server state != provider source of truth         PASS
CLI/HTTP maintenance contracts                  PASS
MCP control plane intentionally absent           PASS
SBOM / provenance                               PASS
Windows portable                                PASS
Linux portable                                  PASS
post-gate executable delta                       NONE
```

## Budgets qualifiés

```text
max concurrent requests   64 default / 1..512
HTTPS listen backlog      bounded independently from application concurrency
max auth identities       256
principal length          1..128 chars
auth file                 <= 256 KiB
token entropy             256 bits
HTTP request body         existing 64 KiB limit
TLS protocols             TLSv1.3 + TLSv1.2
supported backup schema   <= V015
```

## Gate Windows

Commande canonique :

```powershell
.\validate-m26.cmd 1.0.0
```

Preuve machine-readable :

```text
M26 VALIDATION PASS
sha=bf481b24054c4577144b4cb2ede2bdbc4d9974a2
baseRef=origin/develop
version=1.0.0
tests=579
architectureTests=234
lineCoverage=0.443507
branchCoverage=0.378842
localFirst=PASS
remoteTlsAuthRbac=PASS
boundedConcurrency=PASS
secretNonDisclosure=PASS
backupRestore=PASS
schemaCompatibility=PASS
surfaceConvergence=PASS
sqliteV015=PASS
sbom=PASS
provenance=PASS
portable=True
postGateExecutableDelta=NONE
```

Preuves complémentaires Windows :

- `git diff --check` PASS ;
- reactor Maven 17/17 SUCCESS ;
- 579 tests PASS contre baseline M25 >= 565 ;
- 234 tests d'architecture PASS contre baseline M25 >= 231 ;
- `MorpheusRemoteHttpServerTest` PASS avec TLS réel, RBAC, 401/403 et saturation 429 ;
- JaCoCo au-dessus des floors 25% lignes / 20% branches ;
- CycloneDX JSON/XML + provenance PASS ;
- classes TLS/auth/server/maintenance présentes dans le package ;
- provisioning d'identité hash-only PASS ;
- backup + verify + restore offline explicite PASS ;
- frontière local-first + démarrage remote fail-closed PASS ;
- distribution Windows portable créée ;
- `postGateExecutableDelta=NONE`.

## Gate Linux / WSL

Commande canonique :

```bash
bash ./scripts/validate-m26.sh 1.0.0
```

Le harness qualifié découvre sous WSL :

```text
M26 discovered JAVA_HOME=/usr/lib/jvm/java-21-openjdk-amd64
```

Preuve machine-readable :

```text
M26 VALIDATION PASS
sha=bf481b24054c4577144b4cb2ede2bdbc4d9974a2
baseRef=origin/develop
version=1.0.0
tests=579
architectureTests=234
lineCoverage=0.443527
branchCoverage=0.378842
localFirst=PASS
remoteTlsAuthRbac=PASS
boundedConcurrency=PASS
secretNonDisclosure=PASS
backupRestore=PASS
schemaCompatibility=PASS
surfaceConvergence=PASS
sqliteV015=PASS
sbom=PASS
provenance=PASS
portable=true
postGateExecutableDelta=NONE
```

Preuves complémentaires Linux/WSL :

- SHA exact vérifié avant le gate ;
- reactor Maven 17/17 SUCCESS ;
- 579 tests PASS ;
- 234 architecture PASS ;
- TLS/auth/RBAC et bounded concurrency PASS ;
- secret non-disclosure PASS ;
- backup/restore et schema compatibility PASS ;
- CycloneDX JSON/XML + provenance PASS ;
- runtime Linux autonome créé ;
- distribution `morpheus-1.0.0-linux-x64.tar.gz` créée ;
- `postGateExecutableDelta=NONE`.

## Incidents découverts pendant la qualification

### Whitespace documentaire

Le premier candidat `e5be89bbe761f9e5a16dfc03f71cf2f0eb894887` a été arrêté avant Maven par `git diff --check` à cause de trois espaces de fin de ligne dans `docs/roadmap/M26_EXECUTION.md`. Le commit docs-only `81575fff68dd9625efe9ee5af21df3926e78c9df` a corrigé ce bruit sans toucher au runtime.

### Backlog TCP couplé à la concurrence applicative

Le gate Windows sur `81575fff...` a ensuite exposé un défaut réel : `HttpsServer.create(address, maxConcurrentRequests)` utilisait la même valeur pour le listen backlog TCP et le sémaphore applicatif. Avec `maxConcurrentRequests=1`, un burst pouvait provoquer `ConnectException` avant que MORPHEUS puisse retourner `429`.

Le correctif `bf481b24054c4577144b4cb2ede2bdbc4d9974a2` sépare ces budgets : le backlog réseau reste borné mais suffisamment large, tandis que le sémaphore reste la limite stricte d'exécution applicative. Windows puis Linux/WSL ont été rejoués intégralement sur ce SHA et ont passé.

## Persistance et maintenance

M26 n'ajoute aucune migration métier : le schéma supporté reste **V015**. Les identités remote, TLS, limites de concurrence et backups sont de la configuration/opérabilité, pas de la vérité métier.

Backups :

```text
SQLite VACUUM INTO
PRAGMA integrity_check
schema_migrations version check
SHA-256
```

Restore :

```text
offline only
explicit confirmation
server lease exclusion
staging + atomic move when supported
future schema rejected
older schema migrated only by normal startup path
```

## Surfaces qualifiées

```text
server.status          HTTP remote READ
server.identity.create CLI local only
server.backup.create   CLI local + HTTP remote ADMIN
server.backup.verify   CLI local only
server.restore         CLI offline only
MCP control plane      intentionally absent
```

L'absence du provisioning d'identité et du restore dans HTTP/MCP est intentionnelle et constitue une frontière de sécurité M26.

## Conclusion

```text
Windows exact-head       PASS
Linux/WSL exact-head     PASS
Qualified SHA            bf481b24054c4577144b4cb2ede2bdbc4d9974a2
Tests                    579 PASS Windows + Linux
Architecture             234 PASS Windows + Linux
Windows coverage         44.3507% line / 37.8842% branch
Linux coverage           44.3527% line / 37.8842% branch
Local-first              PASS
Remote TLS/auth/RBAC     PASS
Bounded concurrency/429  PASS
Secret non-disclosure    PASS
Backup/restore           PASS
Schema compatibility     PASS
SQLite                   V015
Surface convergence      PASS
SBOM/provenance          PASS Windows + Linux
Portable                 PASS Windows + Linux
Executable delta         NONE Windows + Linux
CI / GitHub Actions      NOT USED — July 2026
ADR-0094                 ACCEPTED — M26
```

Cette preuve qualifie le SHA exact ci-dessus. Toute consolidation post-gate doit rester documentaire uniquement ; elle ne peut pas être utilisée pour qualifier un code différent.

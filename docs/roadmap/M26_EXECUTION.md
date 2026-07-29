# M26 — Optional Team / Remote Server Mode

Statut : **EN COURS — M26-S0→S7 implémentés ; S8 préparé ; S9 qualification restante**

Issue : #109 — **OPEN**  
PR : #110 — **DRAFT vers `develop`**  
Branche : `m26/optional-team-remote-server-mode`  
Baseline : `develop@619237f5273d83ed70728c58e0b97f85803cb167`

## Question de sortie

> MORPHEUS peut-il être utilisé par une équipe via un mode serveur optionnel sans casser le fonctionnement local-first ?

## Invariants

```text
local mode remains first-class
remote mode is opt-in
non-loopback bind requires remote mode
remote mode requires TLS + authentication
plaintext bearer over remote HTTP is forbidden
authentication != authorization
READ != WRITE != ADMIN
caller supplied principal != authentication
token plaintext != persisted credential
backup != live restore
restore != implicit migration
server state != provider source of truth
multi-client concurrency != unbounded concurrency
metrics != secret disclosure
surface parity != same transport shape
```

## Slices

### M26-S0 — cadrage / ADR / roadmap / Draft PR

- [x] baseline exacte `develop@619237f...`
- [x] issue #109
- [x] branche depuis `develop`
- [x] ADR-0094 proposée
- [x] roadmap opérationnelle M26
- [x] Draft PR #110 vers `develop`

### M26-S1 — configuration remote / frontière TLS

- [x] mode LOCAL/REMOTE explicite via launch paths séparés
- [x] mode local officiel limité au loopback
- [x] non-loopback sans remote rejeté
- [x] remote exige keystore PKCS12
- [x] secret TLS hors arguments CLI
- [x] HTTPS JDK sans dépendance cloud

### M26-S2 — authentication / RBAC / hardening HTTP

- [x] auth file avec hashes SHA-256 seulement
- [x] Bearer auth avec `MessageDigest.isEqual`
- [x] rôles READ / WRITE / ADMIN
- [x] mapping route-intent explicite
- [x] 401/403 déterministes
- [x] security headers
- [x] aucun CORS implicite
- [x] aucun secret dans erreur/status/metrics
- [x] Authorization consommé par la façade et jamais forwardé au hop loopback

### M26-S3 — concurrence multi-client / observabilité

- [x] limite de concurrence explicite 1..512
- [x] HTTP 429 sur saturation
- [x] active/total/auth/forbidden/throttled counters
- [x] uptime/start time
- [x] état process-local uniquement
- [x] aucune queue applicative non bornée introduite par M26

### M26-S4 — backup / verify / restore offline

- [x] backup SQLite cohérent via `VACUUM INTO`
- [x] destination non symbolique et bornée au répertoire explicite
- [x] `PRAGMA integrity_check`
- [x] `schema_migrations` version
- [x] reject schema future > V015
- [x] restore offline + confirmation
- [x] server lease empêche restore pendant serveur actif
- [x] staging + move atomique quand supporté
- [x] migration normale au prochain open
- [x] aucune V016 : configuration remote distincte de la vérité métier

### M26-S5 — CLI serveur / maintenance

- [x] `server identity create`
- [x] token généré cryptographiquement, 256 bits, affiché une seule fois
- [x] auth file ne persiste que le hash
- [x] `server backup create`
- [x] `server backup verify`
- [x] `server restore --confirm`
- [x] aide API remote

### M26-S6 — HTTP server status / maintenance

- [x] `GET /api/v1/server/status`
- [x] `POST /api/v1/server/backups`
- [x] backup HTTP ADMIN seulement
- [x] métriques remote ADMIN
- [x] OpenAPI `morpheus-v1-remote-m26.yaml`
- [x] public surface manifest
- [x] identity provisioning et restore explicitement absents du HTTP/MCP

### M26-S7 — tests sécurité / architecture / concurrence

- [x] local compatibility / loopback
- [x] non-loopback fail-closed
- [x] remote missing TLS/auth fail-closed
- [x] READ cannot WRITE
- [x] WRITE atteint les routes WRITE mais pas ADMIN
- [x] ADMIN access
- [x] invalid/missing bearer 401
- [x] token/hash/password non-disclosure
- [x] concurrent saturation 429 avec serveur HTTPS réel
- [x] backup/verify/restore
- [x] future schema rejection
- [x] API/application boundaries
- [x] PKCS12 réel généré par `keytool` pendant le test

### M26-S8 — documentation / packaging / validateurs

- [x] guide utilisateur `TEAM_REMOTE_SERVER.md`
- [x] guide développeur `REMOTE_SERVER_PLATFORM.md`
- [x] supplément OpenAPI M26
- [x] `validate-m26.cmd`
- [x] `scripts/validate-m26.ps1`
- [x] `scripts/validate-m26.sh`
- [ ] ADR index — consolidation après double preuve
- [ ] packaged TLS/server classes proof — à exécuter S9
- [ ] SBOM/provenance/portable — à exécuter S9

### M26-S9 — qualification / intégration

- [ ] Windows exact-head PASS
- [ ] Linux/WSL exact-head PASS même SHA
- [ ] tests >= 565
- [ ] architecture >= 231
- [ ] JaCoCo floors PASS
- [ ] remote TLS/auth/RBAC PASS
- [ ] bounded concurrency/429 PASS
- [ ] secret non-disclosure PASS
- [ ] backup/restore + schema compatibility PASS
- [ ] portable Windows/Linux PASS
- [ ] `postGateExecutableDelta=NONE`
- [ ] ADR-0094 Acceptée
- [ ] PR Ready puis merge dans `develop`
- [ ] issue #109 CLOSED / completed
- [ ] réconciliation post-merge : M26 DONE / M27 NOW

## Budgets M26

```text
max concurrent requests       64 default / 1..512
max auth identities           256
principal length              1..128 chars
auth file                     <= 256 KiB
token entropy                 256 bits
backup destination            répertoire explicite uniquement
HTTP request body             existing 64 KiB limit
TLS protocols                 TLSv1.3 + TLSv1.2
supported backup schema       <= V015
```

## Surface M26

```text
server.status          HTTP remote READ
server.identity.create CLI local only
server.backup.create   CLI local + HTTP remote ADMIN
server.backup.verify   CLI local only
server.restore         CLI offline only
MCP control plane      intentionally absent
```

## Qualification canonique

Windows :

```powershell
.\validate-m26.cmd 1.0.0
```

Linux / WSL :

```bash
bash ./scripts/validate-m26.sh 1.0.0
```

Les deux gates doivent exécuter exactement le même SHA. Toute modification de code/POM/runtime/migration/OpenAPI/packaging/validator après un PASS invalide ce PASS et impose un replay Windows + Linux.

Les consolidations après double PASS devront être exclusivement documentaires avant merge.

En juillet 2026, **aucune GitHub Actions / CI n’est utilisée comme preuve M26**.

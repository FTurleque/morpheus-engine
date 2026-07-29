# M26 — Optional Team / Remote Server Mode

Statut : **EN COURS — 0/10**

Issue : #109 — **OPEN**  
PR : à ouvrir en Draft vers `develop`  
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
- [ ] Draft PR vers `develop`

### M26-S1 — configuration remote / frontière TLS

- [ ] mode LOCAL/REMOTE explicite
- [ ] mode local limité au loopback
- [ ] non-loopback sans remote rejeté
- [ ] remote exige keystore PKCS12
- [ ] secret TLS hors arguments CLI
- [ ] HTTPS JDK sans dépendance cloud

### M26-S2 — authentication / RBAC / hardening HTTP

- [ ] auth file avec hashes SHA-256 seulement
- [ ] Bearer auth en temps constant
- [ ] rôles READ / WRITE / ADMIN
- [ ] mapping route-intent explicite
- [ ] 401/403 déterministes
- [ ] security headers
- [ ] aucun CORS implicite
- [ ] aucun secret dans erreur/status/metrics

### M26-S3 — concurrence multi-client / observabilité

- [ ] limite de concurrence explicite
- [ ] HTTP 429 sur saturation
- [ ] active/total/auth/forbidden/throttled counters
- [ ] uptime/start time
- [ ] état process-local uniquement
- [ ] aucune file de travail non bornée M26

### M26-S4 — backup / verify / restore offline

- [ ] backup SQLite cohérent
- [ ] destination non symbolique
- [ ] integrity_check
- [ ] schema_migrations version
- [ ] reject schema future
- [ ] restore offline + confirmation
- [ ] temp + move atomique quand supporté
- [ ] migration normale au prochain open

### M26-S5 — CLI serveur / maintenance

- [ ] `server identity create`
- [ ] token généré cryptographiquement et affiché une seule fois
- [ ] auth file ne persiste que le hash
- [ ] `server backup create`
- [ ] `server backup verify`
- [ ] `server restore --confirm`
- [ ] aide API remote

### M26-S6 — HTTP server status / maintenance

- [ ] `GET /api/v1/server/status`
- [ ] `POST /api/v1/server/backups`
- [ ] backup HTTP ADMIN seulement
- [ ] métriques remote ADMIN
- [ ] OpenAPI M26
- [ ] public surface manifest

### M26-S7 — tests sécurité / architecture / concurrence

- [ ] local compatibility
- [ ] non-loopback fail-closed
- [ ] remote missing TLS/auth fail-closed
- [ ] READ cannot WRITE
- [ ] WRITE can read/write but not ADMIN
- [ ] ADMIN access
- [ ] invalid bearer 401
- [ ] token/hash/password non-disclosure
- [ ] concurrent saturation 429
- [ ] backup/verify/restore
- [ ] future schema rejection
- [ ] API/application boundaries

### M26-S8 — documentation / packaging / validateurs

- [ ] guide utilisateur `TEAM_REMOTE_SERVER.md`
- [ ] guide développeur `REMOTE_SERVER_PLATFORM.md`
- [ ] API doc mise à jour
- [ ] ADR index
- [ ] validate-m26 Windows
- [ ] validate-m26 Linux/WSL
- [ ] packaged TLS/server classes proof
- [ ] SBOM/provenance/portable

### M26-S9 — qualification / intégration

- [ ] Windows exact-head PASS
- [ ] Linux/WSL exact-head PASS même SHA
- [ ] tests >= 565
- [ ] architecture >= 231
- [ ] JaCoCo floors PASS
- [ ] remote security PASS
- [ ] backup/restore PASS
- [ ] portable Windows/Linux PASS
- [ ] `postGateExecutableDelta=NONE`
- [ ] ADR-0094 Acceptée
- [ ] PR Ready puis merge dans `develop`
- [ ] issue #109 CLOSED / completed
- [ ] M27 passe NOW

## Budgets initiaux M26

```text
max concurrent requests       64 default / 1..512
max auth identities           256
principal length              1..128 chars
auth file                     <= 256 KiB
token entropy                 256 bits
backup list/result            bounded by explicit directory only
HTTP request body             existing 64 KiB limit
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

En juillet 2026, **aucune GitHub Actions / CI n’est utilisée comme preuve M26**.
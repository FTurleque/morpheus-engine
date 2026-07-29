# Mode équipe / serveur distant — M26

M26 ajoute un mode serveur **optionnel** à MORPHEUS. Le fonctionnement local reste le comportement par défaut.

```text
LOCAL   HTTP loopback uniquement, aucune configuration remote requise
REMOTE  HTTPS obligatoire + Bearer auth + rôles READ/WRITE/ADMIN
```

Le mode remote ne transforme pas le serveur en cloud obligatoire et ne change pas la source de vérité des spécifications : providers, snapshots, portfolios et policies conservent leurs contrats existants.

## 1. Préparer un certificat TLS

MORPHEUS attend un keystore **PKCS12** contenant la clé privée et le certificat du serveur. En production d’équipe, utilisez un certificat émis pour le nom DNS réellement utilisé par vos clients.

Exemple de développement local avec le `keytool` d’un JDK :

```bash
keytool -genkeypair \
  -alias morpheus-server \
  -keyalg RSA -keysize 3072 \
  -storetype PKCS12 \
  -keystore morpheus-server.p12 \
  -validity 365 \
  -dname "CN=morpheus.example.internal" \
  -ext "SAN=DNS:morpheus.example.internal" \
  -storepass change-me
```

Ne stockez pas le mot de passe dans une commande MORPHEUS. Injectez-le au processus :

PowerShell :

```powershell
$env:MORPHEUS_SERVER_TLS_PASSWORD = '<secret>'
```

Linux :

```bash
export MORPHEUS_SERVER_TLS_PASSWORD='<secret>'
```

Le chemin du keystore peut être passé par `--tls-keystore` ou `MORPHEUS_SERVER_TLS_KEYSTORE`.

## 2. Créer les identités

Créer d’abord au moins un administrateur :

```bash
morpheus server identity create \
  --principal admin \
  --role ADMIN
```

La commande affiche un token Bearer **une seule fois**. Copiez-le immédiatement dans votre gestionnaire de secrets.

Le fichier par défaut est :

```text
<config-dir>/remote-auth.txt
```

Il ne contient jamais le token clair :

```text
principal|role|sha256(token)
```

Exemple :

```text
admin|ADMIN|7c4a8d09ca3762af61e59520943dc26494f8941b...
```

Un token perdu doit être remplacé par une nouvelle identité/credential ; MORPHEUS ne peut pas reconstruire le token depuis son hash.

### Rôles

| Rôle | Autorisations |
|---|---|
| `READ` | endpoints de lecture, Query DSL, exports, policy evaluate/dry-run, transition-check |
| `WRITE` | READ + mutations métier/configuration existantes |
| `ADMIN` | WRITE + métriques remote + création de backups serveur |

`READ != WRITE != ADMIN`. Un `principal` fourni dans un JSON n’est jamais une authentification.

## 3. Démarrer le serveur remote

```bash
morpheus api --remote \
  --host 0.0.0.0 \
  --port 8765 \
  --tls-keystore /secure/morpheus-server.p12
```

Options utiles :

```text
--auth-file PATH         fichier d'identités, défaut <config-dir>/remote-auth.txt
--max-concurrent N       1..512, défaut 64
```

Variables :

```text
MORPHEUS_SERVER_TLS_PASSWORD       obligatoire
MORPHEUS_SERVER_TLS_KEYSTORE       alternative à --tls-keystore
MORPHEUS_SERVER_AUTH_FILE          alternative à --auth-file
MORPHEUS_SERVER_MAX_CONCURRENT     limite de concurrence
```

Le mode remote refuse de démarrer sans keystore, mot de passe TLS ou identité ADMIN.

À l’inverse, le mode local :

```bash
morpheus api
```

reste disponible sans configuration remote mais **refuse tout bind non-loopback**. Pour exposer MORPHEUS sur le réseau, `api --remote` est obligatoire.

## 4. Appeler l’API

```bash
curl \
  -H "Authorization: Bearer $MORPHEUS_TOKEN" \
  https://morpheus.example.internal:8765/api/v1/health
```

Status remote :

```bash
curl \
  -H "Authorization: Bearer $MORPHEUS_TOKEN" \
  https://morpheus.example.internal:8765/api/v1/server/status
```

La réponse contient uniquement des informations process-local : uptime, requêtes actives/totales, échecs d’authentification/autorisation et throttling. Elle n’expose ni token, ni hash de token, ni mot de passe TLS.

Lorsque le budget de concurrence est atteint, MORPHEUS répond explicitement :

```text
HTTP 429 TOO_MANY_REQUESTS
```

Aucune file non bornée n’est créée par M26.

## 5. Backups

### CLI locale

```bash
morpheus server backup create
morpheus server backup verify --file /path/to/backup.db
```

Par défaut, les backups sont écrits dans le `backupsDirectory` du layout MORPHEUS.

Chaque backup :

- est créé par SQLite `VACUUM INTO` ;
- reçoit un SHA-256 ;
- passe `PRAGMA integrity_check` ;
- contient un ledger `schema_migrations` reconnu ;
- est rejeté si sa version de schéma est plus récente que celle supportée par le runtime.

### HTTP remote

Un ADMIN peut créer un backup :

```bash
curl -X POST \
  -H "Authorization: Bearer $MORPHEUS_ADMIN_TOKEN" \
  https://morpheus.example.internal:8765/api/v1/server/backups
```

`WRITE` et `READ` reçoivent `403`.

## 6. Restauration — offline uniquement

Il n’existe volontairement **aucun endpoint HTTP/MCP de restore**.

1. arrêtez le serveur remote ;
2. vérifiez le backup ;
3. exécutez la restauration avec confirmation explicite :

```bash
morpheus server backup verify --file /path/to/backup.db
morpheus server restore --file /path/to/backup.db --confirm
```

Si un serveur M26 détient encore le lease de la base, la restauration échoue.

Une base future n’est jamais downgradée. Une base historique compatible peut être restaurée ; le mécanisme normal de migration MORPHEUS s’appliquera lors de l’ouverture suivante.

## 7. Headers et CORS

Le frontal remote ajoute notamment :

```text
Cache-Control: no-store
X-Content-Type-Options: nosniff
X-Frame-Options: DENY
Referrer-Policy: no-referrer
Content-Security-Policy: default-src 'none'; frame-ancestors 'none'
X-Request-Id: <uuid>
```

M26 n’active **aucun CORS implicite**.

## 8. Limites M26

M26 fournit un serveur d’équipe auto-hébergeable de référence ; il ne prétend pas fournir :

- un SaaS MORPHEUS opéré ;
- OIDC/SAML/LDAP natif ;
- KMS/HSM ;
- réplication multi-nœuds ;
- haute disponibilité automatique ;
- scheduler distribué ;
- restauration à chaud ;
- exposition MCP réseau.

Le mode MCP reste STDIO local. Les futures intégrations d’identité ou de secrets devront rester derrière des frontières explicites et ne pourront pas affaiblir le mode local-first.

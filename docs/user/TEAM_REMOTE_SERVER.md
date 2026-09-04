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

## 2. Créer et administrer les identités

Créer d’abord au moins un administrateur :

```bash
morpheus server identity create \
  --principal admin \
  --role ADMIN
```

Pour borner automatiquement la durée de vie d’un credential, ajoutez une échéance ISO-8601 :

```bash
morpheus server identity create \
  --principal ci-reader \
  --role READ \
  --expires-at 2030-01-01T00:00:00Z
```

`--expires-at` est optionnel. Sans cette option, le credential est non expirant. La valeur spéciale `never` permet de rendre explicitement permanent un credential lors d’une rotation.

La commande affiche un token Bearer **une seule fois**. Copiez-le immédiatement dans votre gestionnaire de secrets.

Le fichier par défaut est :

```text
<config-dir>/remote-auth.txt
```

Il ne contient jamais le token clair. Le format courant est :

```text
principal|role|sha256(token)[|expiresAt]
```

Exemples :

```text
admin|ADMIN|7c4a8d09ca3762af61e59520943dc26494f8941b...
ci-reader|READ|0123456789abcdef...|2030-01-01T00:00:00Z
```

Les anciennes entrées à trois champs restent valides et sont volontairement interprétées comme **non expirantes**. Une entrée à quatre champs porte une échéance ISO-8601. Dès que cette échéance est atteinte, le token est refusé à l’authentification suivante.

Un token perdu ne peut pas être reconstruit depuis son hash. Utilisez la rotation du credential.

### Cycle de vie administratif

```bash
morpheus server identity list
morpheus server identity rotate --principal alice
morpheus server identity rotate --principal alice --expires-at 2030-01-01T00:00:00Z
morpheus server identity rotate --principal alice --expires-at never
morpheus server identity role --principal alice --role WRITE
morpheus server identity revoke --principal alice
```

Une rotation sans `--expires-at` conserve la politique d’expiration actuelle. Une rotation avec un instant remplace l’échéance ; `--expires-at never` supprime l’échéance. `identity list` expose l’échéance et l’état `expired`, mais jamais le token ni son hash.

`create`, `rotate`, `role` et `revoke` sont sérialisés par un verrou fichier inter-processus afin que deux commandes MORPHEUS concurrentes ne puissent pas écraser silencieusement leurs modifications.

Le serveur remote **relit le fichier d’identités à chaque authentification**. Il n’est donc plus nécessaire de le redémarrer après une mutation :

```text
create   -> le nouveau credential est utilisable dès la requête suivante
rotate   -> l'ancien token devient invalide dès la requête suivante
role     -> le nouveau rôle est appliqué dès la requête suivante
revoke   -> le token révoqué est refusé dès la requête suivante
expiry   -> le token expiré est refusé dès la requête suivante
```

MORPHEUS refuse de révoquer ou rétrograder le dernier `ADMIN` actif.

Le fichier conserve également un audit **sans secret** des mutations. Cet historique est une fenêtre roulante bornée aux **512 événements les plus récents**. Cette compaction fait partie de l'écriture atomique du snapshot : la croissance de l'audit ne peut donc pas remplir indéfiniment le fichier de 256 KiB et empêcher une rotation ou une révocation urgente.

### Rôles et autorisation distante

| Rôle | Autorisations |
|---|---|
| `READ` | endpoints explicitement déclarés read-only, Query DSL, exports, policy evaluate/dry-run, transition-check |
| `WRITE` | READ + mutations métier/configuration explicitement déclarées |
| `ADMIN` | WRITE + métriques remote + création de backups serveur + probe de plugin externe |

`READ != WRITE != ADMIN`. Un `principal` fourni dans un JSON n’est jamais une authentification.

La façade distante applique une **table exhaustive `(méthode HTTP, route) -> rôle minimum`**. Il n’existe plus de règle implicite du type « tout GET vaut READ » ou « tout POST vaut WRITE » :

```text
route inconnue                 -> 404 NOT_FOUND
route connue, méthode inconnue -> 405 METHOD_NOT_ALLOWED
route + méthode connues        -> rôle minimum déclaré explicitement
```

Ainsi, l’ajout futur d’un endpoint GET ne l’expose jamais automatiquement à un principal READ. Toute nouvelle route distante doit être ajoutée explicitement à la politique d’autorisation et couverte par les tests.

## 3. Démarrer le serveur remote

```bash
morpheus api --remote \
  --host 0.0.0.0 \
  --port 8765 \
  --tls-keystore /secure/morpheus-server.p12 \
  --workspace-root /srv/morpheus/workspaces
```

Options utiles :

```text
--auth-file PATH         fichier d'identités, défaut <config-dir>/remote-auth.txt
--max-concurrent N       1..512, défaut 64
--workspace-root PATH    racine workspace autorisée, option répétable
--provider-plugin-dir    répertoire de plugins externe configuré côté serveur
```

Variables :

```text
MORPHEUS_SERVER_TLS_PASSWORD       obligatoire
MORPHEUS_SERVER_TLS_KEYSTORE       alternative à --tls-keystore
MORPHEUS_SERVER_AUTH_FILE          alternative à --auth-file
MORPHEUS_SERVER_MAX_CONCURRENT     limite de concurrence
MORPHEUS_SERVER_WORKSPACE_ROOTS    racines séparées par le séparateur de chemins OS
```

La propriété protégée équivalente est `morpheus.server.workspaceRoots`. Les racines sont configurées uniquement au démarrage du serveur. Elles doivent exister, être des répertoires réels et ne pas être des liens symboliques. Le mode remote refuse de démarrer sans racine valide, keystore, mot de passe TLS ou identité ADMIN.

Un rôle `WRITE` peut enregistrer ou synchroniser uniquement la racine exacte ou un descendant réel de cette allowlist. MORPHEUS refuse les traversals, sorties de racine et chemins utilisant un symlink/junction. La même politique est réappliquée aux workspaces déjà persistés avant chaque synchronisation. Les erreurs remote ne renvoient pas les chemins serveur refusés.

Le scan de synchronisation est borné **avant** fingerprint SHA-256. Les valeurs par défaut sont :

```text
profondeur             128 segments
répertoires traversés  50 000
fichiers                50 000
taille individuelle    64 MiB
volume agrégé           2 GiB
```

Un workspace qui dépasse l’un de ces budgets produit un scan incomplet et ne devient pas une nouvelle baseline. Ces plafonds protègent aussi le cas pathologique d’une arborescence contenant énormément de répertoires vides.

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

`server/status` est servi **hors du budget de requêtes qu'il décrit**, sur une voie bornée qui lui est propre. C'est délibéré : la question « pourquoi le serveur est-il à son plafond ? » doit rester posable quand il y est. Le champ `activeRequests` compte donc le travail en vol, jamais l'appel de status lui-même.

Trois champs servent au diagnostic d'une mutation bloquée :

| Champ | Lecture |
|---|---|
| `activePrivilegedRequests` / `maxConcurrentPrivilegedRequests` | occupation de la voie WRITE/ADMIN |
| `oldestActivePrivilegedRequestMillis` | âge de l'opération privilégiée la plus ancienne encore en cours |
| `throttledPrivilegedRequests` | refus imputables à la pression WRITE/ADMIN (sous-ensemble de `throttledRequests`) |

Un `activePrivilegedRequests` saturé avec un `oldestActivePrivilegedRequestMillis` de quelques secondes est de la charge. Le même compteur saturé avec un âge qui ne cesse de croître est une mutation que rien ne terminera : c'est le signal d'intervention, MORPHEUS ne la tuera pas de lui-même (voir ci-dessous).

Lorsque le budget de concurrence est atteint, MORPHEUS répond explicitement :

```text
HTTP 429 TOO_MANY_REQUESTS
HTTP 429 PRIVILEGED_CONCURRENCY_LIMIT
```

Aucune file non bornée n’est créée par M26. Les opérations read-only dont l'exécution est entièrement contrôlée par MORPHEUS disposent d’un timeout amont de 60 secondes. Les mutations ne reçoivent pas cette deadline arbitraire : le slot de concurrence reste détenu jusqu'à la réponse réelle du traitement interne.

La capacité privilégiée est plafonnée au quart du budget de requêtes (`maxConcurrentPrivilegedRequests`). Des mutations toutes bloquées ne peuvent donc jamais confisquer plus d'un quart de la capacité : les lectures conservent les trois quarts restants. **Risque résiduel assumé en 1.2.1** : une mutation réellement bloquée immobilise son slot jusqu'à sa fin réelle, et une saturation durable de la voie privilégiée refuse les mutations suivantes en `429` sans les mettre en file. MORPHEUS préfère ce refus explicite à une deadline qui rapporterait `504` pour un commit peut-être déjà durable.

Le probe de plugin externe fait volontairement partie des exceptions au timeout façade, même s'il est sémantiquement read-only : il exécute du code tiers explicitement approuvé par ADMIN et ce code ne possède pas de contrat de cancellation coopérative. MORPHEUS ne renvoie donc pas un faux `504` en libérant le slot alors que le plugin pourrait continuer à tourner ; le slot reste détenu jusqu'à la fin réelle du probe. Un administrateur doit considérer un plugin bloquant comme un plugin défectueux et intervenir sur le processus si nécessaire.

Un client doit traiter toute rupture réseau pendant une mutation comme un résultat à réconcilier avant retry.

### Probe de plugin externe en remote

La discovery reste read-only et **metadata-only**. Elle refuse un répertoire de plugins symbolique ainsi que les candidats JAR symboliques ; une discovery ne peut donc pas suivre un lien `*.jar` vers un fichier externe au répertoire configuré.

Le **probe qui charge du code JAR est ADMIN et exige un pin SHA-256** :

```bash
curl -X POST \
  -H "Authorization: Bearer $MORPHEUS_ADMIN_TOKEN" \
  "https://morpheus.example.internal:8765/api/v1/provider-plugins/probe?pluginId=my-plugin&workspace=/srv/morpheus/workspaces/project&sha256=<64-hex>"
```

Le client ne choisit pas le répertoire de plugins : le serveur utilise uniquement son `--provider-plugin-dir`. Le JAR épinglé est copié dans un staging privé, le digest est vérifié sur cette copie puis seule cette copie est chargée ; le chemin mutable d’origine n’est jamais utilisé après la vérification d’intégrité.

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

L'ouverture normale de la base applique la même règle : une base créée par un runtime plus récent est refusée avant l'exécution des migrations connues, afin d'empêcher un downgrade implicite.

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

## 7. Cohérence des synchronisations

La publication d'un snapshot et la persistance de la baseline d'inventaire restent deux mutations distinctes, mais l'orchestration ne transforme plus un résultat déjà commité en faux échec :

1. après une exception de persistance, MORPHEUS relit l'état et accepte un commit déjà visible ;
2. si le commit n'est pas visible, un **unique retry idempotent** de la baseline est tenté ;
3. si une publication a pu réussir mais que la baseline reste impossible à persister, l'état est marqué `BASELINE_INCONSISTENT` plutôt que `EXECUTION_FAILED` ;
4. le prochain plan force alors un full rebuild conservateur ;
5. `SCAN_INCOMPLETE` reste une cause spécifique et n'est pas écrasée par `EXECUTION_FAILED`.

Cette stratégie est une réconciliation bornée et explicite ; elle ne prétend pas transformer deux transactions physiques en une transaction distribuée atomique.

## 8. Permissions locales sensibles

Les fichiers d'authentification, bases, sidecars, locks et staging contrôlés par MORPHEUS sont durcis en permissions propriétaire lorsque le filesystem expose POSIX ou ACL. Sur POSIX, un répertoire sensible préexistant qui est modifiable par le groupe ou les autres utilisateurs est refusé au lieu d'être utilisé silencieusement.

## 9. Headers et CORS

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

## 10. Frontière de confiance et limites M26

Les plugins provider approuvés et les pairs MCP configurés bénéficient de contrôles d’intégrité, de limites de ressources, d’un environnement hérité réduit et d’un nettoyage de processus. Ces mécanismes constituent une isolation de cycle de vie et de secrets ambiants, **pas un sandbox de sécurité OS**.

Un plugin ou pair MCP explicitement approuvé s’exécute avec les droits fichier/réseau du compte système MORPHEUS. Pour exécuter du code tiers réellement non fiable, utilisez une isolation système supplémentaire : compte dédié à privilèges minimaux, conteneur ou mécanisme de sandbox adapté à la plateforme.

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

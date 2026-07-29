# ADR-0094 — Optional team / remote server mode

Statut : **Proposée — M26**

Date : 29 juillet 2026

## Contexte

MORPHEUS est local-first et son API HTTP M11-M25 écoute par défaut sur `127.0.0.1`. M26 doit permettre un usage d’équipe optionnel sans transformer cette API locale en service réseau implicitement exposé ni déplacer l’autorité des faits provider vers un control plane serveur.

## Décision proposée

M26 introduit deux modes explicites :

```text
LOCAL   loopback uniquement, HTTP local historique, auth non imposée
REMOTE  opt-in, TLS obligatoire, authentification obligatoire, RBAC obligatoire
```

Le mode local reste la baseline produit. Un bind non-loopback est refusé en mode local.

## Frontière TLS

Le mode REMOTE utilise le serveur HTTPS JDK et un keystore PKCS12 fourni explicitement. Le mot de passe du keystore vient d’une variable d’environnement/propriété dédiée ; il n’est ni accepté comme argument CLI, ni sérialisé, ni retourné dans status/metrics.

```text
remote != plaintext HTTP
remote startup without TLS material => FAIL
```

## Authentification

Le provider de référence est local et portable : un fichier d’identités contient uniquement :

```text
principal | role | sha256(token)
```

Le token clair n’est jamais persisté. L’en-tête attendu est `Authorization: Bearer <token>`. Les hashes sont comparés en temps constant.

Un `principal` fourni dans un body ou un query param ne constitue jamais une authentification.

## Autorisation

Rôles fermés :

```text
READ
WRITE
ADMIN
```

Ordre de capacité : `READ < WRITE < ADMIN`.

- READ : opérations métier read-only, y compris les POST explicitement read-only (`query execute`, policy dry-run/evaluate, transition-check, augmented context) ;
- WRITE : mutations métier/configuration existantes ;
- ADMIN : surface serveur, backups et métriques remote.

L’autorisation est fail-closed. Une route inconnue n’obtient jamais WRITE/ADMIN par défaut.

## Concurrence

Le serveur remote applique une limite explicite de requêtes concurrentes. Une surcharge produit HTTP `429` et ne met pas en file une quantité non bornée de travail. SQLite conserve ses transactions, busy timeout et CAS existants ; M26 n’introduit pas de last-write-wins silencieux.

## Observabilité

Compteurs process-local uniquement :

```text
startedAt
uptimeSeconds
activeRequests
maxConcurrentRequests
totalRequests
authenticationFailures
authorizationFailures
throttledRequests
```

Aucun token, hash, mot de passe, header Authorization ou contenu métier n’est exposé.

## Backup et restauration

Les backups SQLite utilisent une copie cohérente produite par SQLite (`VACUUM INTO`) vers une destination explicite hors symlink. Chaque backup est vérifié par `PRAGMA integrity_check` et par la version du ledger `schema_migrations`.

La restauration est **offline** : elle n’est jamais exécutée par un endpoint sur la DB active. Elle exige confirmation explicite, vérifie intégrité/version, copie vers un fichier temporaire puis remplace atomiquement la DB cible lorsque possible.

```text
backup != live restore
restore != implicit migration
```

Une base plus récente que le schéma supporté est rejetée. Une base plus ancienne peut être restaurée puis migrée par le mécanisme normal au prochain démarrage ; aucune migration historique n’est réécrite.

## État serveur

Les identités remote, limites de concurrence, TLS et backups sont de la configuration/opérabilité. Ils ne deviennent jamais source de vérité des spécifications, snapshots, providers ou portfolios.

## Conséquences

Positives : exposition réseau explicite et fail-closed, usage équipe possible, authz séparée des capabilities métier, restauration opérable, surcharge bornée et observabilité sans télémétrie obligatoire.

Coûts : configuration TLS/auth supplémentaire, maintenance d’un fichier d’identités, nouvelles surfaces de maintenance et tests multiplateformes.

## Validation requise avant acceptation

ADR-0094 reste **Proposée** jusqu’à preuve Windows + Linux/WSL sur le même SHA exact :

```text
local loopback compatibility PASS
non-loopback local rejection PASS
remote TLS startup PASS
missing TLS/auth fail-closed PASS
Bearer authentication PASS
READ/WRITE/ADMIN authorization PASS
secret non-disclosure PASS
bounded concurrency / 429 PASS
backup + integrity verification PASS
offline restore PASS
schema compatibility PASS
server observability PASS
architecture/security contracts PASS
SBOM/provenance PASS
portable Windows/Linux PASS
postGateExecutableDelta=NONE
```

En juillet 2026, aucune GitHub Actions / CI n’est utilisée comme preuve M26.
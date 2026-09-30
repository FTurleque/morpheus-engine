# ADR-0094 — Optional team / remote server mode

Statut : **Acceptée — M26**

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

Le listen backlog HTTPS est distinct de la limite de concurrence applicative afin qu’une saturation soit observée et rejetée par le contrôle applicatif plutôt que par un refus TCP prématuré. Les deux budgets restent bornés.

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

### Identités historiques à trois champs — position tenue

Les identités remote au format historique à trois champs **n'expirent pas**, et c'est un engagement de
compatibilité, pas un défaut à résorber. Il est verrouillé par `legacyThreeFieldIdentityRemainsNonExpiring`
(`MorpheusRemoteIdentityFileTest`), rendu visible par `server identity list` — qui expose `nonExpiring` par
entrée et le total `nonExpiringIdentities` — et migrable sans rotation de token par
`server identity migrate-legacy`, avec `--dry-run`, écriture atomique verrouillée, audit `EXPIRY_MIGRATED`
et refus complet d'un lockout ADMIN. Les identités **nouvelles** exigent `--expires-at`, `never` restant un
choix explicite et nommé.

**Requalification de `DT-12` (16/09/2026).** Le registre la portait comme dette de sécurité remote alors que
le tableau disait lui-même que retirer le format « reste une évolution explicitement incompatible, pas un
patch 1.2.1 ». C'est donc une position, et elle est ici. **Événement de réouverture** : une version majeure
qui assume la rupture de compatibilité, ou la constatation qu'une identité historique non expirante a servi
à un accès non légitime — auquel cas c'est un incident, pas une dette.

## Conséquences

Positives : exposition réseau explicite et fail-closed, usage équipe possible, authz séparée des capabilities métier, restauration opérable, surcharge bornée et observabilité sans télémétrie obligatoire.

Coûts : configuration TLS/auth supplémentaire, maintenance d’un fichier d’identités, nouvelles surfaces de maintenance et tests multiplateformes.

## Validation d’acceptation

ADR-0094 est acceptée après qualification Windows + Linux/WSL sur le même SHA exact :

```text
qualified SHA                         bf481b24054c4577144b4cb2ede2bdbc4d9974a2
local loopback compatibility          PASS
non-loopback local rejection          PASS
remote TLS startup                    PASS
missing TLS/auth fail-closed          PASS
Bearer authentication                 PASS
READ/WRITE/ADMIN authorization        PASS
secret non-disclosure                 PASS
bounded concurrency / 429             PASS
backup + integrity verification       PASS
offline restore                       PASS
schema compatibility                  PASS
server observability                  PASS
architecture/security contracts       PASS
SBOM/provenance                       PASS Windows + Linux
portable                              PASS Windows + Linux
postGateExecutableDelta               NONE Windows + Linux
```

Preuve : [`../validation/VALIDATION_M26.md`](../validation/VALIDATION_M26.md).

En juillet 2026, aucune GitHub Actions / CI n’est utilisée comme preuve M26.

## Amendement du 25 septembre 2026 (NEX-1, API-2) — une seule projection pour un statut d'intégration

Le statut d'une intégration optionnelle (MINOS, NEXUS) porte l'emplacement du JAR, du répertoire personnel et de la JVM du
serveur. Il était projeté sur `GET /api/v1/integrations/{system}/status` mais relayé tel quel par les deux routes
`augmented-context` (rôle READ) et les deux outils MCP `get_augmented_*_context`, qui portent le même objet.

La projection vit dans `morpheus-application` (`IntegrationStatusDisclosure`, à côté de `ServerLocationDisclosure`) et s'applique à toute
frontière distante ou orientée modèle : la route de statut (`IntegrationStatusViews` n'en est plus que la mise en forme), les deux routes
et les deux outils. Un emplacement est rapporté comme *configuré*, jamais nommé ; les alias NEXUS `jar`/`home` sont ramenés sur `jarPath`/
`homeDirectory` **avant** l'examen de la valeur (une clé inconnue tombait dans le filtre générique, qui supprimait la ligne — l'opérateur
ne pouvait plus distinguer « pas de JAR » de « ligne retirée » — et laissait un chemin relatif en clair). La CLI ne projette pas : c'est
là qu'un opérateur corrige les réglages.

### Alternatives écartées

- **Projeter dans `AugmentedContextService`.** Le service est partagé avec la CLI, qui doit garder les réglages complets.
- **Un filtre par transport.** Deux copies d'un prédicat dérivent, et la plus faible est celle qui fuit.
- **Toujours émettre les trois clés `…Configured`, y compris à `false`.** Changerait la forme de la réponse de tout statut ; une clé absente
  reste l'absence de réglage, comme pour MINOS.

### Conséquences

- **Preuve.** `MorpheusAugmentedContextApiContractTest` et `MorpheusAugmentedContextMcpToolsTest` assertent, sur *chaque valeur de chaîne* de
  `technicalContext` (succès et échec, exigence et change), qu'aucune ne satisfait `namesAServerLocation`, et que `projectId`/`projectName`/
  `estimatedTokens` survivent. `IntegrationStatusProjectionArchitectureTest` exige l'appel de la projection dans tout adaptateur `api`/`mcp`
  qui produit un statut (assertion textuelle : c'est un appel qui doit *exister*, ADR-0103) et interdit la projection dans la CLI.
- **Résidu assumé.** La garde textuelle ne voit qu'un adaptateur qui produit un statut par les formes reconnues (`new AugmentedContextService(`,
  `….status()`) ; un nouveau chemin d'obtention d'un statut hors de ces formes n'est pas détecté. Le contenu du `TechnicalContextBundle` lui-même
  (`items[].path`, `excluded`, `metadata`, issus de la charge utile NEXUS) n'est pas filtré : c'est le contenu voulu du projet indexé, pas un
  réglage du serveur, et le contrat NEXUS qu'ils sont des chemins *relatifs au projet* est une **hypothèse**, non vérifiée par MORPHEUS.
  Sont aussi hors périmètre les messages `INVALID`/`UNAVAILABLE` qui reprennent une chaîne de configuration *relative* de l'opérateur
  (`invalid NEXUS path: …`, `Cannot run program "jdk/bin/java"`) : le prédicat partagé ne reconnaît que les formes absolues, quotées ou espacées.

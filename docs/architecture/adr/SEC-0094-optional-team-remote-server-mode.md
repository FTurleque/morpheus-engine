---
id: SEC-0094
title: "Optional team / remote server mode"
domain: security
rules: false
---

# Optional team / remote server mode

- Statut : **Acceptée**
- Date : 2026-07-29

## Context

MORPHEUS est local-first et son API HTTP écoute par défaut sur la boucle locale. Un usage d'équipe optionnel doit être
possible sans transformer cette API locale en service réseau implicitement exposé, ni déplacer l'autorité des faits
provider vers un control plane serveur.

## Decision

Deux modes explicites :

```text
LOCAL   loopback uniquement, HTTP local, authentification non imposée
REMOTE  opt-in, TLS obligatoire, authentification obligatoire, RBAC obligatoire
```

Le mode local reste la baseline produit. Un bind non-loopback est refusé en mode local
(`LoopbackHostPolicy`).

**Frontière TLS.** Le mode REMOTE utilise le serveur HTTPS du JDK, en TLSv1.3 et TLSv1.2, avec un keystore PKCS12
fourni explicitement. Le mot de passe du keystore vient **uniquement** de la variable d'environnement
`MORPHEUS_SERVER_TLS_PASSWORD` : il n'est accepté ni comme argument CLI, ni comme propriété JVM, et n'est ni
sérialisé, ni retourné dans un statut ou des métriques (ADR-0105).

```text
remote != plaintext HTTP
remote startup without TLS material => FAIL
```

**Authentification.** Le provider de référence est local et portable : un fichier d'identités porte, par principal, son
rôle, l'empreinte SHA-256 de son jeton et, s'il en a une, son expiration. Le jeton en clair n'est jamais persisté.
L'en-tête attendu est `Authorization: Bearer <token>`. Les empreintes sont comparées en temps constant
(`MessageDigest.isEqual`). Un `principal` fourni dans un corps ou un paramètre de requête ne constitue jamais une
authentification.

**Identités au format historique à trois champs.** Elles n'expirent pas, et c'est un engagement de compatibilité, pas
un défaut à résorber. `server identity list` le rend visible (`nonExpiring` par entrée, total
`nonExpiringIdentities`), et `server identity migrate-legacy` leur donne une expiration sans rotation de jeton, avec
`--dry-run`, écriture atomique verrouillée, audit `EXPIRY_MIGRATED` et refus complet d'un lockout ADMIN.
`server identity create` et `server identity rotate` acceptent `--expires-at`, une date ISO-8601 ou `never`, qui rend
l'identité permanente par un choix nommé. Cette position se rouvre sur une version majeure qui assume la rupture de
compatibilité, ou sur la constatation qu'une identité historique non expirante a servi à un accès non légitime, qui
est alors un incident.

**Autorisation.** Rôles fermés, ordonnés `READ < WRITE < ADMIN` (`MorpheusRemoteRole`) :

- READ : opérations métier en lecture seule, y compris les POST explicitement en lecture seule (exécution de requête,
  dry-run et évaluation de policy, transition-check, contexte augmenté) ;
- WRITE : mutations métier et de configuration ;
- ADMIN : surface serveur, backups et métriques remote.

L'autorisation est fail-closed : une route inconnue n'obtient jamais WRITE ni ADMIN par défaut.

**Concurrence.** Le serveur remote applique une limite explicite de requêtes concurrentes. Une surcharge produit HTTP
`429` et ne met pas en file une quantité non bornée de travail. SQLite conserve ses transactions, son busy timeout et
ses CAS ; le mode remote n'introduit aucun last-write-wins silencieux. Le listen backlog HTTPS est distinct de la limite
de concurrence applicative, pour qu'une saturation soit observée et rejetée par le contrôle applicatif plutôt que par un
refus TCP prématuré. Les deux budgets restent bornés.

**Observabilité.** Le statut remote ne porte que des compteurs locaux au processus (`MorpheusRemoteRuntimeState`), dont
`startedAt`, `uptimeSeconds`, `activeRequests`, `maxConcurrentRequests`, `totalRequests`, `authenticationFailures`,
`authorizationFailures` et `throttledRequests`. Aucun jeton, empreinte, mot de passe, en-tête `Authorization` ni
contenu métier n'y est exposé.

**Statut d'une intégration.** Le statut d'une intégration optionnelle (MINOS, NEXUS) porte l'emplacement du JAR, du
répertoire personnel et de la JVM du serveur. Une seule projection, `IntegrationStatusDisclosure`
(`morpheus-application`, à côté de `ServerLocationDisclosure`), s'applique à toute frontière distante ou orientée
modèle : la route de statut d'intégration (dont `IntegrationStatusViews` n'est que la mise en forme), les deux routes
`augmented-context` et les deux outils MCP `get_augmented_*_context`. Un emplacement est rapporté comme *configuré*,
jamais nommé ; les alias NEXUS `jar` et `home` sont ramenés sur `jarPath` et `homeDirectory` **avant** l'examen de la
valeur. Une clé `…Configured` absente reste l'absence de réglage. La CLI ne projette pas : c'est là qu'un opérateur
corrige les réglages. Ni `AugmentedContextService`, partagé avec la CLI, ni un filtre par transport ne porte cette
projection.

**Backup et restauration.** Un backup SQLite est une copie cohérente produite par SQLite (`VACUUM INTO`) vers une
destination explicite qui n'est pas un lien symbolique. Chaque backup est vérifié par `PRAGMA integrity_check` et par
la version du ledger `schema_migrations`. La restauration est **offline** : elle n'est jamais exécutée par un endpoint
sur la base active. Elle exige une confirmation explicite, vérifie intégrité et version, copie vers un fichier
temporaire puis remplace atomiquement la base cible lorsque c'est possible. Une base plus récente que le schéma
supporté est rejetée ; une base plus ancienne peut être restaurée puis migrée par le mécanisme normal au démarrage
suivant, sans réécrire aucune migration historique.

```text
backup != live restore
restore != implicit migration
```

**État serveur.** Les identités remote, limites de concurrence, TLS et backups relèvent de la configuration et de
l'opérabilité. Ils ne deviennent jamais source de vérité des spécifications, snapshots, providers ou portfolios.

## Do's and Don'ts

### Do

- Exiger TLS, authentification Bearer et rôle pour toute exposition non-loopback.
- Lire le mot de passe du keystore dans `MORPHEUS_SERVER_TLS_PASSWORD` seulement.
- Comparer les empreintes de jeton en temps constant.
- Refuser par défaut une route inconnue.
- Rejeter une surcharge par `429` dans une limite de concurrence explicite.
- Passer tout statut d'intégration par `IntegrationStatusDisclosure` avant une frontière distante ou orientée modèle.
- Vérifier chaque backup par `PRAGMA integrity_check` et la version de `schema_migrations`.

### Don't

- Ne pas accepter un bind non-loopback en mode local.
- Ne pas accepter le mot de passe TLS comme argument CLI ni comme propriété JVM.
- Ne pas persister un jeton en clair, ni l'exposer avec son empreinte dans un statut ou des métriques.
- Ne pas traiter un `principal` fourni dans un corps ou un paramètre comme une authentification.
- Ne pas exposer de restauration par un endpoint ; ne pas restaurer la base active.
- Ne pas projeter le statut d'intégration dans la CLI.
- Ne pas faire expirer les identités au format historique sans rupture de compatibilité assumée.

## Consequences

### Positives

- Exposition réseau explicite et fail-closed, usage d'équipe possible.
- Autorisation séparée des capacités métier.
- Restauration opérable, surcharge bornée, observabilité sans télémétrie obligatoire.

### Négatives

- Configuration TLS et authentification supplémentaire, maintenance d'un fichier d'identités.
- Nouvelles surfaces de maintenance et tests multiplateformes.

### Risques

- La garde textuelle de la projection du statut d'intégration ne voit qu'un adaptateur qui obtient un statut par les
  formes reconnues (`new AugmentedContextService(`, `….status()`) ; un autre chemin n'est pas détecté.
- Le contenu du `TechnicalContextBundle` (`items[].path`, `excluded`, `metadata`, issus de NEXUS) n'est pas filtré :
  c'est le contenu voulu du projet indexé, et le fait que ces chemins soient relatifs au projet est une hypothèse du
  contrat NEXUS, non vérifiée par MORPHEUS.
- Les messages `INVALID` ou `UNAVAILABLE` qui reprennent une chaîne de configuration relative de l'opérateur ne sont pas
  filtrés : le prédicat partagé ne reconnaît que les formes absolues, quotées ou espacées.

## Compliance and Enforcement

- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m26/RemoteServerArchitectureTest.java` :
  frontières du serveur remote, OpenAPI remote (authentification Bearer, restauration offline absente), sortie du
  format d'identité non expirant sans rotation de jeton.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m26/RemoteIdentityDecompositionContractTest.java` :
  décomposition du fichier d'identités.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/RemoteHttpServerBootstrapArchitectureTest.java`
  et `morpheus-cli/src/test/java/com/morpheus/cli/RemoteApiLaunchOptionsTest.java` :
  `aTlsPasswordGivenOnlyAsAJvmPropertyIsRefused`.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LocalHttpServerBootstrapArchitectureTest.java` :
  loopback du serveur local.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusRemoteIdentityFileTest.java` :
  `legacyThreeFieldIdentityRemainsNonExpiring`.
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusRemoteRoutePolicyTest.java` : politique de rôles par route.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/IntegrationStatusProjectionArchitectureTest.java` :
  appel de la projection dans tout adaptateur `api` ou `mcp` qui produit un statut, et projection absente de la CLI
  (assertion textuelle).
- `morpheus-api/src/test/java/com/morpheus/api/MorpheusAugmentedContextApiContractTest.java` et
  `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusAugmentedContextMcpToolsTest.java` : aucune chaîne de
  `technicalContext` ne nomme un emplacement du serveur, et `projectId`, `projectName`, `estimatedTokens` survivent.
- `contracts/public-surfaces.tsv` : `server.restore` porte `EXPLICITLY_OFFLINE_ONLY` en HTTP.

## References

- [ADR-0087](SEC-0087-local-first-operability-and-secure-diagnostic-defaults.md) — opérabilité local-first.
- [ADR-0099](SEC-0099-bounded-response-lifetime-on-the-remote-facade.md) — durée de vie bornée d'une réponse remote.
- [ADR-0100](SEC-0100-identity-audit-is-evidence-not-authority.md) — l'audit d'identités est une preuve.
- [ADR-0105](SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md) — pas de coffre de secrets ; mot de passe TLS
  par l'environnement.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie avant migration.
- [ADR-0113](SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.md) — journal d'accès remote.
- [Validation M26](../../project/validation/VALIDATION_M26.md)

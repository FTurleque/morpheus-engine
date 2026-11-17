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

Les deux modes et les invariants que la décision tient :

```text
LOCAL   loopback uniquement, HTTP local, authentification non imposée
REMOTE  opt-in, TLS obligatoire, authentification obligatoire, RBAC obligatoire

remote != plaintext HTTP
remote startup without TLS material => FAIL
backup != live restore
restore != implicit migration
```

Le provider d'authentification de référence est local et portable. Le rôle READ couvre aussi les POST explicitement en
lecture seule : exécution de requête, dry-run et évaluation de policy, transition-check, contexte augmenté. Le listen
backlog HTTPS est distinct de la limite de concurrence pour qu'une saturation soit observée et rejetée par le contrôle
applicatif plutôt que par un refus TCP prématuré. Les compteurs du statut remote comprennent `startedAt`,
`uptimeSeconds`, `activeRequests`, `maxConcurrentRequests`, `totalRequests`, `authenticationFailures`,
`authorizationFailures` et `throttledRequests`.

READ porte les opérations métier en lecture seule, WRITE les mutations métier et de configuration. Le serveur remote
conserve les transactions, le busy timeout et les CAS de SQLite.

La non-expiration des identités historiques à trois champs est un engagement de compatibilité, pas un défaut à
résorber ; `--expires-at never` rend une identité permanente par un choix nommé. `server identity list` porte
`nonExpiring` par entrée et le total `nonExpiringIdentities` ; `server identity migrate-legacy` offre `--dry-run`, une
écriture atomique verrouillée, l'audit `EXPIRY_MIGRATED` et le refus complet d'un lockout ADMIN. Cette position se rouvre sur une
version majeure qui assume la rupture de compatibilité, ou sur la constatation qu'une identité historique non
expirante a servi à un accès non légitime, qui est alors un incident.

Le statut d'une intégration optionnelle (MINOS, NEXUS) porte l'emplacement du JAR, du répertoire personnel et de la
JVM du serveur. `IntegrationStatusDisclosure` vit dans `morpheus-application`, à côté de `ServerLocationDisclosure` ;
`IntegrationStatusViews` n'est que la mise en forme de la route de statut. Une clé `…Configured` absente reste
l'absence de réglage. La CLI ne projette pas parce que c'est là qu'un opérateur corrige les réglages, et
`AugmentedContextService` est partagé avec elle.

La restauration copie vers un fichier temporaire avant le remplacement atomique, quand il est possible ; une base plus
ancienne restaurée est migrée par le mécanisme normal, sans réécrire aucune migration historique. L'état serveur
(identités remote, limites de concurrence, TLS, backups) ne devient jamais source de vérité des spécifications,
snapshots, providers ou portfolios.

## Decision

1. **Modes** : `LOCAL` (baseline, loopback seul, autre bind refusé par `LoopbackHostPolicy`) ; `REMOTE` (opt-in,
   TLS, authentification, RBAC).
2. **TLS** : HTTPS du JDK, TLSv1.3/1.2, keystore PKCS12 explicite, sinon pas de démarrage ; mot de passe selon
   ADR-0105, jamais sérialisé ni exposé.
3. **Authentification** : fichier d'identités (rôle, empreinte du jeton, expiration ; jeton : ADR-0105),
   `Authorization: Bearer <token>` ; un `principal` de corps ou de paramètre n'authentifie jamais.
4. **Identités à trois champs** : sans expiration ; `server identity list` les signale (`nonExpiring`),
   `server identity migrate-legacy` les date sans rotation ni lockout ADMIN, `create`/`rotate` prennent
   `--expires-at` (ISO-8601 ou `never`).
5. **Rôles** : `READ < WRITE < ADMIN` (`MorpheusRemoteRole`) ; ADMIN pour serveur, backups, métriques. Une route
   inconnue n'a jamais WRITE ni ADMIN.
6. **Concurrence** : limite et backlog bornés, surcharge en `429` ; CAS SQLite gardés, aucun last-write-wins.
7. **Statut remote** : compteurs de `MorpheusRemoteRuntimeState`, sans jeton, empreinte, secret ni contenu métier.
8. **Intégrations** : seule `IntegrationStatusDisclosure` projette, sur la route de statut, les routes
   `augmented-context` et les outils `get_augmented_*_context` ; un emplacement est dit *configuré*, jamais nommé
   (`jar`/`home` lus comme `jarPath`/`homeDirectory` avant examen). Ni CLI, ni `AugmentedContextService`, ni filtre.
9. **Backup** : `VACUUM INTO` vers une destination non symbolique, vérifié (`PRAGMA integrity_check`,
   `schema_migrations`).
10. **Restauration** : offline, confirmée, vérifiée, remplacement atomique ; jamais par endpoint ni sur la base active ;
    base plus récente refusée, plus ancienne migrée au démarrage.
11. **État serveur** : configuration, jamais source de vérité métier.

## Do's and Don'ts

### Do

- Exiger TLS, authentification Bearer et rôle pour toute exposition non-loopback.
- Refuser par défaut une route inconnue.
- Rejeter une surcharge par `429` dans une limite de concurrence explicite.
- Passer tout statut d'intégration par `IntegrationStatusDisclosure` avant une frontière distante ou orientée modèle.
- Vérifier chaque backup par `PRAGMA integrity_check` et la version de `schema_migrations`.

### Don't

- Ne pas accepter un bind non-loopback en mode local.
- Ne pas exposer un jeton ni son empreinte dans un statut ou des métriques.
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
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/LocalHttpServerBootstrapArchitectureTest.java` :
  loopback du serveur local.
- `morpheus-api/src/test/java/com/morpheus/api/remote/MorpheusRemoteIdentityFileTest.java` :
  `legacyThreeFieldIdentityRemainsNonExpiring`.
- `morpheus-api/src/test/java/com/morpheus/api/remote/MorpheusRemoteRoutePolicyTest.java` : politique de rôles par route.
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
  par l'environnement, jetons persistés en empreinte.
- [ADR-0110](DATA-0110-a-copy-is-kept-before-the-schema-advances.md) — copie avant migration.
- [ADR-0113](SEC-0113-the-remote-server-records-who-was-authorized-to-do-what.md) — journal d'accès remote.
- [Validation M26](../../project/validation/VALIDATION_M26.md)

# Proposition : refuser un fichier d'identifiants remote que d'autres comptes peuvent écrire (AUD-SEC-21)

## Why

Le fichier d'identités remote est chargé sans que ses propres permissions ni son propriétaire soient vérifiés, alors
qu'ADR-0105 fonde le modèle de menace sur ce fichier en `rw-------` (« protège contre les autres comptes de la
machine »). Constat `AUD-SEC-21` (Moyenne) de l'audit du 17/11/2026 (`docs/project/audits/2026-11-17/findings.md`).

Revérifié sur `1a539c22` : `RemoteIdentityFileStore.readLines` refuse un lien symbolique et un fichier non régulier
(`secureExistingFile`), vérifie le **répertoire** parent par `LocalWritePermissionHardener.requireWriteProtectedDirectory`,
puis lit le fichier. `requireWriteProtectedDirectory` ne parcourt que des répertoires ; `hardenFile` n'est appelé qu'à
l'écriture (`writeAtomically`). La lecture du keystore TLS par `MorpheusRemoteHttpServerBootstrap` suit le même schéma.
Un compte du même groupe, ou titulaire d'une ACL Windows héritée, peut ajouter `moi|ADMIN|<empreinte de son jeton>` à
un fichier provisionné hors de `server identity create` (copie, éditeur, umask `002`) : le cache le recharge dans la
seconde (`MorpheusRemoteIdentitySnapshotCache.DEFAULT_REVALIDATION_INTERVAL`) et ce compte devient ADMIN remote — le
probe de plugin exécute alors du code.

## What Changes

- Avant toute lecture, le fichier d'identités doit être un fichier régulier, possédé par le compte qui exécute
  MORPHEUS (ou un administrateur de confiance), et inscriptible par personne d'autre (POSIX : ni groupe ni autres ;
  ACL : aucun principal non fiable avec un droit d'écriture). Sinon, refus nommé, au démarrage comme au rechargement.
- La même vérification s'applique au keystore TLS avant sa lecture.
- La vérification **ne modifie pas** les permissions : elle refuse et nomme le remède (`chmod 600`, ou
  `server identity rotate` qui réécrit le fichier durci).
- **BREAKING** pour un déploiement dont le fichier ou le keystore est aujourd'hui inscriptible par le groupe : le
  serveur refuse de démarrer jusqu'à correction des permissions.

## Capabilities

### New Capabilities
- `remote-credential-file-trust` : ce que le serveur remote exige d'un fichier d'identifiants (fichier d'identités,
  keystore TLS) avant de le lire.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `RemoteIdentityFileStore.readLines`, la lecture du keystore dans `MorpheusRemoteHttpServerBootstrap`
(`morpheus-api`), une vérification de fichier en lecture seule dans `LocalWritePermissionHardener`
(`morpheus-application`, `com.morpheus.application.files`), leurs tests, `SECURITY.md`,
`docs/user/TEAM_REMOTE_SERVER.md`, la page durable de la capacité.

**Exclus :** le journal d'accès, créé et durci par MORPHEUS (`remote-access-journal`) ; la base SQLite, déjà vérifiée
par `SqliteDatabaseSecurity` ; le fichier de capacité locale éventuel de `decide-who-the-local-api-trusts`.

## Acteurs et surfaces concernés

Un autre compte de la machine (même groupe, ACL élargie) ; l'opérateur qui provisionne le fichier d'identités et le
keystore du mode remote.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le fichier lui-même n'est pas vérifié à la lecture | existant vérifié | `RemoteIdentityFileStore.readLines`, `LocalWritePermissionHardener.requireWriteProtectedDirectory` |
| Le keystore est lu de la même manière | existant vérifié | `MorpheusRemoteHttpServerBootstrap` (lecture par `SafeWorkspaceFileResolver.readBytes`) |
| ADR-0105 suppose un fichier `rw-------` | existant vérifié | `docs/architecture/adr/SEC-0105-no-secret-vault-no-reusable-secret-at-rest.md` |
| Aucun test ne vise l'écriture de groupe sur ce fichier | existant vérifié (audit, grep) | `morpheus-api/src/test`, `morpheus-architecture-tests` |
| Aucun nouvel ADR n'est requis | décision | ADR-0105 décide déjà le modèle ; ce changement le tient |

## Impact

`morpheus-application` (`LocalWritePermissionHardener`), `morpheus-api` (remote). Aucune surface publique, aucune
OpenAPI, aucun test d'architecture modifié a priori. Notes de version de la version qui livre (rupture de démarrage).

## Questions ouvertes

Aucune.

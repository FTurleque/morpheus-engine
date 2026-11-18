# Proposition : décider qui l'API locale croit (AUD-SEC-22)

## Why

L'API locale n'authentifie aucun appelant. Tout autre compte de la machine qui atteint `127.0.0.1` peut enregistrer
un répertoire arbitraire, le synchroniser avec les droits du compte MORPHEUS et écrire dans la base de l'opérateur
(packs de politique, activations, overrides, vues, transitions de cycle de vie). Constat `AUD-SEC-22` (Élevée) de
l'audit du 17/11/2026 (`docs/project/audits/2026-11-17/findings.md`).

Revérifié sur `1a539c22` :

- `MorpheusMain.runApi` démarre le serveur par `MorpheusHttpServer.start(...)`, qui passe
  `new RemoteAccessControls(Optional.empty(), Optional.empty())` : ni capacité interne, ni allowlist de workspaces ;
- sans capacité, seule `LoopbackRequestPolicy` filtre (`Host`, `Origin`, `Sec-Fetch-Site`), ce qui arrête un
  navigateur, pas un autre processus local ;
- sans allowlist, `MorpheusProjectRegistryApiService.registerProject` accepte tout répertoire existant ;
- `CliProjectWriteCapabilityResolver` est câblé : les écritures du cycle de vie sont ouvertes.

Le reste du produit est durci contre les autres comptes de l'hôte : la base (`SqliteDatabaseSecurity`) et ADR-0105,
que `SECURITY.md` résume par « The protection is bounded to other accounts of the host ». Ni `SECURITY.md`, ni
ADR-0065, ni le guide utilisateur ne disent que l'API loopback, elle, est ouverte à ces comptes. Le modèle de menace est
donc contradictoire, et un gate de CI qui lit les politiques de la base peut être faussé par un autre compte.

## What Changes

- La frontière de confiance de l'API locale est **décidée par écrit** par le mainteneur (design.md, D1) et énoncée dans
  `SECURITY.md` et le guide utilisateur.
- Selon l'option recommandée (à valider) : le serveur local exige d'une requête la capacité locale du processus,
  publiée dans un fichier privé du répertoire de données, et refuse sans elle par un code nommé. **BREAKING** pour un
  client HTTP local existant, qui doit lire et envoyer cette capacité.
- Les sondes d'exploitation (`health`, `readiness`) restent servies sans capacité (exigence existante de
  `local-admission-control`, « The probes SHALL answer under saturation », à confirmer par D1).

## Capabilities

### New Capabilities
- `local-api-trust-boundary` : qui l'API locale croit, comment elle le vérifie, et ce que la documentation en dit.

### Modified Capabilities

## Périmètre et exclusions

**Inclus :** `MorpheusHttpServer.start` et `MorpheusLocalHttpServerBootstrap` (`morpheus-api`), `MorpheusMain.runApi`
et l'aide de `morpheus api` (`morpheus-cli`), les clients locaux livrés qui appellent l'API locale, `SECURITY.md`,
`docs/user/CLI.md`, la page durable de la capacité, l'OpenAPI (`docs/developer/reference/openapi/morpheus-v1.yaml`)
pour le refus nommé.

**Exclus :** le serveur remote, qui authentifie déjà par jeton et passe sa capacité interne au saut loopback ; le
transport MCP STDIO, dont l'appelant est le processus parent ; la borne de connexions du serveur remote
(`close-a-remote-connection-that-stalls-before-authentication`).

## Acteurs et surfaces concernés

Un autre compte de la machine (poste partagé, serveur de rebond, session Windows TS) pendant que `morpheus api` tourne ;
l'opérateur et ses clients HTTP locaux (scripts, outils d'éditeur).

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Le serveur local démarre sans capacité ni allowlist | existant vérifié | `MorpheusHttpServer.start`, `MorpheusMain.runApi` |
| Tout répertoire existant est enregistrable en local | existant vérifié | `MorpheusProjectRegistryApiService.registerProject` |
| La base est durcie contre les autres comptes | existant vérifié | `SqliteDatabaseSecurity`, `SECURITY.md`, ADR-0105 |
| La frontière de confiance de l'API locale | question ouverte | design.md, D1 — décision du mainteneur |
| Une capacité locale par fichier privé | proposition à valider | design.md, D1 |

## Impact

`morpheus-api`, `morpheus-cli` ; `contracts/public-surfaces.tsv` n'ajoute aucune ligne (aucune route), mais l'OpenAPI
déclare le refus ; tests d'intégration de `morpheus-api`. Un ADR est à proposer (frontière de confiance durable,
coûteuse à inverser pour les clients) ; il complète ADR-0065 et ADR-0105 sans être déclaré accepté ici.

## Questions ouvertes

- **Décision du mainteneur** : documenter la confiance envers tous les comptes de l'hôte, exiger une capacité locale,
  exiger une allowlist de workspaces en local, ou une combinaison (design.md, D1).
- Les sondes restent-elles servies sans capacité (design.md, D1) ?

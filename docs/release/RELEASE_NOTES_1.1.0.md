# MORPHEUS 1.1.0 — Notes de version

Statut : **CANDIDATE — NON PUBLIÉE**

Date de préparation : 30 juillet 2026

La version 1.1.0 consolide dans une release stable les évolutions M21 à M27 développées après MORPHEUS 1.0.0. La publication reste bloquée jusqu'à la qualification exacte Windows/Linux, au merge dans `main`, au tag `v1.1.0` et aux builds exact-tag.

## Points forts

### Intégrité de production et convergence des surfaces — M21

- contrats CLI/MCP/HTTP réconciliés ;
- manifeste des surfaces publiques READ/WRITE ;
- durcissement du packaging, de la provenance et du SBOM ;
- qualification reproductible Windows/Linux.

### Provider SDK et plugins externes — M22

- Provider SDK v1 public ;
- testkit pour auteurs de providers ;
- découverte passive distincte de l'activation ;
- plugin de référence externe ;
- isolation entre SDK public et implémentation du domaine.

### Intelligence multi-projets et portfolios — M23

- registre de portfolios provider-neutral ;
- identité projet indépendante du workspace, repository et provider ;
- références inter-projets ;
- requêtes projet et portfolio ;
- traversal BFS déterministe, bornée et explicable ;
- persistance Memory et SQLite V013.

### Query DSL, saved views et reporting — M24

- DSL de requête typé sans passthrough SQL ;
- filtres, tris, projections et pagination déterministes ;
- saved views versionnées avec CAS ;
- persistance SQLite V014 ;
- exports JSON canonique, CSV et Markdown ;
- budgets explicites de requête et d'export.

### Policy Packs et gouvernance — M25

- Policy Packs provider-neutral versionnés et immuables ;
- scopes projet/portfolio ;
- décisions PASS/WARN/BLOCK/UNKNOWN ;
- overrides explicites avec provenance ;
- dry-run strictement read-only ;
- audit append-only ;
- persistance SQLite V015.

### Mode équipe / serveur distant optionnel — M26

- mode local toujours first-class ;
- mode remote explicitement activé ;
- HTTPS obligatoire pour tout bind non-loopback ;
- authentification Bearer avec persistance hash-only ;
- RBAC READ/WRITE/ADMIN ;
- concurrence bornée et HTTP 429 ;
- observabilité sans divulgation de secrets ;
- backup SQLite, vérification et restore offline.

### Raisonnement assisté fondé sur des preuves — M27

- séparation structurelle entre faits publiés, inférences, heuristiques et suggestions ;
- confiance explicite et bornée ;
- citations d'évidence et provenance ;
- adaptateurs optionnels et fault-isolated ;
- adaptateur local déterministe sans LLM ;
- mode facts-only ;
- aucune mutation implicite, `mutated=false`.

## Compatibilité et données

La release 1.0.0 utilise le schéma SQLite V012. MORPHEUS 1.1.0 ajoute :

```text
V013  portfolio intelligence
V014  saved views
V015  policy packs
```

L'upgrade est forward-only au niveau du schéma. Il doit préserver les projets, identités métier, snapshots, historiques publiés, références, états de synchronisation, audits et données de composition existantes.

Avant l'upgrade :

1. arrêter toute instance MORPHEUS ;
2. créer et vérifier un backup SQLite ;
3. conserver les binaires et la configuration 1.0.0 ;
4. installer ou extraire 1.1.0 ;
5. démarrer 1.1.0 une première fois pour appliquer V013→V015 ;
6. vérifier le health/readiness, la version et les données principales.

Guide détaillé : [`../user/UPGRADE_1_1.md`](../user/UPGRADE_1_1.md).

## Garanties conservées

```text
local-first remains default
no mandatory LLM in core
facts != inference
inference never overwrites published facts
PROPOSED never leaks into CURRENT
APPLY != PROMOTE != ACTIVATE
READ != WRITE != ADMIN
remote mode remains opt-in
provider discovery != provider activation
conflict != silent last-write-wins
```

## Plateformes et artefacts prévus

```text
Windows x64 setup per-user
Windows x64 portable ZIP
Linux x64 portable tar.gz
embedded Java runtime
SHA-256 checksum per binary payload
Windows/Linux release manifests
CycloneDX JSON/XML
build provenance
```

Assets attendus :

```text
MORPHEUS-1.1.0-windows-x64-setup.exe
MORPHEUS-1.1.0-windows-x64-setup.exe.sha256
morpheus-1.1.0-windows-x64.zip
morpheus-1.1.0-windows-x64.zip.sha256
morpheus-1.1.0-linux-x64.tar.gz
morpheus-1.1.0-linux-x64.tar.gz.sha256
morpheus-1.1.0-windows-x64-release-manifest.json
morpheus-1.1.0-linux-x64-release-manifest.json
```

## Qualification

Baseline de release :

```text
develop@bccc118dda6fd818cf801750187afa4ad10b96e4
```

Le SHA exact 1.1.0, les nombres de tests, couvertures, hashes et tailles d'artefacts seront consignés dans [`../validation/VALIDATION_R2.md`](../validation/VALIDATION_R2.md) après exécution réelle.

## État de publication

```text
Issue             #113 OPEN
PR                #114 DRAFT
Merge main        NOT AUTHORIZED
Tag v1.1.0        NOT CREATED
GitHub Release    NOT CREATED
Result            CANDIDATE
```

Aucune ligne de ce document ne doit être interprétée comme une annonce de disponibilité avant la publication effective de la GitHub Release.
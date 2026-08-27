# MORPHEUS — Distribution et release 1.2

Deux états doivent rester distincts :

```text
release publiée     1.2.0 / tag v1.2.0 / commit 3ad9ebf030b58df97482e21e272c24feae6b9d86
baseline active     1.2.1 corrective / non publiée tant que v1.2.1 n'existe pas
```

Le tag `v1.2.0` et ses artefacts sont historiques et immuables. Le code de développement postérieur utilise `1.2.1` afin qu'un arbre différent ne soit jamais distribué sous l'identité déjà publiée `1.2.0`.

## Artefacts publiés 1.2.0

```text
Windows setup
  MORPHEUS-1.2.0-windows-x64-setup.exe
  MORPHEUS-1.2.0-windows-x64-setup.exe.sha256

Windows portable
  morpheus-1.2.0-windows-x64.zip
  morpheus-1.2.0-windows-x64.zip.sha256
  morpheus-1.2.0-windows-x64-release-manifest.json

Linux portable
  morpheus-1.2.0-linux-x64.tar.gz
  morpheus-1.2.0-linux-x64.tar.gz.sha256
  morpheus-1.2.0-linux-x64-release-manifest.json
```

La preuve R3 confirme la parité publiée **8/8 PASS**.

## Contenu packagé

Les distributions embarquent :

```text
CLI MORPHEUS
serveur MCP STDIO
API HTTP locale
façade remote HTTPS opt-in
services application/domain
Provider SDK + providers
store SQLite + migrations V001→V015
Jackson
runtime Java minimal
integration/configure-mcp-clients.ps1
integration/configure-mcp-clients-setup.ps1
integration/README.md
```

Elles n’embarquent ni l’implémentation MINOS, ni l’implémentation NEXUS, ni JARVIS.

## Windows setup

Le setup est défini par :

```text
distribution/windows/MORPHEUS.iss
```

Contrat :

```text
per-user
PrivilegesRequired=lowest
%LOCALAPPDATA%\Programs\MORPHEUS
AppId stable
PATH utilisateur opt-in
cinq intégrations MCP opt-in
uninstall conservateur
état persistant conservé par défaut
```

## Build actif 1.2.1

Portable Windows :

```powershell
.\distribution\build-portable.ps1 -Version 1.2.1
```

Setup Windows :

```powershell
.\distribution\build-installer.ps1 -Version 1.2.1
```

Portable Linux :

```bash
bash distribution/build-portable.sh 1.2.1
```

Les builders portables/installateur utilisent `1.2.1` par défaut.

## Release 1.2.1 — pas encore publiée

Les builders de release actifs utilisent `1.2.1` par défaut mais sont fail-closed :

```powershell
.\distribution\build-release.ps1 -Version 1.2.1 -ExpectedTag v1.2.1
```

```bash
bash distribution/build-release.sh 1.2.1 v1.2.1
```

Ils refusent l'exécution si :

- le workspace Git n'est pas propre ;
- le tag attendu n'existe pas ;
- le tag ne pointe pas exactement sur HEAD.

Le workflow `.github/workflows/release.yml` ajoute la chaîne de confiance GitHub pour les tags `vX.Y.Z` :

- le SHA du tag doit être atteignable depuis `main` ;
- le build passe par `distribution/build-release.sh` ;
- l'archive Linux reçoit une attestation de provenance GitHub via OIDC avec `actions/attest` pinné par SHA ;
- le bundle d'attestation est conservé comme asset ;
- la GitHub Release est créée sans `--clobber` ;
- une release existante n'est jamais écrasée silencieusement.

Le fichier `.sha256` reste la preuve d'intégrité locale ; l'attestation GitHub ajoute la preuve d'origine liée au commit et au workflow de publication.

Par conséquent, le simple bump de la baseline à `1.2.1` ne crée pas une release et ne peut pas écraser `v1.2.0`.

## Reproduction historique 1.2.0

Pour reproduire exactement la release publiée, checkout le tag immuable `v1.2.0` puis utilisez les commandes/version présentes sur ce tag. Ne tentez pas de reconstruire le HEAD `1.2.1` avec `-Version 1.2.0`.

Preuve autoritative :

```text
docs/release/RELEASE_NOTES_1.2.0.md
docs/validation/VALIDATION_R3.md
```

## Gates de développement

Le gate durable M21 qualifie la baseline active :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

```bash
bash ./scripts/validate-m21.sh 1.2.1
```

Ratchets :

```text
Surefire total       >= 820
architecture         >= 258
line coverage        >= 50.6%
branch coverage      >= 43.0%
changed-line         >= 80%
changed-branch       >= 70%
```

Le gate D2 spécialisé peut toujours être lancé en `1.2.1` sur un périmètre ne modifiant pas `.github/workflows/**`.

Le workflow `MORPHEUS Security` exécute OWASP Dependency-Check sur `main` et `develop`, avec refresh trusted quotidien et cache PR limité à 72 h. `.github/dependabot.yml` maintient les dépendances Maven/GitHub Actions via PR vers `develop`.

## Runtime layout

Overrides MORPHEUS :

```text
--data-dir PATH
--config-dir PATH
--db PATH
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_LOGS_DIR
MORPHEUS_BACKUPS_DIR
MORPHEUS_DB
```

Linux respecte aussi `XDG_DATA_HOME`, `XDG_CONFIG_HOME` et `XDG_STATE_HOME`.

## Documentation

- [Installation de la release publiée 1.2.0](../docs/user/INSTALLATION.md)
- [Upgrade 1.2](../docs/user/UPGRADE_1_2.md)
- [Clients MCP](../docs/user/MCP_CLIENTS.md)
- [Version produit active](../docs/developer/PRODUCT_VERSION.md)
- [Build et tests](../docs/developer/BUILD_AND_TEST.md)
- [Validation R3 / 1.2.0 publiée](../docs/validation/VALIDATION_R3.md)
- [Registre des risques](../docs/architecture/risks/register.md)

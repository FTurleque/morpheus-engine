# R3 — Stabilisation et publication MORPHEUS 1.2.0

Statut : **QUALIFIÉ — PR PRÊTE AU MERGE**

Dernière mise à jour : 30 juillet 2026

```text
Issue                  #117 OPEN
PR                     #118 READY / MERGEABLE
Branch                 r3-release-1.2.0
Main baseline          8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Develop baseline       2080c99895115464dafefb6515541666c5d972d8
Target version         1.2.0
Target tag             v1.2.0
Qualified exact head   d08542026817f0d743766656a0197790c6809eca
Main merge commit      NOT SET
GitHub Release         NOT PUBLISHED
```

## 1. Question de sortie

> M28 peut-il être consolidé dans `main` et publié comme MORPHEUS 1.2.0 avec une version cohérente, une qualification exacte Windows/Linux, des intégrations MCP conservatrices et huit assets reproductibles vérifiés après publication ?

Réponse actuelle : **OUI POUR LE MERGE — PUBLICATION ENCORE À EXÉCUTER**.

## 2. Baseline

```text
main      8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
develop   2080c99895115464dafefb6515541666c5d972d8
relation  develop ahead 41 / behind 0
```

Le delta fonctionnel correspond à M28 — MCP Client Integration & Installer Wiring, déjà qualifié et intégré dans `develop`.

## 3. Contenu release

```text
MCP transport                   native STDIO
launcher                        morpheus.exe mcp --stdio
clients                         5
GitHub Copilot JetBrains        supported / qualified
GitHub Copilot CLI              supported / qualified
Claude Code                     supported / qualified
Claude Desktop                  supported / qualified
OpenAI Codex                    supported / qualified
JSON merge                      conservative
foreign entry overwrite         forbidden
ownership                       managed / preexisting
uninstall                       state-driven
installer choices               opt-in / unchecked
Docker required                 false
SQLite migration                none
```

## 4. Travail de stabilisation

- [x] créer l’issue #117 ;
- [x] créer `r3-release-1.2.0` depuis `develop@2080c998...` ;
- [x] passer les 17 POM en 1.2.0 ;
- [x] mettre à jour le contrat de version produit ;
- [x] passer les builders portable, installer et release en 1.2.0 ;
- [x] rendre le validateur Linux R2 réutilisable pour une version ultérieure ;
- [x] créer les validateurs R3 Windows et Linux/WSL ;
- [x] créer les notes de version 1.2.0 ;
- [x] créer le guide d’upgrade 1.1→1.2 ;
- [x] créer la preuve R3 ;
- [x] ouvrir la PR draft #118 vers `main` ;
- [x] qualifier Windows exact-head ;
- [x] qualifier Linux/WSL exact-head sur le même SHA ;
- [x] démontrer `postGateExecutableDelta=NONE` ;
- [x] vérifier les reviews et threads bloquants ;
- [x] réconcilier la preuve dual-platform avec un delta docs-only ;
- [ ] passer la PR Ready ;
- [ ] merger dans `main` avec contrôle du head ;
- [ ] créer le tag `v1.2.0` sur le merge autorisé ;
- [ ] exécuter les builds exact-tag Windows et Linux ;
- [ ] publier les huit assets ;
- [ ] retélécharger et comparer les huit SHA-256 ;
- [ ] réconcilier la documentation post-publication ;
- [ ] fermer #117 avec la raison `completed`.

## 5. Gates exact-head

### Windows

```text
command                  .\validate-r3.cmd -Version 1.2.0 -BaseRef origin/develop
status                   PASS
SHA                      d08542026817f0d743766656a0197790c6809eca
reactor                  17/17 SUCCESS
tests                    608 PASS
architecture             243 PASS
line coverage            0.452226
branch coverage          0.384456
M28 client manager       PASS
clients                  5
portable                 PASS
installer                PASS
SBOM / provenance        PASS
post-gate executable     NONE
```

### Linux / WSL

```text
command                  MORPHEUS_R3_BASE_REF=origin/develop bash ./scripts/validate-r3.sh 1.2.0
status                   PASS
SHA                      d08542026817f0d743766656a0197790c6809eca
reactor                  17/17 SUCCESS
tests                    608 PASS
architecture             243 PASS
line coverage            0.452246
branch coverage          0.384456
M28 static contract      PASS
clients                  5
portable                 PASS
installer                NOT_APPLICABLE
SBOM / provenance        PASS
post-gate executable     NONE
```

### Parité

```text
same SHA                 PASS
same tests               PASS — 608
same architecture        PASS — 243
same branch coverage     PASS — 0.384456
line coverage threshold  PASS on both platforms
repository post-gate     documentation only
```

## 6. Politique de merge

Tous les gates de merge sont satisfaits :

- Windows et Linux/WSL ont qualifié le même SHA exact ;
- le reactor est intégralement en 1.2.0 ;
- les tests M28 sont verts ;
- les distributions portables Windows et Linux sont produites ;
- l’installateur Windows est produit ;
- le delta exécutable post-gate est `NONE` ;
- le delta de réconciliation post-gate est strictement documentaire ;
- aucun thread de review ni review bloquante n’est présent.

La PR #118 peut être passée Ready et mergée dans `main` avec contrôle du head.

## 7. Tag et publication

Le tag `v1.2.0` sera créé uniquement après merge autorisé dans `main`. Il sera immuable et devra résoudre exactement au commit de release.

Assets attendus :

```text
MORPHEUS-1.2.0-windows-x64-setup.exe
MORPHEUS-1.2.0-windows-x64-setup.exe.sha256
morpheus-1.2.0-windows-x64.zip
morpheus-1.2.0-windows-x64.zip.sha256
morpheus-1.2.0-windows-x64-release-manifest.json
morpheus-1.2.0-linux-x64.tar.gz
morpheus-1.2.0-linux-x64.tar.gz.sha256
morpheus-1.2.0-linux-x64-release-manifest.json
```

Après publication, les huit assets devront être retéléchargés et comparés aux artefacts locaux par SHA-256.

## 8. Politique CI — juillet 2026

```text
GitHub Actions is not a release gate
no workflow inspection
no workflow rerun
no workflow_dispatch
no opportunistic workflow change
local Windows + Linux/WSL exact-head logs are authoritative
```

## 9. Documents

- [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- [`../release/RELEASE_NOTES_1.2.0.md`](../release/RELEASE_NOTES_1.2.0.md)
- [`../user/UPGRADE_1_2.md`](../user/UPGRADE_1_2.md)
- [`M28_EXECUTION.md`](M28_EXECUTION.md)

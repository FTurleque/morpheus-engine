# R3 — Stabilisation et publication MORPHEUS 1.2.0

Statut : **TERMINÉ / VALIDÉ / PUBLIÉ**

Dernière mise à jour : 30 juillet 2026

```text
Issue                    #117 CLOSED / completed
PR                       #118 MERGED
Release branch           r3-release-1.2.0 (supprimée)
Develop baseline         2080c99895115464dafefb6515541666c5d972d8
Qualified executable SHA d08542026817f0d743766656a0197790c6809eca
Final PR head            a2023d96dd0c4ad6d1f7a658bf3e7b4f8390e1bb
Main release commit      3ad9ebf030b58df97482e21e272c24feae6b9d86
Release version          1.2.0
Tag                      v1.2.0
GitHub Release           PUBLISHED / stable / latest
Published assets         8/8
Published parity         8/8 PASS
```

## 1. Question de sortie

> M28 peut-il être consolidé dans `main` et publié comme MORPHEUS 1.2.0 avec une version cohérente, une qualification exacte Windows/Linux, des intégrations MCP conservatrices et huit assets reproductibles vérifiés après publication ?

Réponse finale : **OUI — COMPLETE / VALIDATED / PUBLISHED**.

## 2. Contenu livré

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
portable packaging              Windows + Linux
Docker required                 false
SQLite migration                none
```

## 3. Travail réalisé

- [x] créer l’issue #117 ;
- [x] créer `r3-release-1.2.0` depuis `develop@2080c998...` ;
- [x] passer les 17 POM en 1.2.0 ;
- [x] mettre à jour le contrat de version produit ;
- [x] passer les builders portable, installer et release en 1.2.0 ;
- [x] créer les validateurs R3 Windows et Linux/WSL ;
- [x] créer les notes de version 1.2.0 ;
- [x] créer le guide d’upgrade 1.1→1.2 ;
- [x] ouvrir et qualifier la PR #118 ;
- [x] qualifier Windows exact-head ;
- [x] qualifier Linux/WSL exact-head sur le même SHA ;
- [x] démontrer `postGateExecutableDelta=NONE` ;
- [x] vérifier l’absence de review ou thread bloquant ;
- [x] merger dans `main` avec contrôle du head ;
- [x] créer le tag immuable `v1.2.0` ;
- [x] vérifier que le tag cible le commit de release exact ;
- [x] exécuter les builds exact-tag Windows et Linux ;
- [x] publier les huit assets ;
- [x] retélécharger les huit assets ;
- [x] comparer les huit fichiers publiés aux artefacts locaux par SHA-256 ;
- [x] réconcilier la documentation post-publication ;
- [x] fermer #117 avec la raison `completed`.

## 4. Qualification dual-platform

```text
qualified SHA               d08542026817f0d743766656a0197790c6809eca
Windows exact-head          PASS
Linux/WSL exact-head        PASS
same SHA                    PASS
reactor                     17/17 SUCCESS
tests Windows/Linux         608 / 608
architecture Windows/Linux  243 / 243
line coverage               0.452226 / 0.452246
branch coverage             0.384456 / 0.384456
MCP client integration      PASS
clients                     5
portable Windows/Linux      PASS / PASS
installer Windows           PASS
SBOM / provenance           PASS / PASS
schema migration            UNCHANGED
CI workflow delta           NONE
Docker required             false
post-gate executable        NONE
review threads              0
blocking reviews            0
```

## 5. Merge et tag

```text
PR                       #118 MERGED
final PR head            a2023d96dd0c4ad6d1f7a658bf3e7b4f8390e1bb
main release commit      3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                      v1.2.0
tag target               3ad9ebf030b58df97482e21e272c24feae6b9d86
tag / main relation      IDENTICAL
```

## 6. Builds exact-tag et publication

```text
Windows exact-tag build  PASS
Linux exact-tag build    PASS
GitHub Release           PUBLISHED / stable / latest
published assets         8/8
published parity         8/8 PASS
```

Empreintes principales :

```text
MORPHEUS-1.2.0-windows-x64-setup.exe
bytes   33232992
sha256  6ba9edf723889285c915d7dd4bc64e9c04b1a87c6e868e536af09c4be86f97e8

morpheus-1.2.0-windows-x64.zip
bytes   37647385
sha256  11ecf0d8c7e300137100f1e85d0f79f6291824f79e6cf502ec9aa12410dc25aa

morpheus-1.2.0-linux-x64.tar.gz
bytes   40476355
sha256  54d003cbd30ca96d39717c3d99162d61ca5f0ca5b1ae31dcc0d52a6e8851df41
```

Assets publiés :

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

## 7. Politique CI — juillet 2026

```text
GitHub Actions is not a release gate
no workflow inspection
no workflow rerun
no workflow_dispatch
no opportunistic workflow change
local Windows + Linux/WSL exact-head logs are authoritative
```

## 8. Documents

- [`../validation/VALIDATION_R3.md`](../validation/VALIDATION_R3.md)
- [`../release/RELEASE_NOTES_1.2.0.md`](../release/RELEASE_NOTES_1.2.0.md)
- [`../user/UPGRADE_1_2.md`](../user/UPGRADE_1_2.md)
- [`M28_EXECUTION.md`](M28_EXECUTION.md)

**R3 est archivé comme terminé. MORPHEUS 1.2.0 est la release stable publiée.**
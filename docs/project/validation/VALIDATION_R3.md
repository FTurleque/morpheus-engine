# R3 — Validation MORPHEUS 1.2.0

Statut : **COMPLETE / VALIDATED / PUBLISHED**

Date : 30 juillet 2026

```text
Issue                    #117 CLOSED / completed
PR                       #118 MERGED
Release branch           r3-release-1.2.0 (supprimée après merge)
Develop baseline         2080c99895115464dafefb6515541666c5d972d8
Qualified executable SHA d08542026817f0d743766656a0197790c6809eca
Final PR head            a2023d96dd0c4ad6d1f7a658bf3e7b4f8390e1bb
Main release commit      3ad9ebf030b58df97482e21e272c24feae6b9d86
Version                   1.2.0
Tag                       v1.2.0
Tag target                3ad9ebf030b58df97482e21e272c24feae6b9d86
GitHub Release            PUBLISHED / latest / stable
Published assets          8/8
Published parity          8/8 PASS
```

Plan : [`../roadmap/R3_EXECUTION.md`](../roadmap/R3_EXECUTION.md).

## 1. Périmètre qualifié

R3 consolide M28 et publie MORPHEUS 1.2.0 avec :

- le reactor Maven complet en 1.2.0 ;
- les builders portable, installer et release en 1.2.0 ;
- le contrôle de cohérence des 17 POM ;
- le gestionnaire conservateur pour cinq clients MCP ;
- les tâches opt-in de l’installateur Windows ;
- les distributions autonomes Windows et Linux ;
- les notes de version et le guide d’upgrade 1.1→1.2 ;
- aucune migration SQLite ;
- aucun changement de workflow GitHub Actions.

Aucun workflow GitHub Actions n’a été utilisé comme gate, inspecté, relancé, déclenché ou modifié pour R3.

## 2. Qualification exact-head

Commandes canoniques :

```powershell
.\validate-r3.cmd -Version 1.2.0 -BaseRef origin/develop
```

```bash
MORPHEUS_R3_BASE_REF=origin/develop \
bash ./scripts/validate-r3.sh 1.2.0
```

Les deux plateformes ont qualifié exactement le même SHA exécutable :

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
postGateExecutableDelta     NONE
review threads              0
blocking reviews            0
```

La différence de couverture de lignes de `0.000020` est instrumentale et n’affecte ni les tests, ni les contrats d’architecture, ni le seuil de couverture, ni le SHA qualifié.

## 3. Intégration MCP publiée

```text
transport                       mcp --stdio
GitHub Copilot JetBrains        PASS
GitHub Copilot CLI              PASS
Claude Code                     PASS
Claude Desktop                  PASS
OpenAI Codex                    PASS
JSON merge                      conservative
backup before mutation          PASS
foreign entry overwrite         forbidden / PASS
managed / preexisting ownership PASS
idempotency                     PASS
state-driven uninstall          PASS
Docker required                 false
```

## 4. Compatibilité 1.1.0 → 1.2.0

M28 n’introduit aucune migration SQLite.

```text
schema before            V015
schema after             V015
identity preservation    inherited gates PASS
published facts          inherited gates PASS
MCP client profiles      opt-in / external configuration
```

## 5. Incident de qualification conservé

```text
attempt 1 SHA           a3d6ee4c94c1f1368739b42195392da2de3a05df
platform                Windows
result                  FAIL
failed gate             git diff --check
cause                   trailing whitespace in RELEASE_NOTES_1.2.0.md
corrective commit       d08542026817f0d743766656a0197790c6809eca
corrective delta        documentation only / executable delta NONE
```

Cette tentative FAIL reste une preuve historique et n’est pas réécrite en PASS.

## 6. Merge et tag

```text
PR                       #118 MERGED
final PR head            a2023d96dd0c4ad6d1f7a658bf3e7b4f8390e1bb
main release commit      3ad9ebf030b58df97482e21e272c24feae6b9d86
tag                      v1.2.0
tag target               3ad9ebf030b58df97482e21e272c24feae6b9d86
tag / main relation      IDENTICAL
```

Le merge a été verrouillé sur le head attendu. Le tag annoté `v1.2.0` résout exactement au commit de release dans `main`.

## 7. Builds exact-tag

Les distributions publiées ont été reconstruites depuis `v1.2.0`, pas depuis un workspace de développement.

```text
Windows exact-tag build  PASS
Linux exact-tag build    PASS
packaged version         1.2.0
packaged update channel  stable
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

## 8. Assets publiés

| Asset | Statut |
|---|---|
| `MORPHEUS-1.2.0-windows-x64-setup.exe` | PUBLISHED / PARITY PASS |
| `MORPHEUS-1.2.0-windows-x64-setup.exe.sha256` | PUBLISHED / PARITY PASS |
| `morpheus-1.2.0-windows-x64.zip` | PUBLISHED / PARITY PASS |
| `morpheus-1.2.0-windows-x64.zip.sha256` | PUBLISHED / PARITY PASS |
| `morpheus-1.2.0-windows-x64-release-manifest.json` | PUBLISHED / PARITY PASS |
| `morpheus-1.2.0-linux-x64.tar.gz` | PUBLISHED / PARITY PASS |
| `morpheus-1.2.0-linux-x64.tar.gz.sha256` | PUBLISHED / PARITY PASS |
| `morpheus-1.2.0-linux-x64-release-manifest.json` | PUBLISHED / PARITY PASS |

La GitHub Release est stable, non draft et non prerelease. Les huit assets ont été retéléchargés puis comparés byte-for-byte aux fichiers exact-tag locaux par SHA-256.

## 9. Résultat final

```text
Preparation                    COMPLETE
Windows exact-head             PASS
Linux/WSL exact-head           PASS
same SHA cross-platform        PASS
post-gate executable delta     NONE
post-gate repository delta     documentation only
PR #118                        MERGED
main release commit            3ad9ebf030b58df97482e21e272c24feae6b9d86
tag v1.2.0                     CREATED / VERIFIED
exact-tag builds               PASS Windows + Linux
GitHub Release                 PUBLISHED / stable / latest
published asset parity         8/8 PASS
Result                         R3 COMPLETE / VALIDATED / PUBLISHED
```

**MORPHEUS 1.2.0 est la release stable publiée.**
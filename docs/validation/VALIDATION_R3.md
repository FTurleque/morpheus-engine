# R3 — Validation MORPHEUS 1.2.0

Statut : **NOT RUN — CANDIDAT NON QUALIFIÉ**

Date : 30 juillet 2026

```text
Issue                  #117 OPEN
PR                     NOT CREATED
Branch                 r3-release-1.2.0
Main baseline          8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
Develop baseline       2080c99895115464dafefb6515541666c5d972d8
Target version         1.2.0
Target tag             v1.2.0
Qualified exact head   NOT SET
Main merge commit      NOT SET
GitHub Release         NOT PUBLISHED
```

Plan : [`../roadmap/R3_EXECUTION.md`](../roadmap/R3_EXECUTION.md).

## 1. Périmètre à qualifier

Le candidat R3 comprend :

- le reactor Maven complet en 1.2.0 ;
- les builders portable, installer et release en 1.2.0 ;
- le contrôle de cohérence des 17 POM ;
- le gestionnaire M28 pour cinq clients MCP ;
- les tâches opt-in de l’installateur Windows ;
- les payloads d’intégration embarqués dans les archives Windows et Linux ;
- les validateurs R3 Windows et Linux/WSL ;
- les notes de version et le guide d’upgrade 1.1→1.2 ;
- l’absence de migration SQLite et de changement GitHub Actions.

Aucun fichier `.github/workflows` ne peut servir de preuve ou être modifié dans le cadre de R3 avant août 2026.

## 2. Commandes canoniques

Windows :

```powershell
.\validate-r3.cmd -Version 1.2.0 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_R3_BASE_REF=origin/develop \
bash ./scripts/validate-r3.sh 1.2.0
```

Les deux commandes devront qualifier exactement le même SHA.

## 3. Qualification Windows

Statut : **NOT RUN**

```text
sha                         NOT SET
versionCoherence            NOT RUN
reactor                     NOT RUN
tests                       NOT RUN
architectureTests           NOT RUN
lineCoverage                NOT RUN
branchCoverage              NOT RUN
mcpClientManager            NOT RUN
clients                     5 EXPECTED
jsonMerge                   NOT RUN
cliRegistration             NOT RUN
idempotency                 NOT RUN
foreignEntryPreservation    NOT RUN
modifiedEntryPreservation   NOT RUN
stateDrivenUninstall        NOT RUN
invalidJsonProtection       NOT RUN
portableWindows             NOT RUN
installerWindows            NOT RUN
sbom                        NOT RUN
provenance                  NOT RUN
schemaMigration             UNCHANGED EXPECTED
ciWorkflowDelta             NONE EXPECTED
postGateExecutableDelta     NOT SET
result                      NOT RUN
```

## 4. Qualification Linux / WSL

Statut : **NOT RUN**

```text
sha                         NOT SET
versionCoherence            NOT RUN
reactor                     NOT RUN
tests                       NOT RUN
architectureTests           NOT RUN
lineCoverage                NOT RUN
branchCoverage              NOT RUN
mcpClientManager            NOT RUN
clients                     5 EXPECTED
portableLinux               NOT RUN
installer                   NOT_APPLICABLE
sbom                        NOT RUN
provenance                  NOT RUN
schemaMigration             UNCHANGED EXPECTED
ciWorkflowDelta             NONE EXPECTED
postGateExecutableDelta     NOT SET
result                      NOT RUN
```

Les mutations réelles de profils clients sont qualifiées sur Windows. Linux qualifie le reactor, les contrats statiques et le payload portable.

## 5. Parité requise

```text
Windows SHA              MUST EQUAL Linux SHA
Windows tests            MUST EQUAL Linux tests
Windows architecture     MUST EQUAL Linux architecture
branch coverage          MUST MATCH
post-gate delta          MUST BE NONE or docs-only
```

## 6. Version produit

Le gate exige :

```text
17 POMs                 contain 1.2.0
17 POMs                 do not contain 1.1.0
portable default        1.2.0
installer default       1.2.0
release default         1.2.0
CLI version             1.2.0
API /version            1.2.0
product-info            1.2.0 / stable
```

## 7. Intégration MCP

Contrats à démontrer :

```text
clients                         5
transport                       mcp --stdio
GitHub Copilot JetBrains        PASS required
GitHub Copilot CLI              PASS required
Claude Code                     PASS required
Claude Desktop                  PASS required
OpenAI Codex                    PASS required
JSON merge                      conservative
backup before mutation          required
foreign entry overwrite         forbidden
managed / preexisting ownership required
idempotency                     required
state-driven uninstall          required
Docker required                 false
```

## 8. Compatibilité 1.1.0 → 1.2.0

M28 n’introduit aucune migration SQLite. Le gate R3 refuse toute modification sous le répertoire des migrations.

```text
schema before            V015
schema after             V015
identity preservation    inherited gates
published facts          inherited gates
MCP client profiles      opt-in / external configuration
```

## 9. Merge, tag et publication

```text
PR                      NOT CREATED
main merge commit       NOT SET
tag                     NOT CREATED
tag target              NOT SET
exact-tag Windows       NOT RUN
exact-tag Linux         NOT RUN
GitHub Release          NOT PUBLISHED
published assets        0/8
published parity        NOT RUN
```

## 10. Assets attendus

| Asset | Statut |
|---|---|
| `MORPHEUS-1.2.0-windows-x64-setup.exe` | NOT BUILT |
| `MORPHEUS-1.2.0-windows-x64-setup.exe.sha256` | NOT BUILT |
| `morpheus-1.2.0-windows-x64.zip` | NOT BUILT |
| `morpheus-1.2.0-windows-x64.zip.sha256` | NOT BUILT |
| `morpheus-1.2.0-windows-x64-release-manifest.json` | NOT BUILT |
| `morpheus-1.2.0-linux-x64.tar.gz` | NOT BUILT |
| `morpheus-1.2.0-linux-x64.tar.gz.sha256` | NOT BUILT |
| `morpheus-1.2.0-linux-x64-release-manifest.json` | NOT BUILT |

## 11. Résultat actuel

```text
Preparation                    IN PROGRESS
Windows exact-head             NOT RUN
Linux/WSL exact-head           NOT RUN
same SHA cross-platform        NOT RUN
post-gate executable delta     NOT SET
PR                             NOT CREATED
main                           8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
tag v1.2.0                     NOT CREATED
exact-tag builds               NOT RUN
GitHub Release                 NOT PUBLISHED
published asset parity         NOT RUN
Result                         R3 NOT QUALIFIED
```

**MORPHEUS 1.2.0 ne doit pas être présenté comme publié avant la fin de cette preuve.**

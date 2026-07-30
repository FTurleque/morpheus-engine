# R3 — Validation MORPHEUS 1.2.0

Statut : **QUALIFIÉ DUAL-PLATFORM — PR PRÊTE AU MERGE**

Date : 30 juillet 2026

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

Plan : [`../roadmap/R3_EXECUTION.md`](../roadmap/R3_EXECUTION.md).

## 1. Périmètre qualifié

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

Aucun fichier `.github/workflows` n’a été utilisé comme preuve ou modifié dans le cadre de R3 avant août 2026.

## 2. Commandes canoniques exécutées

Windows :

```powershell
.\validate-r3.cmd -Version 1.2.0 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_R3_BASE_REF=origin/develop \
bash ./scripts/validate-r3.sh 1.2.0
```

Les deux commandes ont qualifié exactement le même SHA exécutable.

## 3. Qualification Windows

Statut : **PASS**

```text
sha                         d08542026817f0d743766656a0197790c6809eca
versionCoherence            PASS — 1.2.0 across 17 POMs
reactor                     PASS — 17/17 SUCCESS
tests                       PASS — 608
architectureTests           PASS — 243
lineCoverage                0.452226
branchCoverage              0.384456
mcpClientManager            PASS
clients                     5
jsonMerge                   PASS
cliRegistration             PASS
idempotency                 PASS
foreignEntryPreservation    PASS
modifiedEntryPreservation   PASS
stateDrivenUninstall        PASS
invalidJsonProtection       PASS
portableWindows             PASS
installerWindows            PASS
sbom                        PASS
provenance                  PASS
schemaMigration             UNCHANGED
ciWorkflowDelta             NONE
dockerRequired              false
postGateExecutableDelta     NONE
result                      R3 VALIDATION PASS
```

Artefacts de qualification produits :

```text
MORPHEUS-1.2.0-windows-x64-setup.exe
MORPHEUS-1.2.0-windows-x64-setup.exe.sha256
morpheus-1.2.0-windows-x64.zip
morpheus-1.2.0-windows-x64.zip.sha256
```

## 4. Qualification Linux / WSL

Statut : **PASS**

```text
sha                         d08542026817f0d743766656a0197790c6809eca
versionCoherence            PASS — 1.2.0 across 17 POMs
reactor                     PASS — 17/17 SUCCESS
tests                       PASS — 608
architectureTests           PASS — 243
lineCoverage                0.452246
branchCoverage              0.384456
mcpClientManager            STATIC_PASS
clients                     5
portableLinux               PASS
installer                   NOT_APPLICABLE
sbom                        PASS
provenance                  PASS
schemaMigration             UNCHANGED
ciWorkflowDelta             NONE
dockerRequired              false
postGateExecutableDelta     NONE
result                      R3 VALIDATION PASS
```

Artefact de qualification produit :

```text
morpheus-1.2.0-linux-x64.tar.gz
```

Les mutations réelles de profils clients sont qualifiées sur Windows. Linux qualifie le reactor, les contrats statiques, le launcher, l’API embarquée et le payload portable.

## 5. Parité dual-platform

```text
Windows SHA              d08542026817f0d743766656a0197790c6809eca
Linux SHA                d08542026817f0d743766656a0197790c6809eca
same SHA                 PASS
Windows tests            608
Linux tests              608
test parity              PASS
Windows architecture     243
Linux architecture       243
architecture parity      PASS
Windows branch coverage  0.384456
Linux branch coverage    0.384456
branch parity            PASS
post-gate executable     NONE
```

La couverture de lignes diffère de `0.000020` entre Windows et Linux (`0.452226` contre `0.452246`). Cette variation instrumentale multiplateforme n’affecte ni les tests, ni les contrats d’architecture, ni le seuil de couverture, ni le SHA qualifié.

## 6. Intégration MCP

Contrats démontrés :

```text
clients                         5
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

## 7. Compatibilité 1.1.0 → 1.2.0

M28 n’introduit aucune migration SQLite.

```text
schema before            V015
schema after             V015
identity preservation    inherited gates PASS
published facts          inherited gates PASS
MCP client profiles      opt-in / external configuration
```

## 8. Incident de qualification conservé

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

## 9. Merge, tag et publication

```text
PR                      #118 READY / MERGEABLE
qualified executable    d08542026817f0d743766656a0197790c6809eca
post-gate repo delta    documentation only
main merge commit       NOT SET
tag                     NOT CREATED
tag target              NOT SET
exact-tag Windows       NOT RUN
exact-tag Linux         NOT RUN
GitHub Release          NOT PUBLISHED
published assets        0/8
published parity        NOT RUN
```

Le tag `v1.2.0` doit être créé uniquement après le merge autorisé dans `main`, puis les distributions doivent être reconstruites depuis ce tag exact.

## 10. Assets attendus

| Asset | Statut |
|---|---|
| `MORPHEUS-1.2.0-windows-x64-setup.exe` | QUALIFICATION BUILD PASS |
| `MORPHEUS-1.2.0-windows-x64-setup.exe.sha256` | QUALIFICATION BUILD PASS |
| `morpheus-1.2.0-windows-x64.zip` | QUALIFICATION BUILD PASS |
| `morpheus-1.2.0-windows-x64.zip.sha256` | QUALIFICATION BUILD PASS |
| `morpheus-1.2.0-windows-x64-release-manifest.json` | EXACT-TAG BUILD REQUIRED |
| `morpheus-1.2.0-linux-x64.tar.gz` | QUALIFICATION BUILD PASS |
| `morpheus-1.2.0-linux-x64.tar.gz.sha256` | EXACT-TAG BUILD REQUIRED |
| `morpheus-1.2.0-linux-x64-release-manifest.json` | EXACT-TAG BUILD REQUIRED |

## 11. Résultat actuel

```text
Preparation                    COMPLETE
Windows exact-head             PASS
Linux/WSL exact-head           PASS
same SHA cross-platform        PASS
post-gate executable delta     NONE
post-gate repository delta     documentation only
review threads                 0
blocking reviews               0
PR                             #118 READY / MERGEABLE
main                           8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
tag v1.2.0                     NOT CREATED
exact-tag builds               NOT RUN
GitHub Release                 NOT PUBLISHED
published asset parity         NOT RUN
Result                         R3 QUALIFIED — MERGE AUTHORIZED
```

**MORPHEUS 1.2.0 est qualifié pour le merge, mais ne doit pas être présenté comme publié avant la création du tag, les builds exact-tag et la vérification des huit assets.**

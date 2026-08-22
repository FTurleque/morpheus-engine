# D2 — Validation Post-R3 Repository Hardening

Statut : **QUALIFIED CANDIDATE — FINAL EXACT-HEAD REVALIDATION REQUIRED AFTER THIS EVIDENCE COMMIT**

Issue : **#120**

> **Historical evidence notice.** This file preserves the literal D2 qualification evidence produced for MORPHEUS
> 1.2.0 on 2026-08-11. The current repository is newer and must not inherit these results. The canonical D2 scripts now
> default to the current product version and derive the reactor from the root `pom.xml` `<modules>` declaration instead
> of assuming the historical 17-project reactor. Any current D2 qualification requires fresh same-SHA Windows and
> Linux/WSL executions; the historical commands and measurements below are intentionally left unchanged.

## 1. Scope

D2 hardens the post-R3 repository without changing product semantics or GitHub Actions.

```text
stable product                 MORPHEUS 1.2.0
stable tag                     v1.2.0
qualification base            origin/develop @ 78d3231cd1c6aa80691135301495f1224e1a10ee
qualified candidate           c5a6694b27bcb95bd51364e31c53a5b7092356f1
GitHub Actions inspection      NOT USED
GitHub Actions execution       NOT USED
.github/workflows modification FORBIDDEN
```

## 2. Implemented hardening

```text
Jackson BOM                    3.1.5 LTS
sqlite-jdbc                    3.53.2.0
OWASP Dependency-Check         12.2.2 local profile
SCA fail threshold             CVSS >= 7.0
maven dependency hygiene       failOnWarning=true
JaCoCo line floor              0.40
JaCoCo branch floor            0.35
excessive JSON nesting test    ADDED
active documentation           reconciled to 1.2.0 / D2
sqlite CPE false positive      exact package-url + CPE suppression
unused SCA suppression         build failure enabled
```

## 3. Canonical gates

Windows:

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Linux / WSL:

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

The two executions must validate exactly the same Git SHA.

## 4. Evidence protocol

The results below are literal local gate results for candidate
`c5a6694b27bcb95bd51364e31c53a5b7092356f1`. This document is committed only
after those executions, so it cannot truthfully claim that the commit containing
it was the tested SHA. Both canonical gates must therefore be executed again on
the final evidence commit. The resulting final SHA and summaries are recorded on
PR #121 without another tracked-file change.

This avoids attributing a gate result to a different SHA and keeps the final
Windows and Linux/WSL proof exact-head.

## 5. Windows candidate evidence

Execution date: **2026-08-11**

Command:

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

```text
SHA                     c5a6694b27bcb95bd51364e31c53a5b7092356f1
clean verify            PASS (17/17 reactor modules)
tests                   PASS (613; 0 failures, 0 errors, 0 skipped)
architecture            PASS (247)
line coverage           PASS (0.452226 >= 0.40)
branch coverage         PASS (0.384456 >= 0.35)
dependency hygiene      PASS
SCA                     PASS (CVSS >= 7 gate)
SBOM                    PASS (CycloneDX aggregate)
portable                PASS (packaging + smoke)
workflow delta          NONE
CI used                 false
workspace tracked final PASS
```

## 6. Linux / WSL candidate evidence

Execution date: **2026-08-11**

Environment: WSL2 Ubuntu, Temurin JDK 24 at `/opt/jdk-24`. `JAVA_HOME` was
defined explicitly because the interactive WSL environment exposed `java` and
`jpackage` through alternatives without defining it.

Command:

```bash
JAVA_HOME=/opt/jdk-24 \
MORPHEUS_D2_BASE_REF=origin/develop \
bash ./scripts/validate-d2.sh 1.2.0
```

```text
SHA                     c5a6694b27bcb95bd51364e31c53a5b7092356f1
clean verify            PASS (17/17 reactor modules)
tests                   PASS (613; 0 failures, 0 errors, 0 skipped)
architecture            PASS (247)
line coverage           PASS (0.451799 >= 0.40)
branch coverage         PASS (0.384456 >= 0.35)
dependency hygiene      PASS
SCA                     PASS (CVSS >= 7 gate)
SBOM                    PASS (CycloneDX aggregate)
portable                PASS (Linux app-image, archive + smoke)
workflow delta          NONE
CI used                 false
workspace tracked final PASS
```

The small line-coverage difference is the measured platform result; both values
exceed the contractual 40% floor. Branch coverage and test counts are identical.

## 7. SCA triage evidence

Dependency-Check initially associated the internal reactor artifact
`io.github.fturleque:morpheus-store-sqlite:1.2.0` with the unrelated product CPE
`cpe:/a:sqlite:sqlite`. The finding was not against the actual runtime driver
`org.xerial:sqlite-jdbc:3.53.2.0`.

The suppression in `config/dependency-check-suppressions.xml` is restricted to
the internal artifact's exact package URL/version and that CPE. The actual
`sqlite-jdbc` component remains scanned. `failBuildOnUnusedSuppressionRule=true`
makes the gate fail if this triage rule becomes stale. Both candidate platform
gates consumed the rule and passed with no unsuppressed CVSS >= 7 finding.

## 8. Cross-platform reconciliation

```text
candidate Windows SHA == Linux SHA   PASS
candidate workflow delta             NONE
candidate post-gate tracked delta    NONE
final evidence-commit SHA             TO BE RECORDED ON PR #121
final Windows exact-head gate         REQUIRED
final Linux/WSL exact-head gate       REQUIRED
review threads                        REQUIRED BEFORE MERGE
blocking reviews                      REQUIRED NONE BEFORE MERGE
```

## 9. Result

The pre-document candidate is qualified on both platforms. D2 remains **NOT
COMPLETE** until both canonical gates pass again on the final commit containing
this evidence and their literal same-SHA summaries are attached to PR #121.

No CI status may be substituted for either local platform proof.

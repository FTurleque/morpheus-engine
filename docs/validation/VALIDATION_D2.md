# D2 — Validation Post-R3 Repository Hardening

Statut : **PENDING LOCAL QUALIFICATION**

Issue : **#120**

## 1. Scope

D2 hardens the post-R3 repository without changing product semantics or GitHub Actions.

```text
stable product                 MORPHEUS 1.2.0
stable tag                     v1.2.0
baseline before D2             4acc3fca82aa676542592d37566630a2304e5ac6
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

## 4. Windows evidence

```text
SHA                     PENDING
clean verify            PENDING
tests                   PENDING
architecture            PENDING
line coverage           PENDING
branch coverage         PENDING
dependency hygiene      PENDING
SCA                     PENDING
SBOM                    PENDING
portable                PENDING
workflow delta          REQUIRED NONE
workspace final         PENDING
```

## 5. Linux / WSL evidence

```text
SHA                     PENDING
clean verify            PENDING
tests                   PENDING
architecture            PENDING
line coverage           PENDING
branch coverage         PENDING
dependency hygiene      PENDING
SCA                     PENDING
SBOM                    PENDING
portable                PENDING
workflow delta          REQUIRED NONE
workspace final         PENDING
```

## 6. Cross-platform reconciliation

```text
Windows SHA == Linux SHA      PENDING
post-gate executable delta    PENDING
review threads                PENDING
blocking reviews              PENDING
```

## 7. Result

D2 remains **NOT COMPLETE** until both local exact-head gates are recorded above on the same SHA and all required gates pass.

No CI status may be substituted for either local platform proof.

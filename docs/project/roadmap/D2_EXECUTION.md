# D2 — Post-R3 Repository Hardening

Statut : **TERMINÉ — VALIDÉ — INTÉGRÉ DANS DEVELOP**

Issue : **#120**

Baseline de départ :

```text
main == develop
SHA             4acc3fca82aa676542592d37566630a2304e5ac6
stable          MORPHEUS 1.2.0
stable tag      v1.2.0
R3              COMPLETE / VALIDATED / PUBLISHED
```

## 1. Question de sortie

> Le HEAD post-R3 est-il sécurisé, documenté et localement qualifié sur Windows + Linux/WSL, sans dépendre d’aucune CI et sans modifier GitHub Actions ?

D2 ne peut répondre **OUI** qu'après qualification locale du même SHA exact sur les deux plateformes.

## 2. Contrainte absolue — aucune CI

D2 est local-only.

```text
GitHub Actions inspection   FORBIDDEN
workflow rerun/dispatch     FORBIDDEN
.github/workflows mutation  FORBIDDEN
CI result as gate           FORBIDDEN
Windows local gate          REQUIRED
Linux/WSL local gate        REQUIRED
same exact SHA              REQUIRED
```

Les validateurs D2 refusent explicitement tout delta sous `.github/workflows/`.

## 3. D2-S1 — Dependency security hardening

Cible : supprimer les versions identifiées pendant l'audit comme obsolètes ou vulnérables.

```text
Jackson BOM     3.0.3    -> 3.1.5 LTS
sqlite-jdbc     3.53.1.0 -> 3.53.2.0
OWASP Dependency-Check   -> 12.2.2, profil Maven local d2-security
```

Le scan SCA est volontairement séparé du `clean verify` ordinaire afin de ne pas transformer tout build développeur en opération réseau. Le gate D2 l'exécute explicitement.

Politique :

```text
runtime/test scope        test scope excluded from SCA
HIGH/CRITICAL threshold   failBuildOnCVSS >= 7.0
scan error                FAIL
report                    ALL sous target/d2-security
suppression               une association CPE interne explicitement triée
unused suppression        FAIL
```

La règle versionnée cible uniquement le package interne `io.github.fturleque:morpheus-store-sqlite:1.2.0`, que Dependency-Check confond avec SQLite 1.2.0. Elle ne masque pas le driver réel `org.xerial:sqlite-jdbc:3.53.2.0`, qui reste entièrement analysé.

## 4. D2-S2 — JSON security regression

Garanties :

```text
HTTP request body remains bounded
FAIL_ON_UNKNOWN_PROPERTIES retained
FAIL_ON_TRAILING_TOKENS retained
excessive nesting rejected before JVM stack exhaustion
no default/polymorphic typing activation introduced
```

Un test de régression dédié exerce un JSON fortement imbriqué et refuse explicitement un `StackOverflowError`.

## 5. D2-S3 — Quality ratchet

Le niveau mesuré à R3 était d'environ 45.2% lignes / 38.45% branches. D2 remonte les floors sans prétendre au niveau mesuré exact :

```text
line floor    0.25 -> 0.40
branch floor  0.20 -> 0.35
```

Le `maven-dependency-plugin:analyze-only` devient bloquant :

```text
failOnWarning=true
```

Le gate Maven canonique reste :

```text
mvnw clean verify
```

`clean test` n'est pas un gate suffisant pour le reactor : les tests d'architecture consomment les JARs et rapports produits jusqu'à `verify`.

## 6. D2-S4 — Documentation reconciliation

Les guides actifs doivent converger sur :

```text
stable version       1.2.0
stable tag           v1.2.0
R3                   published
M28                  delivered in 1.2.0
D2                   active hardening consolidation
Windows validator    scripts/validate.cmd
```

Documents minimum :

```text
README.md
docs/README.md
docs/user/README.md
docs/developer/README.md
docs/user/INSTALLATION.md
docs/developer/BUILD_AND_TEST.md
distribution/README.md
docs/release/RELEASE_NOTES_1.2.0.md
docs/user/UPGRADE_1_2.md
docs/governance/ROADMAP.md
docs/governance/DOCUMENTATION_STATUS.md
docs/roadmap/README.md
docs/validation/README.md
```

Les preuves historiques conservent leurs commandes et versions réellement exécutées ; seules les pages actives sont réconciliées.

## 7. D2-S5 — Exact-head local qualification

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

Chaque gate doit prouver :

```text
workspace tracked clean at start
HEAD immutable during gate
git diff --check PASS
.github/workflows delta NONE
17 POMs remain MORPHEUS 1.2.0
Jackson 3.1.5
sqlite-jdbc 3.53.2.0
clean verify PASS
all Surefire tests PASS
architecture tests PASS
line coverage >= 0.40
branch coverage >= 0.35
dependency hygiene PASS
OWASP Dependency-Check aggregate PASS
CycloneDX SBOM present
portable package build PASS
packaged --version == 1.2.0
workspace tracked clean at end
```

Windows et Linux/WSL doivent qualifier **le même SHA exact**.

## 8. Intégration

D2 cible `develop`.

Ordre :

```text
implementation
  -> local Windows gate
  -> local Linux/WSL gate on same SHA
  -> compare exact SHA + outputs
  -> finalize VALIDATION_D2.md
  -> PR review
  -> merge to develop only if all gates PASS
  -> close #120
```

Aucun résultat GitHub Actions n'entre dans cette décision.

## 9. Exit gates

```text
D2-S1 dependency hardening       REQUIRED
D2-S2 JSON regression            REQUIRED
D2-S3 quality ratchet            REQUIRED
D2-S4 active docs reconciled     REQUIRED
Windows exact-head               REQUIRED
Linux/WSL exact-head             REQUIRED
same SHA                         REQUIRED
CI workflow delta                MUST BE NONE
post-gate executable delta       MUST BE NONE
```

Statut courant : **TERMINÉ** — SHA exact qualifié `fa54b3d6a316357b2ef79afd2243619a64a05f3b`, PR #121 MERGED, issue #120 CLOSED / completed. Voir [`../governance/ROADMAP.md`](../governance/ROADMAP.md) pour l'état d'intégration courant.

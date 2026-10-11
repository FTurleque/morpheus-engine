# Contributing to MORPHEUS

MORPHEUS is proprietary source-available software. Public visibility of this repository is intended to provide transparency and access to the source; it does not make the project open source.

## External contributions

External code contributions are **not accepted by default**.

Do not submit source-code changes, patches or pull requests unless Fabrice Turleque has explicitly authorized the contribution in writing beforehand and, where requested, the applicable contribution or intellectual-property agreement has been completed.

An unsolicited pull request or patch may be closed without review and must not be interpreted as granting either party rights beyond those that already apply under law, GitHub's binding terms or an explicit written agreement.

## Issues and discussions

Bug reports, questions and feature suggestions may be submitted through the repository's public collaboration features when enabled. Submission of an issue or discussion does not grant a license to MORPHEUS and does not authorize use, modification, redistribution or commercialization of the Software.

Do not include confidential information, proprietary third-party source code, credentials, personal data or material you do not have the right to disclose.

## Licensing requests

Requests for evaluation, internal-use, redistribution, integration, commercial use or contribution authorization require prior written permission from the copyright holder.

See [`LICENSE`](LICENSE) for the terms governing MORPHEUS.

---

# État du produit et baseline technique

Cette partie dit où en est MORPHEUS et sur quoi il est construit. Elle s’adresse à qui lit ou modifie le dépôt ; la présentation du produit est dans [`README.md`](README.md), et le suivi détaillé dans [`docs/project/governance/ROADMAP.md`](docs/project/governance/ROADMAP.md).

## État produit

**MORPHEUS 1.2.0 est la release stable publiée.** La baseline de développement courante est **1.2.1**.

```text
Stable version          1.2.0
Stable tag              v1.2.0
Release commit          3ad9ebf030b58df97482e21e272c24feae6b9d86
R3                      COMPLETE / VALIDATED / PUBLISHED
Published assets        8/8 parity PASS
Development baseline    1.2.1
D2                      COMPLETE / QUALIFIED / MERGED
D2 issue                #120 CLOSED / completed
D2 qualified exact head fa54b3d6a316357b2ef79afd2243619a64a05f3b
D2 merge commit         c12882d6e43daab600f6580f22f8eff2fbc6f4de
```

Preuve de release : [`docs/project/validation/VALIDATION_R3.md`](docs/project/validation/VALIDATION_R3.md).
Preuve historique D2 : [`docs/project/validation/VALIDATION_D2.md`](docs/project/validation/VALIDATION_D2.md), complétée par les preuves exact-head de la PR #121.

## Fondation technique

```text
Java                   21
Build                  Maven Wrapper 3.10.0
Release stable         1.2.0
Baseline développement 1.2.1
Persistent store       SQLite
SQLite JDBC            3.53.4.0
Jackson                3.2.3
MCP SDK                Java MCP SDK 2.0.1
HTTP local             JDK jdk.httpserver
Remote HTTPS           JDK HttpsServer, opt-in
Distribution           jpackage + Inno Setup Windows
```

Modules Maven :

```text
morpheus-domain
morpheus-application
morpheus-provider-sdk
morpheus-provider-testkit
morpheus-provider-reference
morpheus-provider-openspec
morpheus-provider-markdown
morpheus-provider-synthetic
morpheus-store-memory
morpheus-store-sqlite
morpheus-mcp-transport
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
morpheus-coverage-report
```

## Build développeur

Le gate Maven canonique est :

```powershell
.\mvnw.cmd clean verify
```

```bash
./mvnw clean verify
```

Le gate durable exact-head utilisé par `MORPHEUS CI` est :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

```bash
bash ./scripts/validate-m21.sh 1.2.1
```

Sur les pull requests, la couverture différentielle impose **≥ 80 % des lignes exécutables changées** et **≥ 70 % des branches changées**. Le ratchet agrégé est **≥ 90,0 % lignes / ≥ 75,7 % branches**, le ratchet par module **≥ 69,1 % lignes / ≥ 61,2 % branches**, avec **≥ 3820 tests Surefire** et **≥ 585 tests d’architecture** (source normative : `config/m21-quality-ratchets.properties`). La mesure canonique de couverture est le rapport JaCoCo agrégé `morpheus-coverage-report/target/site/jacoco-aggregate/jacoco.xml`, qui fusionne aussi l’exécution cross-module des tests d’architecture sans compter leurs classes comme code produit ; l’échelle par module somme au contraire le rapport propre à chaque module et révèle donc un module qui cesse de se tester lui-même. Les deux échelles portent des seuils distincts et ne sont jamais comparées entre elles. Dependency hygiene, SBOM CycloneDX, provenance et smoke packaging font partie du gate durable.

D2 reste une preuve historique distincte : sa qualification finale a été effectuée localement sur Windows et Linux/WSL au même SHA, sans utiliser la CI comme gate D2. Cette contrainte historique ne désactive pas les workflows actuels `MORPHEUS CI`, `MORPHEUS Security` et `MORPHEUS CodeQL`.

## Sécurité et supply chain

```text
OWASP Dependency-Check 13.0.0
SCA fail threshold      CVSS >= 7.0
Dependency hygiene      failOnWarning=true
SBOM                    CycloneDX JSON + XML
Code scanning           CodeQL security-extended
Dependency updates      Dependabot Maven + GitHub Actions
```

La qualification end-to-end du workflow de release attestée sera faite lors de la prochaine vraie release `v1.2.1` ou supérieure ; elle n’est pas simulée sur un tag artificiel. Tant que Dependency-Check 13.0.0 reste affecté par le problème NVD suivi dans #287, le workflow Security conserve son comportement fail-closed et le secret GitHub Actions `NVD_API_KEY` doit être configuré côté dépôt pour rafraîchir le cache trusted.

## Aller plus loin

- [Guide développeur](docs/developer/README.md) — modules, gates, packaging ;
- [Build, tests et validation](docs/developer/BUILD_AND_TEST.md) ;
- [Dossier d’architecture](docs/architecture/README.md) et [registre des ADR](docs/architecture/adr/README.md).

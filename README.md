# MORPHEUS

**MORPHEUS** est un moteur local-first d’intelligence des spécifications et de l’intention (*Specification & Intent Intelligence Engine*).

> Qu’est-ce qui doit être construit, pourquoi, selon quelles règles, et comment prouver que le résultat correspond à l’intention ?

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

Preuve de release : [`docs/validation/VALIDATION_R3.md`](docs/validation/VALIDATION_R3.md).
Preuve historique D2 : [`docs/validation/VALIDATION_D2.md`](docs/validation/VALIDATION_D2.md), complétée par les preuves exact-head de la PR #121.

## Capacités

MORPHEUS fournit notamment :

- ingestion et normalisation de spécifications ;
- providers OpenSpec et Structured Markdown ;
- Provider SDK et plugins externes explicitement découvrables/activables ;
- composition multi-provider déterministe avec provenance et conflits conservés ;
- snapshots versionnés et séparation `CURRENT / PROPOSED / HISTORICAL` ;
- recherche, traçabilité, qualité et analyse de changes ;
- critères d’acceptation, contraintes et lifecycle contrôlé ;
- portfolios multi-projets et références inter-projets ;
- Query DSL provider-neutral, saved views et exports JSON/CSV/Markdown ;
- Policy Packs versionnés, activations, overrides, dry-run et audit ;
- reasoning assisté fondé sur des preuves avec faits/inférences/suggestions séparés ;
- CLI locale scriptable ;
- serveur MCP STDIO natif ;
- API HTTP locale `/api/v1` ;
- serveur d’équipe HTTPS opt-in avec Bearer auth, RBAC et concurrence bornée ;
- backup SQLite cohérent et restore offline ;
- intégrations optionnelles MINOS et NEXUS ;
- câblage MCP opt-in pour Copilot, Claude et Codex ;
- setup Windows per-user et distributions Windows/Linux avec runtime Java embarqué.

Docker n’est pas requis pour le MCP natif ni pour l’exécution locale de MORPHEUS.

## Installation

Documentation active : [`docs/user/INSTALLATION.md`](docs/user/INSTALLATION.md).

Windows :

```powershell
MORPHEUS-1.2.0-windows-x64-setup.exe
morpheus --version
```

Linux :

```bash
tar -xzf morpheus-1.2.0-linux-x64.tar.gz
./morpheus/bin/morpheus --version
```

Les distributions embarquent leur runtime Java ; aucun JDK utilisateur n’est requis.

## Premier projet

```bash
morpheus projects add --workspace /path/to/project
morpheus projects list
morpheus sync --project <projectId>
morpheus requirements find --project <projectId> --query "session"
```

## MCP

```text
morpheus mcp --stdio
```

Clients documentés :

```text
GitHub Copilot — JetBrains / IntelliJ
GitHub Copilot CLI
Claude Code
Claude Desktop
OpenAI Codex
```

Guide : [`docs/user/MCP_CLIENTS.md`](docs/user/MCP_CLIENTS.md).

## Surfaces publiques

Les contrats de convergence CLI/MCP/HTTP sont suivis dans [`contracts/public-surfaces.tsv`](contracts/public-surfaces.tsv).

```text
CLI   humain / scripts / administration locale
MCP   IDE / agents / orchestrateurs via STDIO
HTTP  API locale loopback et façade remote HTTPS opt-in
```

Les omissions de surface sont explicites : par exemple le restore reste offline-only et le provisioning d’identité remote reste local-only.

## Invariants structurants

```text
DomainIdentity != EntityVersionId != SourceLocator != ExternalReference
SpecificationVersion != KnowledgeSnapshot
PROPOSED never leaks into CURRENT
APPLY != PROMOTE != ACTIVATE
Scenario != AcceptanceCriterion
AcceptanceCriterion != Test
Evidence != assertion
UNKNOWN != FAILED
UNKNOWN != BLOCKED
READ_CHANGES != WRITE_CHANGE
ALLOWED != applied
stale revision != overwrite
precedence != provenance erasure
conflict != silent last-write-wins
provider plugin != domain dependency
cross-project identity != source path
portfolio membership != source ownership
DSL != SQL passthrough
saved view != materialized truth
export != mutation
constraint text != executable policy
policy recommendation != applied mutation
dry-run != mutation
local mode remains first-class
remote mode is opt-in
authentication != authorization
READ != WRITE != ADMIN
token plaintext != persisted credential
backup != live restore
facts != inference
inference != suggestion
reasoning execution != mutation
Docker required for native MCP = false
```

## Fondation technique

```text
Java                   21
Build                  Maven Wrapper 3.9.16
Release stable         1.2.0
Baseline développement 1.2.1
Persistent store       SQLite
SQLite JDBC            3.53.2.0
Jackson                3.2.2
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

Sur les pull requests, la couverture différentielle impose **≥ 80 % des lignes exécutables changées** et **≥ 70 % des branches changées**. Le ratchet global est **≥ 51,0 % lignes / ≥ 43,5 % branches**, avec **≥ 860 tests Surefire** et **≥ 265 tests d’architecture**. Dependency hygiene, SBOM CycloneDX, provenance et smoke packaging font partie du gate durable.

D2 reste une preuve historique distincte : sa qualification finale a été effectuée localement sur Windows et Linux/WSL au même SHA, sans utiliser la CI comme gate D2. Cette contrainte historique ne désactive pas les workflows actuels `MORPHEUS CI`, `MORPHEUS Security` et `MORPHEUS CodeQL`.

## Sécurité et supply chain

```text
OWASP Dependency-Check 12.2.2
SCA fail threshold      CVSS >= 7.0
Dependency hygiene      failOnWarning=true
SBOM                    CycloneDX JSON + XML
Code scanning           CodeQL security-extended
Dependency updates      Dependabot Maven + GitHub Actions
```

La qualification end-to-end du workflow de release attestée sera faite lors de la prochaine vraie release `v1.2.1` ou supérieure ; elle n’est pas simulée sur un tag artificiel.

## Documentation

Point d’entrée : [`docs/README.md`](docs/README.md).

- roadmap globale : [`docs/governance/ROADMAP.md`](docs/governance/ROADMAP.md) ;
- état documentaire : [`docs/governance/DOCUMENTATION_STATUS.md`](docs/governance/DOCUMENTATION_STATUS.md) ;
- release 1.2.0 : [`docs/release/RELEASE_NOTES_1.2.0.md`](docs/release/RELEASE_NOTES_1.2.0.md) ;
- installation : [`docs/user/INSTALLATION.md`](docs/user/INSTALLATION.md) ;
- clients MCP : [`docs/user/MCP_CLIENTS.md`](docs/user/MCP_CLIENTS.md) ;
- build/test : [`docs/developer/BUILD_AND_TEST.md`](docs/developer/BUILD_AND_TEST.md) ;
- preuve historique D2 : [`docs/validation/VALIDATION_D2.md`](docs/validation/VALIDATION_D2.md).

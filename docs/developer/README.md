# Guide développeur MORPHEUS

Cette documentation décrit la baseline stable **MORPHEUS 1.2.0** et le chantier actif **D2 — Post-R3 Repository Hardening**.

```text
stable tag             v1.2.0
release commit         3ad9ebf030b58df97482e21e272c24feae6b9d86
R3                     COMPLETE / PUBLISHED
D2 issue               #120
D2 branch              d2-post-r3-hardening
```

## Prérequis

```text
Java   >= 21
Maven  via Maven Wrapper 3.9.16
Git
Windows PowerShell pour le gate Windows
WSL/Linux pour le gate Linux
```

Le reactor compile avec `release=21`.

## Import IntelliJ IDEA

Charger le `pom.xml` racine comme projet Maven. Ne pas créer les sous-modules manuellement : ils sont définis par le reactor.

## Modules

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
morpheus-integration-minos
morpheus-integration-nexus
morpheus-mcp
morpheus-api
morpheus-cli
morpheus-architecture-tests
```

Architecture générale :

```text
adapters / sdk -> application -> domain
```

Le domaine et l’application ne dépendent ni des transports ni des clients MCP.

## Baseline technique D2

```text
product                    1.2.0
Java                       21
Jackson                    3.1.5 LTS
sqlite-jdbc                3.53.2.0
MCP SDK                    2.0.0
OWASP Dependency-Check     12.2.2, profil local d2-security
JaCoCo line floor          40%
JaCoCo branch floor        35%
dependency analyze         failOnWarning=true
```

Le SCA n’est pas attaché au build développeur ordinaire. Il est lancé explicitement par le gate D2 afin de garder `clean verify` reproductible hors opération réseau de sécurité.

## Gate Maven canonique

Windows :

```powershell
.\mvnw.cmd clean verify
```

Linux :

```bash
./mvnw clean verify
```

`clean test` n’est pas la qualification finale : les tests d’architecture consomment les JARs et rapports produits jusqu’à `verify`.

## Gate D2 local-only

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.0 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.0
```

D2 exige Windows + Linux/WSL sur le même SHA exact.

**Aucune CI n’est utilisée pour D2.** Les validateurs refusent tout delta `.github/workflows/**` et aucun statut GitHub Actions ne constitue une preuve.

## Sécurité JSON D2

Les routes HTTP conservent :

```text
MAX_REQUEST_BODY_BYTES = 65536
FAIL_ON_UNKNOWN_PROPERTIES
FAIL_ON_TRAILING_TOKENS
```

Jackson 3.1.5 LTS remplace la branche 3.0.x. Un test de régression vérifie qu’un JSON excessivement imbriqué est rejeté avant épuisement de la pile JVM. Aucun default typing n’est activé.

## SCA local

Le profil Maven est :

```text
d2-security
```

Commande utilisée par le gate :

```text
org.owasp:dependency-check-maven:12.2.2:aggregate
```

Politique : CVSS >= 7.0 fait échouer la qualification ; test scope exclu ; erreur de scan bloquante ; rapports sous `target/d2-security`.

## Packaging

Le gate D2 reconstruit une distribution portable sur chaque plateforme et vérifie le `product-info` packagé en `1.2.0`.

Les distributions continuent d’embarquer leur runtime Java, le MCP STDIO, l’API, les providers et les adapters MINOS/NEXUS optionnels, mais jamais les implémentations MINOS/NEXUS/JARVIS.

## Documentation d’architecture

- [Architecture générale](ARCHITECTURE.md)
- [Provider SDK](PROVIDER_SDK.md)
- [Portfolio Intelligence](PORTFOLIO_INTELLIGENCE.md)
- [Query Platform](QUERY_PLATFORM.md)
- [Policy Platform](POLICY_PLATFORM.md)
- [Remote Server Platform](REMOTE_SERVER_PLATFORM.md)
- [Assisted Reasoning](ASSISTED_REASONING.md)
- [API](API.md)
- [MCP](MCP.md)
- [Build et tests](BUILD_AND_TEST.md)
- [Plan D2](../roadmap/D2_EXECUTION.md)
- [Validation D2](../validation/VALIDATION_D2.md)

# Guide développeur MORPHEUS

Cette documentation distingue deux états :

```text
release publiée         MORPHEUS 1.2.0 / tag v1.2.0
release commit           3ad9ebf030b58df97482e21e272c24feae6b9d86
baseline active          MORPHEUS 1.2.1 corrective
branche d'intégration    develop
```

`1.2.1` est la version du code actif post-audit ; elle n'est pas considérée publiée tant qu'un tag/release `v1.2.1` n'a pas été créé et qualifié. Le tag `v1.2.0` reste immuable.

## Prérequis

```text
Java   >= 21
Maven  via Maven Wrapper 3.9.16 + SHA-256 vérifié
Git
Windows PowerShell pour le gate Windows
Linux/WSL pour le gate Linux
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

## Baseline technique active

```text
product                    1.2.1
Java                       21
Jackson                    3.1.5 LTS
sqlite-jdbc                3.53.2.0
MCP SDK                    2.0.0
OWASP Dependency-Check     12.2.2
JaCoCo ratchet             47% lignes / 40% branches
Surefire floor             698
Architecture floor         250
dependency analyze         failOnWarning=true
```

Le SCA réseau n’est pas attaché au `clean verify` développeur ordinaire. Il est exécuté par le workflow `MORPHEUS Security` sur la frontière `main`, chaque semaine et manuellement. Le profil local `d2-security` reste disponible pour une qualification spécialisée.

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

## Gate durable M21

Windows :

```powershell
.\scripts\validate.cmd m21 -Version 1.2.1
```

Linux :

```bash
bash ./scripts/validate-m21.sh 1.2.1
```

M21 exige le même SHA exact sur Windows et Ubuntu/Linux, vérifie les ratchets de tests/couverture, le SBOM, la provenance, le portable et la convergence de version CLI/API/update.

La CI canonique exécute M21 sur les pull requests ainsi que sur les pushes `main` et `develop`.

## Gate D2 spécialisé

Windows :

```powershell
.\scripts\validate.cmd d2 -Version 1.2.1 -BaseRef origin/develop
```

Linux / WSL :

```bash
MORPHEUS_D2_BASE_REF=origin/develop bash ./scripts/validate-d2.sh 1.2.1
```

D2 conserve sa règle historique de périmètre local : il refuse une PR qui modifie `.github/workflows/**`. Il ne doit donc pas être utilisé comme preuve principale d'une PR dont l'objet est précisément de faire évoluer la CI ; M21 est le gate durable de ce cas.

## Sécurité JSON

Les routes HTTP conservent :

```text
MAX_REQUEST_BODY_BYTES = 65536
FAIL_ON_UNKNOWN_PROPERTIES
FAIL_ON_TRAILING_TOKENS
```

Jackson 3.1.5 LTS est utilisé sans default typing. Les tests de régression couvrent notamment la profondeur JSON, les tailles de requête et les frontières workspace/provider.

## SCA

Profil local :

```text
d2-security
```

Commande :

```text
org.owasp:dependency-check-maven:12.2.2:aggregate
```

Politique : CVSS >= 7.0 fait échouer la qualification ; test scope exclu ; erreur de scan bloquante ; rapports sous `target/d2-security`.

Le workflow `.github/workflows/security.yml` exécute le même contrôle sur :

```text
PR -> main
push -> main
lundi hebdomadaire
workflow_dispatch
```

`.github/dependabot.yml` ouvre en parallèle les mises à jour Maven et GitHub Actions vers `develop`. Les alertes de vulnérabilité Dependabot restent un réglage administrateur suivi dans #154.

## Packaging

Les builders actifs produisent `1.2.1` par défaut :

```powershell
.\distribution\build-portable.ps1 -Version 1.2.1
.\distribution\build-installer.ps1 -Version 1.2.1
```

```bash
bash ./distribution/build-portable.sh 1.2.1
```

Les builders de release refusent de publier si le workspace n'est pas propre ou si le tag attendu `v1.2.1` ne pointe pas exactement sur HEAD.

Les distributions embarquent leur runtime Java, le MCP STDIO, l’API, les providers et les adapters MINOS/NEXUS optionnels, mais jamais les implémentations MINOS/NEXUS/JARVIS.

## Gouvernance mono-développeur

Tant que MORPHEUS est maintenu par un seul développeur :

```text
develop  non protégée volontairement + M21 sur push
main     protection attendue + PR + checks + 0 approbation obligatoire
```

Le ruleset `main`, le Quality Gate Sonar new-code et les alertes Dependabot sont suivis dans #154.

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
- [Version produit](PRODUCT_VERSION.md)
- [Build et tests](BUILD_AND_TEST.md)
- [Registre des risques](../architecture/risks/register.md)
- [Validation R3 / release 1.2.0 historique](../validation/VALIDATION_R3.md)
- [Validation D2 historique](../validation/VALIDATION_D2.md)

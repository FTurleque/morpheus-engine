# Guide développeur MORPHEUS

Cette documentation décrit la baseline stable **MORPHEUS 1.1.0** et le chantier actif **M28 — MCP Client Integration & Installer Wiring**.

```text
stable tag             v1.1.0
release commit         31506029ded1101f0571edeb0d79c59bbf3f68c6
post-release baseline  8dfbe807cb1a57a7750d9b9ac69def0da6c79ff3
M28 branch             m28-mcp-client-integration
M28 issue              #115
```

## 1. Prérequis

```text
Java   >= 21
Maven  via Maven Wrapper
Git
Windows PowerShell pour le gate d’intégration clients
WSL/Linux pour le gate portable Linux
```

```powershell
.\mvnw.cmd --version
```

```bash
./mvnw --version
```

Le reactor compile avec `release=21`.

## 2. Import IntelliJ IDEA

Charger le `pom.xml` racine comme projet Maven. Ne pas créer les sous-modules manuellement : ils sont définis par le reactor.

## 3. Vue du dépôt

```text
morpheus-engine/
├── morpheus-domain/
├── morpheus-application/
├── morpheus-provider-sdk/
├── morpheus-provider-testkit/
├── morpheus-provider-reference/
├── morpheus-provider-openspec/
├── morpheus-provider-markdown/
├── morpheus-provider-synthetic/
├── morpheus-store-memory/
├── morpheus-store-sqlite/
├── morpheus-integration-minos/
├── morpheus-integration-nexus/
├── morpheus-mcp/
├── morpheus-api/
├── morpheus-cli/
├── morpheus-architecture-tests/
├── integration/                 # M28 client MCP wiring
├── distribution/
├── scripts/
├── docs/
└── pom.xml
```

## 4. Architecture

```text
adapters / sdk -> application -> domain
```

Le domaine et l’application ne dépendent ni des transports, ni des clients MCP, ni des formats d’installation.

| Module | Responsabilité |
|---|---|
| `morpheus-domain` | modèle métier et invariants purs |
| `morpheus-application` | use cases, ports, lifecycle, composition, portfolios, queries, policies, reasoning |
| `morpheus-provider-sdk` | SPI plugins providers |
| `morpheus-provider-testkit` | contrats pour auteurs de plugins |
| `morpheus-store-memory` | stores mémoire |
| `morpheus-store-sqlite` | persistance V001→V015 et maintenance |
| `morpheus-integration-minos` | client MINOS via MCP STDIO |
| `morpheus-integration-nexus` | client NEXUS via MCP STDIO |
| `morpheus-mcp` | serveur MCP natif |
| `morpheus-api` | API locale et façade remote HTTPS |
| `morpheus-cli` | composition root et launcher |
| `morpheus-architecture-tests` | contrats cross-module |
| `integration` | gestionnaire M28 des configurations clientes, hors logique métier |

## 5. Principales plateformes

Documentation détaillée :

- [Architecture générale](ARCHITECTURE.md)
- [Provider SDK](PROVIDER_SDK.md)
- [Portfolio Intelligence](PORTFOLIO_INTELLIGENCE.md)
- [Query Platform](QUERY_PLATFORM.md)
- [Policy Platform](POLICY_PLATFORM.md)
- [Remote Server Platform](REMOTE_SERVER_PLATFORM.md)
- [Assisted Reasoning](ASSISTED_REASONING.md)
- [API](API.md)
- [MCP](MCP.md)

## 6. M28 — couche d’intégration MCP

M28 ne modifie pas le protocole MCP ni les handlers métier. Il ajoute une couche de configuration et de packaging :

```text
integration/configure-mcp-clients.ps1
integration/configure-mcp-clients-setup.ps1
scripts/verify-m28-mcp-client-integration.ps1
distribution/windows/MORPHEUS.iss
```

Clients :

```text
Copilot JetBrains
Copilot CLI
Claude Code
Claude Desktop
Codex
```

Invariants :

```text
opt-in only
backup before JSON write
foreign entry never overwritten
preexisting compatible entry never removed
managed modified entry preserved
uninstall driven by ownership state
bounded native command timeout
MCP remains native STDIO
Docker not required
```

Architecture détaillée : [Serveur MCP et intégration clients](MCP.md).

## 7. Gestionnaire PowerShell

Le gestionnaire doit rester compatible avec Windows PowerShell pour l’exécution par l’installateur. Lorsqu’un client est exposé sous forme `.ps1`, il est lancé dans `pwsh` non interactif.

Le registre persistant est séparé du programme :

```text
%LOCALAPPDATA%\MORPHEUS\mcp-client-integrations.json
```

Ne jamais déduire la propriété d’une entrée depuis son seul nom. La propriété est enregistrée après observation ou création réussie.

## 8. Tests M28

Le test PowerShell utilise des profils temporaires et de faux clients CLI. Il couvre :

```text
JSON merge
preservation of unrelated content
five client registrations
argument ordering
MORPHEUS_DATA_DIR / MORPHEUS_CONFIG_DIR
UTF-8 without BOM
backups
idempotency
foreign entry preservation
modified entry preservation
state-driven uninstall
invalid JSON protection
```

Contrat Java :

```text
morpheus-architecture-tests/.../m28/McpClientIntegrationArchitectureTest.java
```

## 9. Gates exact-head

Windows :

```powershell
.\validate-m28.cmd -Version 1.1.0 -BaseRef origin/develop
```

Linux/WSL :

```bash
MORPHEUS_M28_BASE_REF=origin/develop bash ./scripts/validate-m28.sh 1.1.0
```

Les deux gates doivent qualifier le même SHA. Le gate Linux ne prétend pas valider les mutations de profils Windows : il valide le reactor, les contrats statiques et le packaging Linux.

## 10. Packaging

Windows portable et setup embarquent :

```text
morpheus.exe
runtime Java
integration/configure-mcp-clients.ps1
integration/configure-mcp-clients-setup.ps1
integration/README.md
```

Linux embarque le launcher, le runtime et la documentation d’intégration. La configuration automatique reste Windows-only à ce jalon.

## 11. Politique de version

M28 est développé sur la baseline `1.1.0`. Le bump reactor vers `1.2.0`, les builds exact-tag et la publication appartiennent à la consolidation de release suivant l’intégration et la qualification du jalon.

Ne jamais déplacer le tag `v1.1.0`.

## 12. Politique CI — juillet 2026

```text
GitHub Actions is not a gate
no workflow rerun
no workflow dispatch
no opportunistic workflow modification
local Windows + Linux/WSL exact-head logs are authoritative
```

## 13. Documentation active

- [Plan M28](../roadmap/M28_EXECUTION.md)
- [Validation M28](../validation/VALIDATION_M28.md)
- [Guide utilisateurs MCP](../user/MCP_CLIENTS.md)
- [Serveur MCP](MCP.md)
- [Roadmap globale](../governance/ROADMAP.md)

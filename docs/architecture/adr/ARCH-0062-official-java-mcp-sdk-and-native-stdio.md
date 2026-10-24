---
id: ARCH-0062
title: "Java MCP SDK officiel et transport STDIO natif"
domain: architecture
rules: false
---

# Java MCP SDK officiel et transport STDIO natif

- Statut : **Acceptée**
- Date : 2026-07-24

## Context

MORPHEUS expose ses capacités à des clients MCP depuis sa distribution native portable, qui embarque son runtime Java.
Cette exposition ne doit introduire ni serveur web, ni Spring, ni Docker, ni seconde pile métier.

Le Java MCP SDK officiel fournit la négociation du protocole, le catalogue d'outils, la validation JSON Schema des
arguments et une API de serveur synchrone, sans framework web :

- le protocole et sa négociation sont maintenus par l'implémentation de référence Java ;
- les arguments d'un outil sont validés contre son schéma avant l'appel du handler ;
- le transport STDIO ne demande aucun framework web ;
- l'API synchrone convient à des requêtes SQLite locales ;
- un transport HTTP resterait possible plus tard sans contaminer le domaine.

## Decision

Le serveur MCP de MORPHEUS repose sur le Java MCP SDK officiel (`io.modelcontextprotocol.sdk`), importé par son BOM
`mcp-bom` dans le `<dependencyManagement>` du POM racine. Les modules utilisés sont `mcp-core` et
`mcp-json-jackson3` ; la version est celle de la propriété `mcp-sdk.version` du `pom.xml` racine.

Le serveur est construit par `McpServer.sync(...)` dans `MorpheusMcpServer` (`morpheus-mcp`), sur un transport STDIO :
`BoundedStdioServerTransportProvider` (`morpheus-mcp-transport`), implémentation MORPHEUS du
`McpServerTransportProvider` du SDK, qui borne les trames et la durée d'un handler selon ADR-0106.

Le serveur n'annonce que la capacité `tools`, et active `validateToolInputs(true)` : un argument hors schéma est
refusé par le SDK avant tout handler.

Contraintes :

```text
no Spring
no servlet container
no Streamable HTTP MCP transport
no SSE MCP transport
no Docker requirement
validateToolInputs = true
stdout reserved to MCP JSON-RPC
runtime diagnostics to stderr only
```

## Do's and Don'ts

### Do

- Construire le serveur MCP avec le SDK officiel, en version gérée par la propriété `mcp-sdk.version` du POM racine.
- Servir MCP par STDIO, à travers `BoundedStdioServerTransportProvider`.
- Garder `validateToolInputs(true)` sur le serveur.
- Réserver la sortie standard aux trames JSON-RPC et écrire tout diagnostic sur la sortie d'erreur.

### Don't

- Ne pas introduire Spring, un conteneur de servlets ou Docker pour servir MCP.
- Ne pas exposer MCP par Streamable HTTP ni par SSE sans nouvelle décision.
- Ne pas écrire de journal, de trace ou de message humain sur la sortie standard du processus MCP.
- Ne pas déclarer de version du SDK dans un POM de module.

## Consequences

### Positives

- Le protocole, sa négociation et la validation des arguments viennent de l'implémentation de référence.
- Le serveur MCP ne dépend d'aucun framework web ; le domaine ignore le transport.

### Négatives

- Le SDK impose sa ligne Reactor : `reactor-bom` reste sur la ligne compatible avec le SDK (commentaire du POM
  racine, au-dessus de cette dépendance).
- Le transport STDIO du SDK ne suffit pas : MORPHEUS maintient son propre fournisseur de transport borné.

### Risques

- Une écriture accidentelle sur la sortie standard corrompt le flux JSON-RPC du client.

## Compliance and Enforcement

- `morpheus-mcp/src/test/java/com/morpheus/mcp/McpFailureContractTest.java` :
  `theServerArmsSchemaValidationForEveryToolItServes` épingle la validation des arguments par schéma.
- `morpheus-cli/src/test/java/com/morpheus/cli/MorpheusMcpStdioIntegrationTest.java` : négociation, `tools/list`,
  `tools/call` et refus d'un argument invalide sur un vrai processus STDIO.
- `morpheus-cli/src/test/java/com/morpheus/cli/OperationalSlf4jServiceProviderTest.java` : dans un processus réel, le
  journal opérationnel écrit sur la sortie d'erreur et laisse la sortie standard vide.
- `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/m28/McpClientIntegrationArchitectureTest.java` :
  l'intégration des clients MCP ne mentionne pas `docker`.
- Revue : l'absence de Spring, de conteneur de servlets et de transport MCP HTTP ou SSE.

## References

- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — baseline Java 21.
- [ADR-0133](GEN-0133-toolchain-java-21-et-maven-wrapper.md) — fondation de build Maven.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution native, sans image Docker.
- [ADR-0059](GEN-0059-stable-local-cli-contract.md) — contrat CLI local.
- [ADR-0134](GEN-0134-distribution-native-autonome.md) — distribution portable autonome.
- [ADR-0063](ARCH-0063-read-only-mcp-tool-contract.md) — catalogue d'outils MCP.
- [ADR-0102](BE-0102-mcp-failure-contract-is-one-rule.md) — contrat d'échec MCP.
- [ADR-0106](BE-0106-inbound-bound-is-defensive-outbound-bound-is-a-defect.md) — bornes du transport STDIO.
- [ADR-0128](ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md) — pairs MINOS et NEXUS par MCP STDIO.
- [Validation M10](../../project/validation/VALIDATION_M10.md)

# Proposition : faire vérifier à chaque test ce que son nom promet (AUD-TST-35)

## Why

Six assertions `assertTrue(x != null)` portent sur une valeur qui ne peut pas être nulle, et un test n'a aucune
assertion. Revérifié le 17/11/2026 sur `1a539c22` :

- `morpheus-mcp/src/test/java/com/morpheus/mcp/MorpheusPortfolioMcpToolsTest.java:46`
  (`serverCatalogContainsPortfolioTools`), `MorpheusPolicyMcpManagementToolsTest.java:38`,
  `MorpheusPolicyMcpToolsTest.java:76`, `MorpheusReasoningMcpToolsTest.java:65`, `MorpheusQueryMcpToolsTest.java:232` :
  `assertTrue(server != null)` après `MorpheusMcpServer.build(...)`, fabrique qui rend un serveur ou lève ;
- `morpheus-api/src/test/java/com/morpheus/api/local/LoopbackRequestProtectedHttpServerTest.java:44` :
  `assertTrue(direct.getHandler() != null)` ;
- `morpheus-mcp-transport/src/test/java/com/morpheus/transport/mcp/BoundedStdioClientTransportTest.java:399-406`
  (`acceptsCustomStderrHandler`) : aucune assertion.

Ces tests ne prouvent que l'absence d'exception, et le test portfolio promet par son nom une propriété (le catalogue
contient les outils portfolio) que seul son voisin vérifie. Ils comptent pourtant sous `testsMinimum`.

Constat `AUD-TST-35` (Faible), `docs/project/audits/2026-11-17/findings.md`.

## What Changes

- Les cinq tests MCP disent ce qu'ils prouvent : construction du serveur sans collision de nom ni de schéma, par
  `assertDoesNotThrow` ; le test portfolio est renommé pour ce qu'il prouve, la présence des outils restant vérifiée
  par le test voisin.
- `LoopbackRequestProtectedHttpServerTest` vérifie une propriété du gestionnaire du contexte (design.md, D2) au lieu
  de sa non-nullité.
- `acceptsCustomStderrHandler` vérifie que le gestionnaire installé reçoit une ligne de la sortie d'erreur du pair.
- Aucun test n'est retiré : les comptes de présence ne baissent pas.

## Capabilities

### New Capabilities

### Modified Capabilities

Aucune capacité : seuls des tests changent, aucun comportement spécifié ni aucun gate (`skip_specs: true`).

## Périmètre et exclusions

**Inclus :** les sept tests cités, dans `morpheus-mcp`, `morpheus-api` et `morpheus-mcp-transport`.

**Exclus :** une règle de build qui refuserait `assertTrue(… != null)` (design.md, D3) ; le code de production,
inchangé.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Six assertions tautologiques, un test sans assertion | existant vérifié | lignes citées ci-dessus |
| Le contenu du catalogue portfolio est vérifié par le test voisin | existant vérifié | `MorpheusPortfolioMcpToolsTest.java:30-36` |
| Pas de règle de build nouvelle | décision | design.md, D3 |

## Impact

Tests de `morpheus-mcp`, `morpheus-api`, `morpheus-mcp-transport`. Aucune surface publique, aucun test
d'architecture, aucun ratchet. Aucune dépendance envers un autre constat.

## Questions ouvertes

Aucune.

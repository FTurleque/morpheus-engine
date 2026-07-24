# ADR-0062 — Java MCP SDK officiel et transport STDIO natif

- Statut : **Proposée — M10 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0016, ADR-0017, ADR-0027, ADR-0059, ADR-0061
- Portée : M10 — serveur MCP local

## Contexte

M9 fournit une CLI native portable avec runtime Java embarqué. M10 doit exposer les capacités MORPHEUS à des clients MCP sans introduire un serveur web, Spring, Docker ou une seconde stack métier.

Le Java MCP SDK officiel fournit un transport STDIO natif, la négociation du protocole, le catalogue de tools et la validation JSON Schema.

## Décision

Utiliser le BOM et le module officiel :

```text
io.modelcontextprotocol.sdk:mcp-bom:2.0.0
io.modelcontextprotocol.sdk:mcp:2.0.0
```

Transport M10 :

```text
StdioServerTransportProvider
McpServer.sync(...)
```

Le serveur active uniquement la capability `tools` en M10.

## Contraintes

```text
no Spring
no servlet container
no Streamable HTTP in M10
no SSE in M10
no Docker requirement
validateToolInputs = true
stdout reserved to MCP JSON-RPC
runtime diagnostics to stderr only
```

## Pourquoi le SDK officiel

- protocole et négociation maintenus par l'implémentation de référence Java ;
- validation JSON Schema avant handler ;
- transport STDIO disponible sans framework web ;
- API sync adaptée à des queries SQLite locales ;
- possibilité d'étendre plus tard vers HTTP sans contaminer le domaine.

## Critères d'acceptation

ADR acceptée lorsque le gate prouve :

1. dépendance SDK résolue avec Java 21 ;
2. serveur STDIO construit ;
3. `initialize` réussi ;
4. `tools/list` réussi ;
5. `tools/call` réussi ;
6. input invalide rejeté par schema ;
7. aucun octet de log applicatif sur stdout hors protocole.

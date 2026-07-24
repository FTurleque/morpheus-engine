# ADR-0073 — Intégration NEXUS par MCP STDIO inter-processus

- Statut : **Proposée — M13 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0007, ADR-0016, ADR-0062, ADR-0075
- Portée : M13 — transport MORPHEUS / NEXUS

## Contexte

NEXUS possède déjà un serveur MCP STDIO Java 21 validé qui expose son moteur de contexte via `build_context` et `explain_context`. Une dépendance binaire `com.nexus.*` créerait un couplage de build et de release inutile entre les deux moteurs.

## Décision proposée

M13 utilise un process NEXUS externe :

```text
MORPHEUS
 -> Java MCP client 2.0.0
 -> STDIO
 -> java [-Dnexus.home=...] -jar <nexus-mcp-runner.jar>
```

Tools requis :

```text
list_projects
build_context
explain_context
```

MORPHEUS n'importe aucune classe `com.nexus.*`.

## Disponibilité

```text
pas de configuration -> DISABLED
process absent        -> UNAVAILABLE
tools incompatibles   -> UNAVAILABLE
MORPHEUS              -> reste utilisable
```

Le process NEXUS n'est lancé qu'à la demande d'un status live ou d'une construction de contexte.

## Critères d'acceptation

1. aucune dépendance compile-time `com.nexus.*` ;
2. vrai échange MCP STDIO dans un test d'intégration ;
3. vérification des trois tools requis ;
4. timeout borné ;
5. fermeture propre du client/process ;
6. indisponibilité non fatale ;
7. MORPHEUS démarre sans NEXUS configuré ;
8. gate Maven complet vert.
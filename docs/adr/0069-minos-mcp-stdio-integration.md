# ADR-0069 — Intégration MINOS par MCP STDIO inter-processus

- Statut : **Acceptée — M12**
- Date : 24 juillet 2026
- Dépend de : ADR-0007, ADR-0016, ADR-0026, ADR-0041, ADR-0062
- Portée : M12 — transport cross-engine MORPHEUS / MINOS

## Contexte

MORPHEUS cible Java 21. MINOS expose depuis son M10 un serveur MCP STDIO validé et son produit courant cible Java 24. Une dépendance binaire directe à `com.minos.*` couplerait les deux dépôts, leurs baselines Java et leurs cycles de livraison.

## Décision

L'intégration M12 utilise un process MINOS externe :

```text
MORPHEUS
  -> MCP Java client 2.0.0
  -> STDIO
  -> java -cp <minos-all.jar> com.minos.mcp.MinosMcpServer
```

MORPHEUS n'importe aucune classe `com.minos.*`.

Le resolver M12 exige uniquement les tools MINOS :

```text
minos_index_status
minos_find_symbols
```

## Disponibilité

MINOS est optionnel :

```text
pas de configuration -> pas de resolver MINOS
process absent        -> UNAVAILABLE
tools incompatibles   -> UNAVAILABLE
MORPHEUS              -> reste utilisable
```

Aucun process MINOS n'est démarré au bootstrap MORPHEUS tant qu'une résolution n'est pas demandée.

## Critères d'acceptation

1. aucune dépendance compile-time `com.minos.*` ;
2. vrai échange MCP STDIO dans un test d'intégration ;
3. vérification des tools requis ;
4. timeout borné ;
5. fermeture propre du client/process ;
6. indisponibilité non fatale ;
7. MORPHEUS démarre et passe ses smokes sans MINOS configuré ;
8. gate Maven complet vert.

## Preuve M12

```text
MinosMcpTransportIntegrationTest  1/1 PASS
MINOS Integration                 8/8 PASS
Architecture                    153/153 PASS
Maven total                     331/331 PASS
Packaging MINOS optional          PASS
```

Voir `docs/VALIDATION_M12.md`.

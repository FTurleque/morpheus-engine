# ADR-0064 — Routage MCP dans le launcher natif M9

- Statut : **Proposée — M10 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0061, ADR-0062
- Portée : M10 — lancement et distribution

## Contexte

M9 produit un launcher portable `morpheus`/`morpheus.exe` contenant son runtime Java. Créer un second mécanisme d'installation pour MCP compliquerait inutilement la distribution locale.

## Décision

Étendre le launcher officiel avec :

```text
morpheus mcp --stdio
```

Les options globales M9 de localisation des données restent applicables :

```text
--data-dir
--config-dir
--db
MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Le serveur MCP ouvre la même base SQLite que la CLI et reste read-only au niveau des tools exposés.

## Discipline des flux

En mode MCP :

```text
stdout = MCP JSON-RPC uniquement
stderr = diagnostics runtime uniquement
```

Aucun help, banner, log ou message de démarrage ne doit polluer stdout.

## Packaging

Le module MCP devient une dépendance du module CLI afin que le shaded JAR M9 et les app-images Windows/Linux embarquent le serveur et le SDK sans nouveau runtime externe.

## Critères d'acceptation

1. `morpheus mcp --stdio` reconnu ;
2. les autres commandes CLI M9 restent inchangées ;
3. `--db` et variables MORPHEUS_* ciblent la même SQLite ;
4. l'uber-JAR contient le serveur MCP ;
5. app-image Windows/Linux reste autonome ;
6. stdout reste protocol-clean.

# Guide utilisateur MORPHEUS

MORPHEUS s’utilise localement de trois façons complémentaires :

```text
CLI       usage humain, scripts et automatisation locale
MCP STDIO IDE, agents et orchestrateurs compatibles MCP
API HTTP  intégration locale via JSON /api/v1
```

Les trois surfaces utilisent le même moteur et la même base SQLite. La CLI, le serveur MCP et l’API ne réimplémentent pas les règles métier.

## Parcours recommandé

1. [Démarrer MORPHEUS](QUICKSTART.md).
2. Enregistrer un workspace et le synchroniser.
3. Rechercher requirements, changements, contraintes, décisions et tâches avec la [CLI](CLI.md).
4. Utiliser la traçabilité, l’analyse de changement et les diagnostics qualité.
5. Activer éventuellement [MINOS, NEXUS ou JARVIS](INTEGRATIONS.md).

## Ce que MORPHEUS garantit

- séparation explicite `CURRENT / PROPOSED / HISTORICAL` ;
- snapshots publiés versionnés et historique non réécrit ;
- `APPLY != PROMOTE != ACTIVATE` ;
- `Scenario != AcceptanceCriterion` ;
- absence d’un moteur optionnel != panne MORPHEUS ;
- observation externe live != mutation d’un snapshot publié ;
- lifecycle indisponible != lifecycle inféré ;
- évaluation d’une transition != application d’une transition.

## Stockage local

MORPHEUS utilise SQLite par défaut. Les chemins peuvent être contrôlés avec :

```text
--data-dir PATH
--config-dir PATH
--db PATH

MORPHEUS_DATA_DIR
MORPHEUS_CONFIG_DIR
MORPHEUS_DB
```

Defaults de plateforme :

```text
Windows  %LOCALAPPDATA%\Morpheus / %APPDATA%\Morpheus
Linux    répertoires XDG data/config
```

## Intégrations optionnelles

```text
MINOS   code intelligence                 MCP STDIO inter-processus
NEXUS   contexte technique                MCP STDIO inter-processus
JARVIS  orchestration / sequencing        HTTP local read-only côté MORPHEUS
```

MINOS, NEXUS et JARVIS ne sont pas embarqués dans la distribution MORPHEUS.

## Pour aller plus loin

- [Référence CLI](CLI.md)
- [Intégrations](INTEGRATIONS.md)
- [Documentation développeur](../developer/README.md)
- [Portail de documentation](../README.md)

# ADR-0072 — Configuration runtime et surfaces MINOS optionnelles

- Statut : **Proposée — M12 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0063, ADR-0066, ADR-0069
- Portée : M12 — configuration et exposition MINOS

## Décision proposée

MINOS est activé uniquement par configuration runtime :

```text
morpheus.minos.jar / MORPHEUS_MINOS_JAR
morpheus.minos.java / MORPHEUS_MINOS_JAVA
morpheus.minos.home / MORPHEUS_MINOS_HOME
morpheus.minos.timeoutSeconds / MORPHEUS_MINOS_TIMEOUT_SECONDS
```

Priorité : propriété Java > environnement > défaut.

Défauts :

```text
java = java
timeoutSeconds = 20
```

Le timeout est borné `1..120` secondes.

Sans JAR configuré, l'intégration est `DISABLED` et aucun resolver `MINOS` n'est enregistré.

## Surfaces additives

CLI :

```text
minos-status
external-references list
external-references resolve
```

MCP read-only :

```text
list_external_references
resolve_external_reference
```

HTTP `/api/v1` :

```text
GET /integrations/minos/status
GET /projects/{projectId}/external-references
GET /projects/{projectId}/external-references/{referenceId}/resolution
```

Toutes les surfaces partagent le même use case applicatif. Aucune ne réimplémente la résolution.

## Distribution

Le ZIP MORPHEUS embarque le client/adaptateur MINOS mais pas le moteur MINOS ni son JAR.

```text
MORPHEUS standalone -> toujours valide
MINOS process       -> composant optionnel externe
```

## Critères d'acceptation

1. disabled explicite sans configuration ;
2. configuration invalide explicite sans panne du reste de MORPHEUS ;
3. CLI/MCP/API cohérents ;
4. aucune mutation via MCP/API ;
5. `--version`, MCP et API M11 fonctionnent sans MINOS ;
6. shaded JAR contient l'adapter mais pas `com.minos.*` ;
7. packaging portable vert sans MINOS installé/configuré.

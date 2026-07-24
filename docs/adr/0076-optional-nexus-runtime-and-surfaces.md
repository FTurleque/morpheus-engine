# ADR-0076 — Runtime et surfaces NEXUS optionnels

- Statut : **Proposée — M13 gate pending**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0063, ADR-0066, ADR-0073, ADR-0075
- Portée : M13 — configuration et exposition NEXUS

## Configuration proposée

```text
morpheus.nexus.jar / MORPHEUS_NEXUS_JAR
morpheus.nexus.java / MORPHEUS_NEXUS_JAVA
morpheus.nexus.home / MORPHEUS_NEXUS_HOME
morpheus.nexus.timeoutSeconds / MORPHEUS_NEXUS_TIMEOUT_SECONDS
```

Priorité : propriété Java > environnement > défaut.

Défauts :

```text
java = java
timeoutSeconds = 20
```

Timeout borné `1..120` secondes.

Sans JAR configuré, l'intégration est `DISABLED`.

## Surfaces additives

CLI :

```text
nexus-status
augmented-context requirement
augmented-context change
```

MCP read-only :

```text
get_augmented_requirement_context
get_augmented_change_context
```

HTTP `/api/v1` :

```text
GET  /integrations/nexus/status
POST /projects/{projectId}/requirements/{requirementId}/augmented-context
POST /projects/{projectId}/changes/{changeId}/augmented-context
```

Toutes délèguent au même use case applicatif et au même provider de contexte technique.

## Distribution

Le ZIP MORPHEUS embarque le client/adaptateur NEXUS, jamais le moteur NEXUS ni son runner.

```text
MORPHEUS standalone -> toujours valide
NEXUS process        -> composant externe optionnel
```

## Critères d'acceptation

1. disabled explicite sans configuration ;
2. configuration invalide explicite sans panne MORPHEUS ;
3. CLI/MCP/API cohérents ;
4. aucune mutation NEXUS via MORPHEUS ;
5. M12 MINOS et M11 API continuent de fonctionner sans NEXUS ;
6. shaded JAR contient l'adapter NEXUS mais pas `com.nexus.*` ;
7. packaging portable vert sans NEXUS installé/configuré.
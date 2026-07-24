# ADR-0076 — Runtime et surfaces NEXUS optionnels

- Statut : **Acceptée — M13**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0063, ADR-0066, ADR-0073, ADR-0075
- Portée : M13 — configuration et exposition NEXUS

## Configuration

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

## Preuve M13

```text
NEXUS Integration                              7/7 PASS
MCP                                            5/5 PASS
API                                            7/7 PASS
CLI                                          17/17 PASS
Architecture                                154/154 PASS
TOTAL                                       346/346 PASS
MCP/API/MINOS/NEXUS adapter packaging proof     PASS
Packaged standalone optional-engines smoke      PASS
Packaged API health smoke                       PASS
Portable archive creation                       PASS
```

Le ZIP Windows validé fait `33,654,379` octets. Sans MINOS/NEXUS configurés, les deux statuts packagés sont `DISABLED` et l'API packagée répond `UP`.

## Critères d'acceptation

1. disabled explicite sans configuration ;
2. configuration invalide explicite sans panne MORPHEUS ;
3. CLI/MCP/API cohérents ;
4. aucune mutation NEXUS via MORPHEUS ;
5. M12 MINOS et M11 API continuent de fonctionner sans NEXUS ;
6. shaded JAR contient l'adapter NEXUS mais pas `com.nexus.*` ;
7. packaging portable vert sans NEXUS installé/configuré.

Tous les critères sont satisfaits par la validation M13.
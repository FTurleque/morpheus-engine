# ADR-0076 — Runtime et surfaces NEXUS optionnels

> **Archivée.** Cette décision est consolidée dans [ADR-0128](../ARCH-0128-pairs-minos-et-nexus-par-mcp-stdio.md). Ce texte n'est plus en vigueur et n'est pas tenu à jour.

> Lecture : le défaut du lanceur Java de cette décision est remplacé par l'amendement du 13 octobre 2026, en fin de document. (note éditoriale du 21 octobre 2026 : le texte de la décision n'est pas modifié)

- Statut : **Remplacée par ADR-0128**
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

## Amendement du 13 octobre 2026 — le lanceur Java du pair n'est plus cherché dans le `PATH`

Le défaut `java = java` ci-dessus n'est plus vrai. La commande nue était résolue par le système dans le `PATH` du
processus MORPHEUS : un autre `java` placé en tête de ce chemin aurait été lancé comme pair (constat AUD-SEC-05 de
l'audit du 9 octobre 2026).

- **Défaut** : le lanceur du runtime sur lequel MORPHEUS tourne, `<java.home>/bin/java`, comme le fait déjà la sonde
  de plugin. Si ce runtime ne porte aucun lanceur et qu'un pair est configuré, l'intégration est invalide et le dit.
- **Valeur configurée** : le chemin absolu d'un fichier exécutable. Un nom nu ou un chemin relatif rend l'intégration
  invalide ; rien n'est lancé. Sous Windows, le suffixe `.exe` peut être omis.
- **Lien symbolique : résolu, pas refusé.** L'audit demandait le refus. Le lanceur usuel d'un système Linux est une
  chaîne de liens (`/usr/bin/java`), et le dépôt a déjà choisi « résoudre et déclarer » pour le répertoire de plugins.
  Le fichier réel est celui qui est lancé et celui que le statut local affiche.
- **L'invariant est dans le constructeur** du record de réglages : il ne peut pas être construit autour d'une commande
  que le `PATH` résoudrait. Les deux intégrations le portent séparément, puisqu'elles ne peuvent pas dépendre l'une de
  l'autre ; `PeerLauncherArchitectureTest` vérifie qu'aucune des deux ne s'en écarte.

**Conséquence mesurée le 13 octobre 2026** sur l'image Windows packagée, avec les pairs réels : le runtime embarqué est
un Java 21 réduit. Un pair compilé pour Java 21 y démarre ; un pair compilé pour une version plus récente n'y démarre
pas et demande que la variable nomme un JDK de cette version. Avant, ce cas se réglait par le `java` du `PATH`, sans
être dit. Le guide des intégrations et la note de version 1.2.1 le disent.

`PATH` reste dans l'environnement transmis au pair : le pair est du code de confiance et peut en avoir besoin.

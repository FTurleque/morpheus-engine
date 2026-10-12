# ADR-0072 — Configuration runtime et surfaces MINOS optionnelles

- Statut : **Acceptée — M12**
- Date : 24 juillet 2026
- Dépend de : ADR-0059, ADR-0063, ADR-0066, ADR-0069
- Portée : M12 — configuration et exposition MINOS

## Décision

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

## Preuve M12

```text
Maven total                                  331/331 PASS
MORPHEUS CLI                                  15/15 PASS
MORPHEUS API                                   5/5 PASS
MORPHEUS MCP                                   5/5 PASS
MCP/API/MINOS adapter packaging proof          PASS
Packaged standalone MINOS-optional smoke       PASS
Packaged API health smoke                      PASS
Portable archive creation                      PASS
```

Voir `docs/VALIDATION_M12.md`.

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

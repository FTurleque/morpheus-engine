# Proposition : confiner les familles technologiques qu'emploie la CLI

## Why

`ARCH-0150` a sorti du cœur applicatif la seule sortie réseau du produit : la découverte de mise à jour lit son manifeste
par le port `product.UpdateManifestSource`, implémenté dans `morpheus-cli` par `UpdateManifestFetcher`. Cette classe
apporte d'un coup trois familles : réseau sortant (imports `java.net.http`, `HttpClient.newBuilder()`), accès fichier
(`Files.newInputStream`) et fils d'exécution (`Executors.newVirtualThreadPerTaskExecutor()`). Or la discipline
d'`ARCH-0150` — familles mesurées, liste nommée par famille, exactitude dans les deux sens — s'arrête à
`com.morpheus.application` : `ApplicationTechnologyBoundaryArchitectureTest` importe ce seul paquet. Hors de lui, seule
la réflexion est confinée sur tout le produit (`ReflectionAndClassLoadingBoundaryArchitectureTest`), et la seule règle
sur les fils d'exécution (`DatabaseWorkArchitectureTest`, `BE-0159`) est bornée à `com.morpheus.api`. Constat
`AUD-ARC-18` (Faible, partiel) de l'audit `docs/project/audits/2026-10-09/findings.md`, revérifié le 2026-11-06 sur
`30520bd3` : rien n'empêche la prochaine dépendance technique d'entrer dans la CLI sans décision.

## What Changes

- Gate de build ajouté pour `com.morpheus.cli` : chaque famille du vocabulaire d'`ARCH-0150` y a un régime déclaré —
  refusée, confinée à des classes nommées, libre, ou tenue par un autre gate — avec sa raison.
- Familles refusées dans toute la CLI : SQL, bibliothèque JSON, exécution de processus.
- Familles confinées à des classes nommées, exactes dans les deux sens : réseau sortant, fils d'exécution, accès
  fichier, environnement du processus, flux standard.
- Famille libre : horloge murale. Famille tenue ailleurs : réflexion et chargement de classes.
- Les listes nommées de la CLI sont des **tables de faits** déclarées dans `ProductionClassListsArchitectureTest`.
- Les définitions des familles deviennent une seule déclaration, partagée par le gate de l'application et celui de la
  CLI.

## Capabilities

### New Capabilities
- `cli-technology-boundary` : quelles familles technologiques `com.morpheus.cli` peut atteindre, par quelles classes, et
  comment le build le tient.
- `api-technology-boundary` : la même question pour `com.morpheus.api`, sur la même déclaration partagée (volet
  `morpheus-api`, `design.md` D6).

### Modified Capabilities

Aucune. `application-technology-boundary` garde ses exigences : le partage des définitions de familles ne change pas
ce qu'elles refusent ni ce qu'elles confinent dans l'application.

## Périmètre et exclusions

**Inclus :** les classes de production de `com.morpheus.cli` (module `morpheus-cli`) ; la déclaration partagée des
familles dans `morpheus-architecture-tests`.

**Inclus en second volet :** les classes de production de `com.morpheus.api` (module `morpheus-api`), l'autre module de
transport que l'action du constat nomme (`design.md` D6, section « morpheus-api » de `tasks.md`).

**Exclus :**
- dans le premier volet, `morpheus-api`. Il porte déjà des règles par famille et
  par groupe — fils d'exécution (`DatabaseWorkArchitectureTest`, `BE-0159`), bibliothèque JSON
  (`HttpRoutesTransportBoundaryArchitectureTest`, `everyApiJsonMapperIsStrictAboutUnknownPropertiesAndTrailingTokens`) —
  qu'un gate de familles doit d'abord réconcilier sans les répéter (`.claude/rules/architecture.md` : une suite ne répète
  aucun interdit qu'une règle tient déjà) ; et sa surface est plus large (relevé textuel du 06/11 sur `30520bd3` : des
  imports réseau, fichier et fils dans plusieurs classes, là où la CLI n'en a qu'une pour le réseau et les fils). Il est
  traité en second volet de ce changement, sur la déclaration partagée des familles livrée par le premier.
- `morpheus-mcp`, les providers, les stores et les intégrations : hors de l'action du constat.
- tout déplacement de code : le changement mesure et gèle l'état présent, il ne déplace aucune classe.
- la rédaction d'un ADR (voir `design.md`, D5).

## Acteurs et surfaces concernés

Mainteneur qui ajoute une dépendance technique à la CLI ; revue de PR. Aucune surface CLI, MCP ni HTTP ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `UpdateManifestFetcher` atteint réseau, fichier et fils | existant vérifié | `morpheus-cli/src/main/java/com/morpheus/cli/command/UpdateManifestFetcher.java` : imports `java.net.http` (l. 9-11), `java.nio.file.Files` (l. 12), `HttpClient.newBuilder()` (l. 32), `Files.newInputStream` (l. 54), `Executors.newVirtualThreadPerTaskExecutor()` (l. 95) |
| Le gate de l'application n'importe que `com.morpheus.application` | existant vérifié | `ApplicationTechnologyBoundaryArchitectureTest`, `importPackages(APPLICATION)` |
| Aucun test ne confine ces familles dans la CLI | existant vérifié | recherche de `com.morpheus.cli` dans les règles de familles de `morpheus-architecture-tests` ; `DatabaseWorkArchitectureTest` borné à `com.morpheus.api..` |
| Aucune classe de la CLI n'atteint SQL, Jackson ni l'exécution de processus | existant vérifié (texte) | recherche dans `morpheus-cli/src/main/java` ; à confirmer sur le bytecode par la mesure de la tâche 1.1 |
| L'horloge est libre dans la CLI | décision | `design.md`, D2 |
| Les listes de la CLI sont des tables de faits | décision | `design.md`, D3 |
| `morpheus-api` est exclu de ce changement | décision | `design.md`, D4 |
| Aucun ADR n'est rédigé ici | décision, à confirmer | `design.md`, D5 ; question ouverte ci-dessous |

## Impact

- Tests : un `CliTechnologyBoundaryArchitectureTest` ajouté ; `ApplicationTechnologyBoundaryArchitectureTest` lit les
  familles depuis la déclaration partagée ; `ProductionClassListsArchitectureTest` déclare les listes de la CLI.
- Documentation : `.claude/rules/architecture.md` (section de ce que l'application touche, complétée pour la CLI) ;
  page nouvelle `docs/developer/specifications/cli-technology-boundary.md` et l'index
  `docs/developer/specifications/README.md`.
- Aucun code de production, aucun contrat public, aucun cliquet (`config/debt-counter-ratchets.properties` inchangé).

## Questions ouvertes

- Le mainteneur veut-il que les listes de la CLI ne fassent que rétrécir ? Dans ce cas elles deviennent des compteurs
  de dette, ce qui exige un ADR accepté qui les nomme (un ADR consolidé qui remplace `ARCH-0150`, ou un ADR des modules
  de transport) ; ce changement les déclare tables de faits par défaut (`design.md`, D3 et D5).

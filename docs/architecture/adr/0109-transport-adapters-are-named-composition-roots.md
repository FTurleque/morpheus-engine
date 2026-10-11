# ADR-0109 — Les adaptateurs de transport sont des racines de composition nommées, et leur liste ne fait que rétrécir

- Statut : **Acceptée — post-audit 1.2.1**
- Date : 8 octobre 2026
- Dépend de : ADR-0003 (port de stockage de connaissance), ADR-0017 (Maven multi-module), ADR-0065 (HTTP
  `jdk.httpserver`), ADR-0062 (SDK MCP natif)
- Amende : ADR-0003 (§ 7, « aucune dépendance de CLI/MCP/API à la base choisie »)
- Portée : `morpheus-api`, `morpheus-mcp`, `morpheus-mcp-transport`, `morpheus-domain` ;
  `morpheus-architecture-tests` (`AdapterCompositionRootArchitectureTest`)

## Contexte

ADR-0003 range parmi ses conséquences « aucune dépendance de CLI/MCP/API à la base choisie », et les règles du dépôt
disent que les adaptateurs sont frères et que le câblage est explicite dans `MorpheusMain`. Le code dit autre chose :
`morpheus-api` déclare des dépendances de compilation sur `morpheus-store-sqlite` et `morpheus-provider-openspec`,
`morpheus-mcp` sur `morpheus-store-sqlite`, et des classes de ces deux modules construisent elles-mêmes leurs stores.
Aucun ADR ne l'acceptait, aucune règle ne le bornait : le modèle documenté et le code divergeaient sans que rien le
signale (audit outillé du 8 octobre 2026, constat ARC-AUD-3).

**Mesure, par ArchUnit et non par recherche textuelle** (classes de production importées par
`morpheus-architecture-tests`, 8 octobre 2026, `develop` à `3ec3ea46`) :

| Mesure | Résultat |
|---|---|
| classes de `com.morpheus.api` / `com.morpheus.mcp` qui dépendent de `com.morpheus.store..` ou `com.morpheus.provider..` | **32** (20 `api`, 12 `mcp`) — l'audit en comptait 17 par recherche des `import` ; une seule, `MorpheusProjectSyncApiService`, atteint un provider (OpenSpec) |
| classes de `com.morpheus.integration.mcp..` qui dépendent d'un autre paquet `com.morpheus..` | **0** — règle jusqu'ici écrite dans des Javadocs de test seulement |
| cycles entre tranches de `com.morpheus.domain.(*)..` | **2** : `provider ↔ source`, `requirement ↔ scenario` — ArchUnit 1.5.1 ne signale le second qu'une fois le premier écarté |
| cycles entre tranches de `com.morpheus.application.(*)..` | **167** rapportés (`archunit.cycles.maxNumberToDetect` relevé à 2000), 16 tranches impliquées |
| cycles entre tranches de `com.morpheus.api.(*)..` | sans objet : `api` n'a pas de sous-paquet |

## Décision

Décisions du mainteneur du 8 octobre 2026.

### 1. `api` et `mcp` sont des racines de composition de leur propre runtime, par une liste nommée qui ne fait que rétrécir

Les serveurs HTTP et MCP construisent leur runtime à chaque requête ou appel d'outil, à partir du chemin de la base
qu'on leur passe. Les 32 classes mesurées sont reconnues comme les **racines de composition** de ce runtime, et
seulement elles. Elles sont nommées dans `AdapterCompositionRootArchitectureTest.COMPOSITION_ROOTS` :

- `onlyTheNamedCompositionRootsReachAConcreteStoreOrProvider` refuse toute **autre** classe de `api` ou `mcp` qui
  dépend d'un store ou d'un provider concret ;
- `everyNamedCompositionRootStillReachesAConcreteStoreOrProvider` exige que la liste soit **exactement** l'ensemble
  mesuré : une racine qui ne dépend plus d'aucun store doit être retirée. La liste peut rétrécir, elle ne peut pas
  grandir en silence.

**Ajouter une racine exige d'amender cet ADR**, avec la raison pour laquelle la classe ne peut pas passer par un port
de l'application. Le reste des interdits est inchangé : `application` ne connaît aucun adaptateur
(`LayerDependencyTest`), `api` ne dépend ni de `cli` ni de `mcp`, les providers et les stores restent frères.

### 2. `morpheus-mcp-transport` ne dépend d'aucun autre paquet MORPHEUS

Le transport STDIO borné est partagé par le serveur MCP et par les clients MINOS et NEXUS : il ne connaît aucun type
MORPHEUS. Son POM l'empêche déjà ; `theMcpTransportDependsOnNoOtherMorpheusPackage` le tient le jour où une
dépendance y serait ajoutée.

### 3. Les paquets du domaine sont sans cycle, à deux exceptions nommées ; l'application est reportée

`domainPackagesAreFreeOfCyclesBesideTheTwoNamedOnes` interdit tout cycle entre tranches de `com.morpheus.domain`,
sauf les deux mesurés, **nommés par la paire de types exacte** qui ferme chacun : `ProviderProbeResult → SourceLocator`
(un résultat de sonde porte le localisateur de la source sondée) et `RequirementDelta → Scenario` (un delta porte ses
scénarios, un scénario nomme son exigence). Toute autre dépendance entre ces paquets, ou tout nouveau cycle, échoue.

La même règle sur `com.morpheus.application` est **reportée** : 167 cycles sur 16 tranches demanderaient une refonte
des paquets, hors de portée d'une décision d'architecture. La mesure est consignée ici pour la prochaine décision.

## Alternatives écartées

- **Ramener tout le câblage dans `morpheus-cli`** (les adaptateurs ne dépendant plus que des ports de l'application,
  sous une règle `noClasses()` stricte) : c'est le modèle que décrivait la documentation, mais il exige de déplacer
  32 classes, les POMs et les bootstraps des serveurs, sans défaut observé qui le motive aujourd'hui. Il reste possible :
  la liste nommée en est le compteur.
- **Ne rien écrire** : laisser la règle non dite, c'était la situation constatée par l'audit.

## Conséquences

- ADR-0003 est amendé : sa conséquence « aucune dépendance de CLI/MCP/API à la base choisie » vaut pour la CLI et pour
  toute classe de `api` et `mcp` hors des racines nommées.
- `.claude/CLAUDE.md` et `.claude/rules/architecture.md` décrivent ce modèle.
- Une nouvelle classe de `api` ou `mcp` qui veut un store passe par une racine existante ou amende cet ADR.

## Preuves exécutables

`morpheus-architecture-tests/src/test/java/com/morpheus/architecture/AdapterCompositionRootArchitectureTest.java`,
quatre tests verts. Chacun a été **cassé pour prouver qu'il tient**, puis restauré (8 octobre 2026) :

- une classe `com.morpheus.api.PlantedStoreUser` portant un `SqliteSpecificationKnowledgeStore` fait échouer les deux
  tests de la décision 1 ;
- un nom ajouté à la liste sans dépendance concrète (`MorpheusHttpServer`) fait échouer le test d'égalité ;
- une dépendance `morpheus-domain` ajoutée au POM du transport, avec une classe qui l'utilise, fait échouer la
  décision 2 ;
- un record `com.morpheus.domain.provider.PlantedLocatorHolder(SourceLocator)` rouvre le cycle `provider ↔ source`
  par une autre paire de types et fait échouer la décision 3.

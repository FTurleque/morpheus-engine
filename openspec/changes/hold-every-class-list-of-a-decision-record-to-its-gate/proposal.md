# Proposition : comparer à son gate chaque liste de classes qu'un ADR énumère

## Why

Un ADR en vigueur qui énumère des classes et la constante du test d'architecture qui tient la même liste sont deux
copies écrites à la main, que rien ne compare. Le constat `AUD-ARC-17` (Faible) de l'audit
`docs/project/audits/2026-10-09/findings.md`, revérifié le 2026-11-06 sur `30520bd3`, le mesure sur `ARCH-0150` : la
section « Mesure » nomme `product.UpdateDiscoveryService` là où le gate nomme `security.PeerLaunchSettingsResolver`,
place `LocalWritePermissionHardener` dans `security` alors que la classe vit dans `files`, porte des longueurs de
fichier et un total périmés, et attribue à `UpdateDiscoveryService` le réseau sortant et les fils d'exécution que la
décision 1 du même ADR refuse dans toute l'application. Le gate, lui, est exact dans les deux sens
(`ApplicationTechnologyBoundaryArchitectureTest#everyNamedClassStillReachesItsFamily`), et le cliquet
`config/debt-counter-ratchets.properties` compte les noms : ni l'un ni l'autre ne lit l'ADR. Une classe ajoutée au gate
en même temps que sa constante passe sans que l'ADR suive.

## What Changes

- Gate de build : une liste de classes de production qu'un ADR en vigueur énumère est comparée, nom pour nom et dans
  les deux sens, à la constante du test qui la tient ; l'écart fait échouer le build en nommant l'ADR, la constante et
  les noms en trop ou manquants.
- Gate de build : une table de faits qu'un ADR énonce (`ApiEntryTypesArchitectureTest.ENTRY_TYPES`, énoncée par
  `ARCH-0155` §3) déclare cet ADR, comme un compteur de dette déclare le sien.
- Gate de build : toute classe qu'`ARCH-0150` nomme par un nom qualifié existe à ce nom dans les sources.
- Documentation : `ARCH-0150` est clarifié sans changer sa décision — la mesure datée du 25/10/2026 quitte l'ADR pour
  un relevé daté, l'énumération comparée devient une seule table délimitée, la ligne « horloge murale » nomme ses
  classes au lieu de renvoyer à la mesure. `ARCH-0155` §3 délimite sa liste de six types.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `adapter-internal-boundaries` : deux exigences ajoutées — l'énumération d'une liste par un ADR est comparée à sa
  constante, et une table de faits énoncée par un ADR déclare cet ADR.
- `application-technology-boundary` : deux exigences ajoutées — une classe nommée par un nom qualifié existe à ce nom,
  et le registre ne porte aucune mesure périssable de ses classes nommées.

## Périmètre et exclusions

**Inclus :** les listes que `ProductionClassListsArchitectureTest` rattache à un ADR (compteurs de dette, et tables de
faits qui déclarent l'ADR qui les énonce) ; le texte d'`ARCH-0150` et d'`ARCH-0155` §3.

**Exclus :**
- `ARCH-0156` et `ARCH-0158` : ils nomment `COMPOSITION_ROOTS`, `RUNTIME_ENTRY_POINTS` et `RUNTIME_PUBLIC_TYPES` par
  leur seule référence `TestClass.CHAMP`, sans énumérer leurs noms ; il n'y a rien à comparer (voir `design.md`, D1).
- les tables de faits qu'aucun ADR n'énonce ;
- toute modification d'une liste du gate : le changement compare, il n'ajoute ni ne retire aucun nom.

## Acteurs et surfaces concernés

Mainteneur qui ajoute ou retire une classe d'une liste nommée ; agent qui lit l'ADR (Archgate lui remet la section
`Decision`). Aucune surface CLI, MCP ni HTTP ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La section « Mesure » d'`ARCH-0150` diverge du gate de deux noms, un paquet, des longueurs et un total | existant vérifié | `ARCH-0150` (table « Mesure ») contre `ApplicationTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES` ; `morpheus-application/.../files/LocalWritePermissionHardener.java` existe, `security/LocalWritePermissionHardener.java` non |
| `UpdateDiscoveryService` n'atteint ni réseau sortant ni fils d'exécution | existant vérifié | imports de `UpdateDiscoveryService.java` (seul `java.net.URI`) ; famille « outbound network » et « threads » de `UNUSED_FAMILIES` |
| La section `Decision` d'`ARCH-0150` concorde avec le gate, sauf la ligne « horloge murale » qui renvoie à la mesure | existant vérifié | `ARCH-0150` décision 2 |
| La liste de six types d'`ARCH-0155` §3 concorde avec `ENTRY_TYPES` | existant vérifié | `ARCH-0155` §3, `ApiEntryTypesArchitectureTest.ENTRY_TYPES` |
| Aucun test ne lit le contenu d'une liste dans un ADR | existant vérifié | `ProductionClassListsArchitectureTest#everyDebtCounterIsNamedByTheAcceptedDecisionRecordItDeclares` ne cherche que `TestClass.CHAMP` |
| Le gate lit l'ADR (et non l'inverse) | décision | `design.md`, D1 |
| La mesure datée quitte l'ADR | décision | `design.md`, D2 ; `.claude/rules/adr.md` (« mesure datée » n'est pas un ADR) |
| Clarifier `ARCH-0150` ne change pas sa décision | décision | `design.md`, D3 ; `.claude/rules/adr.md` (« clarifier son texte si son sens ne change pas ») |

## Impact

- Tests : `ProductionClassListsArchitectureTest` (sorte « table de faits énoncée par un ADR »), un test de comparaison
  ajouté dans `morpheus-architecture-tests`, `ApplicationTechnologyBoundaryArchitectureTest` et
  `ApiEntryTypesArchitectureTest` (constantes lues par la comparaison).
- ADR : `ARCH-0150` et `ARCH-0155` clarifiés, décisions inchangées ; aucun nouvel ADR.
- Documentation : un relevé daté sous `docs/project/research/` ; pages
  `docs/developer/specifications/adapter-internal-boundaries.md` et
  `docs/developer/specifications/application-technology-boundary.md`.
- Aucun code de production, aucun contrat public, aucun cliquet modifié.

## Questions ouvertes

Aucune.

# Proposition : borner le cycle entre le DSL de requête et les vues enregistrées

## Why

Le constat `AUD-ARC-19` (Faible, sprint 15) de l'audit `docs/project/audits/2026-10-09/findings.md`, revérifié le
2026-11-06 sur `30520bd3` (`TOUJOURS_VALIDE`), relève un cycle vivant entre `com.morpheus.application.query.dsl` et
`com.morpheus.application.query.saved` que rien ne suit. `ApplicationPackageCyclesArchitectureTest` tranche au premier
niveau (`slices().matching(root + ".(*)..")`, ligne 38) : les deux paquets forment une seule tranche, ce que sa
javadoc écrit (lignes 25 à 27) et ce qu'ARCH-0156 assume (conséquence négative : « une dépendance entre `query.dsl` et
`query.saved` lui échappe »). `dsl/QueryPublicViews.java` (lignes 3 à 5) importe trois types de `query.saved`, et
quatre fichiers de `query/saved/` importent `query.dsl`. Le jour où ce couplage se resserre, aucun build ne le
signale. Le mainteneur a décidé le 2026-11-06 de **borner** ce cycle plutôt que de le résorber.

## What Changes

- Une règle ajoutée refuse tout cycle entre les paquets de deuxième niveau de `com.morpheus.application.query` (et la
  racine `query` elle-même), lue sur le bytecode, à la seule exception des arêtes de `query.dsl` vers `query.saved`
  nommées dans une liste exacte.
- La liste exacte des arêtes entre `query.dsl` et `query.saved`, dans les deux sens, est un compteur de dette : une
  arête ajoutée ou disparue fait échouer le build en se nommant, et sa clé de `config/debt-counter-ratchets.properties`
  ne fait que baisser.
- Un ADR consolidé qui remplace ARCH-0156 est à proposer : sa décision 5 et son « Don't » interdisent toute liste
  d'exceptions pour tolérer un cycle, et un compteur de dette exige un ADR accepté qui le nomme.
- Aucun code de production ne change.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `application-package-cycles` : exigences ajoutées — règle de cycle au deuxième niveau sous `query`, liste exacte et
  décroissante des arêtes de la paire `query.dsl` / `query.saved`, compteur de dette adossé à une décision acceptée,
  complétude de la règle. L'exigence de premier niveau (aucune liste de tolérance) est inchangée.

## Périmètre et exclusions

**Inclus :** les paquets sous `com.morpheus.application.query`, la règle et ses tests dans
`ApplicationPackageCyclesArchitectureTest`, la déclaration du compteur dans `ProductionClassListsArchitectureTest` et
`config/debt-counter-ratchets.properties`, l'ADR consolidé, la page durable de la capacité, `.claude/rules/architecture.md`.

**Exclus :**
- la résorption du cycle (remonter les types de `query.saved` qu'emploie `QueryPublicViews`) : écartée par le
  mainteneur ; la liste qui décroît la rend possible plus tard sans changer de règle ;
- une règle de deuxième niveau sous les autres paquets de l'application : aucune mesure ne l'appuie ici ;
- la règle de premier niveau, inchangée.

## Acteurs et surfaces concernés

Mainteneur qui ajoute une dépendance entre paquets de la plateforme de requête. Aucune surface publique ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| La règle actuelle tranche au premier niveau et ne voit pas la paire | existant vérifié | `ApplicationPackageCyclesArchitectureTest.java`, ligne 38 et javadoc lignes 25 à 27 |
| 13 déclarations d'import, 5 fichiers, dans les deux sens | existant vérifié | relevé du 2026-11-06 sur `30520bd3` : `dsl/QueryPublicViews.java:3-5`, `saved/SavedViewService.java:3-8`, `saved/SavedViewStore.java:3`, `saved/SavedViewDefinition.java:3-4`, `saved/SavedViewVersion.java:3` |
| Sur le bytecode, la paire est la seule à former un cycle entre sous-paquets de `query` | existant vérifié | `jdeps -verbose:class` sur `morpheus-application/target/classes`, 2026-11-06 : `dsl→saved`, `saved→dsl`, `export→dsl`, `export→compact` ; la racine `query` ne dépend d'aucun sous-paquet |
| Borner plutôt que résorber | décision | mainteneur, 2026-11-06 |
| Liste exacte des arêtes comme compteur de dette | décision | `design.md`, D2 et D3 |
| ADR consolidé remplaçant ARCH-0156 | proposition à valider | `design.md`, D5 ; `.claude/rules/adr.md` |

## Impact

- Tests d'architecture : `ApplicationPackageCyclesArchitectureTest` (règle, liste, tests sur fixtures),
  `ProductionClassListsArchitectureTest` (déclaration du compteur), fixtures sous
  `morpheus-architecture-tests/src/test/java/com/morpheus/architecture/cycles/`.
- Configuration : une clé ajoutée à `config/debt-counter-ratchets.properties`.
- ADR : ARCH-0156 à remplacer par un ADR consolidé (à proposer, acceptation humaine avant fusion).
- Documentation : `docs/developer/specifications/application-package-cycles.md`, `.claude/rules/architecture.md`
  (section « Les paquets de l'application sont sans cycle »).

## Questions ouvertes

Aucune qui bloque : voir `design.md`, Questions ouvertes.

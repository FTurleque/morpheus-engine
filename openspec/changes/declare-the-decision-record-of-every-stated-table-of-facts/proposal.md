# Proposition : déclarer l'ADR de chaque table de faits qu'un ADR énonce

## Why

`.claude/rules/architecture.md` pose qu'une table de faits « déclare l'ADR qui l'énonce s'il y en a un », et la spec
`adapter-internal-boundaries` l'exige (« Une table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR »). Le
mécanisme ne sait pourtant l'exprimer que si l'ADR **énumère** les noms de la table : le record `Kind` de
`ProductionClassListsArchitectureTest` n'offre que `TABLE_OF_FACTS`, `debtCounterUnder`, `debtCounterEnumeratedBy` et
`tableOfFactsEnumeratedBy`, sans `tableOfFactsUnder`. Une table de faits fondée par un ADR qui la nomme sans l'énumérer
reste donc déclarée sans décision.

Constat `AUD-ARC-21` (sévérité Faible), revérifié le 10/11/2026 sur `38a4079f` : `ARCH-0165` énonce que les listes
confinées des deux modules de transport sont des tables de faits, mais
`ApiTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES` et `CliTechnologyBoundaryArchitectureTest.CONFINED_FAMILIES`
sont déclarées `Kind.TABLE_OF_FACTS`, et `ARCH-0165` n'est cité par aucun `.java`. Mesure du 11/11/2026 sur `develop`
(`3cafc9cc`) : deux autres ADR acceptés nomment par `TestClass.CHAMP` une table de faits qui ne les déclare pas —
`ARCH-0162` nomme `SqliteConnectionScopeUseArchitectureTest.ONE_STORE_PER_OPERATION`, `ARCH-0115` nomme
`StaticOperationalRuntimeUsersArchitectureTest.MAIN_USERS`. Si l'un de ces ADR était remplacé ou archivé, rien ne
signalerait que la table s'appuie sur une décision retirée — défaut que le dépôt a déjà gaté côté OpenSpec
(`AUD-QUA-22`).

## What Changes

- `ProductionClassListsArchitectureTest` : une sorte « table de faits sous un ADR qui la nomme par sa référence », à
  côté de la sorte « énumérée par un ADR ».
- Le contrôle qui exige d'un compteur de dette un ADR accepté qui le nomme s'étend aux tables de faits qui déclarent un
  ADR sans énumération.
- Contrôle inverse : un ADR accepté qui nomme par `TestClass.CHAMP` une table de faits de `KINDS` qui ne le déclare pas
  fait échouer le build.
- Déclarations : les deux `CONFINED_FAMILIES` sous `ARCH-0165`, `ONE_STORE_PER_OPERATION` sous `ARCH-0162`,
  `MAIN_USERS` sous `ARCH-0115`.
- `ARCH-0165`, décision 3 : nommer les deux listes par `TestClass.CHAMP` — clarification sans changement de sens
  (`.claude/rules/adr.md`, « modifier plutôt que créer »).

## Capabilities

### New Capabilities

### Modified Capabilities

- `adapter-internal-boundaries` : l'exigence « Une table de faits qu'un ADR en vigueur énonce DOIT déclarer cet ADR »
  admet un ADR qui nomme la table par sa référence sans l'énumérer ; exigence ajoutée pour le contrôle inverse.

## Périmètre et exclusions

**Inclus :** le record `Kind`, les contrôles de `ProductionClassListsArchitectureTest`, les quatre déclarations
ci-dessus, la clarification d'`ARCH-0165`, la page durable de la capacité et `.claude/rules/architecture.md`.

**Exclus :** la sorte d'une liste (compteur ou table) ne change pour aucune liste ; aucune clé de
`config/debt-counter-ratchets.properties` n'est créée (`cli-technology-boundary` et `api-technology-boundary`
l'interdisent pour les listes confinées) ; les tables de faits qu'aucun ADR ne nomme restent sans ADR.

## Acteurs et surfaces concernés

Mainteneur qui remplace ou archive un ADR ; contributeur qui ajoute une liste à la suite d'architecture ; relecteur.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| `Kind` n'offre pas de table de faits sous un ADR par référence | existant vérifié | `ProductionClassListsArchitectureTest.java:60-74` |
| Les deux `CONFINED_FAMILIES` sont `TABLE_OF_FACTS` sans ADR | existant vérifié | `ProductionClassListsArchitectureTest.java:100,111` |
| `ARCH-0165` décision 3 énonce ces tables sans écrire `TestClass.CHAMP` | existant vérifié | `docs/architecture/adr/ARCH-0165-…md`, §Decision |
| `ARCH-0162` et `ARCH-0115` nomment chacun une table de faits non déclarée | existant vérifié | recherche des clés `TABLE_OF_FACTS` de `KINDS` dans les ADR acceptés, 11/11/2026 |
| Le contrôle inverse ne refuse que la mention `TestClass.CHAMP` | décision | une mention du seul nom de classe de test est de la prose |

## Impact

- `morpheus-architecture-tests` : `ProductionClassListsArchitectureTest` seul.
- `docs/architecture/adr/ARCH-0165-…md` (clarification), `.claude/rules/architecture.md` (paragraphe sur les tables de
  faits), `docs/developer/specifications/adapter-internal-boundaries.md`.
- Aucun nouvel ADR : la règle existe dans `.claude/rules/architecture.md` et dans la spec ; le changement donne au
  mécanisme le moyen de la tenir.

## Questions ouvertes

Aucune.

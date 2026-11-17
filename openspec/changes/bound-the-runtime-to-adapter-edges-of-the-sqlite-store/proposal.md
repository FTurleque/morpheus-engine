# Proposition : borner les arêtes du runtime vers l'adaptateur du store SQLite (AUD-ARC-28)

## Why

Les deux paquets de `morpheus-store-sqlite` forment un cycle que rien ne borne. L'adaptateur
(`com.morpheus.store.sqlite.adapter`) dépend du runtime (`com.morpheus.store.sqlite.runtime`), ce qu'ARCH-0158 prévoit ;
mais deux classes du runtime dépendent en retour de l'adaptateur : `SqliteOperationRuntime`, qui construit les stores
d'une opération (ARCH-0162), et `SqliteServerMaintenance`, qui ouvre le store des snapshots. Aucune règle de
`SqliteStorePackagesArchitectureTest` ne dit quelles classes du runtime peuvent dépendre de l'adaptateur, et aucune
règle `beFreeOfCycles` ne couvre le module : une nouvelle classe du runtime peut dépendre de n'importe quel store sans
que le build le refuse, et le découpage d'ARCH-0158 n'est pas un ordre.

Preuve : constat `AUD-ARC-28` (Faible, sprint 6) de `docs/project/audits/2026-11-17/findings.md`, mesuré par l'audit du
17/11/2026 sur `a04f32bd` et revérifié le 17/11/2026 sur `1a539c22` : `SqliteOperationRuntime` importe 12 classes de
l'adaptateur, `SqliteServerMaintenance` en importe une (`SqliteSpecificationKnowledgeStore`), 12 fichiers de
l'adaptateur importent le runtime ; les règles `beFreeOfCycles` du dépôt sont dans `ApplicationPackageCyclesArchitectureTest`,
`ApiPackageCyclesArchitectureTest`, `CliAndMcpPackageCyclesArchitectureTest` et `AdapterCompositionRootArchitectureTest`
(domaine), aucune sur le store.

## What Changes

- Une règle ajoutée à `SqliteStorePackagesArchitectureTest` refuse, lue sur le bytecode et doublée d'un scan du texte
  des sources du runtime, toute dépendance d'une classe du runtime vers une classe de l'adaptateur hors d'une liste
  exacte d'arêtes nommées.
- Cette liste est un compteur de dette (`.claude/rules/architecture.md`, `ProductionClassListsArchitectureTest`) : une
  arête ajoutée ou disparue fait échouer le build en se nommant, et sa clé de `config/debt-counter-ratchets.properties`
  ne fait que baisser.
- **Décision du mainteneur** (design.md, D1) : un compteur de dette exige un ADR accepté qui le nomme, et ARCH-0158 ne
  le nomme pas ; la recommandation est un ADR consolidé qui remplace ARCH-0158 (ADR à proposer, jamais déclaré accepté
  par ce changement).
- Aucun code de production ne change.

## Capabilities

### New Capabilities

Aucune.

### Modified Capabilities
- `sqlite-store-packages` : deux exigences ajoutées — seules les arêtes nommées vont du runtime vers l'adaptateur, et
  ces arêtes forment une liste exacte qui ne fait que rétrécir.

## Périmètre et exclusions

**Inclus :** `SqliteStorePackagesArchitectureTest` (règle, liste, scan textuel), la déclaration du compteur dans
`ProductionClassListsArchitectureTest`, sa clé dans `config/debt-counter-ratchets.properties`, l'ADR consolidé à
proposer et ses index (`docs/architecture/adr/README.md`, `docs/architecture/arc42/09-decisions.md`), la page durable
`docs/developer/specifications/sqlite-store-packages.md`, `.claude/rules/architecture.md` (section du store SQLite).

**Exclus :**
- la résorption du cycle par un troisième paquet qui accueillerait la fabrique des stores et la maintenance :
  option C de design.md, laissée au mainteneur ; elle change `RUNTIME_ENTRY_POINTS`, que les transports nomment ;
- la direction adaptateur → runtime, prévue par ARCH-0158 et tenue par ses règles existantes ;
- toute modification de `SqliteOperationRuntime` ou `SqliteServerMaintenance`.

## Acteurs et surfaces concernés

Mainteneur qui ajoute une classe au runtime du store SQLite. Aucune surface publique ne change.

## Nature des informations

| Affirmation | Nature | Source ou preuve |
|---|---|---|
| Deux classes du runtime dépendent de l'adaptateur | existant vérifié | `grep -ln "com.morpheus.store.sqlite.adapter" morpheus-store-sqlite/src/main/java/com/morpheus/store/sqlite/runtime/*.java`, le 17/11/2026 |
| Aucune règle ne borne la direction runtime → adaptateur | existant vérifié | `SqliteStorePackagesArchitectureTest` (six méthodes, aucune ne sélectionne le runtime comme origine vers l'adaptateur) |
| La construction d'un store hors des classes nommées est déjà refusée | existant vérifié | `SqliteConnectionScopeUseArchitectureTest` (`SCOPE_OWNERS`, `ONE_STORE_PER_OPERATION`) ; une dépendance sans construction ne l'est pas |
| Un compteur de dette exige un ADR accepté qui le nomme par `TestClass.CHAMP` | existant vérifié | `.claude/rules/architecture.md`, `ProductionClassListsArchitectureTest#everyListIsNamedByTheAcceptedDecisionRecordItDeclares` |
| Une liste d'arêtes par paire de classes | proposition à valider | design.md, D2 |
| ADR consolidé remplaçant ARCH-0158 | question ouverte | design.md, D1 ; `.claude/rules/adr.md` |

## Impact

- Tests d'architecture : `SqliteStorePackagesArchitectureTest`, `ProductionClassListsArchitectureTest`.
- Gouvernance : une clé ajoutée à `config/debt-counter-ratchets.properties` (justification dans la PR, section
  « Justification d'une modification de gouvernance »).
- ADR : ARCH-0158 à remplacer par un ADR consolidé (à proposer ; numéro qui suit le plus haut attribué, registre et
  archive, vérifié au moment de l'écrire).
- Documentation : `docs/developer/specifications/sqlite-store-packages.md`, `.claude/rules/architecture.md`.
- Ordre du sprint 6 : ce changement suit le sprint 5 de l'audit (`AUD-PRF-21` à `AUD-PRF-26`), qui modifie les mêmes
  fichiers de `morpheus-store-sqlite` ; la liste d'arêtes se mesure sur l'arbre où il s'applique. Le changement
  `page-portfolio-references-in-the-store` ne crée pas d'arête nouvelle (`SqliteOperationRuntime` construit déjà
  `SqlitePortfolioStore`).

## Questions ouvertes

- Forme de la décision et de l'ADR (design.md, D1) : liste d'arêtes sous un ADR consolidé qui remplace ARCH-0158
  (recommandé), réemploi de `RUNTIME_ENTRY_POINTS` comme exception de la règle, ou troisième paquet. À trancher par le
  mainteneur avant l'application.

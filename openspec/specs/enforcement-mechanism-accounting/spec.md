# enforcement-mechanism-accounting Specification

## Purpose
Define what the architecture suite measures of its own enforcement mechanisms, which measured population may only
shrink, what a new textual ban of a type must say, and how a check that reads only documents is told apart from a
check on code.

Documentation durable (vue consolidée, diagrammes, traçabilité) : [docs/developer/specifications/enforcement-mechanism-accounting.md](../../../docs/developer/specifications/enforcement-mechanism-accounting.md).

## Requirements

### Requirement: The textual bans of a type that state no intent SHALL be a measured population that only shrinks

The architecture suite MUST measure, in its own test sources, the assertions that refuse the mention of a type name
in a text and carry no message, by an expression held in the suite and proved on fixtures. The measured count MUST
equal a ceiling declared in the suite. The ceiling MUST NOT be raised, and MUST be lowered in the same change as a
member is removed. No document of the repository restates the ceiling's value.

#### Scenario: A textual ban is added without its intent

- **WHEN** an assertion of the form `assertFalse(source.contains("SomeType"))` is added to a test of the suite
- **THEN** the suite fails, gives the measured count and the ceiling, and says that a textual ban of a type states
  its intent as the assertion's message

#### Scenario: A member is removed and the ceiling is left as it was

- **WHEN** an assertion of the family is deleted or replaced, and the declared ceiling is not lowered
- **THEN** the suite fails and asks for the ceiling to be lowered to the measured count

#### Scenario: The expression is proved in both directions

- **WHEN** the measure is run on a fixture holding a member, the same assertion with a message, a ban of a lower-case
  literal, and a member written inside a comment and inside a string
- **THEN** it counts exactly the first

### Requirement: A new textual ban of a type SHALL state which proposition it enforces

A textual assertion that refuses a type name in a production source MUST carry a message that says what is refused -
the mention of the name, or the dependency the mention stands for - so that its failure reads as a sentence.

#### Scenario: The ban fails

- **WHEN** a textual ban of a type that carries its intent is violated
- **THEN** the failure shows the intent, not only the expected and actual boolean

### Requirement: The architecture suite SHALL publish the composition of its own enforcement by one stated method

Each run of the suite MUST write a proof file whose first line names its method and which gives, each by an
expression held in the suite: the rules evaluated on bytecode, the textual assertions over a production source, the
textual assertions over another artefact, and the two families of textual bans of a type, with and without intent.
The suite MUST NOT fail on any of these counts other than the ceiling above.

#### Scenario: The proof file after a build

- **WHEN** the architecture suite has run
- **THEN** the proof file exists under the module's `target` directory, its first line states the method, and every
  count is present

#### Scenario: A count changes

- **WHEN** documentation checks are added and the count of textual assertions over other artefacts rises
- **THEN** the suite still passes, and the proof file shows the new count

### Requirement: A test class that reads only non-compiled artefacts SHALL be marked as a coherence check and SHALL stay in the gate module

A test class of the architecture suite that evaluates no rule on bytecode, reads no production source and loads no
production class MUST carry the coherence marker, and a class that does any of the three MUST NOT. The property MUST
be derived from the class by the suite, not declared in a list. A marked class MUST remain in
`morpheus-architecture-tests`, so that the executions counted against `architectureTestsMinimum` do not decrease.

#### Scenario: A documentation check without its marker

- **WHEN** a test class that only reads files under `docs/` and `scripts/` carries no coherence marker
- **THEN** the suite fails and names the class

#### Scenario: A marked class starts reading a production source

- **WHEN** a marked test class gains an assertion over a file under a `src/main` directory
- **THEN** the suite fails and names the class, until the marker is removed or the assertion moved

#### Scenario: The build selects the coherence checks

- **WHEN** the suite is run with the coherence marker as its only selected group
- **THEN** only the marked classes run, and at least one does

### Requirement: Le gabarit de pull request demande l'intention de chaque assertion textuelle ajoutée

Le dépôt DOIT (MUST) porter un gabarit de description de pull request, à l'emplacement que GitHub lit, dont une
section demande, pour chaque assertion textuelle ajoutée ou modifiée dans la suite d'architecture, la phrase
d'intention qui la justifie et le mécanisme retenu, assertion textuelle ou règle sur le bytecode. La suite
d'architecture DOIT (MUST) échouer, en nommant le fichier, si le gabarit ou cette section manque.

#### Scenario: Le gabarit est présent avec sa section

- **GIVEN** le gabarit de pull request du dépôt, qui porte la section d'intention
- **WHEN** la suite d'architecture s'exécute
- **THEN** le contrôle du gabarit passe
- **AND** aucun fichier n'est écrit ni modifié par ce contrôle

#### Scenario: La section d'intention est retirée du gabarit

- **GIVEN** un gabarit de pull request dont la section d'intention a été supprimée
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue, nomme le chemin du gabarit et dit quelle section manque
- **AND** le gabarit reste tel qu'il a été édité

#### Scenario: Le gabarit est supprimé

- **GIVEN** un dépôt sans gabarit de pull request
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue et nomme le chemin attendu du gabarit

#### Scenario: Une pull request laisse la section vide

- **GIVEN** une pull request dont la description laisse vide la section d'intention alors qu'elle ajoute une assertion
  textuelle
- **WHEN** la suite d'architecture s'exécute sur sa branche
- **THEN** la suite ne juge pas la description, qu'aucun fichier du dépôt ne porte
- **AND** le refus, s'il a lieu, est celui de la revue

### Requirement: Le gabarit de pull request demande la justification d'une modification de gouvernance

Le gabarit de description de pull request DOIT (MUST) porter une section qui demande la justification de toute
modification d'un fichier de gouvernance : manifeste des surfaces publiques, fichiers de ratchets et de compteurs de
dette sous `config/`, contrats OpenAPI, tests de la suite d'architecture. La suite d'architecture DOIT (MUST) échouer, en
nommant le fichier, si cette section manque.

#### Scenario: La section de gouvernance est retirée du gabarit

- **GIVEN** un gabarit de pull request qui garde la section d'intention mais a perdu celle de gouvernance
- **WHEN** la suite d'architecture s'exécute
- **THEN** elle échoue, nomme le chemin du gabarit et dit que la section de gouvernance manque
- **AND** aucun autre fichier n'est lu ni modifié

#### Scenario: La section nomme les fichiers de gouvernance

- **GIVEN** le gabarit de pull request du dépôt
- **WHEN** la suite d'architecture lit sa section de gouvernance
- **THEN** elle y trouve le manifeste `contracts/public-surfaces.tsv`, les fichiers de ratchets sous `config/`, les
  contrats OpenAPI et la suite d'architecture, et passe

### Requirement: Un échec d'un contrôle de cohérence est rapporté comme une désynchronisation documentaire

À chaque exécution de la suite d'architecture, le build DOIT (MUST) classer chaque test en échec selon le marqueur de
cohérence de sa classe et rendre deux verdicts distincts, sur sa sortie et dans un fichier de preuve du module :
désynchronisation documentaire pour un échec d'une classe marquée, violation d'architecture pour tout autre. Chaque
verdict DOIT (MUST) nommer ses tests et le fichier DOIT (MUST) donner le nombre d'exécutions de chaque nature.

#### Scenario: Seul un contrôle de cohérence échoue

- **GIVEN** une page de `docs/` reformulée de sorte qu'une classe marquée de la suite échoue, le code restant conforme
- **WHEN** la suite d'architecture s'exécute
- **THEN** le build échoue, et son verdict nomme le test en échec comme désynchronisation documentaire
- **AND** le verdict de violation d'architecture est vide, et le fichier de preuve l'écrit

#### Scenario: Une règle sur le code échoue

- **GIVEN** une violation d'une règle sur le bytecode introduite dans une classe de production
- **WHEN** la suite d'architecture s'exécute
- **THEN** le build échoue, et son verdict nomme le test en échec comme violation d'architecture
- **AND** aucun test n'est rangé comme désynchronisation documentaire

#### Scenario: Les deux natures échouent dans la même exécution

- **WHEN** un contrôle de cohérence et une règle sur le code échouent ensemble
- **THEN** les deux verdicts sont rendus, chacun avec ses seuls tests

#### Scenario: Aucun échec

- **WHEN** la suite d'architecture passe
- **THEN** le fichier de preuve est écrit avec deux verdicts vides et un nombre d'exécutions de classes marquées
  supérieur à zéro

### Requirement: Le verdict distinct ne change ni la sélection ni le compte des exécutions

Le verdict DOIT (MUST) être rendu dans l'exécution Surefire unique du module, sans second répertoire de rapports ni
renommage des rapports : chaque classe de la suite s'exécute une fois, ses rapports restent dans le répertoire que
lisent les validateurs de présence, et une invocation qui sélectionne des tests par `-Dtest` ou `-Dgroups` garde le
comportement qu'elle avait.

#### Scenario: Le compte des validateurs de présence

- **GIVEN** la même révision construite avec et sans l'écouteur du verdict
- **WHEN** le compte des exécutions déclarées du module est relevé dans `morpheus-architecture-tests/target/surefire-reports/`
- **THEN** les deux comptes sont égaux
- **AND** aucun rapport n'est écrit dans un autre répertoire

#### Scenario: Une seule classe marquée sélectionnée par -Dtest

- **WHEN** la suite est lancée avec `-Dtest` qui ne désigne qu'une classe marquée
- **THEN** cette classe s'exécute et la commande réussit si ses tests passent
- **AND** le verdict ne porte que ses exécutions

### Requirement: Les interdits textuels de type qui attendent une règle sur le bytecode forment une population qui ne fait que rétrécir

La suite d'architecture DOIT (MUST) compter, par une expression qu'elle tient et prouve sur exemples, ses interdits
textuels d'un nom de type, avec ou sans message, dont la cible est déclarée dans les sources principales d'un module
qu'elle importe, ne déclare aucune constante de compilation non privée, et n'est nommée par aucune de ses classes qui
évaluent des règles sur le bytecode. Ce compte DOIT (MUST) égaler un plafond déclaré, jamais relevé.

#### Scenario: Un interdit textuel est ajouté sur une cible qui permet une règle

- **GIVEN** un type des sources principales d'un module importé, sans constante de compilation non privée, qu'aucune
  classe de règles ne nomme
- **WHEN** un test de la suite ajoute `assertFalse(source.contains("CeType"), "…")`
- **THEN** la suite échoue, donne le compte mesuré, le plafond et les membres par fichier
- **AND** le message dit d'écrire la règle sur le bytecode, l'assertion textuelle pouvant rester à côté

#### Scenario: La cible déclare une constante inlinable

- **GIVEN** un type qui déclare une constante `static final` non privée de type `String` ou primitif
- **WHEN** un test de la suite l'interdit par une assertion textuelle
- **THEN** l'interdit n'est pas compté, car une règle sur le bytecode ne verrait pas l'usage de la constante
- **AND** la suite passe sans que le plafond change

#### Scenario: Une règle est écrite et le plafond est laissé tel quel

- **WHEN** une classe de règles de la suite nomme désormais la cible d'un membre, et le plafond déclaré n'est pas abaissé
- **THEN** la suite échoue et demande d'abaisser le plafond au compte mesuré dans le même changement

#### Scenario: L'expression est prouvée dans les deux sens

- **WHEN** la mesure s'exécute sur des exemples : un membre ; une cible à constante non privée ; une cible à constante
  privée seulement ; une cible hors du dépôt ; une cible nommée par une classe de règles ; un interdit écrit dans un
  commentaire ou une chaîne ; un récepteur qui n'est pas un identifiant (`Files.readString(p).contains(…)`)
- **THEN** elle compte exactement le membre, la cible à constante privée seulement et le récepteur qualifié

### Requirement: Une règle qui remplace ou double un interdit textuel est livrée avec sa violation plantée

Le changement qui ajoute une règle sur le bytecode pour remplacer ou doubler un interdit textuel DOIT (MUST) consigner,
dans ses tâches ou sa pull request, la violation introduite pour la faire échouer, l'échec constaté qui nomme la classe
fautive, et le retour arrière. Une règle qui ne retient aucune classe DOIT (MUST) continuer d'échouer
(`archRule.failOnEmptyShould`).

#### Scenario: Une règle migrée sans trace de violation

- **GIVEN** une pull request qui ajoute une règle sur le bytecode à la place d'un interdit textuel
- **WHEN** ses tâches et sa description ne consignent aucune violation plantée
- **THEN** la revue la refuse ; le build, qui ne lit pas cette trace, ne la refuse pas
- **AND** aucune règle n'est acceptée sur la seule foi d'un build vert

#### Scenario: Une règle migrée qui ne voit aucune classe

- **WHEN** une règle ajoutée par la migration cible un paquet absent de l'ensemble importé
- **THEN** la suite échoue par le garde-fou des règles vides
- **AND** aucune exemption ne permet de la garder

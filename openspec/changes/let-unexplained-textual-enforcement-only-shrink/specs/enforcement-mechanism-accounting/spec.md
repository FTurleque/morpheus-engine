# Spec Delta

## Purpose

Define what the architecture suite measures of its own enforcement mechanisms, which measured population may only
shrink, what a new textual ban of a type must say, and how a check that reads only documents is told apart from a
check on code.

## ADDED Requirements

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

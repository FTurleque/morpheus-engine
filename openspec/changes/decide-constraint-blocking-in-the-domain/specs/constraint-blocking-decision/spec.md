# Spec Delta

## Purpose

Define how MORPHEUS decides that a constraint blocks a lifecycle state, and where that decision is written.

## ADDED Requirements

### Requirement: The domain model SHALL decide whether a constraint blocks a lifecycle state

For a constraint and a target lifecycle state, the evaluation - its state among not applicable, unknown, non-blocking
and blocking, and its reason - MUST be produced by the domain model from the constraint's own applicability, blocking
policy and satisfaction. An application service MUST obtain it from the domain and MUST NOT restate it.

#### Scenario: A violated constraint that blocks the target state

- **WHEN** an applicable, violated constraint whose policy blocks when violated and targets the state is evaluated for
  that state
- **THEN** the evaluation is blocking and its reason names the state

#### Scenario: A policy that does not target the state

- **WHEN** the same constraint is evaluated for a state its policy does not target
- **THEN** the evaluation is non-blocking and its reason says the policy does not target that state

#### Scenario: An application service restates the decision

- **WHEN** a class of the application compares a constraint's applicability, blocking mode or satisfaction to decide
  an outcome
- **THEN** the architecture suite fails and names the class, unless the suite names it as an exception with its reason

### Requirement: Unknown SHALL never be decided as blocking

An evaluation MUST be unknown, and MUST NOT be blocking, whenever the constraint's applicability is unknown, its
blocking mode is unknown, or its satisfaction is unknown for a state its policy targets.

#### Scenario: Unknown satisfaction for a targeted state

- **WHEN** an applicable constraint with a blocking policy targeting the state has an unknown satisfaction
- **THEN** the evaluation is unknown, with a reason that says the satisfaction is unknown

#### Scenario: Every combination

- **WHEN** every combination of applicability, blocking mode, satisfaction and targeted or untargeted state is
  evaluated
- **THEN** none with an unknown input the decision needed is blocking

### Requirement: The answers about one constraint SHALL agree

What the orchestration state reports about a change's constraints - whether their blocking is known, how many are
violated explicit blocking policies - MUST be derived from the same decision as the evaluations, so that a constraint
reported as known has no unknown evaluation, and a constraint with a blocking evaluation is counted.

#### Scenario: A constraint reported as known

- **WHEN** the orchestration state reports that all blocking semantics of a change are explicit
- **THEN** no evaluation of any of its constraints, for any lifecycle state, is unknown

#### Scenario: A blocking evaluation

- **WHEN** a constraint evaluates as blocking for some lifecycle state
- **THEN** the orchestration state counts it among the violated explicit blocking policies

### Requirement: Moving the decision SHALL change no answer

The evaluation states, the reason texts and the orchestration availability texts returned by the CLI, the MCP tools
and the HTTP routes MUST be those returned before the decision moved.

#### Scenario: The same snapshot queried before and after

- **WHEN** the constraint evaluations and the orchestration state of a published change are read after the move
- **THEN** every state, count and reason text equals the one read before

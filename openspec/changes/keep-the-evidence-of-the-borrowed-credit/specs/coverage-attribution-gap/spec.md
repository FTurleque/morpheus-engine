# Spec delta : coverage-attribution-gap

## ADDED Requirements

### Requirement: The evidence of the qualified borrowed measurement SHALL be kept in the repository at the head of the coverage qualification

The repository MUST hold the borrowed-credit summary of each qualifying run under the evidence directory of the coverage
qualification, declared with its platform, run and commit, at least two runs per platform, all at the commit that the
kept evidence of the two coverage scales names. A summary of another scope, or at another commit, MUST fail the build.

#### Scenario: The borrowed evidence outlives the workflow artifact

- **GIVEN** a qualification whose workflow artifacts have expired
- **WHEN** the architecture suite runs
- **THEN** the borrowed-credit summaries the qualified measurement was read from are in the repository, declared in the
  evidence manifest with the scale `borrowed`

#### Scenario: Borrowed evidence at another head is refused

- **GIVEN** kept borrowed-credit summaries naming a commit other than the one of the per-module and aggregate evidence
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the head of each scale and of the borrowed credit

#### Scenario: A coverage summary filed as borrowed evidence is refused

- **GIVEN** a file kept as borrowed evidence whose first line is `coverageScope=aggregate`
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the file

#### Scenario: Incomplete borrowed evidence is refused

- **GIVEN** kept borrowed evidence holding one Linux run and two Windows runs
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the missing platform run

### Requirement: The qualified borrowed measurement SHALL equal the highest measurement of the kept evidence

The qualified borrowed lines and branches the gate holds MUST equal the highest total borrowed lines and the highest
total borrowed branches among the kept borrowed-credit summaries, and the build MUST fail, naming the constant, its value
and the measured value, when they differ.

#### Scenario: A qualified measurement under the kept evidence

- **GIVEN** kept summaries whose highest total is 802 lines while the gate holds 801
- **WHEN** the architecture suite runs
- **THEN** the build fails and names the qualified lines constant, 801 and 802
- **AND** no ratchet file is modified

#### Scenario: A measurement qualified on the lowest platform

- **GIVEN** a qualified branches constant equal to the lowest total of the kept summaries instead of the highest
- **WHEN** the architecture suite runs
- **THEN** the build fails in the same way

#### Scenario: The requalified measurement passes

- **GIVEN** the kept summaries of the qualification in force and the constants requalified on them
- **WHEN** the architecture suite runs
- **THEN** the comparison passes and the maximum stays within its qualified window

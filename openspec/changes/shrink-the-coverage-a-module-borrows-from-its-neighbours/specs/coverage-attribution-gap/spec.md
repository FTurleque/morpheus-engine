# Spec Delta

## Purpose

Define how the build measures the coverage a module owes to tests that live in another module, where that measurement
is recorded, and what keeps it from growing - without moving either coverage scale.

## ADDED Requirements

### Requirement: The gap between the two coverage scales SHALL be measured as borrowed credit, per module

For every module of the common population, the build MUST compute its borrowed credit: the lines, and separately the
branches, that the aggregate report counts as covered for that module and that the module's own report does not. The
gap between the two scales MUST be stated as these counts and their sum over the population, and MUST NOT be stated
or enforced as a difference between a per-module ratio and an aggregate ratio.

#### Scenario: Every module of the population has its row

- **WHEN** the reactor has been built and verified in full
- **THEN** the measurement states, for each module that carries a class under `src/main/java`, its lines and branches,
  what its own report covers, what the aggregate report covers, and the difference

#### Scenario: A module covered only by its own tests borrows nothing

- **WHEN** every covered line of a module is covered by a test of that module
- **THEN** its borrowed credit is zero in lines and in branches

### Requirement: The measurement SHALL be recorded in evidence that names its own scope

The borrowed credit MUST be written to an evidence file distinct from the evidence of the two coverage gates, whose
first line declares a scope that is neither the per-module nor the aggregate one. That file MUST be published with
the integrity evidence of each platform's CI run.

#### Scenario: The evidence cannot be taken for a coverage proof

- **WHEN** a milestone validator is given the borrowed-credit evidence in place of the aggregate coverage evidence
- **THEN** it refuses to conclude, as it does for any evidence whose first line is not the aggregate scope

#### Scenario: The evidence is retrievable for both platforms

- **WHEN** a CI run of the main workflow completes on Linux and on Windows
- **THEN** the integrity artifact of each platform contains the borrowed-credit evidence of that run

### Requirement: The measurement SHALL refuse two reports that do not describe the same code

The measurement MUST refuse to conclude, naming the module and the cause, when a module of the population has no
report on one of the two scales, when a report is older than the sources it measures, when the two reports of a
module do not count the same lines or the same branches, or when a module's own report covers more than the
aggregate report does.

#### Scenario: A negative credit is refused, not clamped

- **WHEN** a module's own report counts more covered lines than the aggregate report counts for that module
- **THEN** the build fails, names the module and says the two reports do not describe the same classes

#### Scenario: A partial reactor is refused under the cause the coverage gates already name

- **WHEN** a module of the population has not been built by the invocation
- **THEN** the build fails, names the module, and says to run the whole reactor

### Requirement: A class credited only by other modules' tests SHALL be named, and the list SHALL only shrink

A class is wholly borrowed when it has executable lines, its module's own report covers none of them and the
aggregate report covers at least one. The build MUST hold the exact list of wholly borrowed classes: a class that
becomes wholly borrowed without being listed MUST fail the build, and a listed class that is no longer wholly
borrowed MUST fail the build until its entry is removed.

#### Scenario: A new class tested only through an adapter is refused

- **WHEN** a class is added to a module with a test in another module and none in its own
- **THEN** the build fails and names the class and the module that owns it

#### Scenario: A listed class that gained a test of its own must leave the list

- **WHEN** a test in the owning module covers a line of a listed class
- **THEN** the build fails until the entry is removed, so the list cannot outlive its reasons

### Requirement: The total borrowed credit SHALL NOT exceed a recorded maximum that is only lowered

The total borrowed credit, in lines and in branches, MUST stay at or under a maximum read from the quality ratchet
configuration under keys that name this quantity. The maximum MUST NOT be raised to make a build pass.

#### Scenario: Credit that grows past the maximum fails the build

- **WHEN** a change makes the total borrowed credit exceed the recorded maximum
- **THEN** the build fails, states the measured and the allowed counts, and names the modules whose credit grew

### Requirement: The maximum SHALL be qualified on the highest measurement of both platforms

The maximum MUST sit above a qualified measurement - the highest reproducible exact-head measurement of Linux and
Windows, cited with its commit and runs where the gate holds it - by a margin sized in lines and in branches against
the observed spread. A maximum under the qualified measurement MUST be refused.

#### Scenario: A maximum one platform cannot meet is refused

- **WHEN** the recorded maximum is set under the qualified measurement
- **THEN** the build fails and says the maximum needs a new measurement on both platforms

### Requirement: The borrowed credit SHALL NOT govern, or be governed by, either coverage scale

The gate of this quantity MUST NOT read a coverage ratchet key, and neither coverage gate may read this quantity's
keys or its evidence. No coverage ratchet and no qualified coverage ceiling may be derived from the borrowed credit,
and the two coverage scales MUST NOT be brought to a common value on account of it.

#### Scenario: The separation is enforced on the three gates

- **WHEN** the gate of this quantity reads a coverage ratchet key, or a coverage gate reads this quantity's key or
  evidence
- **THEN** the architecture suite fails and names the gate and what it read

#### Scenario: Reducing the credit moves no coverage threshold

- **WHEN** tests are added in a module and its borrowed credit falls
- **THEN** the four coverage ratchet keys and the qualified ceilings of both scales are unchanged by that change

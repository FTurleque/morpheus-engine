# coverage-qualification-evidence Specification

## Purpose
Guarantee that a qualified coverage cap is backed by evidence kept with the code — measured on both platforms at one
commit — and that the cap equals what that evidence shows.

## Requirements

### Requirement: The evidence of a qualified cap SHALL be kept in the repository while the cap is in force

For each coverage scale, the repository MUST hold the coverage summaries on which the caps in force were qualified:
the summary of that scale from each qualifying run, each stored with its platform, the run it came from and the commit
it measured.

#### Scenario: The evidence outlives the workflow artifact

- **WHEN** the workflow artifacts of the qualifying runs have expired
- **THEN** the summaries the caps were read from are still in the repository at the commit that holds the caps

#### Scenario: A requalification replaces the evidence in the same change

- **WHEN** a cap is raised
- **THEN** the same change replaces the kept evidence of that scale by the summaries of the new qualification

#### Scenario: A summary of the other scale is refused

- **WHEN** a file kept as evidence of the aggregate scale begins with `coverageScope=per-module`
- **THEN** the build fails and names the file

### Requirement: A qualified cap SHALL equal the lowest measurement of its scale in the kept evidence

The line cap and the branch cap of each scale MUST equal, to the precision the summaries carry, the lowest line ratio
and the lowest branch ratio among the kept summaries of that scale, and the build MUST fail when a cap and its evidence
differ.

#### Scenario: A cap typed above the measurement

- **WHEN** a cap constant is higher than the lowest ratio of its evidence
- **THEN** the build fails and names the constant, its value and the measured value

#### Scenario: A cap qualified on the best platform

- **WHEN** a cap equals the highest ratio of its evidence instead of the lowest
- **THEN** the build fails in the same way

#### Scenario: Evidence replaced without the cap

- **WHEN** the kept summaries are replaced by those of another qualification and the constant is unchanged
- **THEN** the build fails and names the constant that no longer matches

#### Scenario: The caps in force pass

- **WHEN** the comparison runs on the evidence of the qualification in force
- **THEN** it passes for the four caps without any of them changing value

### Requirement: A qualification SHALL be complete before it backs a cap

The kept evidence of a scale MUST hold at least two runs on Linux and two on Windows, all measured at the same commit,
and — where the summary names its population — on the same population; evidence that lacks a platform, mixes commits
or mixes populations MUST fail the build.

#### Scenario: A qualification from one platform

- **WHEN** the evidence of a scale holds no summary from one of the two platforms
- **THEN** the build fails and names the missing platform

#### Scenario: Summaries of two commits

- **WHEN** two kept summaries of one scale were measured at different commits
- **THEN** the build fails and names both commits

#### Scenario: Two populations

- **WHEN** two kept summaries of the aggregate scale name different populations
- **THEN** the build fails and names the difference

### Requirement: The developer guide SHALL distinguish a raise by decision from a requalification without stating a margin

The developer guide to the build MUST state that a ratchet may be raised by decision only while it stays under the cap
of its own scale at the distance the rule requires, that a cap is raised only by a qualification on both platforms
whose evidence is kept, and where the measurement, the cap and the ratchet are read side by side; it MUST NOT state
the remaining distance as a number.

#### Scenario: A developer wants to raise a ratchet

- **WHEN** the guide is read before raising a ratchet
- **THEN** it says which file shows the current measurement, the cap and the ratchet of each scale, and which of the
  two procedures applies in each case

#### Scenario: The guide holds no headroom figure

- **WHEN** the guide is read
- **THEN** it states no number of lines, of branches or of points remaining under a cap

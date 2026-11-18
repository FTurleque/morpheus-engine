## ADDED Requirements

### Requirement: A requirement delta follows the same Markdown boundaries as a current requirement
A requirement under an ADDED or MODIFIED delta section MUST end at the next requirement heading or the next level-two heading outside a fenced code block. A heading inside a fenced code block MUST NOT start, end or split a requirement delta, and the statement of a delta MUST equal the statement the current specification reader gives for the same text.

#### Scenario: A level-two example inside a code fence of a delta
- **GIVEN** a change delta whose ADDED requirement shows `## Overview` inside a fenced code block of its statement, followed by a second requirement
- **WHEN** the provider reads the change
- **THEN** the first delta statement contains the whole code block, opening and closing fences included, and the second requirement is read as its own delta
- **AND** no diagnostic is raised and no requirement is skipped

#### Scenario: A requirement heading shown as an example inside a delta section
- **GIVEN** a delta whose ADDED section contains `### Requirement: Example` inside a fenced code block
- **WHEN** the provider reads the change
- **THEN** no requirement delta titled `Example` is published
- **AND** the requirement that contains the block keeps the block in its statement

#### Scenario: The same text read as a delta and as a current specification
- **GIVEN** a requirement whose statement holds a fenced code block with level-two and level-three headings, written once in a change delta and once in a current specification
- **WHEN** the provider reads both
- **THEN** the delta statement and the current requirement statement are equal

### Requirement: A scenario heading inside a code fence does not start a scenario
In a current specification and in a requirement delta, a `#### Scenario:` line inside a fenced code block MUST NOT start a scenario nor end the statement of its requirement.

#### Scenario: A scenario shown as an example in a statement
- **GIVEN** a requirement whose statement shows `#### Scenario: Example` inside a fenced code block, followed by one real scenario
- **WHEN** the provider reads it as a current specification and as a delta
- **THEN** each reading publishes exactly one scenario, and the statement contains the code block
- **AND** no scenario titled `Example` is published

### Requirement: Reading a line costs time linear in its length
The provider MUST recognize a heading, a scenario step, a design decision or a task with a cost that grows linearly with the length of the line. A line of the maximum document size made of a heading followed by whitespace MUST be read without a super-linear cost, and the title it yields MUST be the trimmed text after the heading marker.

#### Scenario: A requirement heading followed by a long run of spaces
- **GIVEN** a current specification whose requirement heading is `### Requirement: a`, then a run of spaces, then `b`, with runs of two lengths, the second twice the first, up to the maximum document size
- **WHEN** the provider reads each workspace
- **THEN** the time of the longer read is bounded by a linear multiple of the shorter, not by four times it
- **AND** the requirement title is `a`, the spaces, `b`, trimmed at both ends

#### Scenario: A title with trailing spaces keeps its identity
- **GIVEN** a requirement heading `### Requirement: Render headings` followed by trailing spaces
- **WHEN** the provider reads it
- **THEN** the title is `Render headings` and the requirement identity is the one derived from that title without the spaces

## ADDED Requirements

### Requirement: Every scale of a qualification SHALL be measured at the same head

The kept evidence of the aggregate scale and of the per-module scale MUST name one and the same commit. Two scales
qualified at different heads MUST be refused, because the gap between them would add a drift between two commits to
the difference between two ways of crediting a line.

#### Scenario: The scales were qualified at two heads

- **WHEN** the kept per-module evidence names one commit and the kept aggregate evidence another
- **THEN** the build fails and names the head of each scale

#### Scenario: A requalification of one scale only

- **WHEN** the per-module cap is requalified at a new head and the aggregate evidence is left at the previous one
- **THEN** the build fails until the aggregate scale is requalified at the same head

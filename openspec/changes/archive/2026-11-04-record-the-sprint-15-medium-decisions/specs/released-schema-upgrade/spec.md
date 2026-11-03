## ADDED Requirements

### Requirement: The product version SHALL be declared released only at the schema level it ships

When the version of the reactor appears among the declared released levels, its level MUST be exactly the schema
version the code supports. A version above the version of the reactor MUST NOT be declared released. The version in
progress need not be declared until it is released.

#### Scenario: The product version declares an older level

- **WHEN** the reactor carries 1.2.1, the code supports schema 24, and 1.2.1 is declared at level 20
- **THEN** the build fails and names the version, the declared level and the supported schema version

#### Scenario: A version above the reactor is declared

- **WHEN** a version higher than the version of the reactor is declared released
- **THEN** the build fails and names that version and the version of the reactor

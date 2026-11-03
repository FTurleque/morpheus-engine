## ADDED Requirements

### Requirement: The reactor version SHALL follow the schema past the last release

An upgrade guide MUST NOT describe a version the reactor has not opened. Once the supported schema has moved past the
level of the last released version, the reactor MUST carry a higher version than that release, and the latest upgrade
guide MUST be the guide of the reactor's version.

#### Scenario: A migration is added without opening a version

- **WHEN** the last released version is 1.2.1 at schema level 20, the code supports schema 24 and the reactor still
  carries 1.2.1
- **THEN** the build fails and says that the reactor still carries 1.2.1 while the schema moved from level 20 to 24

#### Scenario: A guide is written ahead of the reactor

- **WHEN** the latest upgrade guide describes 1.2.2 and the reactor carries 1.2.1
- **THEN** the build fails and says that the guide describes a version the reactor has not opened

# Spec Delta

## Purpose

Define how the product turns a text into a digest or into a stable key, who is allowed to compute one, and what refuses
a private copy.

## ADDED Requirements

### Requirement: The digest of a text SHALL be computed in one place

Every module of the product that needs the SHA-256 of a text MUST obtain it from one function, which MUST return the
lower-case hexadecimal SHA-256 of the text's UTF-8 bytes. Replacing the existing computations MUST NOT change a source
fingerprint, an excerpt digest, a schema checksum or a lifecycle fingerprint.

#### Scenario: The same text in two providers

- **WHEN** the OpenSpec provider and the Markdown provider digest the same excerpt
- **THEN** they obtain the same value from the same function

#### Scenario: A text outside ASCII

- **WHEN** a text that contains accented and non-Latin characters is digested
- **THEN** the value equals the SHA-256 of its UTF-8 bytes, as a fixed test vector states

#### Scenario: Existing fingerprints

- **WHEN** the provider, store and lifecycle suites run after the replacement
- **THEN** they pass without a change to an expected digest

### Requirement: A platform without the digest SHALL be reported with one wording

The failure raised when the platform does not provide SHA-256 MUST have the same message whichever module asked.

#### Scenario: Two providers on a deficient platform

- **WHEN** the digest is unavailable and two different providers ask for it
- **THEN** both failures carry the same message

### Requirement: Only named classes SHALL ask the platform for a message digest

The architecture suite MUST fail when a class of the product that is not on a named list obtains a message digest from
the platform, and MUST name the class. Each entry of the list MUST state why it does not use the shared function. The
list MUST NOT grow without that statement.

#### Scenario: A reader hashes for itself

- **WHEN** a provider class is given a private method that computes a SHA-256
- **THEN** the architecture suite fails and names the class

#### Scenario: An excused class disappears

- **WHEN** a class on the named list no longer computes a digest
- **THEN** the architecture suite fails until the list is shortened

### Requirement: The OpenSpec provider SHALL derive keys and read lines through one helper each

Inside the OpenSpec provider, the derivation of a stable key from a title, the bounded reading of a source into lines
and the joining of content lines MUST each exist once, and the architecture suite MUST fail when a class of the
provider declares its own.

#### Scenario: A fourth reader is added

- **WHEN** a new reader of the OpenSpec provider declares a private key derivation
- **THEN** the architecture suite fails and names the file and the method

#### Scenario: An unreadable source

- **WHEN** a source cannot be read within its budget
- **THEN** the refusal has the same wording whichever reader asked

### Requirement: Two key derivations SHALL NOT share a name while behaving differently

A provider's key derivation MUST either be the shared one or carry a name and a documented statement of how it
differs. Adopting the shared derivation in a provider MUST NOT change a key derived from an existing fixture.

#### Scenario: An accented title in the synthetic provider

- **WHEN** a synthetic scenario title contains an accented letter
- **THEN** the key it yields is the one the documented derivation of that provider states

#### Scenario: A title that leaves nothing

- **WHEN** a title contains no letter or digit
- **THEN** the derivation refuses it by name or, if the provider's documented derivation admits it, the result is
  stated - it is never an empty key by accident

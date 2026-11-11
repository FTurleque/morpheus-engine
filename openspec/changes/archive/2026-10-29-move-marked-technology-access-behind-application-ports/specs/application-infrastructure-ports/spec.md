# Spec Delta

## Purpose

Define how the application reaches a technology that the architecture record has taken out of it.

## ADDED Requirements

### Requirement: A technology marked as leaving SHALL be reached through a port the application declares

For every class the architecture record marks as leaving, the application MUST declare a port stating what the use
case needs, the technology MUST be used only by an implementation outside `com.morpheus.application`, and the process
MUST wire that implementation explicitly.

#### Scenario: The update manifest is fetched over HTTPS

- **WHEN** `update-check --manifest` is given an `https:` manifest and the record marks outbound HTTP as leaving
- **THEN** the manifest is fetched by a class outside the application, and no class of the application depends on the
  HTTP client

#### Scenario: The record marks nothing

- **WHEN** the record keeps every named class in the application
- **THEN** no port is added and this capability does not apply

### Requirement: What is refused before any I/O SHALL still be refused by the application

A request the use case refuses on its own terms - an insecure or unsupported scheme, a missing argument - MUST be
refused by the application before the port is called, with the refusal it gave before the move.

#### Scenario: An insecure manifest address

- **WHEN** `update-check` is given an `http:` manifest
- **THEN** it is refused with the same named refusal as before, and the port is never called

### Requirement: A move SHALL change nothing a caller observes

The bounds, the refusals, the results and the outputs of a capability whose access moved MUST be those it had before.

#### Scenario: A manifest larger than the bound

- **WHEN** the fetched manifest exceeds the size the application states
- **THEN** the check fails with the same named failure as before the move

#### Scenario: A redirect

- **WHEN** the manifest address answers with a redirect
- **THEN** it is not followed, as before the move

### Requirement: An assertion SHALL follow the code it reads

An architecture assertion that read a moved class for an invariant MUST read the class that carries the invariant
after the move, and the named list of the technology gate MUST lose the class in the same change.

#### Scenario: The invariant is removed at its new place

- **WHEN** the moved invariant is deleted from the class that now carries it
- **THEN** the assertion that guarded it fails

#### Scenario: The name stays on the list

- **WHEN** a class no longer reaches the family and its name is still on the gate's list
- **THEN** the architecture suite fails until the name is removed

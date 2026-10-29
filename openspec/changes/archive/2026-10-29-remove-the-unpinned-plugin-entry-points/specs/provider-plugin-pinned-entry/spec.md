# Spec Delta

## Purpose

Define the entries through which third-party provider code is activated and probed, what they refuse, what they load,
and how the build holds it.

## ADDED Requirements

### Requirement: Provider plugin code SHALL be reachable only through an entry that takes a pin

`ProviderPluginActivator` MUST expose exactly one public way to activate a candidate and `ProviderPluginService`
exactly one public way to probe a plugin, each MUST take the expected SHA-256 pin, and the build MUST fail when another
public overload exists.

#### Scenario: The unpinned overloads are gone

- **WHEN** the two classes are read after this change
- **THEN** neither declares a method without a pin parameter, and neither carries `@Deprecated`

#### Scenario: A new unpinned overload fails the build

- **WHEN** a public `activate` or `probe` overload without a pin is added
- **THEN** the architecture suite fails and names the method

### Requirement: Activation SHALL refuse a missing or malformed pin by name before reading the JAR

Activation MUST refuse a pin that is absent or blank with a refusal stating that a trusted SHA-256 pin is required, and
a pin that is not 64 hexadecimal characters with a refusal stating the expected shape; in both cases no staging copy
and no class loader MUST be created.

#### Scenario: No pin

- **WHEN** activation is called with a blank pin for a compatible candidate
- **THEN** it is refused with a message that names the required SHA-256 pin, and the staging directory holds no new
  file

#### Scenario: A pin of the wrong shape

- **WHEN** activation is called with a pin of 63 characters
- **THEN** it is refused with the message that states the expected shape

#### Scenario: A pin that does not match

- **WHEN** activation is called with a well-formed pin that is not the JAR's digest
- **THEN** it is refused as an integrity mismatch before any plugin class is loaded, as today

### Requirement: Activation SHALL load the verified copy or nothing

The class loader created by activation MUST read the staged, verified copy of the JAR, and no code path MUST name the
candidate's original path as the file to load.

#### Scenario: No fallback to the original file

- **WHEN** the source of `ProviderPluginActivator` is read after this change
- **THEN** the load target is the result of the staging call, and no expression falls back to the candidate's path

### Requirement: The security rule SHALL cite what is enforced

The repository's security rule and its audit command MUST describe the pin requirement by the tests that enforce it,
and MUST NOT cite a message that the activating path does not produce.

#### Scenario: The cited message is one the product says

- **WHEN** the rule page quotes a refusal of `ProviderPluginActivator`
- **THEN** a test of the SDK obtains that refusal from the pinned entry

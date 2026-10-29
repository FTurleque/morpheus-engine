# provider-plugin-pinned-entry Specification

## Purpose
Define the entries through which third-party provider code is activated and probed, what they refuse, what they load,
and how the build holds it.

## Requirements

### Requirement: Provider plugin code SHALL be reachable only through the pinned probe

`ProviderPluginService.probe` MUST be the only public entry to provider plugin code, and it MUST take the expected
SHA-256 pin. `ProviderPluginActivator` and `ProviderPluginActivation` MUST NOT be public, `activate` MUST have exactly
one overload, which takes the pin, and neither class MUST carry `@Deprecated`. The build MUST fail when one of these
holds no longer.

#### Scenario: The activator is not public

- **WHEN** the compiled classes of the SDK are read
- **THEN** `ProviderPluginActivator` and `ProviderPluginActivation` are not public, `ProviderPluginService` declares
  one public `probe`, and `activate` declares a pin parameter

#### Scenario: A public activator fails the build

- **WHEN** `ProviderPluginActivator` is made public, or a `probe` or `activate` overload without a pin is added
- **THEN** the architecture suite fails and names the class or the method

### Requirement: The probe SHALL refuse a missing pin by name before reading the plugin directory

`ProviderPluginService.probe` MUST refuse a pin that is absent or blank with a refusal stating that a trusted SHA-256
pin is required, before it reads the plugin directory.

#### Scenario: Probe without a pin

- **WHEN** the probe is called with a null or blank pin
- **THEN** it is refused with the message `provider plugin probe requires a trusted SHA-256 pin`

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
- **THEN** it is refused as an integrity mismatch before any plugin class is loaded

### Requirement: Activation SHALL load the verified copy or nothing

The class loader created by activation MUST read the staged, verified copy of the JAR, and no code path MUST name the
candidate's original path as the file to load.

#### Scenario: No fallback to the original file

- **WHEN** the source of `ProviderPluginActivator` is read
- **THEN** the load target is the result of the staging call, and no expression falls back to the candidate's path

### Requirement: The security rule SHALL cite what is enforced

The repository's security rule and its audit command MUST describe the pin requirement by the tests that enforce it,
and MUST NOT cite a message that the activating path does not produce.

#### Scenario: The cited message is one the product says

- **WHEN** the rule page quotes a refusal of `ProviderPluginActivator`
- **THEN** a test of the SDK obtains that refusal from the pinned entry

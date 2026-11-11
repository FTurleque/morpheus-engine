# Spec Delta

## Purpose

Define that the product carries neither a public method nothing references nor, on a security path, a branch that a
constructor or a guard makes unreachable, and that the build refuses both.

## ADDED Requirements

### Requirement: A source scan SHALL never follow symbolic links, and no parameter SHALL offer to

`SourceScanPolicy` MUST NOT carry a setting that enables symbolic-link traversal, and `LocalSourceInventoryScanner` MUST
refuse a symbolic root, directory or file unconditionally.

#### Scenario: The flag is gone

- **WHEN** the record and the scanner are read after this change
- **THEN** neither names `followSymbolicLinks` nor `FileVisitOption.FOLLOW_LINKS`

#### Scenario: Links are still refused

- **WHEN** a workspace contains a symbolic directory and a symbolic file
- **THEN** the existing scan tests refuse or skip them exactly as before, unmodified

### Requirement: An MCP peer SHALL be launched from the verified copy of its JAR or not at all

The MINOS and NEXUS gateways MUST launch the staged, verified copy of the configured JAR, and MUST NOT hold a code path
that names the original JAR as the launch target.

#### Scenario: The launch cannot name the unverified JAR

- **WHEN** the two gateways are read after this change
- **THEN** the launch target is obtained by `orElseThrow` on the staged copy, and no expression falls back to the
  configured path

#### Scenario: An unpinned JAR is still never executed

- **WHEN** `MinosUnpinnedExecutionTest#existingUnpinnedJarIsNeverExecutable` and its NEXUS counterpart run
- **THEN** they pass unmodified

### Requirement: A public method of a product module SHALL be referenced or excused by name

Every public method declared in a product module MUST be called by a class of the repository, override a supertype
method, or be listed with the reason it is public without a caller; the published plugin SDK is outside this rule; and
the build MUST fail otherwise.

#### Scenario: The sixteen methods are gone

- **WHEN** the gate runs after this change
- **THEN** none of the sixteen methods of the audit exists, and the gate passes

#### Scenario: A new uncalled public method fails the build

- **WHEN** a public method is added to an application service and nothing calls it
- **THEN** the gate fails and names the class and the method

#### Scenario: The plugin SDK is not judged by MORPHEUS's own calls

- **WHEN** a public method of `com.morpheus.sdk` has no caller in the repository
- **THEN** the gate reports nothing for it

#### Scenario: An excuse without a reason, or for a method that is called, fails

- **WHEN** the list names a method with an empty reason, or a method that now has a caller or no longer exists
- **THEN** the gate fails and names the entry

#### Scenario: The misleading peer environment is gone

- **WHEN** `MinosIntegrationSettings` is read after this change
- **THEN** it has no `processEnvironment` method, as NEXUS never had

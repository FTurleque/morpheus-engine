# refusal-test-evidence Specification

## Purpose
Define what a test asserts when it expects the product to refuse, and how the repository refuses a test that asserts
only the type of the refusal.

## Requirements

### Requirement: A test that expects a refusal typed by the JDK SHALL read the refusal it received

Under every `src/test/java` of the repository, an `assertThrows` whose expected type is not declared by the repository
MUST have its result passed to another call or bound to a variable that the same method names again, and the
architecture suite MUST fail on a call that discards its result or binds it to a variable nobody reads.

#### Scenario: A bare assertion on a generic type is refused

- **WHEN** a test file gains `assertThrows(IllegalArgumentException.class, () -> ...);` as a statement
- **THEN** the architecture suite fails and names the file and the line

#### Scenario: A captured exception nobody reads is refused

- **WHEN** the result is assigned to a variable that does not appear again in the method
- **THEN** the architecture suite fails in the same way

#### Scenario: A refusal whose message is asserted is accepted

- **WHEN** the result is bound and its message or code is compared in the same method
- **THEN** the architecture suite passes for that call

#### Scenario: A type of the project is not concerned

- **WHEN** the expected type is a class declared under a `src/main/java` or `src/test/java` of the repository
- **THEN** the call is not refused by this rule

#### Scenario: A proof that a gate refuses is held to the same rule

- **WHEN** a test of the architecture suite expects `AssertionError` from a guard it feeds a violation
- **THEN** the call is refused unless the test reads what the guard said

### Requirement: The rule SHALL prove that it read the test sources

The rule MUST fail when it finds no test source or no `assertThrows` at all, and MUST treat as generic any expected type
it cannot find among the types the repository declares.

#### Scenario: An empty scan is a failure

- **WHEN** the rule is run on a tree where it reads no test file
- **THEN** it fails instead of passing

#### Scenario: A JDK type the rule never listed is still generic

- **WHEN** a test expects a JDK exception type that appears nowhere else in the repository
- **THEN** the rule applies to that call

### Requirement: The sites that predate the rule SHALL be listed exactly and the list SHALL only shrink

While sites that predate the rule remain, the files that hold them MUST be listed with the number of sites each holds;
the rule MUST fail when a file holds more sites than listed, holds fewer than listed, or is listed and holds none; and
no file of `morpheus-api`, `morpheus-cli` or `morpheus-mcp` MUST appear in the list.

#### Scenario: A new site in a listed file fails

- **WHEN** a listed file gains one more discarded `assertThrows` on a JDK type
- **THEN** the architecture suite fails and names the file, the listed count and the measured count

#### Scenario: A corrected file must leave the list

- **WHEN** every site of a listed file is corrected and its entry is left as it was
- **THEN** the architecture suite fails and names the entry that no longer matches

#### Scenario: A boundary module cannot be listed

- **WHEN** a file of `morpheus-api`, `morpheus-cli` or `morpheus-mcp` is added to the list
- **THEN** the architecture suite fails and names the file

#### Scenario: The list ends

- **WHEN** the last listed file is corrected
- **THEN** the list is removed and the rule applies to every test source without exception

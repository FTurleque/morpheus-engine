# Spec Delta

## Purpose

Define where the product may use reflection, service loading and class loading, that no framework wires it, and that
the repository rule describes exactly that.

## ADDED Requirements

### Requirement: Reflection SHALL be used only by classes named for it

A production class under `com.morpheus..` MUST NOT depend on `java.lang.reflect..` unless it is on a list, held by the
architecture tests, that names it with the reason; and the build MUST fail when a listed class no longer has that
dependency.

#### Scenario: A new reflective class fails the build

- **WHEN** a production class that is not on the list calls a method of `java.lang.reflect`
- **THEN** the architecture suite fails and names the class

#### Scenario: A use spelled without an import is seen

- **WHEN** a production class implements `InvocationHandler` through a lambda, or names a reflection type by its fully
  qualified name
- **THEN** the suite fails in the same way

#### Scenario: A listed class stops using reflection

- **WHEN** a class on the list no longer depends on `java.lang.reflect..`
- **THEN** the suite fails and asks for its removal from the list

### Requirement: Service and class loading SHALL be used only by classes named for it

A production class MUST NOT load a service through `ServiceLoader`, create or hold a class loader, or resolve a class
by name, unless it is on a second list that names it with the decision record that allows it; and the build MUST fail
when a listed class no longer does.

#### Scenario: A new service lookup fails the build

- **WHEN** a production class that is not on the list calls `ServiceLoader.load`
- **THEN** the architecture suite fails and names the class

#### Scenario: Each entry cites its decision

- **WHEN** the list is read
- **THEN** every entry names the ADR that decides the loading it performs

### Requirement: The product SHALL depend on no injection framework

No production class under `com.morpheus..` MUST depend on a dependency-injection framework or on an injection
annotation, and the build MUST fail when one does.

#### Scenario: An injection annotation fails the build

- **WHEN** a production class is annotated with `jakarta.inject.Inject`
- **THEN** the architecture suite fails and names the class

### Requirement: The repository rule SHALL forbid what the build forbids

The rule page that forbids frameworks and reflection MUST state the interdictions as the tests enforce them and MUST
cite the test that holds the named lists instead of restating them.

#### Scenario: The absolute sentence is gone

- **WHEN** the section "Pas de framework, pas de magie" is read after this change
- **THEN** it no longer says that reflection or class-path lookup never occurs, and it names the architecture test
  that lists where they do

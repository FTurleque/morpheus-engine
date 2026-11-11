# Spec Delta

## Purpose

Define what the text of a failure MORPHEUS did not author may tell an HTTP caller, on every router of the HTTP adapter,
and how that stays true of a router added later.

## ADDED Requirements

### Requirement: A router SHALL relay a failure message only through the boundary filter

Every response of `morpheus-api` whose text comes from a `Throwable` MUST obtain that text from
`BoundaryFailureMessage.safe`, and a message that names a server location MUST be replaced by the simple name of the
exception.

#### Scenario: A storage failure under a query route names no path

- **WHEN** a query route fails with an exception whose message contains a server path
- **THEN** the response carries the same status and error code as before, its message is the exception's simple name,
  and the path appears nowhere in the body

#### Scenario: The same holds for policy, policy management and reasoning routes

- **WHEN** the same failure is raised under a policy, a policy-management and a reasoning route
- **THEN** each response replaces the message in the same way

#### Scenario: A message MORPHEUS authored is unchanged

- **WHEN** a route refuses a request with a validation message that names no location
- **THEN** the response carries that message verbatim

#### Scenario: A malformed body names no path either

- **WHEN** the request decoder fails on a body and the failure's message would name a location
- **THEN** the text put in the response is the filtered one

### Requirement: No class of the HTTP adapter SHALL turn a failure into response text by itself

Outside `BoundaryFailureMessage`, a class of `com.morpheus.api` MUST NOT read `Throwable.getMessage()` to build a
response, unless it is listed by name with the reason the message is read, and the build MUST fail otherwise.

#### Scenario: The four private helpers are gone

- **WHEN** the architecture suite reads the routers after this change
- **THEN** none declares a method named `safeMessage`, and the rule on `getMessage` passes

#### Scenario: A new router with its own helper fails the build

- **WHEN** a router is added that formats `failure.getMessage()` into an error envelope
- **THEN** the architecture suite fails and names the class

#### Scenario: An excused read carries its reason

- **WHEN** a class reads a failure message for a purpose other than a response
- **THEN** it passes only because it is listed with a reason, and an entry without a reason, or naming a class that no
  longer reads a message, fails the suite

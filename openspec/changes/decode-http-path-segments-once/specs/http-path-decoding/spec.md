# Spec Delta

## Purpose

Define how the HTTP adapter turns a request path into segments: decoded exactly once, by one function, and identically
for the code that authorizes a request and the code that routes it.

## ADDED Requirements

### Requirement: A path segment SHALL be percent-decoded exactly once and `+` SHALL stay `+`

The HTTP adapter MUST split the raw request path into segments and decode each segment's percent escapes exactly once,
MUST NOT turn `+` into a space in a path, and MUST answer 404 to a path with an empty segment or a malformed escape.

#### Scenario: A doubly encoded dot is not a dot

- **WHEN** a request path contains the segment `%252e%252e`
- **THEN** the segment the router sees is `%2e%2e`, not `..`

#### Scenario: An encoded slash does not create a segment

- **WHEN** a request path contains the segment `a%2Fb`
- **THEN** the router sees one segment, `a/b`, and the number of segments is the number of `/` separators sent

#### Scenario: A plus sign is a plus sign

- **WHEN** a request path contains the segment `a+b`
- **THEN** the router sees `a+b`

#### Scenario: A malformed escape is refused

- **WHEN** a request path contains `%zz` or a truncated escape
- **THEN** the answer is 404 with "invalid API path", as for an empty segment

### Requirement: Authorization and routing SHALL see the same segments

The remote server's role decision and the local server's route selection MUST obtain their segments from the same
function applied to the same raw path.

#### Scenario: Hostile encodings are read identically

- **WHEN** each of a set of paths using `%2e`, `%252e`, `%2F`, `%252F`, `+`, `%2B` and mixed case escapes is given to the
  remote role policy and to the local router
- **THEN** both obtain the same list of segments, or both refuse the path

#### Scenario: A route the policy authorizes is the route that is served

- **WHEN** a request is authorized remotely for a route of the route table and forwarded
- **THEN** the local server selects that same route, with the same path parameters

### Requirement: The adapter SHALL hold one path parser

No class of `com.morpheus.api` other than the path parser MUST split a request path into segments or decode a path
escape, and `URLDecoder` MUST NOT be used on a path.

#### Scenario: The two copies are gone

- **WHEN** the architecture suite reads the policy and query routers after this change
- **THEN** neither contains a segment-splitting loop nor a call to `URLDecoder`

#### Scenario: A new form decoding of a path fails the build

- **WHEN** a class of the adapter calls `URLDecoder.decode` and is not one of the classes listed as decoding query
  parameters, each with its reason
- **THEN** the suite fails and names the class

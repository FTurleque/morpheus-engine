# Spec Delta

## Purpose

Define what the SQLite stores ask the database to filter and to order, and what they do themselves, for the
traceability endpoint reads and the source archive listing.

## ADDED Requirements

### Requirement: The SQLite traceability store SHALL apply a relation filter in the database

When the outgoing or incoming links of an endpoint are read with a non-empty set of relation types, the SQLite store
MUST restrict the statement to those types with bound parameters, and MUST NOT read the links of the other types in
order to discard them. An empty set MUST keep meaning every type.

#### Scenario: Only the links of the requested types are returned

- **WHEN** an endpoint has outgoing links of three relation types in a snapshot and the read names two of them
- **THEN** the links of those two types are returned, ordered by link id, and none of the third

#### Scenario: The empty filter returns every link

- **WHEN** the same endpoint is read with an empty set of relation types
- **THEN** every link of the endpoint in that snapshot is returned, ordered by link id

#### Scenario: A type the endpoint has no link of

- **WHEN** the read names only a relation type no link of the endpoint carries
- **THEN** the result is empty and no failure is raised

#### Scenario: Both directions and both stores agree

- **WHEN** the same links are stored in the memory store and in the SQLite store, and the outgoing and the incoming
  reads are made with the empty set, one type, several types and every type
- **THEN** the two stores return the same links in the same order for each read

#### Scenario: A link of another snapshot is not returned

- **WHEN** a link of a requested type exists for the endpoint in another snapshot only
- **THEN** it is not returned

#### Scenario: The database searches on the relation type

- **WHEN** the query plan of the filtered statement is read through the product's driver
- **THEN** the search on the endpoint index names `relation_type` among its terms

#### Scenario: The statement does not depend on a value

- **WHEN** two reads name different relation types in sets of the same size
- **THEN** they execute the same statement text with different bound values

### Requirement: The source archives of a project SHALL be returned by instant, then path, then reason

A sync state store SHALL return the source archives of a project in chronological order of their archive instant, then
by source path, then by reason, the same on every read; the memory and SQLite stores SHALL apply the same order.

#### Scenario: Timestamps whose text forms sort differently from time

- **WHEN** archives of one project are recorded with instants `T…:00Z`, then `T…:00.500Z`, then `T…:00.500100Z`
- **THEN** each store returns them in that order

#### Scenario: Archives sharing one instant

- **WHEN** several archives of one project share an instant
- **THEN** each store returns them ordered by source path, then by reason, and returns the same order on every read

#### Scenario: An existing database keeps its archives

- **WHEN** a database whose archives were written before this change is opened
- **THEN** they are returned in chronological order and none is lost or rewritten, since the stored format does not
  change

### Requirement: A store MUST NOT ask the database for an order it then replaces

A read whose result the store orders itself, because the stored text of its ordering column does not sort as the value
does, MUST NOT also carry an `ORDER BY` in its statement, and MUST state the reason beside the statement.

#### Scenario: The archive listing carries no SQL order

- **WHEN** the statement of the archive listing of `SqliteSyncStateStore` is read
- **THEN** it contains no `ORDER BY`, and the comment above the method says that the archive instant is stored as a
  text that does not sort by time

#### Scenario: The other statements of the store are not concerned

- **WHEN** another statement of the same class orders by a column whose text order is its value order
- **THEN** the assertion reports nothing

#### Scenario: Putting the clause back fails the build

- **WHEN** an `ORDER BY` is added to the archive listing while the store still sorts its result
- **THEN** the build fails and names the statement

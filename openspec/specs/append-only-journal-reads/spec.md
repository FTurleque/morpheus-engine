# append-only-journal-reads Specification

## Purpose

Define how a journal that only grows is read, by whom, and what the product keeps of it.

## Requirements

### Requirement: The policy audit SHALL be read from the store one page at a time

A policy pack store MUST return the audit of a pack as a page - the records between an offset and a limit, in the
order the capability `policy-audit-order` defines, with the total number of records of the pack - and MUST NOT offer a
read of the whole journal. The SQLite store MUST NOT materialise more records than the page holds.

#### Scenario: A page of a long journal

- **WHEN** a pack has many more audit records than a page and the first page is read
- **THEN** the store returns exactly the oldest records up to the limit, the total of the pack, and has built no record
  beyond the page

#### Scenario: Pages put end to end are the journal

- **WHEN** every page of a pack's audit is read in turn, on a journal whose timestamps have fractions of different
  lengths on either side of a page boundary and several records share one instant
- **THEN** the pages concatenated are the journal in chronological order then by identity, with no record repeated or
  missing, and the memory and SQLite stores return the same pages

#### Scenario: An offset past the end

- **WHEN** a page is asked at an offset greater than the number of records
- **THEN** the store returns no record and the same total

#### Scenario: An existing database is read in the same order

- **WHEN** a database whose audit was written before this change is opened
- **THEN** its pages put end to end are the records it held, in chronological order, and the stored text of each
  instant is unchanged

### Requirement: Every transport SHALL page the policy audit with one vocabulary and one set of bounds

The HTTP route, the CLI command and the MCP tool that return the audit of a policy pack MUST each accept an offset and
a limit, MUST answer with the paginated response of ADR-0151, and MUST apply the default and the maximum that the
adapter's other paged reads apply; the OpenAPI document MUST declare both parameters with their bounds and the shape
of the answer.

#### Scenario: HTTP answers with a page

- **WHEN** `GET /api/v1/policy-packs/{id}/audit` is called without parameters on a pack with more records than the
  default limit
- **THEN** the answer carries `offset`, `limit`, `totalMatches`, `hasMore` set to true and the first `items`, and a
  second call with the next offset returns the records that follow

#### Scenario: The three transports agree

- **WHEN** the same page of the same pack is asked through HTTP, the CLI and MCP
- **THEN** the three answers hold the same records in the same order and the same total

#### Scenario: A limit out of range is refused before the journal is read

- **WHEN** the route is called with a limit of zero, a limit above the maximum, or an unknown query parameter
- **THEN** it answers `400` by name and no audit record has been read

#### Scenario: An unknown pack is still unknown

- **WHEN** the audit of a pack that does not exist is asked
- **THEN** the answer is the refusal it was before this change, not an empty page

#### Scenario: A long journal stays readable through the remote server

- **WHEN** a journal whose whole text would exceed the remote proxy's answer bound is read through the remote server,
  one page at a time
- **THEN** every page is answered and none is refused as too large

### Requirement: A journal SHALL NOT be deleted from without a recorded decision

No statement of the product's sources MUST delete rows from the policy audit, the lifecycle mutation audit or the
source archive journal, and a decision record MUST state, for each, that it is kept and what would reopen the
question.

#### Scenario: A purge slipped into a store is refused

- **WHEN** a `DELETE` on one of the three tables is added to the main sources
- **THEN** the architecture suite fails and names the file and the table

#### Scenario: The decision can be read

- **WHEN** the decision record is read
- **THEN** it names the three journals, says each is kept for the life of the database, and says which of them the
  product reads

### Requirement: The lifecycle mutation audit and the source archives SHALL be read from the store one page at a time

A lifecycle mutation store MUST return the audit of a change, and a synchronisation state store the archives of a
project, as a page: the records between an offset and a limit, in the journal's total order, with the journal's total.
Neither store MUST offer a whole journal, and the SQLite store MUST NOT build more records than the page holds.

#### Scenario: Each journal has a total order

- **WHEN** a page of either journal is read
- **THEN** lifecycle audit records come by revision then mutation identity, and archives by instant, path, reason, then
  fingerprint, and the SQLite store sorts nothing after the read

#### Scenario: A page of a long journal

- **WHEN** a change has more lifecycle audit records, or a project more archives, than a page and the first page is
  read
- **THEN** the store returns exactly the first records of the order up to the limit, the total, and whether more follow

#### Scenario: Archive pages put end to end are the journal in time order

- **WHEN** every page of a project's archives is read in turn, on archives whose instants have fractions of different
  lengths on either side of a page boundary and several archives share one instant
- **THEN** the pages concatenated are the archives in the order of the archive record, with no record repeated or
  missing, and the memory and SQLite stores return the same pages

#### Scenario: An offset past the end

- **WHEN** a page is asked at an offset greater than the number of records
- **THEN** the store returns no record and the same total

#### Scenario: Existing archives are read in the same order

- **WHEN** a database whose archives were written before the order key existed is opened
- **THEN** its archive pages put end to end are the records it held, in time order, and the stored text of each
  instant is unchanged

### Requirement: No journal store port SHALL offer a whole journal

No method declared by a journal store port, or by a class implementing one, MUST return a collection of journal
records; a method that returns a page of journal records MUST take a page request.

#### Scenario: An unbounded journal read added to a port is refused

- **WHEN** a method that returns every policy audit record, lifecycle audit record or archive record is declared on a
  journal store port or one of its implementations
- **THEN** the architecture suite fails and names the method

#### Scenario: The rule sees what it judges

- **WHEN** the rule is evaluated
- **THEN** it has judged the paged read of each of the three journals on its port and on both stores, and fails rather
  than passes if it judged none of them

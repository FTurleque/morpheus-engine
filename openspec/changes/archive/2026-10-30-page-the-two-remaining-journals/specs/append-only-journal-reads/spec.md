# Spec Delta

## ADDED Requirements

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

## REMOVED Requirements

### Requirement: A whole journal SHALL NOT be readable from production code

**Reason**: the two reads it fenced are paged at the store; a port no longer offers a whole journal, so there is no
call left to refuse.

**Migration**: "No journal store port SHALL offer a whole journal" holds the ports instead of their callers.

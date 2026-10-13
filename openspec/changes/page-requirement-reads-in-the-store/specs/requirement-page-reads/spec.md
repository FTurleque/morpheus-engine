# Spec Delta

## Purpose

Define how a page of the requirements of a published snapshot is read: who cuts the page, in what order, with what
total, and identically on every store.

## ADDED Requirements

### Requirement: A page of requirements SHALL be cut by the store port

The requirement store port MUST offer a read that takes a snapshot, the criteria of the read, a declared order, an
offset and a limit, and returns the requirements of that page together with the number of requirements the criteria
match. The application services that return a page of requirements MUST obtain it from that read, and MUST NOT load
every CURRENT requirement of the snapshot to return part of them. An adapter MUST NOT cut a page of requirements
itself.

#### Scenario: A search page is not built from the whole snapshot

- **WHEN** a requirement search asks for one page of a snapshot that holds more requirements than the page
- **THEN** the store hands the application at most the page's limit of requirements, and the total it counted

#### Scenario: The requirements of one published version are paged by the application

- **WHEN** the requirements of a published snapshot are requested over HTTP with an offset and a limit
- **THEN** the page is the one the port returned, and the HTTP adapter does not slice a longer list

#### Scenario: A count is asked as a count

- **WHEN** a surface reports how many CURRENT requirements a snapshot holds
- **THEN** it obtains that number from the port without receiving the requirements

### Requirement: Moving the page SHALL NOT change what a paged read returns

For every query, specification, offset and limit, the three paged requirement reads MUST return the same items in the
same order, the same total and the same indication that more remain as before the page moved. A term MUST match
without regard to case for every letter the application's normalisation folds, not only ASCII letters; a character
that is a wildcard in SQL MUST match only itself; and every term MUST be found for a requirement to match.

#### Scenario: An accented upper-case term

- **WHEN** a requirement's title holds an accented letter in upper case and the query holds it in lower case
- **THEN** the requirement matches on both stores

#### Scenario: A percent sign is a percent sign

- **WHEN** the query term is `%`
- **THEN** only the requirements whose key, title or statement contain that character match

#### Scenario: An offset beyond the total

- **WHEN** the offset is greater than or equal to the number of matches
- **THEN** the page is empty, the total is the number of matches and nothing more is announced

#### Scenario: The public contract is untouched

- **WHEN** the change is built
- **THEN** the convergence gates pass with no edit to the surface manifest or to an OpenAPI file

### Requirement: The order of a requirement page SHALL be declared and total

The port MUST return a page in an order the caller names, and that order MUST be total: two requirements that the
named key does not separate are ordered by a second, unique key. Two successive pages of the same read over the same
snapshot MUST NOT repeat or skip a requirement.

#### Scenario: The pages of a read partition its matches

- **WHEN** every page of one read is requested in turn with the same limit
- **THEN** the pages put end to end are the matches, each once, in the declared order

#### Scenario: The store's order is the application's order

- **WHEN** the same read is made on the memory store and on the SQLite store over identities chosen to tell a textual
  order from the domain's order
- **THEN** both return the same sequence

### Requirement: Every shipped store SHALL answer a requirement page identically

The memory store and the SQLite store MUST return the same items, order and total for the same read, and each MUST
implement the paged read itself rather than inherit an answer computed from the whole population.

#### Scenario: Parity over the difficult fixture

- **WHEN** the parity suite runs the paged read on both stores over requirements with accented and non-Latin text,
  SQL wildcard characters, a missing key and a last partial page
- **THEN** every comparison is equal

#### Scenario: A shipped store without its own paged read is refused

- **WHEN** a store of the product stops declaring the paged read and falls back to the port's default
- **THEN** the architecture suite fails and names the store

### Requirement: A page and its total SHALL describe the same state

The items and the total returned by one paged read MUST be computed from the same state of the snapshot. A read of a
snapshot that is not published MUST keep its named refusal.

#### Scenario: The total agrees with the pages

- **WHEN** a paged read returns a total
- **THEN** requesting every page of that read returns exactly that many requirements

#### Scenario: A snapshot that is not published

- **WHEN** a requirement search names a snapshot that is neither ACTIVE nor RETIRED
- **THEN** it is refused with the refusal that names the snapshot and its state, as before

### Requirement: A reader of every CURRENT requirement SHALL be named with its reason

The classes that load the whole CURRENT population of a snapshot MUST be listed by name with the reason each needs
every requirement. A class that calls the unbounded read without being listed MUST fail the build, and a listed
class that no longer calls it MUST fail the build until it is removed from the list.

#### Scenario: A new unbounded reader is refused

- **WHEN** a class that is not listed calls the unbounded read of CURRENT requirements
- **THEN** the architecture suite fails, names the class and says to page, to count or to state its reason

#### Scenario: The paged services are not in the list

- **WHEN** the list is read after the change
- **THEN** it names neither the requirement search, nor the specification context, nor the historical requirement
  read, nor the MCP tool service

### Requirement: The cost of a deep page SHALL be held by a performance budget that runs

The requirement query budget MUST measure, over the large deterministic fixture, the first page, the last page and a
query that matches nothing, in the lane that runs the performance budgets on both platforms. The budget MUST NOT be
raised by this change.

#### Scenario: The last page is measured

- **WHEN** the performance lane runs
- **THEN** it reports a measurement for the last page of the fixture's query and fails if it exceeds the declared
  budget

#### Scenario: The budget is not weakened

- **WHEN** the change is reviewed
- **THEN** the declared budget of the requirement query gate is equal to or lower than before

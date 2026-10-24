# sqlite-index-coverage Specification

## Purpose
Define which indexes the migrated SQLite schema may hold, which reads of the store must be served by an index in the
order they return, and how the build proves both.

## Requirements

### Requirement: The migrated schema SHALL hold no index that repeats the leading columns of another

After the store has applied its migrations, no index created by a `CREATE INDEX` statement, that is neither unique nor
partial, SHALL have a column list - names, collations and directions, in order - equal to the leading part of the
column list of another non-partial index of the same table, whether that other index was created by a statement or by
a `PRIMARY KEY` or `UNIQUE` constraint.

#### Scenario: A new database holds no repeated index

- **WHEN** the store is opened on a new database file and the indexes of every table are compared
- **THEN** no index repeats the leading columns of another, and the check names no index

#### Scenario: An upgraded database loses the repeated indexes and nothing else

- **WHEN** a database at the schema version of the published 1.2.0, holding rows in every table that carried a repeated
  index, is opened by the store
- **THEN** no index repeats the leading columns of another, every row is still present, and every index that carried
  other columns or another order - among them `idx_snapshot_constraints_change`, `idx_snapshot_acceptance_requirement`
  and `idx_portfolio_memberships_project` - still exists

#### Scenario: A later migration that adds a repeated index fails the build

- **WHEN** a migration creates an index whose columns are the leading part of the primary key of its table
- **THEN** the store's schema test fails and names the index, its table and the index it repeats

#### Scenario: An index that shares its leading column with a partial index is not reported

- **WHEN** the check meets `idx_requirement_versions_snapshot`, whose only column also leads a partial unique index
- **THEN** it reports nothing, because a partial index does not serve the rows outside its predicate

#### Scenario: The rule holds no count

- **WHEN** the schema test is read
- **THEN** it contains no number of indexes expected, dropped or kept; it derives the comparison from the database

### Requirement: Removing a repeated index MUST NOT change what the store accepts, refuses or returns

Every constraint the schema enforced before the removal MUST still be enforced after it, and every read of a table that
lost an index MUST return the same rows in the same order.

#### Scenario: Uniqueness is still refused

- **WHEN** a second row with the same primary key, or the same `(pack_id, version_number)`, is inserted in a table that
  lost a repeated index
- **THEN** the insert is refused as before

#### Scenario: A child row without its parent is still refused

- **WHEN** a row is inserted in a table that lost a repeated index and its foreign key names no existing parent
- **THEN** the insert is refused as before

#### Scenario: The persistence parity tests need no edit

- **WHEN** the persistence parity tests of the memory and SQLite stores run after the migration
- **THEN** they pass unmodified

#### Scenario: A read that used a repeated index is still served by an index

- **WHEN** the query plan of a statement that chose a repeated index before the migration is read after it
- **THEN** the plan searches the constraint's index with the same terms, and shows neither a full scan of the table nor
  a temporary sort it did not show before

### Requirement: The CURRENT requirement reads SHALL be served in entity-version order by an index

The bounded and the unbounded read of the CURRENT requirement versions of a snapshot MUST return rows in entity-version
order, as the port states, and the store MUST obtain that order from an index: the bounded read MUST NOT read or sort
the CURRENT rows it does not return.

#### Scenario: The plan of the bounded read has no temporary sort

- **WHEN** `EXPLAIN QUERY PLAN` is run through the product's driver on the statement the bounded read executes
- **THEN** no line of the plan contains `USE TEMP B-TREE` and none starts with `SCAN`

#### Scenario: The order and the rows are unchanged

- **WHEN** the bounded read is asked for `n` rows of a snapshot that holds more CURRENT versions, and versions in other
  temporal states
- **THEN** it returns the `n` CURRENT versions with the lowest entity version ids, in ascending order, identical to
  what the memory store returns for the same content

#### Scenario: The unbounded read shares the index

- **WHEN** the plan of the unbounded CURRENT read is read
- **THEN** it shows no temporary sort either

### Requirement: The saved-view listing of a scope SHALL be served in its order by an index

The listing of the saved views of a scope MUST return them by name without regard to ASCII case, then by id, and the
store MUST obtain that order from the index that serves the scope filter.

#### Scenario: The plan of the listing has no temporary sort

- **WHEN** `EXPLAIN QUERY PLAN` is run through the product's driver on the statement the listing executes
- **THEN** no line of the plan contains `USE TEMP B-TREE`

#### Scenario: The order is unchanged

- **WHEN** a scope holds views named `beta`, `Alpha` and `alpha`
- **THEN** the listing returns the two `alpha` views first, ordered between themselves by id, then `beta`, as before
  the migration

#### Scenario: The budget check is still answered from the index

- **WHEN** the plan of the count that enforces the per-scope saved-view budget is read
- **THEN** it searches a covering index on the scope and does not scan the table

### Requirement: A guarded read MUST fail the build when its plan gains a table scan or a temporary sort

The store module SHALL keep a set of read statements under guard; for each, the build MUST run `EXPLAIN QUERY PLAN`
through the product's driver on a migrated database, against the text the store executes, and MUST fail when the plan
contains a full scan of a table or a temporary sort that is not listed for that statement with a reason.

#### Scenario: The guard explains the statement the store runs

- **WHEN** the text of a guarded statement is changed in the store
- **THEN** the guard explains the changed text without any edit of the test

#### Scenario: Dropping a needed index fails the build

- **WHEN** the index that serves the bounded CURRENT read is removed from the migration
- **THEN** the guard fails, names the statement and shows the plan line that carries the temporary sort

#### Scenario: An excused sort carries its reason

- **WHEN** a guarded statement is listed as allowed to sort
- **THEN** its entry states why, and an entry without a reason fails the build

#### Scenario: A stale excuse fails the build

- **WHEN** a statement listed as allowed to sort no longer sorts
- **THEN** the guard fails and asks for the entry to be removed

#### Scenario: The guard does not compare plan texts

- **WHEN** a new version of the driver words a plan line differently without adding a scan or a sort
- **THEN** the guard still passes

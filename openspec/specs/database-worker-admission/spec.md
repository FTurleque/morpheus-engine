# database-worker-admission Specification

## Purpose
Define how the HTTP adapter admits database work onto its bounded workers, how long a caller waits, and how it is
refused, for the local server and for the backup the remote facade serves (ADR-0159).

## Requirements

### Requirement: Database work SHALL wait for a worker only within a bounded admission

A request that needs the database MUST take one of a fixed number of workers, the same on every machine, admitted in
arrival order. A request that finds no worker within the admission wait MUST be refused by name before any of its work
runs: `429 LOCAL_SERVER_BUSY` on the local server, `429 DATABASE_WORKERS_BUSY` on the remote backup.

#### Scenario: Every worker stays taken

- **WHEN** every worker is held beyond the admission wait and another backup is requested on the remote facade
- **THEN** it is answered `429 DATABASE_WORKERS_BUSY` and its work never reaches a worker

### Requirement: The remote backup SHALL run on a worker after authorization and within its own deadline

The remote facade MUST hand its backup to a worker only after authentication, the administrator role and the
privileged lane, and MUST NOT put its relayed requests on the workers. A backup still running at its deadline MUST be
answered `504 BACKUP_TIMEOUT`, its outcome unknown, and its worker MUST keep its place until the backup returns.

#### Scenario: A backup outlives its deadline

- **WHEN** a remote backup is still running at its deadline
- **THEN** the caller receives `504 BACKUP_TIMEOUT`, and the next database call waits for that worker until the backup
  has returned

#### Scenario: The server keeps answering while backups wait on the database

- **WHEN** twenty backups wait on a busy database
- **THEN** a status probe of the remote facade is still answered

### Requirement: Only the workers SHALL run the SQLite work of the HTTP adapter

Every call to the SQLite maintenance of the HTTP adapter, other than taking the server lease at start-up, MUST be a
named unit of database work, and only the workers MUST run it. No other class of the adapter MUST build a pool of
platform threads.

#### Scenario: A backup called on the request's thread

- **WHEN** the remote facade calls the backup directly instead of handing it to a worker
- **THEN** the architecture suite fails and names the calling method

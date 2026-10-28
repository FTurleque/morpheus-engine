## Why

`ReleasedSchemaUpgradeTest` upgrades, for every released schema level, a database that the test itself builds by
replaying the migration scripts of the current tree and filling with rows in the shape of the stores at the tags. That
proves the migrations apply on a schema of that level and that the current stores read rows of the older shape. It
does not prove that a database the released program actually wrote opens: its real indexes, values, NULLs and encodings
were never in the test (audit 2026-10-09, AUD-TST-10, partial). And `validate-r2` accepted the upgrade report as soon
as it held one test, so a level dropped from the run would not have been noticed.

## What Changes

- One database per released schema level, written by the CLI of the first tag that shipped the level (`v1.0.0` for
  12, `v1.1.0` for 15, `v1.2.1` for 20) from the `openspec-basic` fixture of that tag, is kept under
  `morpheus-store-sqlite/src/test/resources/published-databases/level-<n>/` with a `provenance.properties` (version,
  tag, commit, SHA-256) and a README that gives the commands.
- A second factory of `ReleasedSchemaUpgradeTest` upgrades each of them with the current runtime, finds every row the
  released program wrote with the same values (the one value V020 renames is expected renamed), and reads identities
  and history back through the stores.
- A released level without its published database, like one without row shapes, is refused by name; a test proves
  both guards refuse every level above the last released one.
- `validate-r2.sh` and `validate-r2.ps1` read the declared levels and require one case per distinct level from each
  factory of the upgrade report.

## Capabilities

### New Capabilities

### Modified Capabilities

- `released-schema-upgrade`: the upgrade is also executed on databases that released programs wrote, and the gate
  counts one case per declared level.

## Impact

- `morpheus-store-sqlite` test sources and resources; `scripts/validate-r2.*` and `scripts/tests/`;
  `docs/developer/BUILD_AND_TEST.md`, `docs/project/validation/RELEASE_QUALIFICATION.md`; `.gitattributes` (`*.db
  binary`). No production code changes.

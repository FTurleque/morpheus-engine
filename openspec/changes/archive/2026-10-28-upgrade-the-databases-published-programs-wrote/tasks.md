# Tasks

## 1. Produce the databases

- [x] 1.1 Build `v1.0.0`, `v1.1.0` and `v1.2.1` with the local JDK 21 and publish the `openspec-basic` fixture of each
      tag with its own CLI (two synchronizations, a composition; from 1.1.0 a portfolio, a saved view and a policy pack
      updated once). Proof: the README of `published-databases/`, and `schema_migrations` at 12, 15 and 20.
- [x] 1.2 Record tag, commit and SHA-256 per database. Proof: `provenance.properties`, checked by the test.

## 2. The test

- [x] 2.1 Upgrade each published database and compare every row of every pre-existing table on its pre-existing
      columns. Proof: removing the declared V020 rewrite turns levels 12 and 15 red, level 20 stays green.
- [x] 2.2 Read identities and history back through the stores (project, snapshot chain, next specification sequence,
      sync revision, conflicts, policy pack revisions and audit order).
- [x] 2.3 Prove the two guards refuse every level above the last released one (21, 22 and one beyond the runtime).

## 3. The gate

- [x] 3.1 `validate-r2.sh` and `validate-r2.ps1` require one case per declared level from each factory, the count read
      from `released-schema-levels.properties`. Proof: `scripts/tests` cases for a factory one level short and for a
      level declared after the build, on both interpreters.

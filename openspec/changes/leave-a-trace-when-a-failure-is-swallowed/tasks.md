# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Decide and measure

- [ ] 1.1 Maintainer: confirm decision 4 (a provider of MORPHEUS's own replacing `slf4j-nop`, `slf4j-api` at compile
      scope in `morpheus-cli`). Proof: the answer, in the pull request and the ADR.
- [ ] 1.2 Maintainer: choose between the file mode and removing the logs directory (decision 6). Default: the file
      mode. Proof: the answer.
- [ ] 1.3 Read every `catch` of `src/main` that does not rethrow and classify it: records, or listed with its reason.
      Proof: the table. On 2026-10-13 a name scan found 38 candidates in eight modules; the audit counted 21 by another
      criterion; the table settles the number.
- [ ] 1.4 Read what the three `System.Logger` users log and where it goes today in each launch mode (CLI, API, MCP
      STDIO, packaged). Proof: the observed output.
- [ ] 1.5 Read `GET /api/v1/metrics` in the OpenAPI files: is the set of counters part of the contract? Proof: the
      schema; it decides whether the manifest and the OpenAPI move.

## 2. Reproduce before fixing

- [ ] 2.1 A test per family of 1.3: provoke the swallowed failure, assert the counter. Proof: red on `HEAD`.
- [ ] 2.2 The textual rule on silent catches with its closed list. Proof: red on `HEAD`, naming the sites of 1.3.
- [ ] 2.3 A test that a record logged through SLF4J at warning level reaches the sink when the mode is active and
      nowhere when it is off; a test that MCP STDIO standard output stays frames only. Proof: red on `HEAD`.
- [ ] 2.4 If the file mode is chosen: write, rotation at the bound, generations kept, permissions, unwritable directory
      refused. Proof: red on `HEAD`.

## 3. The change

- [ ] 3.1 `FAILURE_SUPPRESSED`, the families, the recording at each site of 1.3. Proof: 2.1 and 2.2 pass; the textual
      rules on `application.reasoning` and `application.policy` green unmodified.
- [ ] 3.2 The provider, the removal of `slf4j-nop`, the three `System.Logger` users (decision 5, stated). Proof: 2.3
      passes; `./mvnw dependency:analyze` reports no warning.
- [ ] 3.3 The file mode or the removal of the directory, with the user pages. Proof: 2.4, or the documentation gates.
- [ ] 3.4 The ADR. `docs/developer/OPERABILITY.md`: every family and what a non-zero value means.
- [ ] 3.5 Break each rule once, then revert: a silent `catch`; `slf4j-nop` back on the class path. Proof: two named
      failures.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 M19, since counters are added on store paths: `scripts\validate.cmd m19`. Proof: budgets unchanged.
- [ ] 4.3 The packaged image: one SLF4J provider on the class path; `THIRD-PARTY-NOTICES` follows the dependency
      change. Proof: the listing.
- [ ] 4.4 Release note; pull request description: the finding, the table of 1.3, the two decisions.

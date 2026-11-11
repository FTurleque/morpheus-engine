# Tasks

## 1. Measure before changing

- [x] 1.1 Count the `assertThrows` under `morpheus-*/src/test/java`, those whose result is discarded, and among them
      those whose expected type the repository does not declare, by module and by type. Proof: the table. On 2026-10-13
      at `8b26d3d7`: 850 calls in 552 files, 456 discarded, 329 on a JDK type. The audit counted 831 and 300: trust
      neither, re-measure.
- [x] 1.2 Count what the scan of 1.1 cannot see: a result bound to a variable never named again; `fail()` after a call
      in a `try`; `assertDoesNotThrow`. Proof: the three counts, for the record of what is gated and what is not.
- [x] 1.3 List the project exception types expected by a discarded `assertThrows`, with the number of distinct refusals
      each type carries in the product. Proof: the table, input to open question 1 of the design.

## 2. Reproduce before fixing

- [x] 2.1 Write the textual rule with an empty list. Proof: red on `HEAD`, naming the files of 1.1.
- [x] 2.2 Feed the rule the shapes found in the repository — a call split over two lines, `Assertions.assertThrows`, a
      result passed straight to `assertEquals`, a result returned from a helper, a variable read three lines later, a
      variable never read. Proof: one case per shape in the rule's own tests, each with the verdict expected.
- [x] 2.3 For one boundary refusal, remove the validation the test names and show the bare test still passes. Proof: the
      test green on a product that no longer validates — the defect this change closes, shown once.

## 3. The change

- [x] 3.1 Record the measured files and counts as the list, in the same pull request as 3.2 so that the list never
      ships with a file of a boundary module; the rule passes. Mechanism: textual assertion, intent "an
      `assertThrows` on a JDK type whose result nobody reads must not appear under `src/test/java`" (ADR-0103; skill
      `enforcement-choice`).
- [x] 3.2 Lot 1 — `morpheus-api`, `morpheus-cli`, `morpheus-mcp`: read each refusal in the product and assert its
      message or code. Their files leave the list, and the rule forbids them to return. Proof: 2.3 now fails when the
      validation is removed.
- [x] 3.3 Lot 2 — `morpheus-architecture-tests` and `morpheus-coverage-report`: every proof that a guard refuses reads
      what the guard said.
- [x] 3.4 Following lots, one module or family per pull request: `morpheus-application`, `morpheus-domain`,
      `morpheus-store-sqlite` with `morpheus-store-memory`, `morpheus-mcp-transport`, the providers and the SDK. Each
      lowers the list by exactly what it corrected.
- [x] 3.5 List in each pull request the refusals that carry no message. Do not assert `null`; do not change the product
      here.
- [x] 3.6 Remove the list with its last entry.
      Landed in one change: every lot was corrected before the rule was committed, so no transitional list was ever
      written (3.1 and 3.6 are satisfied by an empty list).
- [x] 3.7 Break the rule once per shape of 2.2, then revert. Proof: the named failures.

## 4. Verification and destinations

- [x] 4.1 `./mvnw clean verify` for each lot. Proof: green; the presence ratchets unchanged — if the count of executions
      moved, a test was added or lost and the lot says which.
- [x] 4.2 `.claude/rules/testing.md`: the sentence of the rule names the test that enforces it and the limit of what it
      proves.
- [x] 4.3 Pull request description of each lot: the finding, the counts before and after, the refusals without a
      message, and the justification of the test added or changed under `morpheus-architecture-tests/`.

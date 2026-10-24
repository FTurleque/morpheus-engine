# Proposal

## Why

AUD-TST-03 (sprint 8) reports that 300 of 831 `assertThrows` assert only a generic JDK exception type, with no message
and no rejection code. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds, with different numbers**:

- A scan of the 552 files under `morpheus-*/src/test/java` finds 850 `assertThrows(<Type>.class` (the audit: 831 in 526
  files).
- 456 of them are a statement: the exception `assertThrows` returns is discarded, so nothing after the call can read a
  message or a code. 329 of those expect a type the repository does not declare — `IllegalArgumentException` 218,
  `NullPointerException` 41, `IOException` 17, `AssertionError` 15, `IllegalStateException` 13, `RuntimeException` 10,
  `SQLException` 10, `UnsupportedOperationException` 4, `TimeoutException` 1 — and 127 expect a type of the project.
- By module, for the 329: `morpheus-api` 89, `morpheus-application` 68, `morpheus-architecture-tests` 39,
  `morpheus-domain` 31, `morpheus-store-sqlite` 28, `morpheus-mcp-transport` 25, `morpheus-cli` 11,
  `morpheus-provider-synthetic` 11, `morpheus-store-memory` 10, the rest 17.
- The audit's example still reads as written: `RemoteIdentityFileStoreTest.java:229-235`, four
  `assertThrows(NullPointerException.class, ...)` in a row.
- The scan counts a discarded result. It does not count a result that is bound to a variable nobody reads; the audit's
  window of "about 400 characters" did not either. Both numbers are floors.

`.claude/rules/testing.md` already states the rule: a refusal is tested by `assertThrows` **and** an assertion on the
message or the rejection code, so that a failure for another reason does not make the test pass. Nothing enforces it.
On `IllegalArgumentException` and `NullPointerException` any other validation of the same method satisfies the
assertion: these tests keep passing after the validation they name is removed. The 39 sites of the architecture tests
are the sharpest case — most of them are the proof that a gate refuses a violation, and `AssertionError` is what every
failed assertion throws.

## What Changes

- A test that expects a refusal typed by the JDK reads the refusal it got: the message, or the code the product gives.
- A textual test over `src/test/java` refuses a new `assertThrows` on a JDK type whose result is discarded or never read.
- The existing sites are corrected by module, the system boundaries first; until a module is done its files are named
  in a list that can only shrink, and the change is complete when the list is empty and removed.

## Capabilities

### New Capabilities

- `refusal-test-evidence`: what a test must assert when it expects a refusal, and how the repository refuses a test that
  asserts only the type.

### Modified Capabilities

(none)

## Impact

- Test sources of every module; no product code is expected to change. Where a boundary refuses with an exception that
  carries no message, the site is listed and reported instead of being given an assertion on `null`.
- One textual architecture test and its shrinking list.
- `.claude/rules/testing.md`: the sentence of the rule names the test that enforces it.
- No ADR, no contract, no public surface. The presence ratchets do not move: the tests are the same tests.
- Independent of the other changes of sprint 8. `remove-unreachable-and-unreferenced-code` (sprint 4) and the duplicated
  parsing base of the CLI (AUD-QUA-06, sprint 9) touch the same test files of `morpheus-cli`; a lot that works on both
  rebases, it does not conflict in intent.

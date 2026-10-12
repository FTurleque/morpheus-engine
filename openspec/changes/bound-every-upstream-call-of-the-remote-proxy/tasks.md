# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Measure the duration of the longest proxied writes on a large fixture (sync, lifecycle transition, policy
      activation). Proof: the durations; they size the writes' timeout.
- [ ] 1.2 Read which routes are WRITE or ADMIN and proxied (the backup and the status are served by the facade). Proof:
      the list.
- [ ] 1.3 Read the remote OpenAPI file: which statuses do write operations declare? Proof: the list; it decides whether
      the contract moves.

## 2. Reproduce before fixing

- [ ] 2.1 An upstream that never answers a WRITE: the facade answers within the timeout and the privileged ceiling
      recovers. Proof: on `HEAD` the request does not end - the test is bounded by its own timeout.
- [ ] 2.2 An oversized response to a WRITE. Proof: red on `HEAD`.
- [ ] 2.3 The code and wording of a timed-out write.

## 3. The change

- [ ] 3.1 The two timeouts, the budget on every response, the code. Proof: 2.1 to 2.3 pass; the existing transport
      tests pass.
- [ ] 3.2 The authorization gives a duration; `usesBoundedUpstreamTimeout` goes. Proof: the textual pins that name it
      are updated with their reason; `.claude/rules/security.md` follows.
- [ ] 3.3 The operator guide: the two durations and what a timed-out write means.
- [ ] 3.4 Break once, then revert: no timeout on writes. Proof: 2.1 fails.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 Release note; pull request description: the finding, the durations of 1.1, the new code.

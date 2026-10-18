# Tasks

**2026-10-17: closed without a change, by decision of the maintainer.** The absence of a deadline on proxied writes stays the accepted residual risk documented in `docs/user/TEAM_REMOTE_SERVER.md`. Nothing below is to be done unless that decision is reopened.

**Stopped on 2026-10-13 before any change, on a decision that is the maintainer's.** Read again for the
implementation, the finding is half wrong and its other half reverses a position the product documents as chosen:

- *The memory budget already covers the writes.* `MorpheusRemoteProxyTransport.forward` takes a response slot for a
  mutation once the upstream has answered (`awaitResponseBudget`), and `copyBounded` applies `maxResponseBytes` to every
  response. What differs from a read is *when* the slot is taken, and
  `MorpheusRemoteProxyResponseBudgetTest#aMutationWaitsForTheBudgetRatherThanBeingRefusedAfterItsCommit` holds that on
  purpose. The proposal's "its response is read without the budget the reads have" is not true of the code.
- *The absence of a deadline on writes is a recorded choice.* `docs/user/TEAM_REMOTE_SERVER.md` states it as a residual
  risk accepted in 1.2.1: a blocked mutation holds its slot until it really ends, a saturated privileged lane refuses
  the next mutations with 429, and "MORPHEUS prefers that explicit refusal to a deadline that would report 504 for a
  commit that may already be durable". The privileged lane is capped at a quarter of the request budget so that
  blocked mutations cannot take the reads with them.

This change would replace that choice with a write timeout and an "outcome unknown" answer. Both positions are
defensible; choosing between them, and choosing the duration, is not an implementation detail. Nothing below is done.

## 1. Measure before changing

- [ ] 1.1 Measure the duration of the longest proxied writes on a large fixture (sync, lifecycle transition, policy
      activation). Proof: the durations; they size the writes' timeout.
      *2026-10-13: not measured. The only figure in the repository is the frozen budget of a full publish on the large fixture, sixty seconds at the 95th percentile (`M19FullPublishPerformanceGate`) - the same sixty seconds the reads already have, which says a write timeout cannot simply reuse it.*
- [ ] 1.2 Read which routes are WRITE or ADMIN and proxied (the backup and the status are served by the facade). Proof:
      the list.
- [x] 1.3 Read the remote OpenAPI file: which statuses do write operations declare? Proof: the list; it decides whether
      the contract moves.
      *2026-10-13: the OpenAPI files declare no error status per operation; the contract would not move.*

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

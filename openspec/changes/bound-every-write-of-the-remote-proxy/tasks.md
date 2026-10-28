# Tasks

Reopened and implemented on 2026-10-28, at the maintainer's request, after the decision of 2026-10-17 that closed
AUD-PRF-09 without a change. Each task carries what was done and what was not.

## 1. Measure before changing

- [ ] 1.1 Measure the duration of the longest proxied writes on a large fixture (sync, lifecycle transition, policy
      activation). Proof: the durations.
      *2026-10-28: **not measured.** The write deadline is sized on the only duration the repository states, the
      frozen sixty-second budget of a full publish on the large fixture (`M19FullPublishPerformanceGate`), five times
      over (design, decision 1).*
- [x] 1.2 Read which routes are WRITE or ADMIN and proxied. Proof: the list.
      *2026-10-28: every route of `MorpheusHttpRouteTable` whose role is WRITE or ADMIN, except `server/status` and
      `server/backups`, which the facade serves itself; `MorpheusRemoteRoutePolicyTest#onEveryRouteOfTheTableEveryRoleHasTheUpstreamDeadlineOfItsRole`
      walks them all.*
- [x] 1.3 Read the remote OpenAPI file: which statuses do write operations declare? Proof: the list.
      *2026-10-13 (archived change): the OpenAPI files declare no error status per operation; the contract does not
      move.*

## 2. Reproduce before fixing

- [x] 2.1 An upstream that never answers a WRITE: the facade answers within the timeout and the privileged ceiling
      recovers. Proof: on `HEAD` the request does not end.
      *2026-10-28: on `develop` at `5c3ce4dd`, a WRITE forwarded as the server forwards it (`usesBoundedUpstreamTimeout()`
      false) to an upstream that never answers is still waiting when the test gives up after 10 s
      (`TimeoutException`), WSL Ubuntu, JDK 21. After the change:
      `MorpheusRemoteProxyResponseBudgetTest#aWriteTheUpstreamNeverAnswersEndsAtItsDeadlineAsAnUnknownOutcome` and
      `#asManyStuckWritesAsThePrivilegedCeilingEndAndTheCeilingAdmitsTheNext`, with an injected deadline of 300 ms and
      2 s. **Departure**: the ceiling is proved at the transport, through the same take, forward, give back sequence as
      `serve`, not through a running remote server - the server's deadline is a constant, and injecting one there would
      add a constructor parameter for a test.*
- [x] 2.2 An oversized response to a WRITE. Proof: red on `HEAD`.
      *2026-10-28: **not red on `HEAD`**: `requireBoundedLength` already applied to every response.
      `#anOversizedResponseToAWriteIsRefusedByNameAndItsSlotComesBack` pins it.*
- [x] 2.3 The code and wording of a timed-out write.
      *2026-10-28: the first test of 2.1 asserts status, code and exact message;
      `#aReadTheUpstreamNeverAnswersKeepsTheReadsCodeAndGivesItsSlotBack` keeps the read's code apart.*

## 3. The change

- [x] 3.1 The two timeouts, the budget on every response, the code. Proof: 2.1 to 2.3 pass; the existing transport
      tests pass.
      *2026-10-28: `MorpheusRemoteProxyTransport.UpstreamDeadline` (`READ` 60 s, `WRITE` 300 s); the budget keeps its
      two moments (design, decision 2), so `aMutationWaitsForTheBudgetRatherThanBeingRefusedAfterItsCommit` stays as it
      is.*
- [x] 3.2 The authorization gives a duration; `usesBoundedUpstreamTimeout` goes. Proof: the textual pins that name it
      are updated with their reason; `.claude/rules/security.md` follows.
      *2026-10-28: `Authorization.upstreamDeadline()`. `MorpheusRemoteRoutePolicyTest#onEveryRouteOfTheTableOnlyTheReadRoleHasAnUpstreamDeadline`,
      which held the decision of 2026-10-17, is replaced by `#onEveryRouteOfTheTableEveryRoleHasTheUpstreamDeadlineOfItsRole`:
      on every route and method of the table, the deadline of its role. `HttpPathReadingArchitectureTest` pins
      `route.upstreamDeadline()`; `RemoteServerArchitectureTest` also requires `UPSTREAM_WRITE_TIMEOUT`.
      `.claude/rules/security.md`, `.claude/agents/security-reviewer.md`, `.claude/commands/security-audit.md` follow.*
- [x] 3.3 The operator guide: the two durations and what a timed-out write means.
      *2026-10-28: `docs/user/TEAM_REMOTE_SERVER.md` (table of deadlines, unknown outcome, permit released on expiry,
      plugin probe), `docs/developer/REMOTE_SERVER_PLATFORM.md`.*
- [x] 3.4 Break once, then revert: no timeout on writes. Proof: 2.1 fails.
      *2026-10-28: with the writes' deadline replaced by a year, the two tests of 2.1 fail after 10 s
      (`TimeoutException`), 2 errors out of 9; reverted.*

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
      *2026-10-28: **not run.** Run in WSL Ubuntu, JDK 21, on `5c3ce4dd` plus this change:
      `./mvnw -pl morpheus-api -am test` green (`morpheus-api`: 242 tests, 0 failure); after `install -DskipTests`,
      `./mvnw test -pl morpheus-architecture-tests`: 825 run, one failure,
      `CoverageQualityGateTest#perModuleCoverageDoesNotRegressBelowQualifiedBaseline`, which refuses a reactor that has
      not just run `clean verify` (7 of 16 per-module reports present). The full `clean verify` and the changed-line
      coverage gate are left to the CI of the pull request.*
- [x] 4.2 Release note; pull request description: the finding, the durations, the new code.
      *2026-10-28: `docs/user/release-notes/RELEASE_NOTES_1.2.2.md`. The pull request description is the
      maintainer's.*

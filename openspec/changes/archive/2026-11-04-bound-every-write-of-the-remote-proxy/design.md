# Design

## Context

- The facade forwards with `java.net.http.HttpClient`; `HttpRequest.timeout` bounds the wait until the response
  headers arrive. The body is then copied under the deadlines of `TimedBoundedResponseWriter` (ADR-0099).
- The local server offers no cancellation: after a timeout the operation may still complete.
- The privileged permit and the request slot are taken in `MorpheusRemoteHttpServer.serve` and given back in its
  `finally`, whatever `forward` ends with.
- A read takes a response slot before the call and is refused with `429 RESPONSE_BUDGET_EXHAUSTED` when none is free;
  a call that may change state takes one once the upstream headers are in, and waits for it
  (`MorpheusRemoteProxyResponseBudgetTest#aMutationWaitsForTheBudgetRatherThanBeingRefusedAfterItsCommit`).

## Decisions

1. **Two deadlines, declared side by side** in `MorpheusRemoteProxyTransport.UpstreamDeadline`: `READ`, sixty seconds,
   and `WRITE`, three hundred seconds. The repository measures no proxied write; the only duration it states for the
   longest work a write can trigger is the frozen budget of a full publish on the large fixture, sixty seconds at the
   95th percentile (`M19FullPublishPerformanceGate`). Three hundred seconds gives it five times over; sixty, the reads'
   deadline, would leave a publish at its budget no margin. The comment on `WRITE` and the operator guide say so.

2. **The budget applies to every response, at two moments, unchanged.** Taking the slot before the call for a write
   too, as the archived decision 2 had it, would let eight slow mutations hold the eight slots for up to five minutes
   with no response bytes at all, and refuse every read meanwhile. Refusing a write that finds no slot once answered
   would report a failure for a commit already made. So a write keeps taking its slot after the headers and waiting
   for it; the wait ends because every slot holder writes under the deadlines of `TimedBoundedResponseWriter`. What the
   finding said about the budget ("read without the budget the reads have") was not true of the code: `copyBounded`
   and `requireBoundedLength` already applied to every response; task 2.2 pins it for a write.

3. **A timed-out write says its outcome is unknown**: `504 UPSTREAM_WRITE_TIMEOUT`, message "local MORPHEUS operation
   exceeded the remote facade's timeout; it may have completed: read its state before retrying". A timed-out read keeps
   `504 UPSTREAM_TIMEOUT`. A client retries a read on its code without thinking, and must not do so for a write.

4. **The permit is released when the facade gives up**, and that is written in the comment of `UpstreamDeadline` and in
   the operator guide: the local operation may still run and hold the database, so the privileged ceiling bounds the
   requests the facade waits for, not the work in progress; the local server's admission control bounds that work.

5. **`usesBoundedUpstreamTimeout` is replaced by `Authorization.upstreamDeadline()`**, which gives `READ` for the READ
   role and `WRITE` for WRITE and ADMIN. The transport stays free of the route policy and of roles
   (`RemoteServerArchitectureTest#remoteProxyTransportStaysExtractedBoundedAndPolicyFree`): it receives the deadline,
   whose `readOnly` flag decides when the budget is taken and which code an expiry carries.

6. **No ADR** (proposal, Impact).

## Risks / Trade-offs

- A legitimate write longer than five minutes now fails at the facade while it succeeds locally. Decision 3's wording is
  the mitigation; the alternative is a thread and a permit held for ever.
- Releasing the permit while the local work continues lets a second write start behind a first that still runs: they
  contend on the database, as two concurrent writes do, and the local admission control bounds it.
- The deadline follows the role, not the method: an ADMIN `GET` (`metrics`) gets the writes' deadline and code. That is
  the behaviour the decision asks for ("by role"); a method-aware choice is an open question for the maintainer.

# Design

## Context

- The remote facade refuses beyond its ceiling at once with `429 TOO_MANY_REQUESTS`; it proxies to the local server
  over loopback, so a remote request is also a local one.
- In remote mode the local server only receives requests the facade already admitted.
- `MorpheusHttpRouteTable` lists the routes; `health`, `readiness` and `metrics` are operational probes.
- The sprint's exit criterion: "a peak of load becomes a queue instead of `SQLITE_BUSY` failures".

## Goals / Non-Goals

**Goals**: a ceiling on concurrent local work; a bounded wait before a refusal; probes that still answer; one place
where the numbers are stated.

**Non-Goals**: fairness between clients; a rate limit; changing the remote facade's ceilings; changing the busy timeout
or the journal mode.

## Decisions

1. **A fair semaphore with a bounded wait, then `429`.** A request beyond the ceiling waits; after the bound it is
   refused with a named code and a `Retry-After`. *Rejected*: refusing at once, as the remote facade does - the exit
   criterion asks for a queue, and a single user's burst (an assistant firing several tools) should wait a moment, not
   fail. *Rejected*: waiting without a bound - a queue with no end is the unavailability this sprint is about.

2. **The ceiling follows the database, not the processor count.** It is derived from what the two changes before this
   one establish - the size of the database work pool if there is one, the measured point where contention begins
   otherwise - and it is stated in one constant with its reason.

3. **Probes are admitted apart.** `health` and `readiness` do not take a permit of the main ceiling: a saturated server
   is alive and must say so. `metrics` reads counters and is admitted apart as well.

4. **In remote mode the two ceilings are stated together.** The local ceiling is not lower than the facade's, or the
   facade's admitted requests would queue twice; a test compares the two numbers.

5. **A contract change, declared.** The local OpenAPI gains `429` on the operations concerned; the pull request
   justifies it, as the governance rule asks.

6. **No ADR of its own**: the decision is recorded in RT-01 and in the ADR of the scheduler change, which sizes it.

## Risks / Trade-offs

- A wait holds a virtual thread and a socket; both are cheap, and the bound caps them.
- A ceiling set too low makes a single user wait for nothing. It is derived, measured, and stated.
- A client that treats `429` as fatal sees a new failure where it used to see a slow success or a `409`; the release
  note says so.

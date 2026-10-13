# Design

## Context

- The facade forwards with `java.net.http.HttpClient`; `HttpRequest.timeout` bounds the wait for the response.
- A timed-out read is answered with the code `UPSTREAM_TIMEOUT`.
- The response slots are a fair semaphore sized by the server's configuration; a slot bounds how many responses are
  buffered at once.
- The longest legitimate writes are a project sync and a backup; the backup is served by the facade itself and does not
  go through the proxy.
- The local server offers no cancellation: after a timeout the operation may still complete.

## Goals / Non-Goals

**Goals**: no call that waits for ever; no response read outside the budget; an answer to a timed-out write that does
not lie about the outcome.

**Non-Goals**: cancelling the local operation (it would need a cancellation protocol across the loopback hop);
changing the reads' timeout; idempotency keys.

## Decisions

1. **Two timeouts, both stated beside each other.** The reads keep sixty seconds. The writes get a longer one, chosen
   from the measured duration of the longest proxied write (a sync of a large workspace) with a margin - not guessed
   here.

2. **The budget applies to every response.** A slot is taken for a write as for a read; when none is free the request
   is refused as the reads already are.

3. **A timed-out write says its outcome is unknown.** The same status as a timed-out read, with a code of its own -
   `UPSTREAM_WRITE_TIMEOUT` - and a message that the operation may have completed and that its state must be read
   before retrying. *Rejected*: reusing `UPSTREAM_TIMEOUT` - a client retries a read on it without thinking, and must
   not do the same for a write.

4. **The permit is released when the facade gives up**, and that is stated: the local operation may still be running
   and still holds the database. The privileged ceiling therefore bounds requests the facade is waiting for, not work
   in progress; the operator guide says so, and the admission control of the local server is what bounds the work.

5. **`usesBoundedUpstreamTimeout` is replaced by the timeout itself.** The authorization gives the duration that applies
   to its role; a boolean that meant "bounded or not" no longer describes anything.

6. **No ADR.**

## Risks / Trade-offs

- A legitimate long write now fails at the facade while succeeding locally. Decision 1's measurement and decision 3's
  wording are the mitigation; the alternative is the stuck thread.
- Releasing the permit while the local work continues lets a second write start behind a first that is still running:
  they contend on the database. That is the present behaviour of two writes anyway, and the local admission control
  bounds it.

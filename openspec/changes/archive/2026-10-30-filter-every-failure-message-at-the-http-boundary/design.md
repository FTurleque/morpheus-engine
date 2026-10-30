# Design

## Context

- `BoundaryFailureMessage.safe(Throwable)` is package-private in `com.morpheus.api` and already used by both servers.
- `ServerLocationDisclosure.isSafeToRelay` (application) is the shared predicate; the remote detail allowlist uses it too.
- The four routers register their own HTTP context (`ROUTERS_REGISTERING_THEIR_OWN_CONTEXT`); they must depend on
  `MorpheusHttpRequestDecoder` and `MorpheusHttpResponseWriter` and on neither Jackson nor the serializer. Nothing in
  that classification speaks of failure text.
- Most messages these routers relay are authored by MORPHEUS (validation refusals, budget refusals) and name no
  location: the filter leaves them untouched.

## Goals / Non-Goals

**Goals**: no router relays a message that names a server location; the rule holds for the next router.

**Non-Goals**: changing status or error codes; scrubbing messages (a partly scrubbed path is still a path - the existing
class replaces, it does not edit); logging the original message (the product has no log yet, AUD-TRV-04, sprint 4).

## Decisions

1. **Reuse `BoundaryFailureMessage.safe`, do not write a fifth filter.** *Rejected*: making each `safeMessage` call the
   predicate itself - four copies of a security decision is how this finding happened.

2. **The decoder's `safeMessage` goes through the same call.** A Jackson message quotes the offending token and a
   location in the document, not in the file system, so the filter will normally pass it; routing it through the filter
   costs nothing and removes the one place a reviewer would have to reason about. *Rejected*: listing the decoder as an
   exception by name.

3. **Enforcement: a rule on the method shape, kept with its textual twin (enforcement-choice).** Intention in one
   sentence: "inside `com.morpheus.api`, only `BoundaryFailureMessage` reads `Throwable.getMessage()` to build a
   response". An ArchUnit rule can state it on calls to `Throwable.getMessage` from classes of the package other than
   `BoundaryFailureMessage`, with named exceptions where a message is read for another purpose (to be listed by the
   implementation, each with a reason). The textual twin forbids the method name `safeMessage` in the routers. Both are
   kept: the first sees an unnamed helper, the second sees a constant-folded one. The rule must be broken once.

4. **One behavioural test per router family, through a real exchange.** A service stub throws
   `new IllegalStateException("cannot read /srv/morpheus/data/x.db")`; the response must not contain the path and must
   contain `IllegalStateException`. *Rejected*: a unit test of `BoundaryFailureMessage` only - it already exists and did
   not prevent four routers from bypassing the class.

5. **No ADR.** The decision exists (the remote disclosure boundary); this change makes four routers apply it.

## Risks / Trade-offs

- A caller parsing error messages loses text on the rare failure that named a path. That is the point; the release note
  says so.
- The ArchUnit rule may meet legitimate reads of `getMessage()` in `morpheus-api` (the remote server's audit record, a
  health diagnostic). Each is either moved behind the filter or excused by name with its reason; the count is measured
  before the rule is written (task 1.1).

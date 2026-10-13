# Design

## Context

- The decision record names, per technology family, the application classes that reach it and marks each *stays* or
  *leaves*. This change reads that column and nothing else.
- The application already has the pattern: a port in the package of the capability that needs it
  (`read.SpecificationContentReader`, `identity.EntityIdentityResolver`), implemented by an adapter, wired by hand.
- `UpdateDiscoveryService` mixes three things: the trust policy (schemes, `DISCOVERY_ONLY`, attestation reference),
  the bounds (size, deadline, no redirect) and the transport (`HttpClient`, a virtual-thread executor, `Files`).

## Goals / Non-Goals

**Goals**: the access leaves and the decision stays; no gate loses its target; the named list shrinks.

**Non-Goals**: choosing which classes leave (the record does); a generic file-system or HTTP abstraction; any change
of behaviour seen from a command.

## Decisions

1. **A port says what the use case needs, not what the technology offers.** For update discovery: "give me the bytes
   of this manifest within these bounds, or a named failure" - not a general HTTP port. *Rejected*: `HttpPort`,
   `FileSystemPort` as the audit names them - they would move the technology's vocabulary into the application under
   another name, and every use case would still be written against it.

2. **What is refused before I/O is refused in the application.** The `http:` refusal, the unsupported-scheme refusal
   and the trust classification stay where the textual assertions read them. The adapter receives a request the
   application already accepted. The bounds are stated by the application and enforced by the adapter, which is where
   the stream is.

3. **The implementation goes to the adapter that owns the caller.** Update discovery is CLI-only: `morpheus-cli`.
   A class used by several adapters does not go into one of them - adapters are siblings - and that is the case of the
   workspace classes, which is why moving them needs a home decided first.

4. **Assertions move with the code, in the same commit.** A textual assertion that read the application file for a
   string now carried by the adapter is repointed; one that read it for a string that stayed is left. Each repointed
   assertion is broken once at its new target.

5. **One lot per family.** Outbound network first; file access, if the record marks it, only after the home is
   decided. Each lot shrinks the named list and amends the record.

## Risks / Trade-offs

- A port with one implementation and one caller is the indirection `rules/code-style.md` warns against. It is accepted
  only where the record says the family must not be in the application; this change adds none on its own judgement.
- Splitting `UpdateDiscoveryService` changes which class a test must build to exercise a bound. The existing tests of
  the bounds are kept and pointed at the class that now enforces them.

## Open Questions

- **Under the pure-core option, where do the workspace and integrity classes live?** Recommendation: not decided
  here; a separate ADR, because a new module moves the coverage population.

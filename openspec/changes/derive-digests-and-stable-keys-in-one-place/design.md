# Design

## Context

- The three built-in providers depend on `morpheus-domain` and `morpheus-application` and on nothing else of the
  reactor; `morpheus-store-sqlite` depends on `morpheus-application` too. No provider may depend on another, nor on the
  SDK (`AdapterSiblingArchitectureTest`).
- `morpheus-application` already has a public file digest, `ExternalJarIntegrity.sha256(Path)`, and none for a text.
- Every text copy computes the same thing: the bytes of the text in UTF-8, SHA-256, lower-case hexadecimal.
- SHA-256 is a digest every Java platform must provide; the failure branch exists because the exception is checked,
  not because it happens.
- `RemoteIdentityDecompositionContractTest` requires, as text, that the digest and the constant-time comparison of a
  remote token stay in the credential service.
- ArchUnit sees a call to `MessageDigest.getInstance(String)`; it does not see the algorithm name, which is a string
  argument. `morpheus-provider-testkit` is outside the imported classpath.

## Goals / Non-Goals

**Goals**: one text digest; one wording; a shrinking list of digest sites; one key derivation and one line reader in
the OpenSpec provider; an explicit decision for the synthetic key; unchanged fingerprints and keys.

**Non-Goals**: merging the three file and stream digests (named, left in place); changing a normalisation or an
encoding; moving file reading into `morpheus-application`; the Markdown provider's own key rules.

## Decisions

1. **The text digest lives in a leaf package of `morpheus-application`.** It is a pure function, the five modules that
   need it already depend on the application, and the application's own lifecycle service is one of the copies.
   *Rejected*: `morpheus-domain` - defensible for an identity derivation, but the store and the lifecycle fingerprint
   are not domain concepts and the domain has no helper of this kind today. *Rejected*: one copy per provider kept in
   step by a parity test - the per-module convention is for test support, and a parity test over eight sites is the
   gate without the fix.

2. **The digest sites are a named list.** After the change, the owner and four excused classes call
   `MessageDigest.getInstance`: the two file digests, the streaming fingerprint and the remote credential service,
   each with its reason. The list only shrinks. Converging the two file digests on `ExternalJarIntegrity.sha256(Path)`
   is possible - `morpheus-store-sqlite` may call it - and is left as an open question: the backup path's error
   handling is its own.

3. **One wording**, `SHA-256 must be available`, the one nine sites already use. It is written once, in the owner.

4. **`readAllLines`, `joinContent` and the OpenSpec `slug` become one package-private type of
   `morpheus-provider-openspec`.** The three copies of each are in the same three classes of one package. This is
   ordinary deduplication inside a module; it needs no dependency and no decision of sprint 11. *Rejected*: the
   audit's move to `morpheus-application` - `readAllLines` takes the provider's budget and attribution types, and it
   would add a file read to the layer whose file reads are being inventoried.

5. **The synthetic key is decided by a measurement, not by resemblance.** The first task derives every key the
   committed fixtures and the synthetic tests produce, with both functions. If they agree everywhere, the question is
   only which behaviour a future non-ASCII or empty title gets, and the recommendation is the OpenSpec one (fold, and
   refuse a key that is empty - an empty key is a silent degradation today). If they disagree somewhere, the synthetic
   function stays, renamed for what it does, and the disagreement is written in its comment. Sharing it across the two
   providers then needs a home in the application; while it is a single site it stays where it is.

6. **Enforcement.**
   - *Digest sites*: an ArchUnit rule, and a textual twin. Intent in one sentence: *no class outside the named list
     asks the platform for a message digest.* The rule is on the call to `MessageDigest.getInstance`, so a copy under
     any method name is seen, and `MessageDigest.isEqual` - which `security.md` requires elsewhere - is not touched.
     The twin scans every `src/main/java` for the literal `getInstance("SHA-256")`, because the algorithm name is a
     string the bytecode rule cannot read and one product module is outside its classpath (`.claude/rules/architecture.md`; "in doubt, keep
     both").
   - *Provider-internal helpers*: textual. Intent: *these declarations must not appear outside the owner.* A private
     method that takes a `String` and returns a `String` has no signature a members rule could single out, and it
     depends on nothing; the names are the only handle. The pattern matches declarations, not calls, and is checked
     not to be a family prefix (`sha256(` is also the name of a file digest).

## Risks / Trade-offs

- A digest that changed would change every fingerprint and force a full re-ingestion. The owner is tested against
  fixed vectors, including a non-ASCII text, and the provider and store suites - which compare fingerprints - run
  unmodified.
- Decision 5 may end with two key functions. That is an honest outcome: the criterion is one helper per behaviour, and
  the audit's "identical" was not measured.
- The lifecycle fingerprint (`ControlledChangeLifecycleMutationService`) is compared from one call to the next; its value
  must not move. Same proof: its tests unmodified.

## Open Questions

- **The synthetic key** (decision 5). Recommendation above; it is the maintainer's call if the measurement shows a
  difference on an existing fixture.
- **The two file digests.** Recommendation: leave them named in this change, and let
  `move-marked-technology-access-behind-application-ports` decide where file digests live.
- **One leaf package for this owner and the payload builder?** Recommendation: yes, one package of pure foundation
  types held by one leaf rule, named for its content.

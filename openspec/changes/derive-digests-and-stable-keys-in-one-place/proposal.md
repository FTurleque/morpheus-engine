# Proposal

## Why

AUD-QUA-08 (sprint 9) reports that `sha256`, `slug`, `readAllLines` and `joinContent` are copied across the providers,
"all identical", with two error messages for one condition, and proposes to move the four to `morpheus-application`.
Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding **holds for the counts, and is wrong on two points that
change the remedy**:

- `String sha256(String)`: six private copies in the providers, as counted
  (`StructuredMarkdownSpecificationContentReader.java:465`, `OpenSpecRequirementDeltaReader.java:557`,
  `OpenSpecCurrentSpecificationReader.java:518`, `OpenSpecChangeMetadataReader.java:478`,
  `SyntheticConstraintSemanticsReader.java:170`, `SyntheticSpecificationContentReader.java:466`) in four writings of the
  same computation. **The duplication does not stop at the providers**: `SqliteSchemaManager.java:170` is a seventh
  copy, character for character the OpenSpec one, and `ControlledChangeLifecycleMutationService.java:167` writes it
  inline an eighth time - inside `morpheus-application` itself.
- `MessageDigest.getInstance("SHA-256")` appears at twelve sites of `src/main` in six modules. Eight hash a text; three
  hash a file or a stream (`ExternalJarIntegrity`, `SqliteServerMaintenance`, `SourceFingerprint`); one hashes a
  remote token to bytes (`RemoteIdentityCredentialService`) and is pinned where it is by
  `RemoteIdentityDecompositionContractTest`.
- The message: nine sites say `SHA-256 must be available`, three say `SHA-256 is unavailable` (the Markdown reader and
  the two synthetic readers), as reported.
- `slug(String)`: four copies, as counted - **but not identical**. The three OpenSpec copies fold accents (NFD, marks
  removed) and refuse a value that leaves nothing (`Cannot derive a stable key from: ...`). The synthetic copy
  (`SyntheticSpecificationContentReader.java:475`) does neither: an accented letter becomes a separator, and an empty
  result is returned as a key. The key feeds an external identifier (`scenario:<requirement>/<slug>`), so merging the
  two as the audit proposes changes identities for any non-ASCII title.
- `readAllLines` (three copies) and `joinContent` (three copies) are identical, as reported - and **all six are in one
  module**, `morpheus-provider-openspec`, in the same three classes. `readAllLines` takes the provider's read budget and
  reports through `OpenSpecSourceAttribution`: it cannot move to `morpheus-application` without those types, and a file
  read is exactly what sprint 11 is deciding whether the application may do.

The digest of an excerpt is what the provenance fingerprints are made of, and the product sells those as
deterministic. Eight writings in five modules are eight places where a change of encoding or normalisation can be
applied to some.

## What Changes

- One function gives the SHA-256 of a text, for every module; the sites allowed to ask the platform for a digest are a
  named list that only shrinks.
- The failure for a platform without SHA-256 has one wording.
- The OpenSpec provider has one stable-key derivation, one line reader and one content joiner, inside its own module.
- The synthetic provider's key derivation is either the shared one, with proof that no existing key changes, or a
  second function with a name that says how it differs.

## Capabilities

### New Capabilities

- `text-digest-and-key-derivation`: how a text becomes a digest or a stable key, and who is allowed to compute one.

### Modified Capabilities

(none)

## Impact

- **Production code.** Digest: the eight text sites in `morpheus-provider-openspec`, `morpheus-provider-markdown`,
  `morpheus-provider-synthetic`, `morpheus-store-sqlite` and `morpheus-application`; one new type in a leaf package of
  `morpheus-application`, on which the five already depend. Providers are siblings
  (`AdapterSiblingArchitectureTest`: a provider never reads through another provider), so nothing can be shared
  provider to provider. Keys and line reading: one package-private type inside `morpheus-provider-openspec` - no new
  edge at all.
- **Departure from the audit**: `readAllLines` and `joinContent` do not move to the application; `slug` moves only on
  proof; the count of copies to remove is not the audit's sixteen.
- No contract, no OpenAPI, no manifest line. Every fingerprint and every derived key is unchanged, proven on the
  fixtures of `experiments/m0/fixtures/`.
- One ArchUnit rule with a textual twin, and one textual rule for the provider-internal helpers;
  `architectureTestsMinimum` rises.
- No ADR of its own. The digest owner is pure - no file, no network - and is compatible with either outcome of
  `decide-what-technology-the-application-core-may-touch` (sprint 11); the three file and stream digests it leaves in
  place are part of that change's inventory, not of this one.
- Coverage: covered duplicate lines leave five modules. Both scales read before and after; no ratchet lowered.
- Independent of the other changes of sprint 9, apart from sharing the leaf package of
  `build-payload-maps-through-one-checked-builder` if the maintainer chooses one home for both.

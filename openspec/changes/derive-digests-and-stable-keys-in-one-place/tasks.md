# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [x] 1.1 List every site of `src/main/java` that obtains a message digest, with what it hashes (text, file, stream,
      token) and its failure message. Proof: the table; on 2026-10-13 at `8b26d3d7`, twelve sites in six modules, eight
      of them on a text. The audit counted the six of the providers only.
- [x] 1.2 List the declarations of `slug`, `readAllLines` and `joinContent`, with their normalised bodies. Proof: four,
      three and three; the synthetic `slug` differs from the three others.
- [x] 1.3 **The unverified premise.** Derive, with both key functions, every key that the fixtures of
      `experiments/m0/fixtures/` and the synthetic test inputs produce. Proof: the list of inputs on which they differ -
      empty decides decision 5 one way, non-empty the other. Nothing about the synthetic key is implemented before this.
- [x] 1.4 Read the architecture tests that pin a digest site as text (`RemoteIdentityDecompositionContractTest`, the D2
      and M26 suites). Proof: the list of sites that must stay, with the test that says so.
- [x] 1.5 Read both coverage scales and the presence ratchets from the last validation summary. Proof: the figures and
      their source.

## 2. Reproduce before fixing

- [x] 2.1 The ArchUnit rule on calls to `MessageDigest.getInstance` and its textual twin on `getInstance("SHA-256")`.
      Intent: *no class outside the named list asks the platform for a message digest.* Proof: red on `HEAD`, naming
      the eight text sites; the four excused classes listed with their reasons; the rule retains classes.
- [x] 2.2 The textual rule on the provider-internal declarations. Intent: *these declarations must not appear outside
      the owner.* Proof: red on `HEAD`, naming the nine OpenSpec declarations; the pattern shown not to match
      `ExternalJarIntegrity.sha256(Path)`.
- [x] 2.3 Tests of the digest owner on fixed vectors: the empty text, an ASCII text, a text with accented and
      non-Latin characters. Proof: red until 3.1.

## 3. The change

- [x] 3.1 The text digest in a leaf package of `morpheus-application`, with the single failure wording. Proof: 2.3
      passes.
- [x] 3.2 Replace the eight text sites, one module per commit. Proof: 2.1 passes; the provider, store and lifecycle
      suites pass with no expected digest edited.
- [x] 3.3 The OpenSpec helper type: key derivation, line reading, content joining. Proof: 2.2 passes; the OpenSpec
      suites unmodified.
- [x] 3.4 The synthetic key, as 1.3 decided: adopt, or rename and document. Proof: the synthetic suites unmodified in
      the first case; the comment and a test on an accented and on an empty title in the second.
      *Renamed and documented (`persistedScenarioKey`): 1.3 found no differing key in the repository's inputs, but
      synthetic identities are persisted by installed products, where an accented or keyless title would change identity
      or fail the read. Tests `anAccentedScenarioTitleKeepsTheKeyItsPersistedIdentityWasDerivedFrom` and
      `aScenarioTitleThatLeavesNoKeyKeepsTheIdentityOfItsRequirementPrefix`.*
- [x] 3.5 Break each rule once, then revert: a private `sha256` under another name in a provider; a private `slug` in
      a new OpenSpec class. Proof: the two named failures.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning; `ProviderAntiLockInTest` and
      the persistence parity tests unmodified.
- [ ] 4.2 Both coverage scales and the changed-line gate on Linux and Windows. Proof: the figures next to 1.5; tests are
      written if a ratio falls, no ratchet is lowered.
- [x] 4.3 `.claude/rules/code-style.md`, section "Determinisme": the digest owner named. Proof: the diff.
- [ ] 4.4 Pull request description: the finding and where this reading departs from it, the tables of 1.1 to 1.3, the
      named list of digest sites, the justification of the architecture tests.

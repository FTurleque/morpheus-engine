# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Questions only the maintainer can answer

- [ ] 1.1 Is a direct upgrade from 1.1.0 to 1.2.1 supported, or must 1.2.0 be installed in between? Proof: the answer,
      recorded in the guide's "from which version" paragraph.
- [ ] 1.2 Confirm the location operators should keep the pre-upgrade backup in (the `backups` root of the layout, or
      anywhere outside the installation directory). Proof: the answer, in the guide.

## 2. Reproduce before fixing

- [ ] 2.1 Add the coherence test (suggested home: `RepositoryDocumentationCoherenceTest`, or a sibling in the same
      package) with four methods, one per requirement. Proof: all four fail on `HEAD` — the latest guide is
      `UPGRADE_1_2.md`, which states V015, makes the backup optional (`:24`), quotes no refusal, and
      `UPGRADE_1_1.md` carries no notice.
- [ ] 2.2 Prove the selection is not vacuous: assert the set of `UPGRADE_*.md` is non-empty and that the version ordering
      is numeric (`1_2_1` after `1_2`, `1_10` after `1_9`). Proof: a unit test of the ordering inside the test class.
- [ ] 2.3 Reproduce the trap the guide warns about, once, by hand or in a store test: open a frozen V012 fixture
      (`R2UpgradeCompatibilityTest` holds one) through `createBackup`, and read the backup's
      `MAX(version)`. Proof: it equals `SUPPORTED_SCHEMA_VERSION`, not 12. Keep the test if it is cheap; it documents
      the behaviour `back-up-before-a-schema-migration` builds on.

## 3. The change

- [ ] 3.1 Write `docs/user/UPGRADE_1_2_1.md` in French, like its siblings: scope and source versions; schema reached;
      **mandatory backup with the installed version** (stop, `server backup create`, `server backup verify`, where to
      keep it, why a later backup does not help); installation per platform (Windows setup, Windows ZIP, Linux
      tar.gz); first start and what to check; **way back** (the refusal quoted, the offline restore, what is lost);
      diagnostics (history mismatch: do not edit `schema_migrations`). Proof: 2.1 passes for the three content
      requirements.
- [ ] 3.2 Add the opening notice to `UPGRADE_1_2.md` and `UPGRADE_1_1.md`; bound `UPGRADE_1_2.md:108` to 1.2.0. Leave
      every other dated sentence as it is. Proof: 2.1's fourth method passes; `git diff` shows only the notice and that
      one sentence.
- [ ] 3.3 Link the guide from `docs/user/README.md` and from `docs/user/release-notes/RELEASE_NOTES_1.2.1.md` (one line each).
      Proof: `RepositoryDocumentationCoherenceTest` and the link checks stay green.
- [ ] 3.4 Break each rule once, then revert: raise the number in the guide by one; remove the refusal quote; remove a
      notice. Proof: three named failures, in the pull request.

## 4. Verification

- [ ] 4.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green; the
      new methods are counted (`architectureTestsMinimum` only rises).
- [ ] 4.2 Every command the new guide names resolves (`gate-reachability`, "Documentation that is current SHALL name
      only commands that resolve"). Proof: the existing gate, green.
- [ ] 4.3 Pull request description justifies the test added under `morpheus-architecture-tests/` (governance file).

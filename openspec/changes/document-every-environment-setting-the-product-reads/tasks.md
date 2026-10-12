# Tasks

Specified by pull request 437; implemented on `feat/document-every-environment-setting-the-product-reads`.

## 1. Measure before changing

- [x] 1.1 Re-list the `"MORPHEUS_[A-Z0-9_]+"` literals under `*/src/main/java` and, for each, the operator pages that
      name it. Proof: the table; on 2026-10-11 it held 24 literals, 2 refusal codes, 22 variables, 1 undocumented.
      Re-measure; do not reuse these counts.
  - 2026-10-11: re-measured: 24 literals, 2 refusal codes, 22 variables, 1 named in no page under `docs/user` or `docs/developer`.
- [x] 1.2 Check that no variable name is built by concatenation (a prefix plus a suffix). Proof: the search and its
      empty result, quoted in the test's comment.
  - 2026-10-11: `git grep` for a `MORPHEUS_` literal followed by `+`, and for a `+ "_X` suffix, under `*/src/main/java`: no match. Said in the test's comment.

## 2. Reproduce before fixing

- [x] 2.1 Add the coherence test (suggested home: beside `RepositoryDocumentationCoherenceTest`), with the excused list
      and its two self-checks (entry without a reason; entry no source mentions). Proof: red on `HEAD`, naming
      `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` and `RemoteApiLaunchOptions.java`, and nothing else.
  - 2026-10-11: `LaunchEnvironmentDisclosureTest`; red on `HEAD`, naming `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` (mentioned by `RemoteApiLaunchOptions.java`) and nothing else.
- [x] 2.2 Look for a test of `RemoteApiLaunchOptions` covering the four sources of the plugin directory in order. If one
      is missing, add it in `morpheus-cli` before documenting the precedence: option over variable, variable over
      property, property over default. Proof: the test names; green on `HEAD` (it pins existing behaviour).
  - 2026-10-11: the option and the default were covered; `remotePluginDirectoryIsTakenFromTheOptionThenTheEnvironmentThenTheJvmProperty` adds property over default, variable over property, option over variable. Green on `HEAD`: it pins existing behaviour.

## 3. The change

- [x] 3.1 `docs/user/TEAM_REMOTE_SERVER.md`: add the variable to the block at `:206-210`; add the precedence sentence
      near `--provider-plugin-dir` (`:200`); correct the paragraph at `:380`. Proof: 2.1 passes.
  - 2026-10-11: variable added to the block, the resolution order stated, the paragraph that named the option as the only source corrected.
- [x] 3.2 `docs/user/PROVIDER_PLUGINS.md`: one line pointing to the remote guide for where the server looks. Proof: the
      link checks stay green.
  - 2026-10-11: one paragraph in `PROVIDER_PLUGINS.md`.
- [x] 3.3 Break the rule once, then revert: remove `MORPHEUS_BACKUPS_DIR` from `docs/user/INSTALLATION.md` (it is named
      in two pages — remove both mentions, or pick a variable named once). Proof: the named failure.
  - 2026-10-11: `MORPHEUS_SERVER_AUTH_FILE` renamed in the guide: the test failed naming it; reverted.

## 4. Verification

- [ ] 4.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green.
  - 2026-10-11: 611 of 612 pass locally. The one failure is `CoverageQualityGateTest`, which refuses a partial population (15 of 16 JaCoCo reports) after a `test` run of `morpheus-cli` alone on this machine; it does not concern this change and CI runs the full reactor.
- [x] 4.2 Confirm the remote projection is untouched: no key was added to `REMOTE_SAFE_DETAIL_KEYS`, no path reaches a
      remote response. Proof: `git diff --stat` shows documentation and tests only.
  - 2026-10-11: documentation and tests only; no file under `src/main/java`.
- [x] 4.3 Pull request description: finding covered, the measured table, and the justification for the test added under
      `morpheus-architecture-tests/`.
  - 2026-10-11: in the description of the pull request that carries this lot.

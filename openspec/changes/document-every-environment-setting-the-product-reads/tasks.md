# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Re-list the `"MORPHEUS_[A-Z0-9_]+"` literals under `*/src/main/java` and, for each, the operator pages that
      name it. Proof: the table; on 2026-10-11 it held 24 literals, 2 refusal codes, 22 variables, 1 undocumented.
      Re-measure; do not reuse these counts.
- [ ] 1.2 Check that no variable name is built by concatenation (a prefix plus a suffix). Proof: the search and its
      empty result, quoted in the test's comment.

## 2. Reproduce before fixing

- [ ] 2.1 Add the coherence test (suggested home: beside `RepositoryDocumentationCoherenceTest`), with the excused list
      and its two self-checks (entry without a reason; entry no source mentions). Proof: red on `HEAD`, naming
      `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` and `RemoteApiLaunchOptions.java`, and nothing else.
- [ ] 2.2 Look for a test of `RemoteApiLaunchOptions` covering the four sources of the plugin directory in order. If one
      is missing, add it in `morpheus-cli` before documenting the precedence: option over variable, variable over
      property, property over default. Proof: the test names; green on `HEAD` (it pins existing behaviour).

## 3. The change

- [ ] 3.1 `docs/user/TEAM_REMOTE_SERVER.md`: add the variable to the block at `:206-210`; add the precedence sentence
      near `--provider-plugin-dir` (`:200`); correct the paragraph at `:380`. Proof: 2.1 passes.
- [ ] 3.2 `docs/user/PROVIDER_PLUGINS.md`: one line pointing to the remote guide for where the server looks. Proof: the
      link checks stay green.
- [ ] 3.3 Break the rule once, then revert: remove `MORPHEUS_BACKUPS_DIR` from `docs/user/INSTALLATION.md` (it is named
      in two pages — remove both mentions, or pick a variable named once). Proof: the named failure.

## 4. Verification

- [ ] 4.1 `./mvnw test -pl morpheus-architecture-tests` after building `morpheus-provider-reference`. Proof: green.
- [ ] 4.2 Confirm the remote projection is untouched: no key was added to `REMOTE_SAFE_DETAIL_KEYS`, no path reaches a
      remote response. Proof: `git diff --stat` shows documentation and tests only.
- [ ] 4.3 Pull request description: finding covered, the measured table, and the justification for the test added under
      `morpheus-architecture-tests/`.

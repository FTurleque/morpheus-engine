# Tasks

Implemented on 2026-10-12 by pull request 452.

## 1. Reproduce before fixing

- [x] 1.1 The presence assertion, one per pattern. Proof: red on `HEAD`, naming the six.
      *`RepositoryCredentialHygieneTest#theRepositoryIgnoresIdentityFilesAndKeyMaterial`, red before the change.*
- [x] 1.2 The tracked-file assertion over `git ls-files`, with the non-empty guard. Proof: green on `HEAD` (nothing is
      tracked), and red after `git add -f` of a scratch `x.pem`, then reverted.
      *Green on `HEAD` as expected. **The forced addition was not replayed against the index**: the same proposition is
      held by `aTrackedPathIsMatchedByItsNameWhereverItSits`, which gives the matcher tracked paths directly.*

## 2. The change

- [x] 2.1 The six lines in `.gitignore`, under a comment naming what they are for. Proof: 1.1 passes; a scratch
      `server.p12` does not appear in `git status`.
      *A scratch `server.p12` and `remote-auth.txt` at the root were not listed.*
- [x] 2.2 `SECURITY.md`: the sentence gains "the repository ignores them by default, and the build refuses a tracked
      one". Proof: the documentation gates.

## 3. Verification

- [x] 3.1 `./mvnw test -pl morpheus-architecture-tests`. Proof: green.
      *Green in the required checks of the pull request.*
- [x] 3.2 Pull request description: the finding and the justification for the gate edit.

## Not in the specification, found while doing it

- The test started `git` by its bare name, which the code scanner reported as a search-path dependency. It now finds
  the executable on the search path itself and starts it by its absolute path.

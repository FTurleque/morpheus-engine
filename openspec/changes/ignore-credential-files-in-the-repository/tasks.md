# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Reproduce before fixing

- [ ] 1.1 The presence assertion, one per pattern. Proof: red on `HEAD`, naming the six.
- [ ] 1.2 The tracked-file assertion over `git ls-files`, with the non-empty guard. Proof: green on `HEAD` (nothing is
      tracked), and red after `git add -f` of a scratch `x.pem`, then reverted.

## 2. The change

- [ ] 2.1 The six lines in `.gitignore`, under a comment naming what they are for. Proof: 1.1 passes; a scratch
      `server.p12` does not appear in `git status`.
- [ ] 2.2 `SECURITY.md`: the sentence gains "the repository ignores them by default, and the build refuses a tracked
      one". Proof: the documentation gates.

## 3. Verification

- [ ] 3.1 `./mvnw test -pl morpheus-architecture-tests`. Proof: green.
- [ ] 3.2 Pull request description: the finding and the justification for the gate edit.

# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Record the headers of a success, a 400, a 404, a 405, a 409 and an origin refusal on the local server, and of
      a proxied response, a 401, a 403 and a 429 on the remote facade. Proof: the table.
- [ ] 1.2 Read what the security gate asserts textually about these headers. Proof: the assertions that must still pass.

## 2. Reproduce before fixing

- [ ] 2.1 The behavioural test of both servers. Proof: red on `HEAD` for the local success and errors (two headers
      missing) and for every remote response (HSTS missing).

## 3. The change

- [ ] 3.1 One declaration of the local set; the shared writer and the origin-refusal path use it. Proof: local half of
      2.1 passes.
- [ ] 3.2 HSTS in the remote writer. Proof: remote half of 2.1 passes.
- [ ] 3.3 `.claude/rules/security.md` remote table and the textual gate gain HSTS in the same change. Proof: the gate.
- [ ] 3.4 Break once, then revert: remove one header from the shared set. Proof: the named failure.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`. Proof: green.
- [ ] 4.2 `docs/user/TEAM_REMOTE_SERVER.md`: one sentence on HSTS and its one-year pin. Pull request description: the
      finding, the table of 1.1.

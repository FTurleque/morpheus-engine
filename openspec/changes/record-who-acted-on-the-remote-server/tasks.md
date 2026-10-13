# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Decide and measure

- [ ] 1.1 Maintainer: the failure policy of decision 4. Default: refuse WRITE and ADMIN, serve READ, count. Proof: the
      answer, in the ADR.
- [ ] 1.2 Read every path of `serve` that ends a request (served by the facade, proxied, refused, failed, timed out)
      and say where its status is known. Proof: the list; it is where the record is written.
- [ ] 1.3 Measure one append under a lock against the remote load profile. Proof: the two profiles.

## 2. Reproduce before fixing

- [ ] 2.1 Through a real remote server: a READ, a WRITE, a 403, a 401, a proxied failure. Assert the records and their
      absence. Proof: red on `HEAD`.
- [ ] 2.2 No secret: a journal read after traffic contains no token, no fingerprint, no identifier of the path.
- [ ] 2.3 Rotation at the bound; permissions; a journal that cannot be written, under the policy of 1.1.

## 3. The change

- [ ] 3.1 The writer and its wiring; the record at each exit of 1.2. Proof: 2.1 to 2.3 pass.
- [ ] 3.2 `SECURITY.md`, the team server guide, `.claude/rules/security.md`, the ADR.
- [ ] 3.3 A textual rule: the remote server does not end a request without the journal being consulted. Break it once.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The remote disclosure tests: the journal's location appears in no remote response. Proof: green unmodified.
- [ ] 4.3 Release note; pull request description: the finding, the record's fields, the policy.

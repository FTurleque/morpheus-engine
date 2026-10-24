# Tasks

**Precondition**: the ADR of `decide-what-technology-the-application-core-may-touch` is accepted and marks at least
one class as leaving. Otherwise withdraw this change.

## 1. Measure before changing

- [ ] 1.1 Read the record and list the classes marked as leaving, per family. Proof: the list; one lot per family.
- [ ] 1.2 For each: its callers, the adapter that owns them, and every architecture assertion that reads the file by
      path, with the string it looks for. Proof: the table; on 2026-10-13 four test classes read
      `UpdateDiscoveryService.java`.
- [ ] 1.3 For each: the behaviours a caller can observe - refusals, bounds, results - and the test that holds each.
      Proof: the list; a behaviour without a test gets one before the move.

## 2. Characterise before moving

- [ ] 2.1 The missing tests of 1.3, green on `HEAD`. Proof: for update discovery - `http:` refused before I/O, a
      redirect not followed, an oversized manifest and an expired deadline refused by name, a `file:` manifest read.
      Each asserts the message or the code, not only the exception type.

## 3. The move, one family at a time

- [ ] 3.1 The port, in the package of the capability; the implementation, in the adapter that owns the caller; the
      wiring, explicit. Proof: 2.1 still green, unchanged.
- [ ] 3.2 Repoint the assertions of 1.2 whose string moved; leave the others. Proof: each repointed assertion fails
      once when its string is removed at the new place, then is restored.
- [ ] 3.3 Remove the class from the named list of the technology gate. Proof: the gate fails with the name still
      present, passes without it.
- [ ] 3.4 `./mvnw dependency:analyze`. Proof: 0 warning; no dependency added to `morpheus-application`'s POM.

## 4. Verification and record

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green on both platforms through the required
      checks.
- [ ] 4.2 Clarify the ADR: where each class lives (what moved and when goes to the pull request). Update `.claude/rules/architecture.md` if it named the
      class. Proof: the diff.
- [ ] 4.3 Pull request description: the finding, the record's decision, the table of 1.2, and that no command changed
      its output.

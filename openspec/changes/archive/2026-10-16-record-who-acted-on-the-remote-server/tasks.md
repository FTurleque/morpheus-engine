# Tasks

Implemented on 2026-10-13; each task carries what was done and what was not.

## 1. Decide and measure

- [x] 1.1 Maintainer: the failure policy of decision 4. Default: refuse WRITE and ADMIN, serve READ, count. Proof: the
      answer, in the ADR.
      *2026-10-13: not answered. The maintainer launched the lot without amending the default; it is applied and ADR-0113 says on whose word.*
  - 2026-10-16: the maintainer confirmed the default explicitly; ADR-0113 says so.
- [x] 1.2 Read every path of `serve` that ends a request (served by the facade, proxied, refused, failed, timed out)
      and say where its status is known. Proof: the list; it is where the record is written.
      *2026-10-13: every exit of `serve` goes through its `finally` - the 403, the status served by the facade, the backup, the proxied call, a `RemoteFailure`, a bad request, an unforeseen failure. The status is read there from the exchange (`getResponseCode()`, -1 when nothing was sent). Two exits are deliberately not recorded: a failed authentication, and an authenticated request for no route or a refused method, which was never authorized.*
- [x] 1.3 Measure one append under a lock against the remote load profile. Proof: the two profiles.
      *2026-10-13: **not measured.** The load profile was not run against the journal. One append under a lock per request, two for a mutation, with the file reopened each time; the cost is stated in the ADR, not measured.*
  - 2026-10-16: measured with a throwaway probe on Windows, 16 cores: 0.20 ms per append alone (p99 0.38 ms); serialized on the lock, 1.7 ms median at 8 concurrent callers and 6.4 ms at 32. In ADR-0113, with the extrapolation to the default limit of 64. Not replayed on Linux.

## 2. Reproduce before fixing

- [x] 2.1 Through a real remote server: a READ, a WRITE, a 403, a 401, a proxied failure. Assert the records and their
      absence. Proof: red on `HEAD`.
      *2026-10-13: `RemoteAccessJournalHttpTest`, through a real server: a read, a 403, a write, a write on a path with an identifier, two 401 and a 404. **Departures**: not red on `HEAD` first - there was no journal to fail; a proxied failure is covered by the write that the local server refuses (400), not by an upstream that is down.*
- [x] 2.2 No secret: a journal read after traffic contains no token, no fingerprint, no identifier of the path.
      *2026-10-13: same test - no token, no SHA-256 of a token, no project identifier in the journal; `RemoteAccessJournalTest` gives the redactor a principal shaped like a secret.*
- [x] 2.3 Rotation at the bound; permissions; a journal that cannot be written, under the policy of 1.1.
      *2026-10-13: rotation and the lost record in `RemoteAccessJournalTest`; the 503 for a write and the served read through a real server. Permissions are those of `RotatingOperationalLogFile`, whose own test asserts them on POSIX; not asserted again here.*

## 3. The change

- [x] 3.1 The writer and its wiring; the record at each exit of 1.2. Proof: 2.1 to 2.3 pass.
      *2026-10-13: `RemoteAccessJournal`, opened beside the identity file by the server's constructor; `accepted` after the role check, `completed` in the `finally`. The writer is the operational file sink's, opened under another name (`RotatingOperationalLogFile.openFile`) with a `write` that reports its failure. **Departure from the proposal**: no change in `morpheus-cli` - the location needs no resolution, it is the directory of the identity file. The server status does not state it.*
- [x] 3.2 `SECURITY.md`, the team server guide, `.claude/rules/security.md`, the ADR.
      *2026-10-13: `SECURITY.md`, `docs/user/TEAM_REMOTE_SERVER.md`, `.claude/rules/security.md`, ADR-0113.*
- [x] 3.3 A textual rule: the remote server does not end a request without the journal being consulted. Break it once.
      *2026-10-13: `RemoteAccessJournalArchitectureTest`. Broken once: the outcome record removed from the `finally`, the rule fails naming it; reverted.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
  - 2026-10-16: the pull request's required checks ran `clean verify` on Linux and Windows and the changed-line gate, green before merge; `develop` at `557fed02`, which carries every lot, is green on push (run 38825491416). (PRs 474, 478, 480, 485).
- [x] 4.2 The remote disclosure tests: the journal's location appears in no remote response. Proof: green unmodified.
  - 2026-10-16: the remote disclosure tests run unmodified in those checks, green.
- [x] 4.3 Release note; pull request description: the finding, the record's fields, the policy.
  - 2026-10-16: `RELEASE_NOTES_1.2.1.md` describes `morpheus-remote-access.jsonl`, its fields and the refusal; PR 474 carries the finding.

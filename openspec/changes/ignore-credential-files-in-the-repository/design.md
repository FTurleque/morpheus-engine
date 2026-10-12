# Design

## Context

- `D2RepositoryHardeningArchitectureTest` already asserts the literal presence of pinned versions and hardening strings
  in repository files; it is the home of "this line must stay".
- An ignore rule prevents `git add` of an untracked file by pattern; it does nothing for a file already tracked, and
  `git add -f` bypasses it.

## Goals / Non-Goals

**Goals**: the default path of least effort does not commit key material; a tracked file of that kind fails the build.

**Non-Goals**: secret scanning of file contents (CodeQL and the push protection of the hosting platform do that);
history rewriting (the audit found no secret in the history, AUD-SEC-09).

## Decisions

1. **Patterns**: `remote-auth*.txt`, `*.p12`, `*.jks`, `*.pfx`, `*.pem`, `*.key`. The first follows the default file
   name; the others are the formats the server's options accept or that usually sit beside them.

2. **Two assertions, both textual by nature**: each pattern is a line of `.gitignore`; and no path listed by
   `git ls-files` matches one. The second is what an ignore rule cannot give: it catches `git add -f` and a file
   committed before the rule. *Rejected*: the presence check alone.

3. **The tracked-file check reads `git ls-files`.** The architecture suite already runs inside a checkout; where it does
   not (a source archive), the check must say it could not conclude rather than pass - a named assumption in the test,
   as the repository does elsewhere.

4. **No ADR.**

## Risks / Trade-offs

- `*.key` and `*.pem` are broad. A future fixture with such an extension will be ignored on `git add` and refused if
  forced; it is renamed or generated at test time, and the test's message says so.

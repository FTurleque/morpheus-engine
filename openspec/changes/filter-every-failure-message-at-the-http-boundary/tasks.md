# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List every call to `Throwable.getMessage()` from `com.morpheus.api` and what each result becomes. Proof: the
      table; it is the initial content of the excused list, each line with its reason.
- [ ] 1.2 List the routes each of the four routers serves and which error codes use `safeMessage`. Proof: the table, in
      the pull request.

## 2. Reproduce before fixing

- [ ] 2.1 One test per router family (query, policy, policy management, reasoning) through a real exchange: a stub
      service throws with a message naming a path; assert status, error code, absence of the path, presence of the
      simple name. Proof: four failures on `HEAD`.
- [ ] 2.2 The architecture rule and its textual twin. Proof: red on `HEAD`, naming the four routers (and the decoder if
      it is not excused).

## 3. The change

- [ ] 3.1 Replace the four `safeMessage` by `BoundaryFailureMessage.safe`; route the decoder's through it. Proof: 2.1
      and 2.2 pass; `HttpRoutesTransportBoundaryArchitectureTest` and `HttpRoutesFamilyArchitectureTest` green
      unmodified.
- [ ] 3.2 Break the rule once, then revert: restore one private helper. Proof: the named failure.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate replayed. Proof: green.
- [ ] 4.2 Release note: one line under the 1.2.1 notes, in the terms of a caller (what an error message no longer
      shows). `docs/user/` carries no SHA, issue number or gate name.
- [ ] 4.3 Pull request description: the finding, the measured tables, the justification for the architecture rule.

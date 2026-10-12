# Tasks

Implemented on 2026-10-12 by pull request 453.

## 1. Measure before changing

- [x] 1.1 List every call to `Throwable.getMessage()` from `com.morpheus.api` and what each result becomes. Proof: the
      table; it is the initial content of the excused list, each line with its reason.
      *The excused list of `HttpFailureDisclosureArchitectureTest` is that table: the filter itself and five failure
      types MORPHEUS authors for the caller, each with its reason. The rule found three reads in the remote facade that
      a textual search had been read past.*
- [ ] 1.2 List the routes each of the four routers serves and which error codes use `safeMessage`. Proof: the table, in
      the pull request.
      *Not written as a table: the pull request names the four routers and the count of routes, not each route.*

## 2. Reproduce before fixing

- [x] 2.1 One test per router family (query, policy, policy management, reasoning) through a real exchange: a stub
      service throws with a message naming a path; assert status, error code, absence of the path, presence of the
      simple name. Proof: four failures on `HEAD`.
      *`ExtensionRoutesFailureDisclosureTest`, four failures on the commit that adds it alone. No stub service: a
      request is enough, several refusals quoting a value of the request; a fifth test replaces the database by a file
      that is not one, for a failure raised below a service.*
- [x] 2.2 The architecture rule and its textual twin. Proof: red on `HEAD`, naming the four routers (and the decoder if
      it is not excused).

## 3. The change

- [x] 3.1 Replace the four `safeMessage` by `BoundaryFailureMessage.safe`; route the decoder's through it. Proof: 2.1
      and 2.2 pass; `HttpRoutesTransportBoundaryArchitectureTest` and `HttpRoutesFamilyArchitectureTest` green
      unmodified.
      *`MorpheusProjectSyncApiService` wrapped one foreign message unfiltered as well; it goes through the filter too.*
- [x] 3.2 Break the rule once, then revert: restore one private helper. Proof: the named failure.
      *A raw `failure.getMessage()` restored in the decoder failed naming `MorpheusHttpRequestDecoder.readBody`.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`; changed-line coverage gate replayed. Proof: green.
      *`clean verify` green on Windows before the last test was added; the changed-line gate was first under its
      threshold, the storage-failure test was added for it, and the measurement that counts is the required Linux check
      of the pull request, green.*
- [x] 4.2 Release note: one line under the 1.2.1 notes, in the terms of a caller (what an error message no longer
      shows). `docs/user/` carries no SHA, issue number or gate name.
- [x] 4.3 Pull request description: the finding, the measured tables, the justification for the architecture rule.
      *The finding and the justification; of the tables, see 1.2.*

## Noticed, not changed

- A JSON decoding refusal quotes the Java class of the request. It names no location and the filter lets it through; it
  is a different disclosure and is not in this finding.

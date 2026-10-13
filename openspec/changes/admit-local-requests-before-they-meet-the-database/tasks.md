# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 With the harness of `keep-the-scheduler-free-while-sqlite-waits`: at which concurrency do local requests begin
      to fail with a busy failure, with and without a writer? Proof: the curve; it sizes the ceiling.
- [ ] 1.2 Read the four ceilings of the remote facade and how each is derived. Proof: the lines.
- [ ] 1.3 Read which local operations declare which statuses in the OpenAPI files. Proof: the list of operations that
      gain `429`.

## 2. Reproduce before fixing

- [ ] 2.1 A burst beyond the ceiling on a real local server: all answered. A request that waits past the bound: `429`,
      named, with `Retry-After`. Proof: red on `HEAD` - the first shows busy failures, the second has no bound.
- [ ] 2.2 Health under a saturated ceiling. Proof: red or green on `HEAD`, stated.
- [ ] 2.3 The comparison of the two ceilings in remote mode.

## 3. The change

- [ ] 3.1 The admission, the bound, the refusal. Proof: 2.1 to 2.3 pass.
- [ ] 3.2 The OpenAPI files; `PublicHttpRouteConvergenceTest` green. The risk register and the operability page.
- [ ] 3.3 Break once, then revert: remove the permit release on a failed request. Proof: a test that the ceiling is not
      leaked fails.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate. Proof: green.
- [ ] 4.2 The remote load profile the repository already has, before and after. Proof: the two profiles.
- [ ] 4.3 Release note; pull request description: the finding, the curve of 1.1, the contract change and its reason.

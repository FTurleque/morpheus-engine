# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 For each hostile encoding of the second requirement, record what `getPath()`, the three parsers and the remote
      policy produce today. Proof: the table; at least one row where authorization and routing differ.
- [ ] 1.2 Read how the remote role policy matches a path against the route table, and whether it splits on `/` itself.
      Proof: the lines.

## 2. Reproduce before fixing

- [ ] 2.1 Unit tests of the parser for the four scenarios of the first requirement. Proof: `%252e%252e` and `a+b` fail
      on `HEAD`.
- [ ] 2.2 The equality test of the second requirement, parameterised over the encodings. Proof: red on `HEAD` for the
      rows of 1.1.
- [ ] 2.3 The textual rule on `URLDecoder` with its named exceptions. Proof: red on `HEAD`, naming the parser and the
      two routers.

## 3. The change

- [ ] 3.1 The single function (raw split, one decoding, no `+`). Proof: 2.1 passes.
- [ ] 3.2 Local server, the two routers and the remote policy use it; the copies are deleted. Decision 3 of the design
      is taken and written down. Proof: 2.2 and 2.3 pass; `HttpRoutesFamilyArchitectureTest` green, and its ban broken
      once to prove it still bites.
- [ ] 3.3 `PublicHttpRouteConvergenceTest` (it starts a real local server and routes every table entry). Proof: green
      unmodified.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`; changed-line coverage gate replayed. Proof: green.
- [ ] 4.2 Release note in the terms of a caller. Pull request description: finding, the table of 1.1, decision 3.

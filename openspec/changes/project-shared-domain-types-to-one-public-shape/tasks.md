# Tasks

Implementation is a separate lot, started after this specification is reviewed and after
`build-payload-maps-through-one-checked-builder` is delivered. Nothing below is done by the pull request that
introduces this change.

## 1. Measure before changing

- [ ] 1.1 Re-list every method of `morpheus-api`, `morpheus-mcp` and `morpheus-cli` that takes one domain value and
      returns a map or an object built from it, grouped by domain type, with the keys each copy writes. Proof: the
      table; on 2026-10-13 at `8b26d3d7`, nineteen declarations for eight shared types, all copies of a type equal.
- [ ] 1.2 Search the three adapters for the same shapes built inline (a lambda, a stream) rather than in a named
      method. Proof: the list; these are the sites the members rule will not see.
- [ ] 1.3 For each shared type, name the HTTP route and the MCP tool (and the CLI command, if any) that show it, and the
      envelope each puts around it. Proof: the inventory that becomes the list of decision 4.
- [ ] 1.4 Read both coverage scales and the presence ratchets from the last validation summary. Proof: the figures and
      their source.

## 2. Reproduce before fixing

- [ ] 2.1 The convergence test on `openspec-basic` through the memory store, over the inventory of 1.3. Proof: its
      result on `HEAD` - green if the transports agree today, which is what the reading predicts; any red is a finding
      recorded before the move.
- [ ] 2.2 Prove the convergence test bites: add a key to one of the four `requirement` copies on a scratch branch.
      Proof: the failure naming the type, the key and the transports. This is the defect the audit describes, shown.
- [ ] 2.3 The ArchUnit members rule. Intent: *no method of a transport adapter takes a shared domain type and returns
      its payload.* Proof: red on `HEAD`, naming the methods of 1.1; the rule retains classes.

## 3. The change

- [ ] 3.1 The projections in `morpheus-application`, one per shared type, written with the payload builder. Proof: a
      unit test per shape asserting keys and values on a fixture value.
- [ ] 3.2 Replace the adapter methods, one adapter per commit. Proof: 2.3 passes; the adapter suites pass with no
      expected payload edited; 2.1 stays green.
- [ ] 3.3 The inline sites of 1.2 call the shared projection. Proof: 2.1 green; the list of 1.2 empty.
- [ ] 3.4 Break the members rule once, then revert: a private `requirement(Requirement)` in a class of `morpheus-mcp`
      under another name. Proof: the named failure.
- [ ] 3.5 The ADR, from `docs/architecture/adr/template.md`, with its line in the index (`AdrIndexCoherenceTest`).
      Proof: the file and the index line.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning;
      `PublicHttpRouteConvergenceTest` and `PublicSurfaceManifestCoversEveryServedToolTest` unmodified.
- [ ] 4.2 Both coverage scales and the changed-line gate on Linux and Windows. Proof: the figures next to 1.4; tests are
      written if a ratio falls, no ratchet is lowered.
- [ ] 4.3 `.claude/rules/governance.md`: the payload comparison added to "what the gates really compare", with what it
      does not cover (envelopes, single-transport types). `.claude/skills/public-capability`: one step naming the
      shared-type list. Proof: the diff.
- [ ] 4.4 Pull request description: the finding, the tables of 1.1 and 1.3, the result of 2.1 on `HEAD`, the
      justification of the two architecture tests.

# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Decide and measure

- [ ] 1.1 Maintainer: confirm the deletion of the six `snapshot*` methods of `BusinessContentQueryService`, or name the
      surface they were meant for. Default: delete. Proof: the answer, in the pull request.
- [ ] 1.2 Run the bytecode rule in report-only form and record what it finds beyond the sixteen (record accessors,
      entry points, methods called only from another module's tests). Proof: the list, classified; it decides the scope
      and the excused entries of decision 4.
- [ ] 1.3 Re-run the name scan of the audit. Proof: on 2026-10-12 it found sixteen in 1 202 files; re-measure.

## 2. Reproduce before fixing

- [ ] 2.1 The gate, scoped as 1.2 decided. Proof: red on `HEAD`, naming the sixteen.
- [ ] 2.2 Textual assertions for the two security paths: no `followSymbolicLinks` / `FOLLOW_LINKS` in the two `sync`
      classes; no `orElse(jar)` in the gateways. Proof: red on `HEAD`.

## 3. The change

- [ ] 3.1 Remove the flag from `SourceScanPolicy` and its constructors, and the four conditionals of the scanner; adjust
      the callers. Proof: the scan tests pass unmodified apart from constructor calls.
- [ ] 3.2 `orElseThrow()` in both gateways. Proof: the unpinned-execution tests pass unmodified.
- [ ] 3.3 Delete the sixteen methods and what only they used. Proof: 2.1 passes; `./mvnw dependency:analyze` reports no
      warning (a deleted method can orphan a dependency).
- [ ] 3.4 Break each rule once, then revert: an uncalled public method in an application service; `orElse(jar)`
      restored. Proof: two named failures.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`. The coverage ratios may rise; no ratchet or ceiling is edited here
      (`coverage-ratchet` is a separate decision). Proof: green, both gates.
- [ ] 4.2 Parity suites and M19 are untouched by construction; the gate of M19 runs in the nightly lane.
- [ ] 4.3 Pull request description: both findings, the measured lists, decision 4 and its scope.

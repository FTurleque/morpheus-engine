# Tasks

Implemented on 2026-10-12 by pull request 455.

## 1. Decide and measure

- [ ] 1.1 Maintainer: confirm the deletion of the six `snapshot*` methods of `BusinessContentQueryService`, or name the
      surface they were meant for. Default: delete. Proof: the answer, in the pull request.
      *The default was applied: the six are deleted. The maintainer's answer is still asked for in the pull request; if
      they were meant for a surface, they come back with their consumer and their manifest line.*
- [x] 1.2 Run the bytecode rule in report-only form and record what it finds beyond the sixteen (record accessors,
      entry points, methods called only from another module's tests). Proof: the list, classified; it decides the scope
      and the excused entries of decision 4.
      *It reported 96 methods, most of them called only by the tests of their own module, which the architecture module
      does not have on its classpath. The count decided the scope; the 96 were not classified one by one.*
- [x] 1.3 Re-run the name scan of the audit. Proof: on 2026-10-12 it found sixteen in 1 202 files; re-measure.
      *Sixteen before the change, none after.*

## 2. Reproduce before fixing

- [x] 2.1 The gate, scoped as 1.2 decided. Proof: red on `HEAD`, naming the sixteen.
      *`UnreferencedPublicMethodArchitectureTest`. **A departure from the specification**: it is the name scan of the
      audit, not a bytecode rule, for the reason of 1.2. Its limit is the opposite one — a dead method that shares its
      name with a live one is not seen — and the test's documentation records the attempt, the number and that limit.*
- [x] 2.2 Textual assertions for the two security paths: no `followSymbolicLinks` / `FOLLOW_LINKS` in the two `sync`
      classes; no `orElse(jar)` in the gateways. Proof: red on `HEAD`.
      *`UnreachableSecurityBranchArchitectureTest`. The traversal option is forbidden by its full name,
      `FileVisitOption.FOLLOW_LINKS`: the bare word also matched `NOFOLLOW_LINKS`, which the scanner must keep.*

## 3. The change

- [x] 3.1 Remove the flag from `SourceScanPolicy` and its constructors, and the four conditionals of the scanner; adjust
      the callers. Proof: the scan tests pass unmodified apart from constructor calls.
      *`SourceScanPolicySymlinkTest`, which asserted the constructor's refusal of the flag, goes with the parameter it
      tested.*
- [x] 3.2 `orElseThrow()` in both gateways. Proof: the unpinned-execution tests pass unmodified.
      *The changed-line gate found that no test executed the line: the only test reaching a gateway through a pin
      substituted the JAR and stopped at the integrity check. One test per gateway now launches a pinned JAR that still
      matches its pin.*
- [x] 3.3 Delete the sixteen methods and what only they used. Proof: 2.1 passes; `./mvnw dependency:analyze` reports no
      warning (a deleted method can orphan a dependency).
- [x] 3.4 Break each rule once, then revert: an uncalled public method in an application service; `orElse(jar)`
      restored. Proof: two named failures.
      *An unused public method added to `QualityFactValue` is refused by name; `orElse(jar)` restored in
      `MinosMcpCodeGateway` fails `anMcpPeerIsLaunchedFromItsVerifiedCopyOrNotAtAll`, naming the file.*

## 4. Verification

- [x] 4.1 `./mvnw clean verify`. The coverage ratios may rise; no ratchet or ceiling is edited here
      (`coverage-ratchet` is a separate decision). Proof: green, both gates.
      *By the required checks of the pull request on both platforms. Locally: the architecture module and
      `dependency:analyze`.*
- [x] 4.2 Parity suites and M19 are untouched by construction; the gate of M19 runs in the nightly lane.
- [x] 4.3 Pull request description: both findings, the measured lists, decision 4 and its scope.

## After the reviews of 2026-10-12 and 2026-10-13

- **Seventeen methods, not sixteen.** The scan counted a mention in a comment or a string as a reference, and let two
  overloads vouch for each other. Corrected, it found `MinosIntegrationRuntime.disabled()`, removed with the rest. The
  specification's "sixteen" is the audit's count.
- The scan no longer judges a process entry point (`main`), which passed only because tests held local variables of
  that name. Its limit is stated as wide as it is: an occurrence is any identifier of that spelling.
- Three package-private wrappers this sprint left behind, called by tests only, are removed:
  `MorpheusRemoteHttpServer.usesBoundedUpstreamTimeout(String, String)` and its policy counterpart,
  `usesPrivilegedConcurrency(String, String)`, and `MorpheusRemoteRoutePolicy.requiredRole`. The scan could not see
  them: it judges public methods.
- The textual rules read every source of the `sync` package and every spelling of the fallback
  (`staged.orElse(`, `staged.orElseGet(`), not two files and one variable name.
- The scanner's refusal said a symbolic root "is not followed by the active scan policy"; there is no such setting any
  more, and the message says "is not followed".

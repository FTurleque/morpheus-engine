# Tasks

Implementation is a separate lot, started after this specification is reviewed and after the sprint 11 decision
(`decide-what-technology-the-application-core-may-touch`) is accepted naming the resolver, or replaced by a consolidated ADR that names it. Nothing below is
done by the pull request that introduces this change.

## 1. Measure before changing

- [ ] 1.1 Diff the two settings files after replacing the peer's name, and the two pairs of test classes the same way.
      Proof: the diffs; on 2026-10-13 at `8b26d3d7`, 233 and 221 lines, eleven hunks, one of them a message. Re-measure:
      the files grew by a third between the audit and this reading.
- [ ] 1.2 List every reader of the two settings types and of their public constants, in product code, tests,
      architecture tests, validators and documentation. Proof: the list; it bounds what may not change.
- [ ] 1.3 List the textual assertions that name either settings file (`PeerLauncherArchitectureTest`, any D2, M21 or
      M28 suite). Proof: the list, each with the sentence it enforces - these are retargeted in 3.4, none dropped.
- [ ] 1.4 Read the state of the sprint 11 decision: is file-system access in `morpheus-application` listed by class,
      and what does it say of `ExternalJarIntegrity`? Proof: the paragraph; it decides the home (design, decision 1).
- [ ] 1.5 Read the presence ratchets, the execution counts of the four twin test classes and both coverage scales from
      the last validation summary. Proof: the figures and their source (`.claude/skills/live-numbers`).

## 2. Reproduce before fixing

- [ ] 2.1 The parity test in `morpheus-architecture-tests`, over a table of configurations. Proof: its result on
      `HEAD` - one difference expected, the NEXUS "runner JAR" message; anything else is a drift found before the
      merge.
- [ ] 2.2 The ArchUnit dependency rule. Intent: *the settings types of the integrations do not depend on the file
      system API nor on the pin verification.* Proof: red on `HEAD`, naming both types; the rule retains classes.
- [ ] 2.3 Prove the parity test bites: change one bound on one side on a scratch branch. Proof: the failure naming the
      input and the peer.

## 3. The change

- [ ] 3.1 The resolver, in the home decided by 1.4, with the peer as a three-part parameter. Proof: a parameterised
      suite over the two peers covering every row of 2.1, with an assertion on each refusal's wording.
- [ ] 3.2 The two settings types become thin records over the shared result. Proof: 2.2 passes; the four existing
      suites and `MinosUnpinnedExecutionTest` pass unmodified.
- [ ] 3.3 The message of decision 4, if accepted. Proof: 2.1 green with no listed exception; the status tests updated
      in the same commit.
- [ ] 3.4 Retarget `PeerLauncherArchitectureTest`: its assertions on the resolver, and the absence of a bare launcher
      literal in the two settings files. Proof: the test before and after, side by side in the pull request - same
      sentences, one more.
- [ ] 3.5 Break each rule once, then revert: a `Files` check in one settings type; a bare `java` fallback in the
      resolver; a bound changed on one side. Proof: the three named failures.
- [ ] 3.6 Clarify ADR-0128 (the settings resolution is shared, and where it lives); add the resolver to the sprint 11 list if 1.4 requires it. Proof: the diffs;
      `AdrIndexCoherenceTest` green.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify` and `./mvnw dependency:analyze`. Proof: green, no warning; the execution counts of 1.5
      not lower.
- [ ] 4.2 Both coverage scales and the changed-line gate on Linux and Windows - the launcher resolution has
      platform-specific branches (`java.exe`, symbolic links). Proof: the figures next to 1.5; tests are written if a
      ratio falls, no ratchet is lowered.
- [ ] 4.3 The real-peer compatibility scripts under `distribution/`, on both platforms. Proof: MINOS and NEXUS start
      with the default launcher and with a configured one, as before.
- [ ] 4.4 `.claude/rules/security.md`, section on external MCP processes: the launcher invariant named on the resolver
      instead of on the two constructors. `SECURITY.md` unchanged unless 3.3 changed a message it quotes. Proof: the
      diff.
- [ ] 4.5 Pull request description: the finding, the diffs of 1.1, the result of 2.1 on `HEAD`, the home chosen and
      why, the retargeted test, the justification of the architecture tests.

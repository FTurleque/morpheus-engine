# Tasks

Implemented on 2026-10-12 by pull request 456.

## 1. Measure before changing

- [x] 1.1 List where `javaCommand` is read, printed and tested in both modules and in the status views. Proof: the list.
      *Read by the launch of each gateway (`MinosMcpCodeGateway`, `NexusMcpContextGateway`); written into the status
      details by `MinosIntegrationRuntime` and `NexusMcpTechnicalContextProvider`; projected for an HTTP caller by
      `IntegrationStatusDisclosure`, where `javaCommand` is a location key reported as configured, never named.*
- [x] 1.2 Start MINOS and NEXUS with the packaged runtime's `bin/java` (the compatibility scripts under `distribution/`
      exist for this). Proof: both start, or the missing module - which decides whether the default can ship.
      *2026-10-13: done on the Windows portable image, with the real peers built on the maintainer's machine. **NEXUS 0.2.0** (runner compiled for Java 21) starts on the embedded runtime: `AVAILABLE`, smoke PASS. **MINOS 1.3.0-SNAPSHOT** is compiled for Java 24 and cannot run on the embedded Java 21 (`UnsupportedClassVersionError`, not a missing module); with `MORPHEUS_MINOS_JAVA` naming a JDK 24 it is `AVAILABLE`, smoke PASS. The default therefore ships, and the guide and the release note say what to do when a peer needs another Java. Two defects stood in the way and are fixed with this entry: the compatibility scripts passed a bare `java` and no pin, both of which 1.2.1 refuses; and the transport closed on a peer warning that was not UTF-8 (see the last section).*

## 2. Reproduce before fixing

- [x] 2.1 Settings tests, both modules: default under `java.home`; bare name, relative path, missing file and directory
      refused with `assertThrows` or the settings' error and an assertion on the reason; link resolved (Linux only,
      conditioned on the platform). Proof: red on `HEAD`.
      *`MinosPeerLauncherResolutionTest` and `NexusPeerLauncherResolutionTest`, committed red before the change with seven methods each, eleven since the review of 2026-10-12. The link test runs on Linux and macOS; on Windows it is skipped by its platform condition, and the suffix test runs there only.*
- [x] 2.2 A test that a `PATH` naming another directory first does not change the resolved launcher. Proof: red on
      `HEAD` in the sense that the launcher is the bare name.

## 3. The change

- [x] 3.1 Both settings classes: default and validation. No helper shared across the two modules. Proof: 2.1 and 2.2
      pass; the real-process tests of `BoundedStdioClientTransport` pass unmodified.
      *Both integration modules in full, their real-process transport tests included.*
- [x] 3.2 Status views: the reported launcher is the resolved path; remote allowlist untouched. Proof: the existing
      disclosure tests.
      *The local views report the resolved path. Over HTTP the launcher was already reported as configured rather
      than named (see 1.1), which a default that is now a full server path makes necessary rather than cautious.*
- [x] 3.3 `docs/user/INTEGRATIONS.md`: the two variables take an absolute path; without them the peer runs on MORPHEUS's
      runtime. Release note line. Proof: the documentation gates.

## 4. Verification

- [x] 4.1 `./mvnw clean verify`. Proof: green on both platforms in CI.
      *By the required checks of the pull request; not run locally.*
- [x] 4.2 Pull request description: the finding, decision 3 (links resolved, not refused) and why it departs from the
      audit's wording, the result of 1.2.
      *The result of 1.2 is stated as what it is: not obtained.*

## Departures from the specification, declared after the reviews of 2026-10-12 and 2026-10-13

- **A launcher written without its `.exe` is accepted** when the file named does not exist and its `.exe` sibling
  does. The specification asked for "an existing regular, executable file". On Windows the operating system used to
  add the suffix, so a value that worked on 1.2.0 would have been refused. The file launched and reported is the
  `.exe`.
- **A runtime that carries no launcher is named** when a peer is configured without one: `... Java command is not
  configured and the MORPHEUS runtime carries no launcher`. The specification did not foresee the case; the default
  was returned unchecked and failed only at the first call.
- **The invariant is in the compact constructor** of both settings records, and `PeerLauncherArchitectureTest` holds
  the two classes to it. The specification placed the validation in the resolution only.
- **Every refused setting is reported**, joined by `; `, where the last one overwrote the others.
- **A leftover `MORPHEUS_MINOS_JAVA=java` without a JAR makes the integration `INVALID`** where it was `DISABLED`.
  Kept: every other refused setting already does so without a JAR (an invalid timeout, a pin without a JAR). The
  release note says so.
- **Not changed, noticed**: when a configured launcher is refused, the local status shows the runtime's launcher in
  `javaCommand` beside the refusal; and over HTTP a joined message that names a path is replaced whole by the
  "not reachable" wording although the state is `INVALID`. Both are fail-safe and are left as they are.

## Found while doing 1.2, outside this specification

- `BoundedStdioClientTransport` read a peer's standard error as strict UTF-8 and failed closed on the first byte
  that was not. NEXUS warns in French about the access rights of its home directory, in the Windows code page: the
  peer answered `initialize` correctly and MORPHEUS reported it unreachable after the whole timeout. Present before
  this sprint. Standard error is now read as a diagnostic (`BoundedStdioLineReader.diagnostics`): same bound,
  replacement instead of refusal. The protocol frames stay strict.
- `distribution/test-minos-compatibility.ps1` and `test-nexus-compatibility.ps1` could not pass: they set the
  launcher variable to `java` by default, did not pin the JAR, and the NEXUS one pointed at an image directory that
  no longer exists. They now leave the variable unset unless a launcher is given, pin the JAR they are handed, and
  point at the current image.

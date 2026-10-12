# Tasks

Implemented on 2026-10-12 by pull request 456.

## 1. Measure before changing

- [x] 1.1 List where `javaCommand` is read, printed and tested in both modules and in the status views. Proof: the list.
      *Read by the launch of each gateway (`MinosMcpCodeGateway`, `NexusMcpContextGateway`); written into the status
      details by `MinosIntegrationRuntime` and `NexusMcpTechnicalContextProvider`; projected for an HTTP caller by
      `IntegrationStatusDisclosure`, where `javaCommand` is a location key reported as configured, never named.*
- [ ] 1.2 Start MINOS and NEXUS with the packaged runtime's `bin/java` (the compatibility scripts under `distribution/`
      exist for this). Proof: both start, or the missing module - which decides whether the default can ship.
      ***Not done: neither peer's JAR was available.** This is the risk of the change. On a packaged installation the
      default launcher is now the embedded runtime, a reduced one; a peer that needs a module it does not carry fails
      to start until `MORPHEUS_MINOS_JAVA` / `MORPHEUS_NEXUS_JAVA` names a full JDK. To be run by the maintainer with
      `distribution/test-minos-compatibility.ps1` and `test-nexus-compatibility.ps1` before the release.*

## 2. Reproduce before fixing

- [x] 2.1 Settings tests, both modules: default under `java.home`; bare name, relative path, missing file and directory
      refused with `assertThrows` or the settings' error and an assertion on the reason; link resolved (Linux only,
      conditioned on the platform). Proof: red on `HEAD`.
      *`MinosPeerLauncherResolutionTest` and `NexusPeerLauncherResolutionTest`, seven methods each, committed red
      before the change. The link test runs on Linux and macOS; on Windows it is skipped by its platform condition.*
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

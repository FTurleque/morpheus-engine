# Tasks

Implementation is a separate lot, started after this specification is reviewed. Nothing below is done by the pull request
that introduces this change.

## 1. Measure before changing

- [ ] 1.1 List where `javaCommand` is read, printed and tested in both modules and in the status views. Proof: the list.
- [ ] 1.2 Start MINOS and NEXUS with the packaged runtime's `bin/java` (the compatibility scripts under `distribution/`
      exist for this). Proof: both start, or the missing module - which decides whether the default can ship.

## 2. Reproduce before fixing

- [ ] 2.1 Settings tests, both modules: default under `java.home`; bare name, relative path, missing file and directory
      refused with `assertThrows` or the settings' error and an assertion on the reason; link resolved (Linux only,
      conditioned on the platform). Proof: red on `HEAD`.
- [ ] 2.2 A test that a `PATH` naming another directory first does not change the resolved launcher. Proof: red on
      `HEAD` in the sense that the launcher is the bare name.

## 3. The change

- [ ] 3.1 Both settings classes: default and validation. No helper shared across the two modules. Proof: 2.1 and 2.2
      pass; the real-process tests of `BoundedStdioClientTransport` pass unmodified.
- [ ] 3.2 Status views: the reported launcher is the resolved path; remote allowlist untouched. Proof: the existing
      disclosure tests.
- [ ] 3.3 `docs/user/INTEGRATIONS.md`: the two variables take an absolute path; without them the peer runs on MORPHEUS's
      runtime. Release note line. Proof: the documentation gates.

## 4. Verification

- [ ] 4.1 `./mvnw clean verify`. Proof: green on both platforms in CI.
- [ ] 4.2 Pull request description: the finding, decision 3 (links resolved, not refused) and why it departs from the
      audit's wording, the result of 1.2.

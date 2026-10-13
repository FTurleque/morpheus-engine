# Design

## Context

- The two settings classes are near copies (AUD-QUA-09, sprint 9, will merge them); this change edits both the same way
  and adds no shared helper across the two integration modules, which may not depend on each other.
- `java.home` names the runtime of the current JVM; in a packaged distribution it is the embedded runtime, which carries
  `bin/java` (the packaging proves it for the plugin probe).
- On Linux, `/usr/bin/java` is commonly a chain of symbolic links.
- The status views expose `javaCommand`; the remote projection treats path-bearing details through
  `ServerLocationDisclosure` and an allowlist.

## Goals / Non-Goals

**Goals**: no `PATH` lookup for the peer's launcher; a configured launcher is a path the operator named in full.

**Non-Goals**: removing `PATH` from the peer's environment (the peer is trusted code and may start tools of its own);
verifying the launcher's signature or version; sandboxing the peer.

## Decisions

1. **Default: `<java.home>/bin/java` (`java.exe` on Windows), resolved when the settings are.** Same choice as the
   plugin probe. *Rejected*: keeping `java` and documenting the risk.

2. **A configured value must be absolute and name an existing regular file.** A bare name or a relative path is refused
   at resolution, as a configuration error naming the variable and saying "must be an absolute path".

3. **A symbolic link is resolved and the real path is what is launched and reported; it is not refused.** The audit
   asks for "non symbolic". Refusing links would reject the most common Linux configuration, and the repository already
   decided, for the plugin directory, to resolve and declare rather than refuse a link an operator did not create. What
   is refused is ambiguity about what will run: the launch uses the resolved path. *Rejected*: refusing links.

4. **Executable bit**: checked where the platform reports it (`Files.isExecutable`); a file that is not executable is a
   configuration error rather than a failure at first use.

5. **Disclosure unchanged.** The launcher path is a server location like the JAR path: local views show it, the remote
   projection does not gain a key.

6. **No ADR.** Hardening inside ADR-0106's boundary (peer launch environment and lifecycle).

## Risks / Trade-offs

- An existing configuration with `MORPHEUS_MINOS_JAVA=java` stops working until it is given a full path or removed.
  Removing it now gives the safe default, which is the release-note sentence.
- The peer runs on MORPHEUS's own runtime by default. The embedded runtime is a reduced one (three added modules); a
  peer that needs more modules must be given a full JDK through the variable. Task 1.2 checks MINOS and NEXUS against
  the packaged runtime before this default ships.

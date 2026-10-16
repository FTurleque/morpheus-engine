# Proposal

## Why

AUD-DEP-15 (sprint 2, Low, no dependency) reports that `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` is read by the remote
server and documented nowhere. Re-read on `develop` at `9277a083` on 2026-10-11, the finding **holds exactly**:

- `RemoteApiLaunchOptions.java:143-147` resolves the provider-plugin directory from, in order, `--provider-plugin-dir`,
  the environment variable `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR`, the JVM property
  `morpheus.server.providerPluginDirectory`, then `<config>/provider-plugins`.
- `docs/user/TEAM_REMOTE_SERVER.md:200` documents the option; `:206-210` lists five `MORPHEUS_SERVER_*` variables and not
  this one; `:380` says "le serveur utilise uniquement son `--provider-plugin-dir`", which the code contradicts.
  `git grep MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` finds the name in the source and in the audit only; the JVM property is
  absent from `docs/` as well.
- Measured over `*/src/main/java`: 24 distinct string literals of the form `MORPHEUS_[A-Z0-9_]+`. Two are refusal codes,
  not variables (`MORPHEUS_VERSION_TOO_NEW`, `MORPHEUS_VERSION_TOO_OLD`). Of the 22 environment variables, 21 are named
  in `docs/user` or `docs/developer`, and one is not: this one.

That directory is where the remote server looks for provider plugins — third-party code run under the MORPHEUS account
once its SHA-256 pin is verified. An operator who hardens a deployment from the documentation does not know the
environment can impose it. One undocumented variable out of twenty-two is an omission; nothing in the repository would
have caught it, and nothing would catch the next.

## What Changes

- `MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR` joins the environment table of the remote server guide, with its precedence
  against the option, the JVM property and the default, and the sentence at `:380` is corrected.
- A test derives the set of `MORPHEUS_*` names the main sources mention and requires each to be documented for
  operators, the names that are not environment variables being excused by name with a reason.

## Capabilities

### New Capabilities

- `launch-environment-disclosure`: every environment variable the product reads is documented where an operator looks,
  and the build refuses a variable that is not.

### Modified Capabilities

(none)

## Impact

- `docs/user/TEAM_REMOTE_SERVER.md` (table and one sentence), `docs/user/PROVIDER_PLUGINS.md` (a pointer).
- One test in `morpheus-architecture-tests`.
- No CLI command, MCP tool or HTTP route moves; no code under `src/main/java` changes. The remote disclosure boundary is
  untouched: the variable sets a server-side path, and that path was already kept out of remote responses.
- `plan-action.md` groups this finding with the security hygiene lot of sprint 3 (C20); `sprints.md` places it in sprint
  2. It depends on nothing and nothing depends on it, so it can ship with either.

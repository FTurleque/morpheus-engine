# Proposal

## Why

AUD-QUA-07 (sprint 9) reports ten private copies of a varargs `Map` builder in the HTTP and MCP adapters, under two
names, with an unchecked `(String)` cast of the key. Re-read on `develop` at `8b26d3d7` on 2026-10-13, the finding
**holds, with the same count and one more divergence than reported**:

- Ten declarations of a private method taking `Object...` and returning `Map<String, Object>` under `src/main`: seven
  in `morpheus-api` (`MorpheusApiService.java:203`, `MorpheusChangeQueryApiService.java:204`,
  `MorpheusDiagnosticsApiService.java:138`, `MorpheusHistoryApiService.java:144`,
  `MorpheusProjectSyncApiService.java:151`, `MorpheusRequirementQueryApiService.java:107`,
  `MorpheusSpecificationQueryApiService.java:152`) and three in `morpheus-mcp` (`MorpheusMcpToolService.java:398`,
  `MorpheusCompositionMcpTools.java:97`, and `MorpheusMcpToolCatalog.java:106` under the name `props`). No other module
  declares one. The line numbers moved since the audit; the set did not.
- **Four bodies, three behaviours**, which the audit did not separate:

  | Copies | Odd number of arguments | Absent value | Result |
  |---|---|---|---|
  | 7 (`morpheus-api`) | refused, `map entries must be key/value pairs` | admitted | unmodifiable view, insertion order |
  | 1 (`MorpheusMcpToolService`) | refused, same message | refused, `map value` | `Map.copyOf` |
  | 1 (`MorpheusMcpToolCatalog.props`) | refused, `property entries must be name/schema pairs` | refused by `Map.copyOf`, unnamed | `Map.copyOf` |
  | 1 (`MorpheusCompositionMcpTools`) | **not checked**: an index failure | refused, `map value` | `Map.copyOf` |

- All ten cast the key with `(String) entries[index]`: a key of another type is a `ClassCastException`, not a named
  refusal. All ten call `put`, so a key given twice **silently keeps the last value** - which
  `.claude/rules/code-style.md` forbids in so many words.
- The absent value is a real difference, not a style: `MorpheusHistoryApiService.java:81` passes `orElse(null)` for a
  missing side of a difference, and the HTTP copies carry it; the same call through an MCP copy would fail.

Both adapters hand the result to `CanonicalJsonSerializer`, which orders keys and refuses a non-string key of its own.
So the order of a built map is not observable today, and the cast failure is the only one a caller can meet before the
serializer's named one.

## What Changes

- One builder constructs a string-keyed payload map from alternating keys and values, for every module that needs one.
- It refuses by name an odd argument count, a key that is not a string and a key given twice.
- Whether an absent value is admitted is said by the caller, at the call, not by which copy was pasted.
- A test refuses a second builder anywhere in the product's sources.

## Capabilities

### New Capabilities

- `payload-map-construction`: how a public payload map is built, and what a malformed construction answers.

### Modified Capabilities

(none)

## Impact

- **Production code**: the ten sites and their callers in `morpheus-api` and `morpheus-mcp` (some sixty calls counted on
  2026-10-13); one new type in `morpheus-application`.
- **Where it lives.** `api` and `mcp` are siblings (`LayerDependencyTest#httpApiMustRemainSiblingOfCliAndMcp`) and both
  depend on `morpheus-application`, which is where `CanonicalJsonSerializer` - the consumer of these maps - already
  lives. `morpheus-domain` is the model, not a payload vocabulary. A new module is not justified by one type.
- No public payload changes: no contract, no OpenAPI, no manifest line. One internal failure changes its type (a named
  refusal instead of a class cast or an index failure).
- One architecture rule (ArchUnit) with a textual twin; `architectureTestsMinimum` rises.
- No ADR of its own. The home package is a leaf of `morpheus-application`; the sprint 11 decision on the purity of the
  application core (AUD-ARC-01, AUD-ARC-03) is not constrained by it and should name it among the pure leaves.
- Coverage: nine well-covered private methods disappear from two modules. A ratio can move in either direction when
  covered lines are removed; both scales are read before and after (`.claude/skills/coverage-ratchet`), and a ratchet
  is never lowered to absorb it.
- **Unblocks** `project-shared-domain-types-to-one-public-shape` (AUD-ARC-10), whose projections are written with this
  builder. The audit also orders it before AUD-QUA-09; no code link was found (neither integration module declares a
  varargs builder), so that order is not a dependency.

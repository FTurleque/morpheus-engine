# public-surface-convergence Specification

## Purpose
Guarantee that every public HTTP route of MORPHEUS is served, declared in the convergence manifest and documented
in OpenAPI consistently, or explicitly excluded from one of them with a written reason.

## Requirements

### Requirement: Served HTTP routes are documented

Every HTTP route registered by the local or remote server SHALL appear, with the same method and path, in an
OpenAPI document of `docs/openapi/`, unless it is listed as an explicit exclusion with a reason.

#### Scenario: The provider-plugin discovery route

- **WHEN** the remote server registers `GET /api/v1/provider-plugins/discover`
- **THEN** an OpenAPI document describes that method and path

#### Scenario: An undocumented route is refused

- **WHEN** a server registers a route that no OpenAPI document describes and no exclusion names
- **THEN** the build fails and names the route

### Requirement: Manifest, OpenAPI and code agree in both directions

The `http` column of the convergence manifest SHALL only name routes that a server registers and that an OpenAPI
document describes, and a documented route that a server does not register MUST fail the build.

#### Scenario: A manifest route that nothing serves

- **WHEN** the manifest names an HTTP route that no server registers
- **THEN** the build fails and names the manifest line

#### Scenario: Path parameter names differ

- **WHEN** the manifest writes `{id}` where the OpenAPI document writes `{savedViewId}` for the same route
- **THEN** the comparison treats them as the same route after normalising parameter names

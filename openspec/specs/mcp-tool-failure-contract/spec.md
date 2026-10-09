# mcp-tool-failure-contract Specification

## Purpose
Define what an MCP client receives when a MORPHEUS tool cannot complete, and the bound on how long a tool call can
last before MORPHEUS answers it.

## Requirements

### Requirement: Tool failures are tool results

A MORPHEUS MCP tool SHALL answer a failure it can reach with a tool result marked `isError` and carrying a message
that names no server path, and MUST NOT turn such a failure into a JSON-RPC protocol error.

#### Scenario: The database is reserved by an offline maintenance

- **WHEN** a tool that reads the knowledge store is called while an offline restore holds the database exclusively
- **THEN** the client receives a tool result with `isError: true` and a message stating the store is unavailable

#### Scenario: The database cannot be opened

- **WHEN** a tool that reads the knowledge store is called and the SQLite driver fails to open the database
- **THEN** the client receives a tool result with `isError: true` whose text contains no exception chain and no
  absolute path

### Requirement: A peer operation within its configured bound never closes the session

A tool call that delegates to an external MCP peer SHALL end with the peer's own bounded error, or with its result,
when every request to the peer stays within the configured peer timeout; the handler safety bound MUST be larger
than the longest such operation.

#### Scenario: A slow peer at its maximum timeout

- **WHEN** a NEXUS or MINOS operation is called with the maximum configurable timeout and the peer answers each
  request just within that timeout
- **THEN** the client receives the operation's result or bounded error and the MCP session stays open

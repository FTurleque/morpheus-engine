package com.morpheus.integration.mcp;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * What MORPHEUS launches as an MCP peer: a command, its arguments, and the environment variables MORPHEUS itself
 * configured for that peer -- nothing a third-party parameter object may have filled in by default. The transport
 * adds only its launch allowlist to {@code explicitEnvironment}.
 */
public record McpPeerLaunch(String command, List<String> arguments, Map<String, String> explicitEnvironment) {
    public McpPeerLaunch {
        Objects.requireNonNull(command, "command");
        if (command.isBlank()) throw new IllegalArgumentException("command must not be blank");
        arguments = List.copyOf(Objects.requireNonNull(arguments, "arguments"));
        explicitEnvironment = Map.copyOf(Objects.requireNonNull(explicitEnvironment, "explicitEnvironment"));
    }
}

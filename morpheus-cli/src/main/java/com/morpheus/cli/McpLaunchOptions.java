package com.morpheus.cli;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Parses the small native MCP launcher surface without writing to protocol stdout. */
record McpLaunchOptions(CliLayout layout) {

    static boolean isMcpCommand(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index++;
                continue;
            }
            if (token.startsWith("--data-dir=") || token.startsWith("--config-dir=") || token.startsWith("--db=")) {
                continue;
            }
            return token.equals("mcp");
        }
        return false;
    }

    static McpLaunchOptions parse(
            String[] args,
            Map<String, String> environment,
            Properties properties) {
        Optional<Path> data = Optional.empty();
        Optional<Path> config = Optional.empty();
        Optional<Path> database = Optional.empty();
        boolean commandSeen = false;
        boolean stdio = false;
        List<String> unknown = new ArrayList<>();

        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("mcp")) {
                if (commandSeen) {
                    throw new IllegalArgumentException("mcp command must appear exactly once");
                }
                commandSeen = true;
                continue;
            }
            if (token.equals("--stdio")) {
                stdio = true;
                continue;
            }
            if (token.equals("--json")) {
                throw new IllegalArgumentException("--json is not valid for MCP transport mode");
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(token + " requires a path");
                }
                Path value = Path.of(args[++index]);
                switch (token) {
                    case "--data-dir" -> data = Optional.of(value);
                    case "--config-dir" -> config = Optional.of(value);
                    case "--db" -> database = Optional.of(value);
                    default -> throw new IllegalStateException("unreachable global option");
                }
                continue;
            }
            if (token.startsWith("--data-dir=") || token.startsWith("--config-dir=") || token.startsWith("--db=")) {
                int separator = token.indexOf('=');
                Path value = Path.of(token.substring(separator + 1));
                String option = token.substring(0, separator);
                switch (option) {
                    case "--data-dir" -> data = Optional.of(value);
                    case "--config-dir" -> config = Optional.of(value);
                    case "--db" -> database = Optional.of(value);
                    default -> throw new IllegalStateException("unreachable global option");
                }
                continue;
            }
            unknown.add(token);
        }

        if (!commandSeen) {
            throw new IllegalArgumentException("mcp command is required");
        }
        if (!stdio) {
            throw new IllegalArgumentException("M10 supports only: mcp --stdio");
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown MCP launcher arguments: " + unknown);
        }
        return new McpLaunchOptions(CliLayout.resolve(data, config, database, environment, properties));
    }
}

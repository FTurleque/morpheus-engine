package com.morpheus.cli;

import com.morpheus.api.MorpheusHttpServer;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;

/** Parses native M11 API launcher options without coupling the API adapter back to CLI. */
record ApiLaunchOptions(CliLayout layout, String host, int port) {

    static boolean isApiCommand(String[] args) {
        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("--json")) {
                continue;
            }
            if (token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                index++;
                continue;
            }
            if (token.startsWith("--data-dir=") || token.startsWith("--config-dir=") || token.startsWith("--db=")) {
                continue;
            }
            return token.equals("api");
        }
        return false;
    }

    static ApiLaunchOptions parse(
            String[] args,
            Map<String, String> environment,
            Properties properties) {
        Optional<Path> data = Optional.empty();
        Optional<Path> config = Optional.empty();
        Optional<Path> database = Optional.empty();
        String host = MorpheusHttpServer.DEFAULT_HOST;
        int port = MorpheusHttpServer.DEFAULT_PORT;
        boolean commandSeen = false;
        List<String> unknown = new ArrayList<>();

        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("api")) {
                if (commandSeen) {
                    throw new IllegalArgumentException("api command must appear exactly once");
                }
                commandSeen = true;
                continue;
            }
            if (token.equals("--json")) {
                throw new IllegalArgumentException("--json is not valid for API server mode");
            }
            if (token.equals("--host") || token.equals("--port")
                    || token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(token + " requires a value");
                }
                String value = args[++index];
                switch (token) {
                    case "--host" -> host = requireHost(value);
                    case "--port" -> port = parsePort(value);
                    case "--data-dir" -> data = Optional.of(Path.of(value));
                    case "--config-dir" -> config = Optional.of(Path.of(value));
                    case "--db" -> database = Optional.of(Path.of(value));
                    default -> throw new IllegalStateException("unreachable API option");
                }
                continue;
            }
            if (token.startsWith("--data-dir=") || token.startsWith("--config-dir=") || token.startsWith("--db=")
                    || token.startsWith("--host=") || token.startsWith("--port=")) {
                int separator = token.indexOf('=');
                String option = token.substring(0, separator);
                String value = token.substring(separator + 1);
                switch (option) {
                    case "--host" -> host = requireHost(value);
                    case "--port" -> port = parsePort(value);
                    case "--data-dir" -> data = Optional.of(Path.of(value));
                    case "--config-dir" -> config = Optional.of(Path.of(value));
                    case "--db" -> database = Optional.of(Path.of(value));
                    default -> throw new IllegalStateException("unreachable API option");
                }
                continue;
            }
            unknown.add(token);
        }

        if (!commandSeen) {
            throw new IllegalArgumentException("api command is required");
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("unknown API launcher arguments: " + unknown);
        }
        return new ApiLaunchOptions(
                CliLayout.resolve(data, config, database, environment, properties),
                host,
                port);
    }

    private static String requireHost(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("--host must not be blank");
        }
        return value.trim();
    }

    private static int parsePort(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > 65_535) {
                throw new IllegalArgumentException("--port must be between 1 and 65535");
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--port must be an integer", failure);
        }
    }
}

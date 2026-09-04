package com.morpheus.cli;

import com.morpheus.api.MorpheusRemoteHttpServer;

import java.io.File;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Properties;
import java.util.regex.Pattern;

/** Parses only the explicit `api --remote` M26 launch path, leaving legacy local API parsing unchanged. */
record RemoteApiLaunchOptions(
        CliLayout layout,
        String host,
        int port,
        Path authFile,
        Path tlsKeyStore,
        TlsKeystorePassword tlsPassword,
        int maxConcurrentRequests,
        Path providerPluginDirectory,
        List<Path> allowedWorkspaceRoots) {

    RemoteApiLaunchOptions {
        allowedWorkspaceRoots = List.copyOf(allowedWorkspaceRoots);
    }

    static boolean isRemoteApiCommand(String[] args) {
        boolean api = false;
        boolean remote = false;
        for (String token : args) {
            if (token.equals("api")) api = true;
            if (token.equals("--remote")) remote = true;
        }
        return api && remote;
    }

    static RemoteApiLaunchOptions parse(
            String[] args,
            Map<String, String> environment,
            Properties properties) {
        Optional<Path> data = Optional.empty();
        Optional<Path> config = Optional.empty();
        Optional<Path> database = Optional.empty();
        Optional<Path> explicitAuthFile = Optional.empty();
        Optional<Path> explicitKeyStore = Optional.empty();
        Optional<Path> explicitProviderPluginDirectory = Optional.empty();
        List<Path> explicitWorkspaceRoots = new ArrayList<>();
        String host = "127.0.0.1";
        int port = 8765;
        int maxConcurrent = MorpheusRemoteHttpServer.DEFAULT_MAX_CONCURRENT_REQUESTS;
        boolean commandSeen = false;
        boolean remoteSeen = false;
        boolean maxConcurrentExplicit = false;
        List<String> unknown = new ArrayList<>();

        for (int index = 0; index < args.length; index++) {
            String token = args[index];
            if (token.equals("api")) {
                if (commandSeen) throw new IllegalArgumentException("api command must appear exactly once");
                commandSeen = true;
                continue;
            }
            if (token.equals("--remote")) {
                remoteSeen = true;
                continue;
            }
            if (token.equals("--json")) {
                throw new IllegalArgumentException("--json is not valid for API server mode");
            }
            if (takesValue(token)) {
                if (index + 1 >= args.length) throw new IllegalArgumentException(token + " requires a value");
                String value = args[++index];
                switch (token) {
                    case "--host" -> host = requireNonBlank(value, "--host");
                    case "--port" -> port = parsePort(value);
                    case "--data-dir" -> data = Optional.of(Path.of(value));
                    case "--config-dir" -> config = Optional.of(Path.of(value));
                    case "--db" -> database = Optional.of(Path.of(value));
                    case "--auth-file" -> explicitAuthFile = Optional.of(Path.of(value));
                    case "--tls-keystore" -> explicitKeyStore = Optional.of(Path.of(value));
                    case "--provider-plugin-dir" -> explicitProviderPluginDirectory = Optional.of(Path.of(value));
                    case "--workspace-root" -> explicitWorkspaceRoots.add(Path.of(value));
                    case "--max-concurrent" -> {
                        maxConcurrent = parseConcurrency(value);
                        maxConcurrentExplicit = true;
                    }
                    default -> throw new IllegalStateException("unreachable remote API option");
                }
                continue;
            }
            if (token.startsWith("--") && token.contains("=")) {
                int separator = token.indexOf('=');
                String option = token.substring(0, separator);
                String value = token.substring(separator + 1);
                if (!takesValue(option)) {
                    unknown.add(token);
                    continue;
                }
                switch (option) {
                    case "--host" -> host = requireNonBlank(value, "--host");
                    case "--port" -> port = parsePort(value);
                    case "--data-dir" -> data = Optional.of(Path.of(value));
                    case "--config-dir" -> config = Optional.of(Path.of(value));
                    case "--db" -> database = Optional.of(Path.of(value));
                    case "--auth-file" -> explicitAuthFile = Optional.of(Path.of(value));
                    case "--tls-keystore" -> explicitKeyStore = Optional.of(Path.of(value));
                    case "--provider-plugin-dir" -> explicitProviderPluginDirectory = Optional.of(Path.of(value));
                    case "--workspace-root" -> explicitWorkspaceRoots.add(Path.of(value));
                    case "--max-concurrent" -> {
                        maxConcurrent = parseConcurrency(value);
                        maxConcurrentExplicit = true;
                    }
                    default -> throw new IllegalStateException("unreachable remote API option");
                }
                continue;
            }
            unknown.add(token);
        }

        if (!commandSeen || !remoteSeen) throw new IllegalArgumentException("remote API requires `api --remote`");
        if (!unknown.isEmpty()) throw new IllegalArgumentException("unknown remote API arguments: " + unknown);

        CliLayout layout = CliLayout.resolve(data, config, database, environment, properties);
        Path authFile = explicitAuthFile
                .or(() -> envPath(environment, "MORPHEUS_SERVER_AUTH_FILE"))
                .or(() -> propertyPath(properties, "morpheus.server.auth.file"))
                .orElse(layout.configDirectory().resolve("remote-auth.txt"))
                .toAbsolutePath().normalize();
        Path keyStore = explicitKeyStore
                .or(() -> envPath(environment, "MORPHEUS_SERVER_TLS_KEYSTORE"))
                .or(() -> propertyPath(properties, "morpheus.server.tls.keystore"))
                .orElseThrow(() -> new IllegalArgumentException(
                        "remote mode requires --tls-keystore or MORPHEUS_SERVER_TLS_KEYSTORE"))
                .toAbsolutePath().normalize();
        Path providerPluginDirectory = explicitProviderPluginDirectory
                .or(() -> envPath(environment, "MORPHEUS_SERVER_PROVIDER_PLUGIN_DIR"))
                .or(() -> propertyPath(properties, "morpheus.server.providerPluginDirectory"))
                .orElse(layout.configDirectory().resolve("provider-plugins"))
                .toAbsolutePath().normalize();
        List<Path> allowedWorkspaceRoots = resolveWorkspaceRoots(
                explicitWorkspaceRoots,
                environment,
                properties);
        // The value is deliberately not captured here. Only the way back to it is: the JVM already holds the
        // environment and property strings, and a field on these options would be a second copy living as long
        // as the server does. Presence is still proven now, so a misconfigured launch fails before startup.
        TlsKeystorePassword password = new TlsKeystorePassword(
                () -> nonBlank(environment.get("MORPHEUS_SERVER_TLS_PASSWORD"))
                        .or(() -> nonBlank(properties.getProperty("morpheus.server.tls.password"))));
        if (!password.isPresent()) {
            throw new IllegalArgumentException(
                    "remote mode requires the TLS keystore password from environment or protected property");
        }
        if (!maxConcurrentExplicit) {
            Optional<String> configured = nonBlank(environment.get("MORPHEUS_SERVER_MAX_CONCURRENT"))
                    .or(() -> nonBlank(properties.getProperty("morpheus.server.maxConcurrent")));
            if (configured.isPresent()) maxConcurrent = parseConcurrency(configured.orElseThrow());
        }
        return new RemoteApiLaunchOptions(
                layout,
                host,
                port,
                authFile,
                keyStore,
                password,
                maxConcurrent,
                providerPluginDirectory,
                allowedWorkspaceRoots);
    }

    private static boolean takesValue(String token) {
        return token.equals("--host") || token.equals("--port")
                || token.equals("--data-dir") || token.equals("--config-dir") || token.equals("--db")
                || token.equals("--auth-file") || token.equals("--tls-keystore")
                || token.equals("--provider-plugin-dir") || token.equals("--workspace-root")
                || token.equals("--max-concurrent");
    }

    private static List<Path> resolveWorkspaceRoots(
            List<Path> explicit,
            Map<String, String> environment,
            Properties properties) {
        List<Path> configured = new ArrayList<>(explicit);
        if (configured.isEmpty()) {
            String raw = nonBlank(environment.get("MORPHEUS_SERVER_WORKSPACE_ROOTS"))
                    .or(() -> nonBlank(properties.getProperty("morpheus.server.workspaceRoots")))
                    .orElseThrow(() -> new IllegalArgumentException(
                            "remote mode requires at least one --workspace-root or MORPHEUS_SERVER_WORKSPACE_ROOTS"));
            for (String item : raw.split(Pattern.quote(File.pathSeparator))) {
                if (!item.isBlank()) configured.add(Path.of(item.trim()));
            }
        }
        if (configured.isEmpty()) {
            throw new IllegalArgumentException("remote workspace roots must not be empty");
        }
        return configured.stream().map(path -> path.toAbsolutePath().normalize()).distinct().toList();
    }

    private static int parsePort(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > 65_535) throw new IllegalArgumentException("--port must be between 1 and 65535");
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--port must be an integer", failure);
        }
    }

    private static int parseConcurrency(String raw) {
        try {
            int value = Integer.parseInt(raw);
            if (value < 1 || value > MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS) {
                throw new IllegalArgumentException("--max-concurrent must be between 1 and "
                        + MorpheusRemoteHttpServer.MAX_CONCURRENT_REQUESTS);
            }
            return value;
        } catch (NumberFormatException failure) {
            throw new IllegalArgumentException("--max-concurrent must be an integer", failure);
        }
    }

    private static String requireNonBlank(String value, String option) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(option + " must not be blank");
        return value.trim();
    }

    private static Optional<Path> envPath(Map<String, String> environment, String key) {
        return nonBlank(environment.get(key)).map(Path::of);
    }

    private static Optional<Path> propertyPath(Properties properties, String key) {
        return nonBlank(properties.getProperty(key)).map(Path::of);
    }

    private static Optional<String> nonBlank(String value) {
        return Optional.ofNullable(value).map(String::trim).filter(item -> !item.isEmpty());
    }
}
